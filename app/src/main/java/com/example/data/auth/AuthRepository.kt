package com.example.data.auth

import com.example.data.model.AccountStatus
import com.example.data.model.Role
import com.example.data.model.UserProfile
import com.example.data.repository.SeedUsers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object AuthConfig {
  const val UNIVERSITY_EMAIL_DOMAIN = "campus.ac.tz"

  /**
   * PROTOTYPE ONLY. There is no mail server yet, so emailed codes are shown on screen.
   * A connected build must set this to false and send the code by email instead.
   */
  const val SHOW_CODES_ON_SCREEN = true

  const val CODE_TTL_MILLIS = 10 * 60 * 1000L
  const val MAX_CODE_ATTEMPTS = 5
  const val MIN_PASSWORD_LENGTH = 8
}

enum class CodePurpose { VERIFY_EMAIL, SECOND_STEP, RESET_PASSWORD }

enum class PasswordStrength { WEAK, FAIR, STRONG }

enum class PasswordProblem { TOO_SHORT, COMMON, NEEDS_LETTER_AND_DIGIT, CONTAINS_EMAIL, REPEATED }

data class PasswordCheck(val strength: PasswordStrength, val problems: List<PasswordProblem>) {
  val acceptable: Boolean get() = problems.isEmpty()
}

enum class AuthError {
  NAME_REQUIRED,
  EMAIL_INVALID,
  EMAIL_NOT_UNIVERSITY,
  EMAIL_TAKEN,
  NUMBER_INVALID,
  NUMBER_TAKEN,
  PROGRAMME_REQUIRED,
  PASSWORD_WEAK,
  WRONG_CREDENTIALS,
  ACCOUNT_NOT_ACTIVE,
  CODE_WRONG,
  CODE_EXPIRED,
  TOO_MANY_ATTEMPTS
}

sealed interface AuthOutcome {
  /** A session was started. */
  data class Success(val userId: String) : AuthOutcome

  /** A six-digit code was issued and must be entered next. */
  data class NeedCode(val purpose: CodePurpose, val email: String) : AuthOutcome

  /** Password was changed. The person now signs in with the new one. */
  object PasswordChanged : AuthOutcome

  data class Failure(val error: AuthError) : AuthOutcome
}

data class Session(val userId: String, val isDemo: Boolean, val deviceId: String)

data class DeviceSession(val id: String, val label: String, val lastActive: String)

data class Account(
  val profile: UserProfile,
  val salt: String,
  val passwordHash: String,
  val emailVerified: Boolean,
  val trustedDevices: Set<String> = emptySet()
)

/**
 * Local stand-in for the sign-in backend.
 *
 * It follows the same rules the vision describes (university email only, emailed code, registry
 * approval, second step for staff, sessions per device) so the screens can be built and tested.
 * It keeps everything in memory and is NOT secure storage. A connected build replaces this class
 * with calls to the real auth service and keeps the same outcomes.
 */
class AuthRepository {

  val thisDeviceId: String = "dev-" + UUID.randomUUID().toString().take(8)
  private val thisDeviceLabel = "This phone (Android)"

  private val random = SecureRandom()

  private val _accounts = MutableStateFlow(seedAccounts())
  val accounts: StateFlow<Map<String, Account>> = _accounts.asStateFlow()

  private val _session = MutableStateFlow<Session?>(null)
  val session: StateFlow<Session?> = _session.asStateFlow()

  private val _devices = MutableStateFlow(
    mapOf(
      SeedUsers.asha.id to listOf(
        DeviceSession("dev-seed-1", "Tecno Spark 10 (Android)", "Yesterday, 21:40")
      )
    )
  )
  val devices: StateFlow<Map<String, List<DeviceSession>>> = _devices.asStateFlow()

  /** Code to show on screen while there is no mail server (see AuthConfig.SHOW_CODES_ON_SCREEN). */
  private val _visibleCode = MutableStateFlow<String?>(null)
  val visibleCode: StateFlow<String?> = _visibleCode.asStateFlow()

  private data class IssuedCode(
    val purpose: CodePurpose,
    val email: String,
    val code: String,
    val expiresAt: Long,
    val attempts: Int = 0
  )

  private var issued: IssuedCode? = null

  /** Wired by the data repository: (userId, title, message). */
  var onNotify: (String, String, String) -> Unit = { _, _, _ -> }

  // ---------- Password rules ----------

  private val commonPasswords = setOf(
    "password", "password1", "password123", "12345678", "123456789", "1234567890",
    "qwerty123", "qwertyuiop", "iloveyou", "admin123", "welcome1", "letmein123",
    "abc12345", "11111111", "campus123", "university", "tanzania1", "dar es salaam"
  )

