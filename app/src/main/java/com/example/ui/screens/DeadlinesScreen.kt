package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DeadlineItem
import com.example.data.model.Role
import com.example.ui.components.DeadlineCard
import com.example.ui.theme.DeepBlue
import com.example.viewmodel.CampusViewModel
import com.example.viewmodel.ScreenTab

@Composable
fun DeadlinesScreen(
  viewModel: CampusViewModel,
  onOpenAddDeadlineDialog: () -> Unit,
  onSubmitWorkClick: (DeadlineItem) -> Unit,
  modifier: Modifier = Modifier
) {
  val user by viewModel.currentUser.collectAsState()
  val deadlines by viewModel.deadlines.collectAsState()

  val pendingDeadlines = deadlines.filter { !it.isCompleted }
  val completedDeadlines = deadlines.filter { it.isCompleted }

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = viewModel.getString("tab_calendar"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Assignments, CATs & Submission Dropboxes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // Shortcut to Submissions tab
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = DeepBlue.copy(alpha = 0.12f),
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { viewModel.setTab(ScreenTab.SUBMISSIONS) }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("nav_to_submissions_btn")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AssignmentTurnedIn, contentDescription = "Submissions", tint = DeepBlue, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (user.role == Role.STUDENT) "My Works" else "All Submissions",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = DeepBlue
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (pendingDeadlines.isNotEmpty()) {
          item {
            Text(
              text = "Upcoming Deadlines (${pendingDeadlines.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          items(pendingDeadlines) { dl ->
            val canModerate = viewModel.canEditOrDelete(dl.lecturerId)

            DeadlineCard(
              deadline = dl,
              onToggleComplete = { viewModel.toggleDeadlineCompleted(dl.id) },
              onSubmitClick = { onSubmitWorkClick(dl) },
              onDeleteClick = if (canModerate) { { viewModel.deleteDeadline(dl.id) } } else null,
              canModerate = canModerate
            )
          }
        }

        if (completedDeadlines.isNotEmpty()) {
          item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Submitted / Completed (${completedDeadlines.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          items(completedDeadlines) { dl ->
            val canModerate = viewModel.canEditOrDelete(dl.lecturerId)

            DeadlineCard(
              deadline = dl,
              onToggleComplete = { viewModel.toggleDeadlineCompleted(dl.id) },
              onSubmitClick = { onSubmitWorkClick(dl) },
              onDeleteClick = if (canModerate) { { viewModel.deleteDeadline(dl.id) } } else null,
              canModerate = canModerate
            )
          }
        }

        item {
          Spacer(modifier = Modifier.height(80.dp))
        }
      }
    }

    if (user.role == Role.LECTURER || user.role == Role.ADMIN) {
      FloatingActionButton(
        onClick = onOpenAddDeadlineDialog,
        containerColor = DeepBlue,
        contentColor = Color.White,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(20.dp)
          .testTag("fab_add_deadline")
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Create Deadline"
        )
      }
    }
  }
}
