package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import com.example.data.model.AttachmentFile
import com.example.data.model.DeadlineItem
import com.example.data.model.StudentSubmission
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmAmber

@Composable
fun SubmitWorkDialog(
  deadline: DeadlineItem,
  onDismiss: () -> Unit,
  onSubmit: (notes: String, file: AttachmentFile) -> Unit,
  onPreviewFile: (AttachmentFile) -> Unit
) {
  var selectedFile by remember { mutableStateOf<AttachmentFile?>(null) }
  var studentNotes by remember { mutableStateOf("") }

  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
        .testTag("submit_work_dialog_modal")
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.AssignmentTurnedIn,
              contentDescription = "Submit",
              tint = DeepBlue
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Submit Coursework",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Assignment Details Card
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "${deadline.courseCode}: ${deadline.title}",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = DeepBlue
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Due: ${deadline.dueDateTime}",
              style = MaterialTheme.typography.labelSmall,
              color = WarmAmber,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Lecturer Preferences: ${deadline.acceptedFormats}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "Upload Solution Document / Image:",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Integrated File Picker Component
        CampusFilePicker(
          selectedFile = selectedFile,
          onFileSelected = { selectedFile = it },
          onClearFile = { selectedFile = null },
          onPreviewFile = onPreviewFile,
          title = "Select Coursework PDF or Photo",
          allowedTypesDescription = deadline.acceptedFormats
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
          value = studentNotes,
          onValueChange = { studentNotes = it },
          label = { Text("Submission Notes / Comments (Optional)") },
          placeholder = { Text("e.g. Solved with Dijkstra and unit tests...") },
          modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .testTag("submission_notes_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = {
              selectedFile?.let { file ->
                onSubmit(studentNotes, file)
                onDismiss()
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
            enabled = selectedFile != null,
            modifier = Modifier.testTag("confirm_submit_work_btn")
          ) {
            Icon(Icons.Default.Send, contentDescription = "Submit", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Submit Work")
          }
        }
      }
    }
  }
}

@Composable
fun GradeSubmissionDialog(
  submission: StudentSubmission,
  onDismiss: () -> Unit,
  onPreviewDocument: (AttachmentFile) -> Unit,
  onGradeSubmitted: (grade: String, feedback: String) -> Unit
) {
  var grade by remember { mutableStateOf(submission.grade ?: "85/100") }
  var feedback by remember { mutableStateOf(submission.feedback ?: "") }

  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
        .testTag("grade_submission_dialog_modal")
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Grade, contentDescription = "Grade", tint = WarmAmber)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Review & Grade Submission",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = "Student: ${submission.studentName} (${submission.studentNumber})",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "${submission.courseCode} • ${submission.assignmentTitle}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "Submitted: ${submission.submittedAt}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Submitted File Preview Trigger
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = submission.attachment.fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "${submission.attachment.fileSize} • Tap to view in reader",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Button(
              onClick = { onPreviewDocument(submission.attachment) },
              colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
              modifier = Modifier.testTag("btn_inspect_submission_file")
            ) {
              Icon(Icons.Default.Visibility, contentDescription = "Inspect", modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Inspect")
            }
          }
        }

        if (submission.notes.isNotBlank()) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Student Notes: \"${submission.notes}\"",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF334155)
          )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
          value = grade,
          onValueChange = { grade = it },
          label = { Text("Score / Grade") },
          placeholder = { Text("e.g. 92/100 or A") },
          modifier = Modifier.fillMaxWidth().testTag("grade_input_field"),
          singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = feedback,
          onValueChange = { feedback = it },
          label = { Text("Lecturer Feedback") },
          placeholder = { Text("Provide constructive evaluation notes...") },
          modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .testTag("grade_feedback_field")
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = {
              if (grade.isNotBlank()) {
                onGradeSubmitted(grade, feedback)
                onDismiss()
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
            enabled = grade.isNotBlank(),
            modifier = Modifier.testTag("submit_grade_btn")
          ) {
            Text("Post Grade & Feedback")
          }
        }
      }
    }
  }
}
