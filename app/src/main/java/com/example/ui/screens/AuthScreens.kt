package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.auth.AuthOutcome
import com.example.data.auth.CodePurpose
import com.example.data.auth.PasswordStrength
import com.example.data.model.AccountStatus
import com.example.data.model.AppLanguage
import com.example.data.repository.SeedUsers
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmAmberLight
import com.example.viewmodel.CampusViewModel

private enum class AuthPage { SIGN_IN, SIGN_UP, CODE, FORGOT, RESET }

/** Shown to anyone who has not signed in. */
@Composable
fun AuthGate(viewModel: CampusViewModel) {
  val language by viewModel.appLanguage.collectAsState()
  val shownCode by viewModel.visibleCode.collectAsState()

  var page by remember { mutableStateOf(AuthPage.SIGN_IN) }
  var pendingEmail by remember { mutableStateOf("") }
  var codePurpose by remember { mutableStateOf(CodePurpose.VERIFY_EMAIL) }
  var error by remember { mutableStateOf<String?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }

  fun go(next: AuthPage) {
    error = null
    page = next
  }

  // Returns the text of an error to show, or null when the flow moved on.
  fun handle(outcome: AuthOutcome) {
    when (outcome) {
      is AuthOutcome.Failure -> error = viewModel.authErrorText(outcome.error)
      is AuthOutcome.NeedCode -> {
        pendingEmail = outcome.email
        codePurpose = outcome.purpose
        notice = null
        go(if (outcome.purpose == CodePurpose.RESET_PASSWORD) AuthPage.RESET else AuthPage.CODE)
      }
      is AuthOutcome.PasswordChanged -> {
        notice = viewModel.t(
          "Password changed. Your other phones were signed out. Sign in with the new one.",
          "Nenosiri limebadilishwa. Simu zako nyingine zimetolewa. Ingia kwa nenosiri jipya."
        )
        go(AuthPage.SIGN_IN)
      }
      is AuthOutcome.Success -> Unit // session started: the app switches screens by itself
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .systemBarsPadding()
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 12.dp)
      .testTag("auth_gate")
  ) {
    LanguageSwitch(viewModel, language)
    Spacer(modifier = Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(DeepBlue),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.School, contentDescription = "CampusConnect", tint = Color.White, modifier = Modifier.size(26.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Text("CampusConnect", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = viewModel.t(
        "Your timetable, announcements and deadlines in one place.",
        "Ratiba, matangazo na tarehe za mwisho zako mahali pamoja."
      ),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(20.dp))

    if (shownCode != null && (page == AuthPage.CODE || page == AuthPage.RESET)) {
      PrototypeCodeNotice(viewModel, shownCode ?: "")
      Spacer(modifier = Modifier.height(12.dp))
    }

    notice?.let {
      Text(it, color = SuccessGreen, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("auth_notice"))
      Spacer(modifier = Modifier.height(10.dp))
    }

    when (page) {
      AuthPage.SIGN_IN -> SignInPage(
        viewModel = viewModel,
        error = error,
        onSubmit = { email, password ->
          error = null
          handle(viewModel.signIn(email, password))
        },
        onForgot = { notice = null; go(AuthPage.FORGOT) },
        onCreate = { notice = null; go(AuthPage.SIGN_UP) }
      )

      AuthPage.SIGN_UP -> SignUpPage(
        viewModel = viewModel,
        error = error,
        onError = { error = it },
        onSubmit = { name, email, number, staff, programme, password ->
          error = null
          handle(viewModel.signUp(name, email, number, staff, programme, password))
        },
        onBack = { go(AuthPage.SIGN_IN) }
      )

      AuthPage.CODE -> CodePage(
        viewModel = viewModel,
        purpose = codePurpose,
        email = pendingEmail,
        error = error,
        onSubmit = { code ->
          error = null
          handle(viewModel.submitCode(codePurpose, pendingEmail, code))
        },
        onResend = {
          error = null
          viewModel.resendCode(codePurpose, pendingEmail)
        },
        onBack = { go(AuthPage.SIGN_IN) }
      )

      AuthPage.FORGOT -> ForgotPage(
        viewModel = viewModel,
        onSubmit = { email -> handle(viewModel.requestPasswordReset(email)) },
        onBack = { go(AuthPage.SIGN_IN) }
      )

      AuthPage.RESET -> ResetPage(
        viewModel = viewModel,
        email = pendingEmail,
        error = error,
        onError = { error = it },
        onSubmit = { code, password ->
          error = null
          handle(viewModel.resetPassword(pendingEmail, code, password))
        },
        onBack = { go(AuthPage.SIGN_IN) }
      )
    }
    Spacer(modifier = Modifier.height(24.dp))
  }
}

// ---------- Pages ----------

@Composable
private fun SignInPage(
  viewModel: CampusViewModel,
  error: String?,
  onSubmit: (String, String) -> Unit,
  onForgot: () -> Unit,
  onCreate: () -> Unit
) {
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }

  Text(viewModel.t("Sign in", "Ingia"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
  Spacer(modifier = Modifier.height(10.dp))
  AuthField(email, { email = it }, viewModel.t("University email", "Barua pepe ya chuo"), "auth_email", KeyboardType.Email)
  Spacer(modifier = Modifier.height(8.dp))
  AuthField(password, { password = it }, viewModel.t("Password", "Nenosiri"), "auth_password", KeyboardType.Password, isPassword = true)
  ErrorLine(error)
  Spacer(modifier = Modifier.height(12.dp))
  Button(
    onClick = { onSubmit(email, password) },
    enabled = email.isNotBlank() && password.isNotEmpty(),
    colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
    modifier = Modifier.fillMaxWidth().testTag("auth_sign_in_btn")
  ) { Text(viewModel.t("Sign in", "Ingia")) }

  TextButton(onClick = onForgot, modifier = Modifier.testTag("auth_forgot_btn")) {
    Text(viewModel.t("Forgot password?", "Umesahau nenosiri?"))
  }
  OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().testTag("auth_create_btn")) {
    Text(viewModel.t("Create account", "Fungua akaunti"))
  }

  if (BuildConfig.DEMO_MODE_ENABLED) {
    Spacer(modifier = Modifier.height(20.dp))
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = WarmAmberLight,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          viewModel.t("Demo mode", "Hali ya onyesho"),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF78350F)
        )
        Text(
          viewModel.t(
            "Try the app as a student, lecturer or registry officer. Fake people and sample data only; nothing is saved.",
            "Jaribu programu kama mwanafunzi, mhadhiri au afisa wa usajili. Watu wa kubuni na data za mfano tu; hakuna kinachohifadhiwa."
          ),
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF78350F)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { viewModel.startDemo() }, modifier = Modifier.testTag("auth_demo_btn")) {
          Text(viewModel.t("Open demo", "Fungua onyesho"))
        }
      }
    }
  }
}