  fun checkPassword(password: String, email: String = ""): PasswordCheck {
    val problems = mutableListOf<PasswordProblem>()
    if (password.length < AuthConfig.MIN_PASSWORD_LENGTH) problems += PasswordProblem.TOO_SHORT
    if (password.lowercase() in commonPasswords) problems += PasswordProblem.COMMON
    if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
      problems += PasswordProblem.NEEDS_LETTER_AND_DIGIT
    }
    val local = email.trim().substringBefore('@').lowercase()
    if (local.length >= 3 && password.lowercase().contains(local)) problems += PasswordProblem.CONTAINS_EMAIL
    if (password.isNotEmpty() && password.toSet().size <= 2) problems += PasswordProblem.REPEATED

    val strength = if (problems.isNotEmpty()) {
      PasswordStrength.WEAK
    } else {
      var score = 0
      if (password.length >= 12) score++
      if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
      if (password.any { !it.isLetterOrDigit() }) score++
      if (score >= 2) PasswordStrength.STRONG else PasswordStrength.FAIR
    }
    return PasswordCheck(strength, problems)
  }

  // ---------- Sign up ----------

  fun signUp(
    fullName: String,
    email: String,
    idNumber: String,
    isStaff: Boolean,
    programmeOrDepartment: String,
    password: String
  ): AuthOutcome {
    val cleanEmail = normalizeEmail(email)
    val cleanNumber = idNumber.trim().uppercase()

    if (fullName.trim().length < 3) return AuthOutcome.Failure(AuthError.NAME_REQUIRED)
    if (!EMAIL_REGEX.matches(cleanEmail)) return AuthOutcome.Failure(AuthError.EMAIL_INVALID)
    if (!cleanEmail.endsWith("@" + AuthConfig.UNIVERSITY_EMAIL_DOMAIN)) {
      return AuthOutcome.Failure(AuthError.EMAIL_NOT_UNIVERSITY)
    }
    val numberOk = if (isStaff) STAFF_NUMBER_REGEX.matches(cleanNumber) else STUDENT_NUMBER_REGEX.matches(cleanNumber)
    if (!numberOk) return AuthOutcome.Failure(AuthError.NUMBER_INVALID)
    if (!isStaff && programmeOrDepartment !in SeedUsers.courseCatalog.keys) {
      return AuthOutcome.Failure(AuthError.PROGRAMME_REQUIRED)
    }

    val all = _accounts.value.values
    if (all.any { it.profile.email.equals(cleanEmail, ignoreCase = true) }) {
      return AuthOutcome.Failure(AuthError.EMAIL_TAKEN)
    }
    if (all.any { (it.profile.studentNumber ?: it.profile.staffNumber) == cleanNumber }) {
      return AuthOutcome.Failure(AuthError.NUMBER_TAKEN)
    }
    if (!checkPassword(password, cleanEmail).acceptable) return AuthOutcome.Failure(AuthError.PASSWORD_WEAK)

    // The account always starts as a PENDING student. Nobody can sign up as lecturer or admin:
    // `requestedRole` is only a hint for the registry.
    val profile = UserProfile(
      id = "usr-" + UUID.randomUUID().toString().take(8),
      fullName = fullName.trim(),
      email = cleanEmail,
      role = Role.STUDENT,
      status = AccountStatus.PENDING,
      studentNumber = if (isStaff) null else cleanNumber,
      staffNumber = if (isStaff) cleanNumber else null,
      programme = programmeOrDepartment.ifBlank { "Unassigned" },
      cohort = if (isStaff) "Staff (awaiting role)" else "Awaiting registry",
      requestedRole = if (isStaff) Role.LECTURER else Role.STUDENT
    )
    val salt = UUID.randomUUID().toString()
    _accounts.value = _accounts.value + (profile.id to Account(profile, salt, hash(password, salt), emailVerified = false))

    issueCode(CodePurpose.VERIFY_EMAIL, cleanEmail)
    return AuthOutcome.NeedCode(CodePurpose.VERIFY_EMAIL, cleanEmail)
  }

  // ---------- Sign in ----------

  fun signIn(email: String, password: String): AuthOutcome {
    val account = findByEmail(email) ?: return AuthOutcome.Failure(AuthError.WRONG_CREDENTIALS)
    if (hash(password, account.salt) != account.passwordHash) {
      return AuthOutcome.Failure(AuthError.WRONG_CREDENTIALS)
    }
    val profile = account.profile
    if (profile.status == AccountStatus.REJECTED) return AuthOutcome.Failure(AuthError.ACCOUNT_NOT_ACTIVE)

    if (!account.emailVerified) {
      issueCode(CodePurpose.VERIFY_EMAIL, profile.email)
      return AuthOutcome.NeedCode(CodePurpose.VERIFY_EMAIL, profile.email)
    }

    // Stronger sign-in for people with power:
    //  - administrators confirm a second step every time,
    //  - lecturers confirm it on every new phone.
    val needsSecondStep = profile.status == AccountStatus.APPROVED && (
      profile.role == Role.ADMIN ||
        (profile.role == Role.LECTURER && thisDeviceId !in account.trustedDevices)
      )
    if (needsSecondStep) {
      issueCode(CodePurpose.SECOND_STEP, profile.email)
      return AuthOutcome.NeedCode(CodePurpose.SECOND_STEP, profile.email)
    }
    return startSession(account)
  }

  // ---------- Codes ----------

  fun submitCode(purpose: CodePurpose, email: String, code: String): AuthOutcome {
    val error = consumeCode(purpose, email, code)
    if (error != null) return AuthOutcome.Failure(error)

    val account = findByEmail(email) ?: return AuthOutcome.Failure(AuthError.CODE_EXPIRED)
    return when (purpose) {
      CodePurpose.VERIFY_EMAIL -> {
        val verified = account.copy(emailVerified = true)
        putAccount(verified)
        startSession(verified)
      }
      CodePurpose.SECOND_STEP -> {
        val updated = if (account.profile.role == Role.LECTURER) {
          account.copy(trustedDevices = account.trustedDevices + thisDeviceId).also { putAccount(it) }
        } else {
          account // administrators confirm every time
        }
        startSession(updated)
      }
      CodePurpose.RESET_PASSWORD -> AuthOutcome.Failure(AuthError.CODE_EXPIRED) // use resetPassword()
    }
  }

  fun resendCode(purpose: CodePurpose, email: String) {
    issueCode(purpose, normalizeEmail(email))
  }

  // ---------- Forgot password ----------

  /** Always answers the same way, so the screen never reveals which emails have accounts. */
  fun requestPasswordReset(email: String): AuthOutcome {
    val clean = normalizeEmail(email)
    if (findByEmail(clean) != null) issueCode(CodePurpose.RESET_PASSWORD, clean) else issued = null
    return AuthOutcome.NeedCode(CodePurpose.RESET_PASSWORD, clean)
  }

  fun resetPassword(email: String, code: String, newPassword: String): AuthOutcome {
    val account = findByEmail(email)
    if (!checkPassword(newPassword, email).acceptable) return AuthOutcome.Failure(AuthError.PASSWORD_WEAK)
    val error = consumeCode(CodePurpose.RESET_PASSWORD, email, code)
    if (error != null || account == null) return AuthOutcome.Failure(error ?: AuthError.CODE_EXPIRED)

    val salt = UUID.randomUUID().toString()
    putAccount(account.copy(salt = salt, passwordHash = hash(newPassword, salt), trustedDevices = emptySet()))

    // A stolen session must not survive a password change: every phone is signed out.
    val id = account.profile.id
    _devices.value = _devices.value - id
    if (_session.value?.userId == id) _session.value = null
    return AuthOutcome.PasswordChanged
  }

  // ---------- Sessions ----------

  fun startDemoSession(userId: String) {
    _session.value = Session(userId = userId, isDemo = true, deviceId = thisDeviceId)
  }

  fun signOut() {
    val s = _session.value
    if (s != null && !s.isDemo) removeDevice(s.userId, thisDeviceId)
    _session.value = null
    _visibleCode.value = null
  }

  fun signOutDevice(userId: String, deviceId: String) {
    if (deviceId == thisDeviceId) {
      signOut()
    } else {
      removeDevice(userId, deviceId)
    }
  }

  fun signOutEverywhere(userId: String) {
    _devices.value = _devices.value - userId
    if (_session.value?.userId == userId) _session.value = null
  }

  // ---------- Registry actions (permission is checked by the caller) ----------

  fun registryMatches(profile: UserProfile): Boolean {
    return if (profile.requestedRole == Role.LECTURER || (profile.staffNumber != null && profile.studentNumber == null)) {
      profile.staffNumber in SeedUsers.staffList
    } else {
      profile.studentNumber in SeedUsers.studentList
    }
  }

  fun approve(userId: String, role: Role, programme: String?, teaches: Set<String>): UserProfile? {
    val account = _accounts.value[userId] ?: return null
    val old = account.profile
    val updated = when (role) {
      Role.STUDENT -> {
        val prog = programme ?: old.programme ?: SeedUsers.programmes.first()
        old.copy(
          role = Role.STUDENT,
          status = AccountStatus.APPROVED,
          programme = prog,
          cohort = if (old.cohort.isNullOrBlank() || old.cohort == "Awaiting registry") "Year 1 • Semester 1" else old.cohort,
          enrolledCourses = SeedUsers.courseCatalog[prog] ?: emptySet()
        )
      }
      Role.LECTURER -> old.copy(
        role = Role.LECTURER,
        status = AccountStatus.APPROVED,
        cohort = if (old.cohort.isNullOrBlank() || old.cohort == "Staff (awaiting role)") "Lecturer" else old.cohort,
        teachesCourses = teaches,
        enrolledCourses = emptySet()
      )
      // Administrator accounts are never created from this queue.
      Role.ADMIN -> return null
    }
    putAccount(account.copy(profile = updated))
    return updated
  }

  fun reject(userId: String): UserProfile? {
    val account = _accounts.value[userId] ?: return null
    val updated = account.profile.copy(status = AccountStatus.REJECTED)
    putAccount(account.copy(profile = updated))
    removeAllDevices(userId)
    return updated
  }

  // ---------- Internals ----------

  private fun startSession(account: Account): AuthOutcome {
    val id = account.profile.id
    val existing = _devices.value[id].orEmpty()
    val already = existing.any { it.id == thisDeviceId }
    if (!already) {
      _devices.value = _devices.value + (id to (existing + DeviceSession(thisDeviceId, thisDeviceLabel, "Just now")))
      if (existing.isNotEmpty()) {
        onNotify(id, "Signed in on a new device", "Your account was just signed in on a new phone. Not you? Open Profile > Devices and sign it out.")
      }
    }
    _session.value = Session(userId = id, isDemo = false, deviceId = thisDeviceId)
    _visibleCode.value = null
    return AuthOutcome.Success(id)
  }

  private fun issueCode(purpose: CodePurpose, email: String) {
    val code = "%06d".format(random.nextInt(1_000_000))
    issued = IssuedCode(purpose, email, code, System.currentTimeMillis() + AuthConfig.CODE_TTL_MILLIS)
    _visibleCode.value = if (AuthConfig.SHOW_CODES_ON_SCREEN) code else null
  }

  /** Returns null when the code is right and has been used up. */
  private fun consumeCode(purpose: CodePurpose, email: String, code: String): AuthError? {
    val current = issued ?: return AuthError.CODE_EXPIRED
    if (current.purpose != purpose || current.email != normalizeEmail(email)) return AuthError.CODE_EXPIRED
    if (System.currentTimeMillis() > current.expiresAt) {
      issued = null
      return AuthError.CODE_EXPIRED
    }
    if (current.attempts >= AuthConfig.MAX_CODE_ATTEMPTS) {
      issued = null
      return AuthError.TOO_MANY_ATTEMPTS
    }
    if (code.trim() != current.code) {
      issued = current.copy(attempts = current.attempts + 1)
      return AuthError.CODE_WRONG
    }
    issued = null
    _visibleCode.value = null
    return null
  }

  private fun removeDevice(userId: String, deviceId: String) {
    val list = _devices.value[userId].orEmpty().filterNot { it.id == deviceId }
    _devices.value = if (list.isEmpty()) _devices.value - userId else _devices.value + (userId to list)
  }

  private fun removeAllDevices(userId: String) {
    _devices.value = _devices.value - userId
    if (_session.value?.userId == userId) _session.value = null
  }

  private fun putAccount(account: Account) {
    _accounts.value = _accounts.value + (account.profile.id to account)
  }

  private fun findByEmail(email: String): Account? {
    val clean = normalizeEmail(email)
    return _accounts.value.values.firstOrNull { it.profile.email.equals(clean, ignoreCase = true) }
  }

  private fun normalizeEmail(raw: String) = raw.trim().lowercase()

  private fun hash(password: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest((salt + password).toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
  }

  private fun seedAccounts(): Map<String, Account> {
    return SeedUsers.directory.associate { profile ->
      val salt = "seed-" + profile.id
      profile.id to Account(
        profile = profile,
        salt = salt,
        passwordHash = hash(SeedUsers.SAMPLE_PASSWORD, salt),
        emailVerified = true
      )
    }
  }

  private companion object {
    val EMAIL_REGEX = Regex("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")
    val STUDENT_NUMBER_REGEX = Regex("^T\\d{2}-\\d{2}-\\d{5}$")
    val STAFF_NUMBER_REGEX = Regex("^STF-[A-Z]{2,3}-\\d{3}$")
  }
}
