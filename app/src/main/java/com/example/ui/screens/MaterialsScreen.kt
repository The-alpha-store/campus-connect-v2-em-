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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.model.AttachmentFile
import com.example.data.model.ContentApprovalStatus
import com.example.data.model.Role
import com.example.data.model.StudyMaterial
import com.example.ui.components.StatusBadge
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedLight
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenLight
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmAmberLight
import com.example.viewmodel.CampusViewModel

@Composable
fun MaterialsScreen(
  viewModel: CampusViewModel,
  onOpenUploadDialog: () -> Unit,
  onPreviewDocument: (AttachmentFile, authorName: String, courseCode: String) -> Unit,
  modifier: Modifier = Modifier
) {
  val user by viewModel.currentUser.collectAsState()
  val materials by viewModel.materials.collectAsState()

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = viewModel.getString("tab_materials"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Offline course notes, slides & handouts",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Materials List
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(materials, key = { it.id }) { mat ->
          Box(modifier = Modifier.animateItem()) {
          val canModerate = viewModel.canEditOrDelete(mat.uploaderId)

          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
              )
              .testTag("material_item_${mat.id}")
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (mat.isDownloaded) SuccessGreenLight else DeepBlue.copy(alpha = 0.12f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = if (mat.isDownloaded) Icons.Default.FileDownloadDone else Icons.Default.PictureAsPdf,
                    contentDescription = "File",
                    tint = if (mat.isDownloaded) SuccessGreen else DeepBlue,
                    modifier = Modifier.size(24.dp)
                  )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = mat.courseCode,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = DeepBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "• ${mat.attachment.fileSize}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (mat.isDownloaded) {
                      Spacer(modifier = Modifier.width(6.dp))
                      StatusBadge(
                        text = "Cached",
                        backgroundColor = SuccessGreenLight,
                        contentColor = SuccessGreen
                      )
                    }
                    if (mat.approvalStatus == ContentApprovalStatus.PENDING_REVIEW) {
                      Spacer(modifier = Modifier.width(6.dp))
                      StatusBadge(
                        text = "Pending",
                        backgroundColor = WarmAmberLight,
                        contentColor = Color(0xFFB45309)
                      )
                    } else if (mat.approvalStatus == ContentApprovalStatus.DISAPPROVED) {
                      Spacer(modifier = Modifier.width(6.dp))
                      StatusBadge(
                        text = "Disapproved",
                        backgroundColor = DangerRedLight,
                        contentColor = DangerRed
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(4.dp))

                  Text(
                    text = mat.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )

                  Spacer(modifier = Modifier.height(2.dp))

                  Text(
                    text = "By ${mat.uploadedBy} • ${mat.uploadedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                // Download Toggle
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (mat.isDownloaded) MaterialTheme.colorScheme.surfaceVariant else DeepBlue,
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.toggleMaterialDownloaded(mat.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("download_toggle_${mat.id}")
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = if (mat.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                      contentDescription = "Download state",
                      tint = if (mat.isDownloaded) SuccessGreen else Color.White,
                      modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = if (mat.isDownloaded) "Saved" else "Save",
                      color = if (mat.isDownloaded) MaterialTheme.colorScheme.onSurface else Color.White,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Actions: Built-in Document Preview & Moderation
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                // Built-in Document Viewer Button
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = DeepBlue.copy(alpha = 0.1f),
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onPreviewDocument(mat.attachment, mat.uploadedBy, mat.courseCode) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("view_doc_btn_${mat.id}")
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = "View", tint = DeepBlue, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Read in Viewer", color = DeepBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                  }
                }

                // Admin & Uploader moderation controls
                if (canModerate) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    // Admin Approval / Disapproval
                    if (user.role == Role.ADMIN) {
                      if (mat.approvalStatus != ContentApprovalStatus.APPROVED) {
                        IconButton(
                          onClick = { viewModel.setMaterialApproval(mat.id, ContentApprovalStatus.APPROVED) },
                          modifier = Modifier.size(30.dp).testTag("approve_mat_${mat.id}")
                        ) {
                          Icon(Icons.Default.ThumbUp, contentDescription = "Approve", tint = SuccessGreen, modifier = Modifier.size(16.dp))
                        }
                      }
                      if (mat.approvalStatus != ContentApprovalStatus.DISAPPROVED) {
                        IconButton(
                          onClick = { viewModel.setMaterialApproval(mat.id, ContentApprovalStatus.DISAPPROVED) },
                          modifier = Modifier.size(30.dp).testTag("disapprove_mat_${mat.id}")
                        ) {
                          Icon(Icons.Default.ThumbDown, contentDescription = "Disapprove", tint = DangerRed, modifier = Modifier.size(16.dp))
                        }
                      }
                    }

                    // Delete button
                    IconButton(
                      onClick = { viewModel.deleteMaterial(mat.id) },
                      modifier = Modifier.size(30.dp).testTag("delete_mat_${mat.id}")
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

        item {
          Spacer(modifier = Modifier.height(80.dp))
        }
      }
    }

    if (user.role == Role.LECTURER || user.role == Role.ADMIN) {
      FloatingActionButton(
        onClick = onOpenUploadDialog,
        containerColor = DeepBlue,
        contentColor = Color.White,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(20.dp)
          .testTag("fab_upload_material")
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Upload Study Material"
        )
      }
    }
  }
}
