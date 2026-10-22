package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthError
import com.example.data.auth.AuthOutcome
import com.example.data.auth.CodePurpose
import com.example.data.auth.DeviceSession
import com.example.data.auth.PasswordCheck
import com.example.data.auth.PasswordProblem
import com.example.data.model.AnnouncementPriority
import com.example.data.model.AppLanguage
import com.example.data.model.AttachmentFile
import com.example.data.model.AudienceType
import com.example.data.model.ContentApprovalStatus
import com.example.data.model.Role
import com.example.data.model.UserProfile
import com.example.data.repository.AudienceOption
import com.example.data.repository.CampusRepository
import com.example.data.repository.PostError
import com.example.data.repository.SeedUsers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ScreenTab {
  HOME,
  TIMETABLE,
  MATERIALS,
  CALENDAR,
  SUBMISSIONS,
  PROFILE,
  ADMIN,
  NOTIFICATIONS
}

class CampusViewModel(
  private val repository: CampusRepository = CampusRepository()
) : ViewModel() {

  // ---------- Sign-in state ----------

  val session = repository.session
  val isDemoMode = repository.isDemoMode
  val visibleCode = repository.auth.visibleCode
  val demoPersonas: List<UserProfile> = repository.demoPersonas

  /** Campuses offered at sign-up, and the programmes each has set up (may be none yet). */
  val universities = SeedUsers.universities
  fun programmesFor(universityId: String): List<String> = SeedUsers.programmesFor(universityId)

  val currentUser = repository.currentUser
  val appLanguage = repository.appLanguage
  val isOffline = repository.isOffline
  val lastSyncedAt = repository.lastSyncedAt
  val is24HourFormat = repository.is24HourFormat
  val sessions = repository.sessions
  val announcements = repository.announcements
  val remindedAnnouncements = repository.remindedAnnouncements
  val materials = repository.materials
  val deadlines = repository.deadlines
  val submissions = repository.submissions
  val notifications = repository.notifications
  val pendingUsers = repository.pendingUsers
  val auditLogs = repository.auditLogs
  val deletionRequests = repository.deletionRequests

  /** Phones signed in to the current person's account. */
  val myDevices: StateFlow<List<DeviceSession>> = combine(repository.devices, repository.currentUser) { all, user ->
    all[user.id].orEmpty()
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val thisDeviceId: String = repository.auth.thisDeviceId

  // Active Navigation Tab
  private val _currentTab = MutableStateFlow(ScreenTab.HOME)
  val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

  // Timetable State
  private val _selectedDay = MutableStateFlow(todayDay())
  val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

  private val _isWeekView = MutableStateFlow(false)
  val isWeekView: StateFlow<Boolean> = _isWeekView.asStateFlow()

  // Filter for Announcements
  private val _announcementFilter = MutableStateFlow("ALL")
  val announcementFilter: StateFlow<String> = _announcementFilter.asStateFlow()

  // Filter for Submissions
  private val _submissionFilterCourse = MutableStateFlow("ALL")
  val submissionFilterCourse: StateFlow<String> = _submissionFilterCourse.asStateFlow()

  // One-line messages shown at the bottom of the screen
  private val _message = MutableStateFlow<String?>(null)
  val message: StateFlow<String?> = _message.asStateFlow()

  fun showMessage(text: String) {
    _message.value = text
  }

  fun messageShown() {
    _message.value = null
  }

  // Unread counts
  val unreadNotificationsCount: StateFlow<Int> = notifications.map { list ->
    list.count { !it.isRead }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  val unreadAnnouncementsCount: StateFlow<Int> = announcements.map { list ->
    list.count { !it.isRead }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  // ---------- Language helper ----------

  val isSwahili: Boolean get() = appLanguage.value == AppLanguage.SWAHILI

  fun t(english: String, swahili: String): String = if (isSwahili) swahili else english

  // ---------- Authentication ----------

  fun checkPassword(password: String, email: String): PasswordCheck = repository.auth.checkPassword(password, email)

  fun signUp(
    universityId: String,
    fullName: String,
    email: String,
    idNumber: String,
    isStaff: Boolean,
    programmeOrDepartment: String,
    password: String
  ): AuthOutcome =
    repository.auth.signUp(universityId, fullName, email, idNumber, isStaff, programmeOrDepartment, password)

  /** [identifier] is a registration/staff number or an email. */
  fun signIn(identifier: String, password: String): AuthOutcome = repository.auth.signIn(identifier, password)

  fun submitCode(purpose: CodePurpose, email: String, code: String): AuthOutcome =
    repository.auth.submitCode(purpose, email, code)

  fun resendCode(purpose: CodePurpose, email: String) = repository.auth.resendCode(purpose, email)

  fun requestPasswordReset(email: String): AuthOutcome = repository.auth.requestPasswordReset(email)

  fun resetPassword(email: String, code: String, newPassword: String): AuthOutcome =
    repository.auth.resetPassword(email, code, newPassword)

  fun startDemo() {
    resetNavigation()
    repository.startDemo()
  }

  fun signOut() {
    repository.signOut()
    resetNavigation()
  }

  fun signOutEverywhere() {
    repository.signOutEverywhere()
    resetNavigation()
  }

  fun signOutDevice(deviceId: String) {
    repository.signOutDevice(deviceId)
    if (deviceId == thisDeviceId) resetNavigation()
  }

  private fun resetNavigation() {
    _currentTab.value = ScreenTab.HOME
    _selectedDay.value = todayDay()
    _isWeekView.value = false
    _announcementFilter.value = "ALL"
    _submissionFilterCourse.value = "ALL"
    _message.value = null
  }

  fun authErrorText(error: AuthError): String = when (error) {
    AuthError.NAME_REQUIRED -> t("Please enter your full name.", "Tafadhali andika jina lako kamili.")
    AuthError.EMAIL_INVALID -> t("That email address does not look right.", "Anwani hii ya barua pepe haionekani sahihi.")
    AuthError.UNIVERSITY_REQUIRED -> t("Please choose your university.", "Tafadhali chagua chuo chako.")
    AuthError.USE_EMAIL -> t(
      "That number exists at more than one university. Sign in with your email instead.",
      "Namba hiyo ipo katika vyuo zaidi ya kimoja. Ingia kwa barua pepe yako."
    )
    AuthError.EMAIL_TAKEN -> t("An account with this email already exists.", "Akaunti yenye barua pepe hii tayari ipo.")
    AuthError.NUMBER_INVALID -> t(
      "That registration or staff number is not in the right format.",
      "Namba ya usajili au ya mfanyakazi haiko katika muundo sahihi."
    )
    AuthError.NUMBER_TAKEN -> t(
      "That number is already linked to an account.",
      "Namba hiyo tayari imeunganishwa na akaunti."
    )
    AuthError.PROGRAMME_REQUIRED -> t("Please choose your programme.", "Tafadhali chagua programu yako.")
    AuthError.PASSWORD_WEAK -> t(
      "Choose a stronger password.",
      "Chagua nenosiri lenye nguvu zaidi."
    )
    AuthError.WRONG_CREDENTIALS -> t("Email or password is not correct.", "Barua pepe au nenosiri si sahihi.")
    AuthError.ACCOUNT_NOT_ACTIVE -> t("Account not active.", "Akaunti haifanyi kazi.")
    AuthError.CODE_WRONG -> t("That code is not correct.", "Msimbo huo si sahihi.")
    AuthError.CODE_EXPIRED -> t(
      "The code has expired. Ask for a new one.",
      "Msimbo umekwisha muda. Omba mpya."
    )
    AuthError.TOO_MANY_ATTEMPTS -> t(
      "Too many tries. Ask for a new code.",
      "Majaribio mengi mno. Omba msimbo mpya."
    )
  }

  fun passwordProblemText(problem: PasswordProblem): String = when (problem) {
    PasswordProblem.TOO_SHORT -> t("At least 8 characters", "Angalau herufi 8")
    PasswordProblem.COMMON -> t("Too common: easy to guess", "Imezoeleka mno: rahisi kubashiri")
    PasswordProblem.CONTAINS_EMAIL -> t("Do not include your email name", "Usiweke jina la barua pepe yako")
    PasswordProblem.REPEATED -> t("Too repetitive", "Inarudiarudia mno")
  }

  // ---------- Navigation ----------

  /** Today as Monday = 1 ... Saturday = 6. Sunday shows Monday, the next teaching day. */
  fun todayDay(): Int = when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
    java.util.Calendar.SUNDAY -> 1
    else -> java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1
  }

  fun canEditOrDelete(uploaderOrAuthorId: String): Boolean = repository.canEditOrDelete(uploaderOrAuthorId)

  fun canApproveOrDisapprove(): Boolean = repository.canApproveOrDisapprove()

  fun setTab(tab: ScreenTab) {
    _currentTab.value = tab
  }

  fun setSelectedDay(day: Int) {
    _selectedDay.value = day
  }

  fun toggleWeekView() {
    _isWeekView.value = !_isWeekView.value
  }

  fun setAnnouncementFilter(filter: String) {
    _announcementFilter.value = filter
  }

  fun setSubmissionFilterCourse(course: String) {
    _submissionFilterCourse.value = course
  }

  /** Demo mode only. Ignored in a real session. */
  fun switchDemoPersona(user: UserProfile) {
    repository.switchDemoPersona(user)
    resetNavigation()
  }

  fun toggleLanguage() {
    val newLang = if (appLanguage.value == AppLanguage.ENGLISH) AppLanguage.SWAHILI else AppLanguage.ENGLISH
    repository.setLanguage(newLang)
  }

  fun toggle24HourFormat() {
    repository.toggle24HourFormat()
  }

  fun toggleOfflineSimulation() {
    repository.toggleOfflineSimulation()
  }

  fun triggerSync() {
    repository.triggerSync()
  }

  fun markAnnouncementRead(id: String) {
    repository.markAnnouncementRead(id)
  }

  fun toggleDeadlineCompleted(id: String) {
    repository.toggleDeadlineCompleted(id)
  }

  fun toggleMaterialDownloaded(id: String) {
    repository.toggleMaterialDownloaded(id)
  }

  fun markAllNotificationsRead() {
    repository.markAllNotificationsRead()
  }

  // ---------- Student work ----------

  fun submitStudentWork(
    deadlineId: String,
    courseCode: String,
    assignmentTitle: String,
    notes: String,
    attachment: AttachmentFile
  ) {
    repository.submitStudentWork(deadlineId, courseCode, assignmentTitle, notes, attachment)
  }

  fun gradeStudentWork(submissionId: String, grade: String, feedback: String) {
    repository.gradeStudentWork(submissionId, grade, feedback)
  }

  fun deleteStudentSubmission(submissionId: String) {
    repository.deleteStudentSubmission(submissionId)
  }

  // ---------- Announcements ----------

  fun audienceOptions(): List<AudienceOption> = repository.audienceOptions()

  fun urgentLeftToday(): Int = repository.urgentLeftToday()

  fun canUseUrgentFor(type: AudienceType): Boolean = repository.canUseUrgentFor(type)

  /** Returns true when the notice went out. A refusal is explained in a message. */
  fun publishAnnouncement(
    title: String,
    body: String,
    audienceType: AudienceType,
    audienceName: String,
    audienceCode: String,
    priority: AnnouncementPriority,
    attachment: AttachmentFile?
  ): Boolean {
    val error = repository.publishAnnouncement(title, body, audienceType, audienceName, audienceCode, priority, attachment)
    if (error != null) {
      showMessage(postErrorText(error))
      return false
    }
    return true
  }

  private fun postErrorText(error: PostError): String = when (error) {
    PostError.NOT_ALLOWED -> t("You are not allowed to post notices.", "Huruhusiwi kuweka matangazo.")
    PostError.CAMPUS_REGISTRY_ONLY -> t(
      "Only the Academic Registry can post to the whole campus.",
      "Ni Msajili wa Taaluma pekee anayeweza kutangaza kwa chuo kizima."
    )
    PostError.AUDIENCE_NOT_YOURS -> t(
      "You can only post to courses you teach.",
      "Unaweza kutangaza kwa kozi unazofundisha tu."
    )
    PostError.URGENT_NOT_ALLOWED -> t(
      "Urgent can only be used for your own course.",
      "Dharura inaweza kutumika kwa kozi yako tu."
    )
    PostError.URGENT_LIMIT_REACHED -> t(
      "Daily limit for Urgent notices reached. Use Academic instead.",
      "Kikomo cha kila siku cha matangazo ya Dharura kimefikiwa. Tumia Kitaaluma."
    )
  }

  fun updateAnnouncement(id: String, newTitle: String, newBody: String, newPriority: AnnouncementPriority) {
    repository.updateAnnouncement(id, newTitle, newBody, newPriority)
  }

  fun deleteAnnouncement(id: String) {
    repository.deleteAnnouncement(id)
  }

  fun retractAnnouncement(id: String) {
    if (repository.retractAnnouncement(id)) {
      showMessage(t("Notice withdrawn. A correction was sent.", "Tangazo limeondolewa. Marekebisho yametumwa."))
    }
  }

  fun remindUnread(id: String) {
    val count = repository.remindUnread(id)
    showMessage(
      if (count > 0) t("Reminder sent to $count people.", "Ukumbusho umetumwa kwa watu $count.")
      else t("No one left to remind.", "Hakuna wa kukumbushwa.")
    )
  }

  fun setAnnouncementApproval(id: String, status: ContentApprovalStatus) {
    repository.setAnnouncementApproval(id, status)
  }

  // ---------- Study materials ----------

  fun uploadMaterial(title: String, courseCode: String, courseTitle: String, description: String, attachment: AttachmentFile): Boolean {
    val ok = repository.uploadMaterial(title, courseCode, courseTitle, description, attachment)
    if (!ok) showMessage(t("You can only share files for courses you teach.", "Unaweza kushiriki mafaili ya kozi unazofundisha tu."))
    return ok
  }

  fun updateMaterial(id: String, newTitle: String, newDescription: String) {
    repository.updateMaterial(id, newTitle, newDescription)
  }

  fun deleteMaterial(id: String) {
    repository.deleteMaterial(id)
  }

  fun setMaterialApproval(id: String, status: ContentApprovalStatus) {
    repository.setMaterialApproval(id, status)
  }

  // ---------- Timetable ----------

  fun updateSessionVenue(sessionId: String, newVenue: String, reason: String) {
    repository.updateSessionVenue(sessionId, newVenue, reason)
  }

  fun cancelSession(sessionId: String, reason: String) {
    repository.cancelSession(sessionId, reason)
  }

  fun restoreSession(sessionId: String) {
    repository.restoreSession(sessionId)
  }

  fun deleteSession(sessionId: String) {
    repository.deleteSession(sessionId)
  }

  fun setSessionApproval(sessionId: String, status: ContentApprovalStatus) {
    repository.setSessionApproval(sessionId, status)
  }

  // ---------- Deadlines ----------

  fun addDeadline(courseCode: String, courseTitle: String, title: String, instructions: String, dueDateTime: String, acceptedFormats: String): Boolean {
    val ok = repository.addDeadline(courseCode, courseTitle, title, instructions, dueDateTime, acceptedFormats)
    if (!ok) showMessage(t("You can only set deadlines for courses you teach.", "Unaweza kuweka tarehe za mwisho kwa kozi unazofundisha tu."))
    return ok
  }

  fun updateDeadline(id: String, newTitle: String, newInstructions: String, newDueDateTime: String) {
    repository.updateDeadline(id, newTitle, newInstructions, newDueDateTime)
  }

  fun deleteDeadline(id: String) {
    repository.deleteDeadline(id)
  }

  fun setDeadlineApproval(id: String, status: ContentApprovalStatus) {
    repository.setDeadlineApproval(id, status)
  }

  // ---------- Registry ----------

  /** True/false against the university list, or null when that university has not uploaded one. */
  fun registryMatches(user: UserProfile): Boolean? = repository.registryMatches(user)

  fun approveUser(userId: String, role: Role, programme: String?, teaches: Set<String>) {
    repository.approveUser(userId, role, programme, teaches)
  }

  fun rejectUser(userId: String) {
    repository.rejectUser(userId)
  }

  // ---------- My data ----------

  fun exportMyData(): String = repository.exportMyData()

  fun requestDataDeletion() {
    repository.requestDataDeletion()
    showMessage(t("Request sent to the Academic Registry.", "Ombi limetumwa kwa Msajili wa Taaluma."))
  }

  // ---------- Words used in several places ----------

  fun getString(key: String): String {
    val sw = isSwahili
    return when (key) {
      "tab_home" -> if (sw) "Nyumbani" else "Home"
      "tab_timetable" -> if (sw) "Ratiba" else "Timetable"
      "tab_announcements" -> if (sw) "Matangazo" else "Announcements"
      "tab_materials" -> if (sw) "Vifaa" else "Materials"
      "tab_calendar" -> if (sw) "Tarehe" else "Deadlines"
      "tab_submissions" -> if (sw) "Kazi Zilizotumwa" else "Submissions"
      "tab_profile" -> if (sw) "Wasifu" else "Profile"
      "tab_admin" -> if (sw) "Utawala" else "Admin"
      "notifications" -> if (sw) "Arifa" else "Notifications"
      "room_changed" -> if (sw) "Chumba kimebadilishwa" else "Room changed"
      "cancelled" -> if (sw) "Imeghairiwa" else "Cancelled"
      "scheduled" -> if (sw) "Imepangwa" else "Scheduled"
      "greeting" -> if (sw) "Habari" else "Hello"
      "today_classes" -> if (sw) "Masomo ya Leo" else "Today's Classes"
      "latest_announcement" -> if (sw) "Tangazo Lililo Angaziwa" else "Highlighted Announcement"
      "next_deadline" -> if (sw) "Tarehe Inayofuata" else "Next Upcoming Deadline"
      "quick_actions" -> if (sw) "Vitendo vya Haraka" else "Quick Actions"
      "my_assigned_courses" -> if (sw) "Masomo Yangu Niliyopangiwa" else "My Assigned Courses"
      "offline_notice" -> if (sw) "Nje ya mtandao" else "Offline"
      else -> key
    }
  }
}