@Composable
private fun SignUpPage(
  viewModel: CampusViewModel,
  error: String?,
  onError: (String?) -> Unit,
  onSubmit: (name: String, email: String, number: String, staff: Boolean, programme: String, password: String) -> Unit,
  onBack: () -> Unit
) {
  var name by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var number by remember { mutableStateOf("") }
  var isStaff by remember { mutableStateOf(false) }
  var programme by remember { mutableStateOf(SeedUsers.programmes.first()) }
  var department by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf("") }

  Text(viewModel.t("Create account", "Fungua akaunti"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
  Spacer(modifier = Modifier.height(10.dp))

  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    ChoiceChip(viewModel.t("Student", "Mwanafunzi"), !isStaff, "auth_type_student") { isStaff = false }
    ChoiceChip(viewModel.t("Staff", "Mfanyakazi"), isStaff, "auth_type_staff") { isStaff = true }
  }
  Spacer(modifier = Modifier.height(8.dp))

  AuthField(name, { name = it }, viewModel.t("Full name", "Jina kamili"), "auth_name")
  Spacer(modifier = Modifier.height(8.dp))
  AuthField(email, { email = it }, viewModel.t("University email (name@campus.ac.tz)", "Barua pepe ya chuo (jina@campus.ac.tz)"), "auth_signup_email", KeyboardType.Email)
  Spacer(modifier = Modifier.height(8.dp))
  AuthField(
    number,
    { number = it },
    if (isStaff) viewModel.t("Staff number (e.g. STF-CS-041)", "Namba ya mfanyakazi (mf. STF-CS-041)")
    else viewModel.t("Registration number (e.g. T22-03-08492)", "Namba ya usajili (mf. T22-03-08492)"),
    "auth_number"
  )
  Spacer(modifier = Modifier.height(8.dp))

  if (isStaff) {
    AuthField(department, { department = it }, viewModel.t("Department", "Idara"), "auth_department")
  } else {
    Text(viewModel.t("Programme", "Programu"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      SeedUsers.programmes.forEach { p ->
        ChoiceChip(p, programme == p, "auth_programme_$p") { programme = p }
      }
    }
  }

  Spacer(modifier = Modifier.height(8.dp))
  AuthField(password, { password = it }, viewModel.t("Password", "Nenosiri"), "auth_signup_password", KeyboardType.Password, isPassword = true)
  PasswordHint(viewModel, password, email)
  Spacer(modifier = Modifier.height(8.dp))
  AuthField(confirm, { confirm = it }, viewModel.t("Repeat password", "Rudia nenosiri"), "auth_confirm_password", KeyboardType.Password, isPassword = true)

  ErrorLine(error)
  Spacer(modifier = Modifier.height(12.dp))
  Button(
    onClick = {
      if (password != confirm) {
        onError(viewModel.t("The two passwords are not the same.", "Manenosiri mawili hayafanani."))
      } else {
        onSubmit(name, email, number, isStaff, if (isStaff) department else programme, password)
      }
    },
    enabled = name.isNotBlank() && email.isNotBlank() && number.isNotBlank() && password.isNotEmpty(),
    colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
    modifier = Modifier.fillMaxWidth().testTag("auth_sign_up_btn")
  ) { Text(viewModel.t("Create account", "Fungua akaunti")) }

  TextButton(onClick = onBack) { Text(viewModel.t("Back to sign in", "Rudi kuingia")) }
}

@Composable
private fun CodePage(
  viewModel: CampusViewModel,
  purpose: CodePurpose,
  email: String,
  error: String?,
  onSubmit: (String) -> Unit,
  onResend: () -> Unit,
  onBack: () -> Unit
) {
  var code by remember { mutableStateOf("") }
  val title = if (purpose == CodePurpose.SECOND_STEP) {
    viewModel.t("Second step", "Hatua ya pili")
  } else {
    viewModel.t("Check your email", "Angalia barua pepe yako")
  }
  Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
  Spacer(modifier = Modifier.height(6.dp))
  Text(
    viewModel.t(
      "We sent a six-digit code to $email. Enter it to continue.",
      "Tumetuma msimbo wa tarakimu sita kwa $email. Uandike ili kuendelea."
    ),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
  Spacer(modifier = Modifier.height(10.dp))
  AuthField(code, { code = it.filter(Char::isDigit).take(6) }, viewModel.t("Six-digit code", "Msimbo wa tarakimu sita"), "auth_code", KeyboardType.NumberPassword)
  ErrorLine(error)
  Spacer(modifier = Modifier.height(12.dp))
  Button(
    onClick = { onSubmit(code) },
    enabled = code.length == 6,
    colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
    modifier = Modifier.fillMaxWidth().testTag("auth_code_btn")
  ) { Text(viewModel.t("Continue", "Endelea")) }
  Row {
    TextButton(onClick = onResend) { Text(viewModel.t("Send a new code", "Tuma msimbo mpya")) }
    TextButton(onClick = onBack) { Text(viewModel.t("Cancel", "Ghairi")) }
  }
}

@Composable
private fun ForgotPage(
  viewModel: CampusViewModel,
  onSubmit: (String) -> Unit,
  onBack: () -> Unit
) {
  var email by remember { mutableStateOf("") }
  Text(viewModel.t("Forgot password", "Umesahau nenosiri"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
  Spacer(modifier = Modifier.height(6.dp))
  Text(
    viewModel.t(
      "Enter your university email. If it has an account, we send a code.",
      "Andika barua pepe yako ya chuo. Ikiwa ina akaunti, tutatuma msimbo."
    ),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
  Spacer(modifier = Modifier.height(10.dp))
  AuthField(email, { email = it }, viewModel.t("University email", "Barua pepe ya chuo"), "auth_forgot_email", KeyboardType.Email)
  Spacer(modifier = Modifier.height(12.dp))
  Button(
    onClick = { onSubmit(email) },
    enabled = email.isNotBlank(),
    colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
    modifier = Modifier.fillMaxWidth().testTag("auth_forgot_send_btn")
  ) { Text(viewModel.t("Send code", "Tuma msimbo")) }
  TextButton(onClick = onBack) { Text(viewModel.t("Back to sign in", "Rudi kuingia")) }
}

@Composable
private fun ResetPage(
  viewModel: CampusViewModel,
  email: String,
  error: String?,
  onError: (String?) -> Unit,
  onSubmit: (code: String, password: String) -> Unit,
  onBack: () -> Unit
) {
  var code by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf("") }

  Text(viewModel.t("Choose a new password", "Chagua nenosiri jipya"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
  Spacer(modifier = Modifier.height(6.dp))
  Text(
    viewModel.t(
      "If $email has an account, a six-digit code was sent to it.",
      "Ikiwa $email ina akaunti, msimbo wa tarakimu sita umetumwa."
    ),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
  Spacer(modifier = Modifier.height(10.dp))
  AuthField(code, { code = it.filter(Char::isDigit).take(6) }, viewModel.t("Six-digit code", "Msimbo wa tarakimu sita"), "auth_reset_code", KeyboardType.NumberPassword)
  Spacer(modifier = Modifier.height(8.dp))
  AuthField(password, { password = it }, viewModel.t("New password", "Nenosiri jipya"), "auth_reset_password", KeyboardType.Password, isPassword = true)
  PasswordHint(viewModel, password, email)
  Spacer(modifier = Modifier.height(8.dp))
  AuthField(confirm, { confirm = it }, viewModel.t("Repeat new password", "Rudia nenosiri jipya"), "auth_reset_confirm", KeyboardType.Password, isPassword = true)
  ErrorLine(error)
  Spacer(modifier = Modifier.height(12.dp))
  Button(
    onClick = {
      if (password != confirm) {
        onError(viewModel.t("The two passwords are not the same.", "Manenosiri mawili hayafanani."))
      } else {
        onSubmit(code, password)
      }
    },
    enabled = code.length == 6 && password.isNotEmpty(),
    colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
    modifier = Modifier.fillMaxWidth().testTag("auth_reset_btn")
  ) { Text(viewModel.t("Change password", "Badilisha nenosiri")) }
  TextButton(onClick = onBack) { Text(viewModel.t("Cancel", "Ghairi")) }
}

// ---------- Waiting room ----------

/** What a brand-new (or switched-off) account sees: nothing but its own status. */
@Composable
fun WaitingRoomScreen(viewModel: CampusViewModel) {
  val user by viewModel.currentUser.collectAsState()
  val language by viewModel.appLanguage.collectAsState()
  val notActive = user.status == AccountStatus.REJECTED

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .systemBarsPadding()
      .padding(20.dp)
      .testTag("waiting_room"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    LanguageSwitch(viewModel, language)
    Spacer(modifier = Modifier.weight(1f))
    Icon(
      imageVector = if (notActive) Icons.Default.Block else Icons.Default.HourglassTop,
      contentDescription = null,
      tint = if (notActive) DangerRed else WarmAmber,
      modifier = Modifier.size(56.dp)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = if (notActive) viewModel.t("Account not active", "Akaunti haifanyi kazi")
      else viewModel.t("Awaiting approval", "Inasubiri idhini"),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = if (notActive) {
        viewModel.t(
          "Please contact the Academic Registry.",
          "Tafadhali wasiliana na Msajili wa Taaluma."
        )
      } else {
        viewModel.t(
          "The Academic Registry checks every new account. You will get a notification as soon as you are approved.",
          "Msajili wa Taaluma hukagua kila akaunti mpya. Utapata arifa mara tu utakapoidhinishwa."
        )
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Card(
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(user.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          user.studentNumber ?: user.staffNumber ?: "",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    Spacer(modifier = Modifier.weight(1f))
    OutlinedButton(onClick = { viewModel.signOut() }, modifier = Modifier.fillMaxWidth().testTag("waiting_sign_out_btn")) {
      Text(viewModel.t("Sign out", "Toka"))
    }
  }
}

// ---------- Demo banner ----------

@Composable
fun DemoBanner(viewModel: CampusViewModel) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(DangerRed)
      .padding(vertical = 4.dp)
      .testTag("demo_banner"),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = viewModel.t("DEMO: sample data only", "ONYESHO: data za mfano tu"),
      color = Color.White,
      fontWeight = FontWeight.Bold,
      fontSize = 11.sp,
      letterSpacing = 0.8.sp
    )
  }
}

// ---------- Small pieces ----------

@Composable
private fun LanguageSwitch(viewModel: CampusViewModel, language: AppLanguage) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically
  ) {
    ChoiceChip("English", language == AppLanguage.ENGLISH, "lang_en") {
      if (language != AppLanguage.ENGLISH) viewModel.toggleLanguage()
    }
    Spacer(modifier = Modifier.width(6.dp))
    ChoiceChip("Kiswahili", language == AppLanguage.SWAHILI, "lang_sw") {
      if (language != AppLanguage.SWAHILI) viewModel.toggleLanguage()
    }
  }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = if (selected) DeepBlue else MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, if (selected) DeepBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = Modifier
      .clip(RoundedCornerShape(20.dp))
      .clickable { onClick() }
      .testTag(tag)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
      color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
    )
  }
}

@Composable
private fun AuthField(
  value: String,
  onChange: (String) -> Unit,
  label: String,
  tag: String,
  keyboard: KeyboardType = KeyboardType.Text,
  isPassword: Boolean = false
) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = true,
    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    modifier = Modifier.fillMaxWidth().testTag(tag)
  )
}

@Composable
private fun ErrorLine(error: String?) {
  if (error != null) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(error, color = DangerRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("auth_error"))
  }
}

@Composable
private fun PasswordHint(viewModel: CampusViewModel, password: String, email: String) {
  if (password.isEmpty()) return
  val check = viewModel.checkPassword(password, email)
  val (label, color) = when (check.strength) {
    PasswordStrength.WEAK -> viewModel.t("Weak", "Dhaifu") to DangerRed
    PasswordStrength.FAIR -> viewModel.t("Fair", "Wastani") to WarmAmber
    PasswordStrength.STRONG -> viewModel.t("Strong", "Imara") to SuccessGreen
  }
  Spacer(modifier = Modifier.height(4.dp))
  Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
  check.problems.forEach {
    Text("• " + viewModel.passwordProblemText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun PrototypeCodeNotice(viewModel: CampusViewModel, code: String) {
  Surface(shape = RoundedCornerShape(10.dp), color = WarmAmberLight, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        viewModel.t("Prototype notice", "Taarifa ya mfano"),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF78350F)
      )
      Text(
        viewModel.t(
          "No email server is connected yet, so the code is shown here: $code",
          "Seva ya barua pepe haijaunganishwa bado, kwa hiyo msimbo unaonyeshwa hapa: $code"
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF78350F),
        modifier = Modifier.testTag("prototype_code")
      )
    }
  }
}
