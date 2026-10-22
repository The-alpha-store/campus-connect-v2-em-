package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Announcement
import com.example.data.model.AnnouncementPriority
import com.example.data.model.AudienceType
import com.example.data.model.ClassSession
import com.example.data.model.ContentApprovalStatus
import com.example.data.model.DeadlineItem
import com.example.data.model.Role
import com.example.data.model.SessionStatus
import com.example.ui.util.formatClock
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedLight
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenLight
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmAmberLight

@Composable
fun StatusBadge(
  text: String,
  backgroundColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(backgroundColor)
      .padding(horizontal = 8.dp, vertical = 3.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = contentColor,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
fun OfflineBanner(
  isOffline: Boolean,
  statusText: String,
  onSyncClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isOffline) WarmAmberLight else MaterialTheme.colorScheme.surfaceVariant
    ),
    modifier = modifier
      .fillMaxWidth()
      .testTag("offline_banner_card")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.Sync,
          contentDescription = "Sync Status",
          tint = if (isOffline) WarmAmber else DeepBlue,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isOffline) Color(0xFF78350F) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("sync_status_text")
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isOffline) WarmAmber else DeepBlue,
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable { onSyncClick() }
          .padding(horizontal = 10.dp, vertical = 5.dp)
          .testTag("sync_button")
      ) {
        Text(
          text = if (isOffline) "Go Online" else "Sync",
          color = Color.White,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
fun SessionCard(
  session: ClassSession,
  currentUserRole: Role,
  is24Hour: Boolean = true,
  canModerate: Boolean = false,
  onEditClick: (() -> Unit)? = null,
  onCancelClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val isChanged = session.status == SessionStatus.CHANGED
  val isCancelled = session.status == SessionStatus.CANCELLED

  val borderColor = when (session.status) {
    SessionStatus.CHANGED -> WarmAmber
    SessionStatus.CANCELLED -> DangerRed
    SessionStatus.SCHEDULED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
  }

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = if (isChanged || isCancelled) 1.5.dp else 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(14.dp)
      )
      .testTag("session_card_${session.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(DeepBlue)
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = session.courseCode,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.labelSmall
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.AccessTime,
              contentDescription = "Time",
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "${formatClock(session.startTime, is24Hour)} - ${formatClock(session.endTime, is24Hour)} EAT",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.Medium
            )
          }
        }

        // Status Badge
        when (session.status) {
          SessionStatus.CHANGED -> {
            StatusBadge(
              text = "Room changed",
              backgroundColor = WarmAmberLight,
              contentColor = Color(0xFFB45309)
            )
          }
          SessionStatus.CANCELLED -> {
            StatusBadge(
              text = "Cancelled",
              backgroundColor = DangerRedLight,
              contentColor = DangerRed
            )
          }
          SessionStatus.SCHEDULED -> {
            StatusBadge(
              text = "Confirmed",
              backgroundColor = SuccessGreenLight,
              contentColor = SuccessGreen
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = session.courseTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None,
        color = if (isCancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(6.dp))

      // Venue Row
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.LocationOn,
          contentDescription = "Venue",
          modifier = Modifier.size(16.dp),
          tint = if (isChanged) WarmAmber else DeepBlue
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = session.venue,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = if (isChanged) WarmAmber else MaterialTheme.colorScheme.onSurface
        )
      }

      // Change or cancellation note
      if (!session.changeReason.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCancelled) DangerRedLight.copy(alpha = 0.5f) else WarmAmberLight.copy(alpha = 0.5f))
            .padding(8.dp)
        ) {
          Row(verticalAlignment = Alignment.Top) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = "Notice",
              tint = if (isCancelled) DangerRed else WarmAmber,
              modifier = Modifier
                .size(14.dp)
                .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = session.changeReason,
              style = MaterialTheme.typography.bodySmall,
              color = if (isCancelled) Color(0xFF991B1B) else Color(0xFF78350F)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Lecturer & Action buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "Lecturer",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = session.lecturerName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // Controls for Admin or Assigned Lecturer
        if (currentUserRole == Role.ADMIN || canModerate) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isCancelled && onCancelClick != null) {
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = DangerRedLight,
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .clickable { onCancelClick() }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
                  .testTag("cancel_session_${session.id}")
              ) {
                Text(
                  text = "Cancel",
                  color = DangerRed,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold
                )
              }
              Spacer(modifier = Modifier.width(6.dp))
            }

            if (onEditClick != null) {
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = DeepBlue.copy(alpha = 0.1f),
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .clickable { onEditClick() }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
                  .testTag("change_room_${session.id}")
              ) {
                Text(
                  text = "Relocate",
                  color = DeepBlue,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            if (onDeleteClick != null && currentUserRole == Role.ADMIN) {
              Spacer(modifier = Modifier.width(4.dp))
              IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(28.dp).testTag("delete_session_${session.id}")
              ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed, modifier = Modifier.size(16.dp))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun AnnouncementCard(
  announcement: Announcement,
  onClick: () -> Unit,
  showReadCount: Boolean = false,
  modifier: Modifier = Modifier
) {
  val isUrgent = announcement.priority == AnnouncementPriority.URGENT

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .border(
        width = 1.dp,
        color = if (isUrgent) DangerRed.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        shape = RoundedCornerShape(14.dp)
      )
      .testTag("announcement_card_${announcement.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Audience badge
          val audienceBg = when (announcement.audienceType) {
            AudienceType.CAMPUS -> DeepBlue.copy(alpha = 0.15f)
            AudienceType.PROGRAMME -> WarmAmber.copy(alpha = 0.15f)
            AudienceType.COURSE -> SuccessGreen.copy(alpha = 0.15f)
          }
          val audienceText = when (announcement.audienceType) {
            AudienceType.CAMPUS -> DeepBlue
            AudienceType.PROGRAMME -> Color(0xFFB45309)
            AudienceType.COURSE -> SuccessGreen
          }

          StatusBadge(
            text = announcement.audienceName,
            backgroundColor = audienceBg,
            contentColor = audienceText
          )

          Spacer(modifier = Modifier.width(6.dp))
          when (announcement.priority) {
            AnnouncementPriority.URGENT -> StatusBadge(
              text = "URGENT",
              backgroundColor = DangerRedLight,
              contentColor = DangerRed
            )
            AnnouncementPriority.ACADEMIC -> StatusBadge(
              text = "ACADEMIC",
              backgroundColor = DeepBlue.copy(alpha = 0.12f),
              contentColor = DeepBlue
            )
            AnnouncementPriority.NORMAL -> StatusBadge(
              text = "NORMAL",
              backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
              contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          if (announcement.isRetracted) {
            Spacer(modifier = Modifier.width(6.dp))
            StatusBadge(
              text = "Withdrawn",
              backgroundColor = DangerRedLight,
              contentColor = DangerRed
            )
          }

          if (announcement.approvalStatus == ContentApprovalStatus.PENDING_REVIEW) {
            Spacer(modifier = Modifier.width(6.dp))
            StatusBadge(
              text = "Pending Review",
              backgroundColor = WarmAmberLight,
              contentColor = Color(0xFFB45309)
            )
          }
        }

        // Unread badge or read count
        if (!announcement.isRead) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(DeepBlue)
          )
        } else if (showReadCount) {
          // Counts are for the sender and the registry. A student never sees who read what.
          Text(
            text = "${announcement.readCount} read",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = announcement.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = announcement.body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
      )

      // Attachment preview
      if (announcement.attachment != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Attachment,
            contentDescription = "Attachment",
            tint = DeepBlue,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "${announcement.attachment.fileName} (${announcement.attachment.fileSize})",
            style = MaterialTheme.typography.labelSmall,
            color = DeepBlue,
            fontWeight = FontWeight.Medium
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = announcement.authorName + " • " + announcement.authorRole,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.Medium
        )

        Text(
          text = announcement.publishedAt,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun DeadlineCard(
  deadline: DeadlineItem,
  onToggleComplete: () -> Unit,
  onSubmitClick: (() -> Unit)? = null,
  onEditClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  canModerate: Boolean = false,
  modifier: Modifier = Modifier
) {
  val isCompleted = deadline.isCompleted

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = if (isCompleted) SuccessGreen.copy(alpha = 0.3f) else WarmAmber.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp)
      )
      .testTag("deadline_card_${deadline.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
      ) {
        // Checkbox
        Box(
          modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.surfaceVariant)
            .border(
              width = 1.5.dp,
              color = if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.outline,
              shape = RoundedCornerShape(8.dp)
            )
            .clickable { onToggleComplete() }
            .testTag("toggle_complete_${deadline.id}"),
          contentAlignment = Alignment.Center
        ) {
          if (isCompleted) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Completed",
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = deadline.courseCode,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = DeepBlue
            )

            StatusBadge(
              text = if (isCompleted) "Submitted/Done" else deadline.urgencyText,
              backgroundColor = if (isCompleted) SuccessGreenLight else WarmAmberLight,
              contentColor = if (isCompleted) SuccessGreen else Color(0xFFB45309)
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = deadline.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = deadline.instructions,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )

          Spacer(modifier = Modifier.height(6.dp))

          // Lecturer Preferences format note
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Preferences: ${deadline.acceptedFormats}",
              style = MaterialTheme.typography.labelSmall,
              color = DeepBlue,
              fontWeight = FontWeight.Medium
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "Due",
                tint = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else WarmAmber,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = deadline.dueDateTime,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else WarmAmber
              )
            }

            // Submit Coursework button
            if (onSubmitClick != null) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = DeepBlue,
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { onSubmitClick() }
                  .padding(horizontal = 10.dp, vertical = 6.dp)
                  .testTag("submit_work_btn_${deadline.id}")
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.UploadFile, contentDescription = "Submit", tint = Color.White, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (isCompleted) "Resubmit" else "Submit Work",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      }

      // Moderation controls for Admin/Lecturer
      if (canModerate) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          if (onEditClick != null) {
            IconButton(onClick = onEditClick, modifier = Modifier.size(28.dp).testTag("edit_dl_${deadline.id}")) {
              Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DeepBlue, modifier = Modifier.size(16.dp))
            }
          }
          if (onDeleteClick != null) {
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp).testTag("delete_dl_${deadline.id}")) {
              Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }
  }
}
