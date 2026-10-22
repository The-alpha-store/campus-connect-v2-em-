package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import com.example.ui.util.syncStatusText
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccountStatus
import com.example.data.model.Role
import com.example.ui.components.AnnouncementCard
import com.example.ui.components.DeadlineCard
import com.example.ui.components.OfflineBanner
import com.example.ui.components.SessionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmAmberLight
import com.example.viewmodel.CampusViewModel
import com.example.viewmodel.ScreenTab

@Composable
fun HomeScreen(
  viewModel: CampusViewModel,
  onNavigateToNotifications: () -> Unit,
  onOpenAnnouncementDetail: (String) -> Unit,
  onOpenPublishDialog: () -> Unit,
  onOpenChangeVenueDialog: (String) -> Unit,
  onOpenUploadDialog: () -> Unit,
  onOpenAddDeadlineDialog: () -> Unit,
  modifier: Modifier = Modifier
) {
  val user by viewModel.currentUser.collectAsState()
  val isOffline by viewModel.isOffline.collectAsState()
  val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
  val language by viewModel.appLanguage.collectAsState()
  val is24Hour by viewModel.is24HourFormat.collectAsState()
  val now by produceState(initialValue = System.currentTimeMillis()) {
    while (true) {
      delay(30_000L)
      value = System.currentTimeMillis()
    }
  }
  val sessions by viewModel.sessions.collectAsState()
  val announcements by viewModel.announcements.collectAsState()
  val deadlines by viewModel.deadlines.collectAsState()
  val pendingUsers by viewModel.pendingUsers.collectAsState()
  val unreadNotifs by viewModel.unreadNotificationsCount.collectAsState()

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(modifier = Modifier.height(8.dp))
      // Top Greeting Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          val greetingPrefix = viewModel.getString("greeting")
          val firstName = user.fullName.split(" ").firstOrNull() ?: user.fullName
          Text(
            text = "$greetingPrefix, $firstName 👋",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
          )
          Text(
            text = user.programme ?: "CampusConnect Tanzania",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // Notifications Bell
        IconButton(
          onClick = onNavigateToNotifications,
          modifier = Modifier.testTag("notification_bell_btn")
        ) {
          BadgedBox(
            badge = {
              if (unreadNotifs > 0) {
                Badge(
                  containerColor = DeepBlue,
                  contentColor = Color.White
                ) {
                  Text(text = "$unreadNotifs")
                }
              }
            }
          ) {
            Icon(
              imageVector = Icons.Default.Notifications,
              contentDescription = "Notifications",
              tint = MaterialTheme.colorScheme.onBackground,
              modifier = Modifier.size(26.dp)
            )
          }
        }
      }
    }

    // Pending Account Warning (if user status is PENDING)
    if (user.status == AccountStatus.PENDING) {
      item {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = WarmAmberLight),
          modifier = Modifier.fillMaxWidth().testTag("pending_status_card")
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.AddAlert,
              contentDescription = "Pending Approval",
              tint = Color(0xFFB45309),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Guest Access • Account Pending Registry Approval",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF78350F)
              )
              Text(
                text = "You have full access to view all timetables, announcements, study materials, and deadlines freely while waiting for verification.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF92400E)
              )
            }
          }
        }
      }
    }

    // Offline / Sync Status Banner
    item {
      OfflineBanner(
        isOffline = isOffline,
        statusText = syncStatusText(isOffline, lastSyncedAt, now, language),
        onSyncClick = { if (isOffline) viewModel.toggleOfflineSimulation() else viewModel.triggerSync() }
      )
    }

    // Role-specific Home Content
    when (user.role) {
      Role.STUDENT -> {
        // Next Upcoming Deadline
        val nextDeadline = deadlines.firstOrNull { !it.isCompleted }
        if (nextDeadline != null) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = viewModel.getString("next_deadline"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "View all",
                style = MaterialTheme.typography.labelMedium,
                color = DeepBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { viewModel.setTab(ScreenTab.CALENDAR) }
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            DeadlineCard(
              deadline = nextDeadline,
              onToggleComplete = { viewModel.toggleDeadlineCompleted(nextDeadline.id) }
            )
          }
        }

        // Highlighted Latest Announcement
        val topAnnouncement = announcements.firstOrNull()
        if (topAnnouncement != null) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = viewModel.getString("latest_announcement"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Inbox",
                style = MaterialTheme.typography.labelMedium,
                color = DeepBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { viewModel.setTab(ScreenTab.HOME) }
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            AnnouncementCard(
              announcement = topAnnouncement,
              showReadCount = user.role != Role.STUDENT,
              onClick = {
                viewModel.markAnnouncementRead(topAnnouncement.id)
                onOpenAnnouncementDetail(topAnnouncement.id)
              }
            )
          }
        }

        // Today's Classes Section
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = viewModel.getString("today_classes"),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Full Timetable",
              style = MaterialTheme.typography.labelMedium,
              color = DeepBlue,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.clickable { viewModel.setTab(ScreenTab.TIMETABLE) }
            )
          }
        }

        // Today's sessions (dayOfWeek == 1 or 2 as sample active day)
        val todaySessions = sessions.filter { it.dayOfWeek == 1 || it.dayOfWeek == 2 }
        items(todaySessions) { session ->
          SessionCard(
            session = session,
            currentUserRole = user.role,
            is24Hour = is24Hour
          )
        }
      }

      Role.LECTURER -> {
        // Lecturer Quick Actions
        item {
          Text(
            text = viewModel.getString("quick_actions"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            LecturerQuickActionButton(
              title = "Post Notice",
              icon = Icons.Default.Campaign,
              color = DeepBlue,
              onClick = onOpenPublishDialog,
              modifier = Modifier.weight(1f)
            )
            LecturerQuickActionButton(
              title = "Change Venue",
              icon = Icons.Default.EditCalendar,
              color = WarmAmber,
              onClick = {
                val firstSession = sessions.firstOrNull { it.lecturerName.contains("Mushi") }
                if (firstSession != null) onOpenChangeVenueDialog(firstSession.id)
              },
              modifier = Modifier.weight(1f)
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            LecturerQuickActionButton(
              title = "Upload Notes",
              icon = Icons.Default.CloudUpload,
              color = SuccessGreen,
              onClick = onOpenUploadDialog,
              modifier = Modifier.weight(1f)
            )
            LecturerQuickActionButton(
              title = "Add Deadline",
              icon = Icons.Default.CalendarMonth,
              color = Color(0xFF7C3AED),
              onClick = onOpenAddDeadlineDialog,
              modifier = Modifier.weight(1f)
            )
          }
        }

        // Lecturer's Assigned Courses
        item {
          Text(
            text = viewModel.getString("my_assigned_courses"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(8.dp))
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = "CS210 • Data Structures & Algorithms",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "BSc Computer Science • 54 Enrolled Students",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "CS215 • Object-Oriented Software Engineering",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "BSc Computer Science • 54 Enrolled Students",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // Lecturer's Recent Posts & Read Receipts
        item {
          Text(
            text = "Recent Announcements & Read Receipts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        val lecturerPosts = announcements.filter { it.authorId == user.id }
        items(lecturerPosts) { announcement ->
          AnnouncementCard(
            announcement = announcement,
            showReadCount = true,
            onClick = { onOpenAnnouncementDetail(announcement.id) }
          )
        }
      }

      Role.ADMIN -> {
        // Admin Summary
        item {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DeepBlue),
            modifier = Modifier.fillMaxWidth().testTag("admin_summary_card")
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Administrator Control Center",
                  style = MaterialTheme.typography.titleMedium,
                  color = Color.White,
                  fontWeight = FontWeight.Bold
                )
                Icon(
                  imageVector = Icons.Default.VerifiedUser,
                  contentDescription = "Admin",
                  tint = Color.White
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Academic Registry & Faculty Schedule Management",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
              )
              Spacer(modifier = Modifier.height(14.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                AdminStatItem("Pending Users", "${pendingUsers.size}", WarmAmberLight)
                AdminStatItem("Active Sessions", "${sessions.size}", Color(0xFFDCFCE7))
                AdminStatItem("Announcements", "${announcements.size}", Color(0xFFE0E7FF))
              }
              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = { viewModel.setTab(ScreenTab.ADMIN) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().testTag("go_to_admin_portal_btn")
              ) {
                Text(
                  text = "Open Approvals & Audit Log (${pendingUsers.size} pending)",
                  color = DeepBlue,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }

        item {
          Text(
            text = "Active Timetable Schedule Overview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        items(sessions.take(4)) { session ->
          SessionCard(
            session = session,
            currentUserRole = user.role,
            is24Hour = is24Hour,
            onEditClick = { onOpenChangeVenueDialog(session.id) }
          )
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
fun LecturerQuickActionButton(
  title: String,
  icon: ImageVector,
  color: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .clickable { onClick() }
      .testTag("action_${title.lowercase().replace(" ", "_")}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = color,
          modifier = Modifier.size(18.dp)
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
fun AdminStatItem(label: String, value: String, bgColor: Color) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(Color.White.copy(alpha = 0.15f))
      .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = Color.White
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = Color.White.copy(alpha = 0.85f)
    )
  }
}
