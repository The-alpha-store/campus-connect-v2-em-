package com.example.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AccountStatus
import com.example.data.model.Announcement
import com.example.data.model.AttachmentFile
import com.example.data.model.DeadlineItem
import com.example.data.model.Role
import com.example.data.model.StudentSubmission
import com.example.data.model.StudyMaterial
import com.example.ui.components.AddDeadlineDialog
import com.example.ui.components.AnnouncementDetailDialog
import com.example.ui.components.CancelSessionDialog
import com.example.ui.components.ChangeVenueDialog
import com.example.ui.components.DocumentViewerDialog
import com.example.ui.components.EditAnnouncementDialog
import com.example.ui.components.GradeSubmissionDialog
import com.example.ui.components.PublishAnnouncementDialog
import com.example.ui.components.SubmitWorkDialog
import com.example.ui.components.UploadMaterialDialog
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthGate
import com.example.ui.screens.DemoBanner
import com.example.ui.screens.WaitingRoomScreen
import com.example.ui.screens.AnnouncementsScreen
import com.example.ui.screens.DeadlinesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MaterialsScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SubmissionsScreen
import com.example.ui.screens.TimetableScreen
import com.example.ui.theme.CampusConnectTheme
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.WarmAmber
import com.example.viewmodel.CampusViewModel
import com.example.viewmodel.ScreenTab

/**
 * Root of the app. Decides what a person may see:
 *  - not signed in -> sign-in screens
 *  - signed in but not approved by the registry -> waiting room, nothing else
 *  - approved (or demo mode) -> the app
 */
