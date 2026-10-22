package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.AttachmentFile
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.SuccessGreen

@Composable
fun DocumentViewerDialog(
  attachment: AttachmentFile,
  authorOrUploaderName: String? = null,
  courseCode: String? = null,
  onDismiss: () -> Unit
) {
  var currentPage by remember { mutableIntStateOf(1) }
  val totalPages = if (attachment.isImage) 1 else 3
  var zoomLevel by remember { mutableFloatStateOf(1.0f) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier
        .fillMaxWidth(0.96f)
        .fillMaxHeight(0.92f)
        .padding(8.dp)
        .testTag("built_in_document_viewer_modal")
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Viewer Top Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (attachment.isImage) Color(0xFFE0E7FF) else Color(0xFFFEE2E2)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (attachment.isImage) Icons.Default.Image else Icons.Default.PictureAsPdf,
                contentDescription = "Document Icon",
                tint = if (attachment.isImage) DeepBlue else DangerRed,
                modifier = Modifier.size(20.dp)
              )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
              Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
              )
              Text(
                text = "${attachment.fileSize} • ${if (attachment.isImage) "Image" else "PDF Document"}${if (courseCode != null) " • $courseCode" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            // Zoom controls
            IconButton(
              onClick = { if (zoomLevel > 0.8f) zoomLevel -= 0.15f },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
            }
            Text(
              text = "${(zoomLevel * 100).toInt()}%",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold
            )
            IconButton(
              onClick = { if (zoomLevel < 1.6f) zoomLevel += 0.15f },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("close_document_viewer_btn")
            ) {
              Icon(Icons.Default.Close, contentDescription = "Close Viewer")
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Document Canvas / Scrollable View Area
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(Color(0xFFE2E8F0))
            .padding(16.dp),
          contentAlignment = Alignment.TopCenter
        ) {
          // Document Sheet Paper representation
          Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
              .fillMaxWidth(0.96f)
              .verticalScroll(rememberScrollState())
              .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
          ) {
            Column(modifier = Modifier.padding(20.dp)) {
              if (attachment.isImage) {
                // Image Document Presentation
                Text(
                  text = "IMAGE ATTACHMENT VIEW",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = DeepBlue
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (attachment.uriString != null) {
                  AsyncImage(
                    model = attachment.uriString,
                    contentDescription = attachment.fileName,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(260.dp)
                      .clip(RoundedCornerShape(8.dp))
                  )
                } else {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(240.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color(0xFFF1F5F9))
                      .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                  ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Image preview",
                        tint = DeepBlue,
                        modifier = Modifier.size(54.dp)
                      )
                      Spacer(modifier = Modifier.height(8.dp))
                      Text(
                        text = "Scanned Assignment Solution Diagram",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                      )
                      Text(
                        text = "High resolution photo (1920 x 1080) • Verified readable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }
              } else {
                // PDF Document Presentation
                // Institutional Header
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column {
                    Text(
                      text = "CAMPUSCONNECT UNIVERSITY OF TANZANIA",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = DeepBlue,
                      fontSize = 11.sp
                    )
                    Text(
                      text = "Faculty of Computing & Information Technology",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFF64748B)
                    )
                  }
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Verified,
                      contentDescription = "Verified Official",
                      tint = SuccessGreen,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Official Academic Doc",
                      style = MaterialTheme.typography.labelSmall,
                      color = SuccessGreen,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(14.dp))

                // Document Title
                Text(
                  text = attachment.fileName.substringBeforeLast("."),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF0F172A)
                )

                if (authorOrUploaderName != null) {
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Contributor / Uploader: $authorOrUploaderName",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium
                  )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Document Body Content
                Text(
                  text = attachment.previewText ?: "DOCUMENT CONTENT OVERVIEW:\n\n1. Abstract & Scope\nThis academic document outlines verified lecture curriculum materials, mathematical proofs, and assignment requirements.\n\n2. Implementation Specifications\nStudents must adhere to standard time complexity constraints O(V + E) and ensure memory bounds are respected.\n\n3. Verification & Grading Matrix\n- Correctness: 50 points\n- Test Case Coverage: 30 points\n- Documentation & Code Cleanliness: 20 points.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color(0xFF1E293B),
                  lineHeight = 22.sp,
                  fontFamily = if (attachment.previewText?.contains("class") == true) FontFamily.Monospace else FontFamily.Default
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Watermark & Page Footer
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "CampusConnect Secure Viewer • Verified Copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                  )
                  Text(
                    text = "Page $currentPage of $totalPages",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                  )
                }
              }
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Viewer Bottom Control Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Pagination controls
          if (!attachment.isImage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              OutlinedButton(
                onClick = { if (currentPage > 1) currentPage-- },
                enabled = currentPage > 1,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
              ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Prev", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Prev", style = MaterialTheme.typography.labelSmall)
              }

              Spacer(modifier = Modifier.width(8.dp))

              Text(
                text = "Page $currentPage / $totalPages",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
              )

              Spacer(modifier = Modifier.width(8.dp))

              OutlinedButton(
                onClick = { if (currentPage < totalPages) currentPage++ },
                enabled = currentPage < totalPages,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
              ) {
                Text("Next", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = "Next", modifier = Modifier.size(14.dp))
              }
            }
          } else {
            Text(
              text = "Full Resolution Image",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = DeepBlue
            )
          }

          Row {
            Button(
              onClick = onDismiss,
              colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
              modifier = Modifier.height(36.dp)
            ) {
              Text("Close Document")
            }
          }
        }
      }
    }
  }
}
