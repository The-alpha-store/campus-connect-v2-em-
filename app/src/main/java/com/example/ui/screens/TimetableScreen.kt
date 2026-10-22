package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassSession
import com.example.data.model.Role
import com.example.data.model.SessionStatus
import com.example.ui.components.SessionCard
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmAmberLight
import com.example.viewmodel.CampusViewModel

@Composable
fun TimetableScreen(
  viewModel: CampusViewModel,
  onOpenChangeVenueDialog: (String) -> Unit,
  onOpenCancelDialog: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val user by viewModel.currentUser.collectAsState()
  val sessions by viewModel.sessions.collectAsState()
  val is24Hour by viewModel.is24HourFormat.collectAsState()
  val selectedDay by viewModel.selectedDay.collectAsState()
  val isWeekView by viewModel.isWeekView.collectAsState()

  val days = listOf(
    1 to "Mon",
    2 to "Tue",
    3 to "Wed",
    4 to "Thu",
    5 to "Fri",
    6 to "Sat"
  )

  val filteredSessions = if (isWeekView) {
    sessions
  } else {
    sessions.filter { it.dayOfWeek == selectedDay }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Spacer(modifier = Modifier.height(8.dp))

    // Header & Week Toggle
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = viewModel.getString("tab_timetable"),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "East Africa Time (EAT) • Semester 1",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (isWeekView) DeepBlue else MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { viewModel.toggleWeekView() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("toggle_week_view_btn")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = if (isWeekView) Icons.Default.ViewAgenda else Icons.Default.ViewWeek,
              contentDescription = "Toggle view",
              modifier = Modifier.size(16.dp),
              tint = if (isWeekView) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (isWeekView) "Day View" else "Week View",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = if (isWeekView) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Day Strip (only active if not in full week view)
    if (!isWeekView) {
      val today = remember { viewModel.todayDay() }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        days.forEach { (dayNum, dayLabel) ->
          val isSelected = selectedDay == dayNum
          val isToday = today == dayNum
          val count = sessions.count { it.dayOfWeek == dayNum }
          val hasChanges = sessions.any { it.dayOfWeek == dayNum && it.status == SessionStatus.CHANGED }

          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(14.dp))
              .background(if (isSelected) DeepBlue else MaterialTheme.colorScheme.surface)
              .border(
                width = if (isToday && !isSelected) 1.5.dp else 1.dp,
                color = when {
                  isSelected -> DeepBlue
                  isToday -> DeepBlue
                  else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(14.dp)
              )
              .clickable { viewModel.setSelectedDay(dayNum) }
              .padding(vertical = 10.dp)
              .testTag("day_strip_$dayLabel"),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = dayLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(3.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = if (count > 0) "$count" else "\u2013",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasChanges) {
                  Spacer(modifier = Modifier.width(3.dp))
                  Box(
                    modifier = Modifier
                      .size(6.dp)
                      .clip(RoundedCornerShape(3.dp))
                      .background(if (isSelected) WarmAmberLight else WarmAmber)
                  )
                }
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(14.dp))
    }

    // Sessions List
    if (filteredSessions.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = "No classes",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "No sessions scheduled",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Enjoy your day off or review course materials",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(filteredSessions, key = { it.id }) { session ->
          Box(modifier = Modifier.animateItem()) {
          SessionCard(
            session = session,
            currentUserRole = user.role,
            is24Hour = is24Hour,
            canModerate = viewModel.canEditOrDelete(session.lecturerId),
            onEditClick = { onOpenChangeVenueDialog(session.id) },
            onCancelClick = { onOpenCancelDialog(session.id) },
            onDeleteClick = if (viewModel.canEditOrDelete(session.lecturerId)) { { viewModel.deleteSession(session.id) } } else null
          )
        
          }
        }

        item {
          Spacer(modifier = Modifier.height(24.dp))
        }
      }
    }
  }
}