@Composable
fun CampusConnectApp(
  viewModel: CampusViewModel = viewModel()
) {
  val session by viewModel.session.collectAsState()
  val user by viewModel.currentUser.collectAsState()
  val current = session

  CampusConnectTheme {
    when {
      current == null -> AuthGate(viewModel)
      !current.isDemo && user.status != AccountStatus.APPROVED -> WaitingRoomScreen(viewModel)
      else -> CampusMainApp(viewModel)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusMainApp(viewModel: CampusViewModel) {
  val isDemo by viewModel.isDemoMode.collectAsState()
  val message by viewModel.message.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(message) {
    message?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.messageShown()
    }
  }
  val remindedIds by viewModel.remindedAnnouncements.collectAsState()
  val currentTab by viewModel.currentTab.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val isOffline by viewModel.isOffline.collectAsState()
  val unreadNotifs by viewModel.unreadNotificationsCount.collectAsState()
  val pendingUsers by viewModel.pendingUsers.collectAsState()
  val submissions by viewModel.submissions.collectAsState()

  val sessions by viewModel.sessions.collectAsState()
  val announcements by viewModel.announcements.collectAsState()

  // Dialog & Viewer states
  var selectedAnnouncementId by remember { mutableStateOf<String?>(null) }
  var showPublishDialog by remember { mutableStateOf(false) }
  var showUploadDialog by remember { mutableStateOf(false) }
  var showAddDeadlineDialog by remember { mutableStateOf(false) }
  var venueChangeSessionId by remember { mutableStateOf<String?>(null) }
  var cancelSessionId by remember { mutableStateOf<String?>(null) }

  var editingAnnouncement by remember { mutableStateOf<Announcement?>(null) }
  var submitWorkDeadline by remember { mutableStateOf<DeadlineItem?>(null) }
  var gradingSubmission by remember { mutableStateOf<StudentSubmission?>(null) }

  // Built-in Document Viewer state
  var viewingDocument by remember { mutableStateOf<AttachmentFile?>(null) }
  var viewingDocAuthor by remember { mutableStateOf<String?>(null) }
  var viewingDocCourse by remember { mutableStateOf<String?>(null) }

  CampusConnectTheme {
    Scaffold(
      modifier = Modifier
        .fillMaxSize()
        .testTag("campus_connect_main_scaffold"),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(DeepBlue),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.School,
                  contentDescription = "Logo",
                  tint = Color.White,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "CampusConnect",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  // Role Badge
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(
                        when (currentUser.role) {
                          Role.ADMIN -> Color(0xFFDCFCE7)
                          Role.LECTURER -> WarmAmber.copy(alpha = 0.18f)
                          Role.STUDENT -> DeepBlue.copy(alpha = 0.12f)
                        }
                      )
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    Text(
                      text = currentUser.role.name,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = when (currentUser.role) {
                        Role.ADMIN -> Color(0xFF166534)
                        Role.LECTURER -> Color(0xFFB45309)
                        Role.STUDENT -> DeepBlue
                      },
                      fontSize = 9.sp
                    )
                  }
                }
                Text(
                  text = if (isOffline) viewModel.getString("offline_notice") else "${currentUser.fullName.split(" ").firstOrNull() ?: ""} • Tanzania",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isOffline) WarmAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          },
          actions = {
            // Quick access to student coursework submissions
            IconButton(
              onClick = { viewModel.setTab(ScreenTab.SUBMISSIONS) },
              modifier = Modifier.testTag("top_bar_submissions_btn")
            ) {
              BadgedBox(
                badge = {
                  val pendingReviewCount = submissions.count { it.grade == null }
                  if (pendingReviewCount > 0 && currentUser.role != Role.STUDENT) {
                    Badge(containerColor = WarmAmber, contentColor = Color.White) {
                      Text("$pendingReviewCount")
                    }
                  }
                }
              ) {
                Icon(
                  imageVector = Icons.Default.UploadFile,
                  contentDescription = "Submissions",
                  tint = if (currentTab == ScreenTab.SUBMISSIONS) DeepBlue else MaterialTheme.colorScheme.onSurface
                )
              }
            }

            if (currentUser.role == Role.ADMIN) {
              IconButton(
                onClick = { viewModel.setTab(ScreenTab.ADMIN) },
                modifier = Modifier.testTag("top_bar_admin_btn")
              ) {
                BadgedBox(
                  badge = {
                    if (pendingUsers.isNotEmpty()) {
                      Badge(containerColor = WarmAmber, contentColor = Color.White) {
                        Text("${pendingUsers.size}")
                      }
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Admin Portal",
                    tint = if (currentTab == ScreenTab.ADMIN) DeepBlue else MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }

            IconButton(
              onClick = { viewModel.setTab(ScreenTab.NOTIFICATIONS) },
              modifier = Modifier.testTag("top_bar_notif_btn")
            ) {
              BadgedBox(
                badge = {
                  if (unreadNotifs > 0) {
                    Badge(containerColor = DeepBlue, contentColor = Color.White) {
                      Text("$unreadNotifs")
                    }
                  }
                }
              ) {
                Icon(
                  imageVector = Icons.Default.Notifications,
                  contentDescription = "Notifications",
                  tint = if (currentTab == ScreenTab.NOTIFICATIONS) DeepBlue else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        )
      },
      bottomBar = {
        NavigationBar(
          containerColor = MaterialTheme.colorScheme.surface,
          modifier = Modifier.testTag("bottom_nav_bar")
        ) {
          NavigationBarItem(
            selected = currentTab == ScreenTab.HOME,
            onClick = { viewModel.setTab(ScreenTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text(viewModel.getString("tab_home")) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = DeepBlue.copy(alpha = 0.15f)),
            modifier = Modifier.testTag("nav_item_home")
          )

          NavigationBarItem(
            selected = currentTab == ScreenTab.TIMETABLE,
            onClick = { viewModel.setTab(ScreenTab.TIMETABLE) },
            icon = { Icon(Icons.Default.CalendarToday, contentDescription = "Timetable") },
            label = { Text(viewModel.getString("tab_timetable")) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = DeepBlue.copy(alpha = 0.15f)),
            modifier = Modifier.testTag("nav_item_timetable")
          )

          NavigationBarItem(
            selected = currentTab == ScreenTab.MATERIALS,
            onClick = { viewModel.setTab(ScreenTab.MATERIALS) },
            icon = { Icon(Icons.Default.Folder, contentDescription = "Materials") },
            label = { Text(viewModel.getString("tab_materials")) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = DeepBlue.copy(alpha = 0.15f)),
            modifier = Modifier.testTag("nav_item_materials")
          )

          NavigationBarItem(
            selected = currentTab == ScreenTab.CALENDAR,
            onClick = { viewModel.setTab(ScreenTab.CALENDAR) },
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Deadlines") },
            label = { Text(viewModel.getString("tab_calendar")) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = DeepBlue.copy(alpha = 0.15f)),
            modifier = Modifier.testTag("nav_item_calendar")
          )

          NavigationBarItem(
            selected = currentTab == ScreenTab.PROFILE,
            onClick = { viewModel.setTab(ScreenTab.PROFILE) },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text(viewModel.getString("tab_profile")) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = DeepBlue.copy(alpha = 0.15f)),
            modifier = Modifier.testTag("nav_item_profile")
          )
        }
      }
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
      if (isDemo) DemoBanner(viewModel)
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        when (currentTab) {
          ScreenTab.HOME -> HomeScreen(
            viewModel = viewModel,
            onNavigateToNotifications = { viewModel.setTab(ScreenTab.NOTIFICATIONS) },
            onOpenAnnouncementDetail = { selectedAnnouncementId = it },
            onOpenPublishDialog = { showPublishDialog = true },
            onOpenChangeVenueDialog = { venueChangeSessionId = it },
            onOpenUploadDialog = { showUploadDialog = true },
            onOpenAddDeadlineDialog = { showAddDeadlineDialog = true }
          )

          ScreenTab.TIMETABLE -> TimetableScreen(
            viewModel = viewModel,
            onOpenChangeVenueDialog = { venueChangeSessionId = it },
            onOpenCancelDialog = { cancelSessionId = it }
          )

          ScreenTab.MATERIALS -> MaterialsScreen(
            viewModel = viewModel,
            onOpenUploadDialog = { showUploadDialog = true },
            onPreviewDocument = { file, author, course ->
              viewingDocument = file
              viewingDocAuthor = author
              viewingDocCourse = course
            }
          )

          ScreenTab.CALENDAR -> DeadlinesScreen(
            viewModel = viewModel,
            onOpenAddDeadlineDialog = { showAddDeadlineDialog = true },
            onSubmitWorkClick = { dl -> submitWorkDeadline = dl }
          )

          ScreenTab.SUBMISSIONS -> SubmissionsScreen(
            viewModel = viewModel,
            onPreviewDocument = { file ->
              viewingDocument = file
              viewingDocAuthor = null
              viewingDocCourse = null
            },
            onGradeSubmission = { sub -> gradingSubmission = sub }
          )

          ScreenTab.PROFILE -> ProfileScreen(
            viewModel = viewModel
          )

          ScreenTab.ADMIN -> AdminScreen(
            viewModel = viewModel
          )

          ScreenTab.NOTIFICATIONS -> NotificationsScreen(
            viewModel = viewModel
          )
        }
      }
      }

      // Announcement Detail Dialog
      selectedAnnouncementId?.let { id ->
        val announcement = announcements.find { it.id == id }
        if (announcement != null) {
          AnnouncementDetailDialog(
            announcement = announcement,
            currentUserRole = currentUser.role,
            canModerate = viewModel.canEditOrDelete(announcement.authorId),
            onDismiss = { selectedAnnouncementId = null },
            onPreviewAttachment = { file ->
              viewingDocument = file
              viewingDocAuthor = announcement.authorName
              viewingDocCourse = announcement.audienceName
            },
            onEditClick = {
              selectedAnnouncementId = null
              editingAnnouncement = announcement
            },
            onDeleteClick = {
              viewModel.deleteAnnouncement(announcement.id)
              selectedAnnouncementId = null
            },
            onToggleApproval = { status ->
              viewModel.setAnnouncementApproval(announcement.id, status)
            },
            showReadStats = currentUser.role != Role.STUDENT && viewModel.canEditOrDelete(announcement.authorId),
            reminderSent = announcement.id in remindedIds,
            onRemindClick = { viewModel.remindUnread(announcement.id) },
            onRetractClick = {
              viewModel.retractAnnouncement(announcement.id)
              selectedAnnouncementId = null
            }
          )
        }
      }

      // Publish Announcement Dialog
      if (showPublishDialog) {
        PublishAnnouncementDialog(
          options = viewModel.audienceOptions(),
          urgentLeft = viewModel.urgentLeftToday(),
          canUseUrgentFor = { viewModel.canUseUrgentFor(it) },
          onDismiss = { showPublishDialog = false },
          onPublish = { title, body, audienceType, audienceName, audienceCode, priority, attachment ->
            viewModel.publishAnnouncement(title, body, audienceType, audienceName, audienceCode, priority, attachment)
          },
          onPreviewAttachment = { file ->
            viewingDocument = file
          }
        )
      }

      // Edit Announcement Dialog
      editingAnnouncement?.let { ann ->
        EditAnnouncementDialog(
          announcement = ann,
          onDismiss = { editingAnnouncement = null },
          onSave = { newTitle, newBody, newPriority ->
            viewModel.updateAnnouncement(ann.id, newTitle, newBody, newPriority)
          }
        )
      }

      // Upload Study Material Dialog
      if (showUploadDialog) {
        UploadMaterialDialog(
          onDismiss = { showUploadDialog = false },
          defaultCourseCode = currentUser.teachesCourses.minOrNull() ?: "CS210",
          onUpload = { title, code, courseTitle, desc, attachment ->
            viewModel.uploadMaterial(title, code, courseTitle, desc, attachment)
          },
          onPreviewAttachment = { file ->
            viewingDocument = file
          }
        )
      }

      // Add Deadline Dialog
      if (showAddDeadlineDialog) {
        AddDeadlineDialog(
          onDismiss = { showAddDeadlineDialog = false },
          defaultCourseCode = currentUser.teachesCourses.minOrNull() ?: "CS210",
          onAdd = { code, courseTitle, title, instructions, dueDateTime, formats ->
            viewModel.addDeadline(code, courseTitle, title, instructions, dueDateTime, formats)
          }
        )
      }

      // Student Coursework Submit Dialog
      submitWorkDeadline?.let { dl ->
        SubmitWorkDialog(
          deadline = dl,
          onDismiss = { submitWorkDeadline = null },
          onSubmit = { notes, file ->
            viewModel.submitStudentWork(
              deadlineId = dl.id,
              courseCode = dl.courseCode,
              assignmentTitle = dl.title,
              notes = notes,
              attachment = file
            )
          },
          onPreviewFile = { file ->
            viewingDocument = file
            viewingDocAuthor = currentUser.fullName
            viewingDocCourse = dl.courseCode
          }
        )
      }

      // Lecturer / Admin Grade Submission Dialog
      gradingSubmission?.let { sub ->
        GradeSubmissionDialog(
          submission = sub,
          onDismiss = { gradingSubmission = null },
          onPreviewDocument = { file ->
            viewingDocument = file
            viewingDocAuthor = sub.studentName
            viewingDocCourse = sub.courseCode
          },
          onGradeSubmitted = { grade, feedback ->
            viewModel.gradeStudentWork(sub.id, grade, feedback)
          }
        )
      }

      // Change Venue Dialog
      venueChangeSessionId?.let { sId ->
        val session = sessions.find { it.id == sId }
        if (session != null) {
          ChangeVenueDialog(
            session = session,
            onDismiss = { venueChangeSessionId = null },
            onConfirm = { newVenue, reason ->
              viewModel.updateSessionVenue(sId, newVenue, reason)
            }
          )
        }
      }

      // Cancel Session Dialog
      cancelSessionId?.let { sId ->
        val session = sessions.find { it.id == sId }
        if (session != null) {
          CancelSessionDialog(
            session = session,
            onDismiss = { cancelSessionId = null },
            onConfirm = { reason ->
              viewModel.cancelSession(sId, reason)
            }
          )
        }
      }

      // Built-in Document Viewer Dialog
      viewingDocument?.let { doc ->
        DocumentViewerDialog(
          attachment = doc,
          authorOrUploaderName = viewingDocAuthor,
          courseCode = viewingDocCourse,
          onDismiss = {
            viewingDocument = null
            viewingDocAuthor = null
            viewingDocCourse = null
          }
        )
      }
    }
  }
}
