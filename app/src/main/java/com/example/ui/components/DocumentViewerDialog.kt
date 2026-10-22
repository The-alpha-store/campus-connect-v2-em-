package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.AttachmentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val ViewerBackground = Color(0xFF111318)
private val ViewerBar = Color(0xFF1B1E26)

/**
 * Full-screen viewer for PDFs and pictures.
 *  - PDF pages are drawn by Android's PdfRenderer, one page at a time, as you scroll.
 *  - Pictures are loaded with Coil.
 *  - Pinch to zoom, drag to move when zoomed, double-tap to zoom in and out.
 *  - Back or the close button leaves the viewer.
 */
@Composable
fun DocumentViewerDialog(
  attachment: AttachmentFile,
  authorOrUploaderName: String? = null,
  courseCode: String? = null,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val subtitle = listOfNotNull(
    attachment.fileSize.takeIf { it.isNotBlank() },
    courseCode,
    authorOrUploaderName
  ).joinToString("  \u2022  ")

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
  ) {
    Surface(
      color = ViewerBackground,
      modifier = Modifier.fillMaxSize().testTag("built_in_document_viewer_modal")
    ) {
      var pageLabel by remember { mutableStateOf<String?>(null) }

      Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Top bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(ViewerBar)
            .padding(horizontal = 4.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_document_viewer_btn")) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
          }
          Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
              text = attachment.fileName,
              color = Color.White,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank() || pageLabel != null) {
              Text(
                text = listOfNotNull(pageLabel, subtitle.takeIf { it.isNotBlank() }).joinToString("  \u2022  "),
                color = Color(0xFFB6BDCB),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          if (attachment.uriString != null) {
            IconButton(onClick = { openInAnotherApp(context, attachment) }) {
              Icon(Icons.Default.OpenInNew, contentDescription = "Open in another app", tint = Color.White)
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          val uri = attachment.uriString
          when {
            uri == null -> ViewerMessage(
              title = "Sample file",
              body = "This is a sample entry with no file attached, so there is nothing to open."
            )
            attachment.isImage -> ImagePage(uri = uri, name = attachment.fileName)
            else -> PdfPages(uri = uri, onPageLabel = { pageLabel = it })
          }
        }
      }
    }
  }
}

// ---------- Pictures ----------

@Composable
private fun ImagePage(uri: String, name: String) {
  ZoomBox(modifier = Modifier.fillMaxSize()) {
    SubcomposeAsyncImage(
      model = uri,
      contentDescription = name,
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxSize(),
      loading = { LoadingIndicator() },
      error = { ViewerMessage("Could not open this picture", "The file may have been moved or deleted.") }
    )
  }
}

// ---------- PDF ----------

private class PdfDoc(private val renderer: PdfRenderer, private val descriptor: ParcelFileDescriptor) {
  // PdfRenderer allows one open page at a time, so renders take turns.
  private val lock = Mutex()
  val pageCount: Int = renderer.pageCount

  suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
    withContext(Dispatchers.IO) {
      val page = renderer.openPage(index)
      try {
        val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
      } finally {
        page.close()
      }
    }
  }

  fun close() {
    runCatching { renderer.close() }
    runCatching { descriptor.close() }
  }
}

private sealed interface PdfLoad {
  object Loading : PdfLoad
  class Ready(val doc: PdfDoc) : PdfLoad
  object Failed : PdfLoad
}

@Composable
private fun PdfPages(uri: String, onPageLabel: (String?) -> Unit) {
  val context = LocalContext.current

  val load by produceState<PdfLoad>(PdfLoad.Loading, uri) {
    value = withContext(Dispatchers.IO) {
      try {
        val fd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
          ?: return@withContext PdfLoad.Failed
        try {
          PdfLoad.Ready(PdfDoc(PdfRenderer(fd), fd))
        } catch (e: Exception) {
          fd.close() // damaged, empty or password-protected
          PdfLoad.Failed
        }
      } catch (e: Exception) {
        PdfLoad.Failed
      }
    }
  }

  DisposableEffect(load) {
    onDispose { (load as? PdfLoad.Ready)?.doc?.close() }
  }

  when (val state = load) {
    is PdfLoad.Loading -> LoadingIndicator()
    is PdfLoad.Failed -> ViewerMessage(
      "Could not open this PDF",
      "It may be damaged, protected by a password, or no longer on this phone."
    )
    is PdfLoad.Ready -> {
      val doc = state.doc
      val listState = rememberLazyListState()
      val current by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
      androidx.compose.runtime.LaunchedEffect(current, doc) { onPageLabel("Page $current of ${doc.pageCount}") }

      ZoomBox(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
          val density = LocalDensity.current
          // Draw at about 1.6x the screen width so zooming stays sharp.
          val widthPx = with(density) { (maxWidth * 1.6f).roundToPx() }.coerceIn(480, 2400)

          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)
          ) {
            itemsIndexed(List(doc.pageCount) { it }, key = { _, page -> page }) { _, index ->
              PdfPageImage(doc = doc, index = index, widthPx = widthPx)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PdfPageImage(doc: PdfDoc, index: Int, widthPx: Int) {
  val bitmap by produceState<Bitmap?>(null, doc, index, widthPx) {
    value = try {
      doc.render(index, widthPx)
    } catch (e: Exception) {
      null
    }
  }
  val image = bitmap
  if (image != null) {
    Image(
      bitmap = image.asImageBitmap(),
      contentDescription = "Page ${index + 1}",
      contentScale = ContentScale.FillWidth,
      modifier = Modifier.fillMaxWidth()
    )
  } else {
    // Keeps the scroll position steady while the page is drawn.
    Box(
      modifier = Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color(0xFF1B1E26)),
      contentAlignment = Alignment.Center
    ) { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp), color = Color(0xFF8FA8FF)) }
  }
}

// ---------- Zoom and pan ----------

/**
 * Pinch to zoom (1x to 5x), drag to pan while zoomed, double-tap to jump in and out.
 * With one finger at 1x the gesture is left alone, so lists inside still scroll normally.
 */
@Composable
private fun ZoomBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  Box(
    modifier = modifier
      .clipToBounds()
      .pointerInput(Unit) {
        detectTapGestures(onDoubleTap = {
          if (scale > 1f) {
            scale = 1f
            offset = Offset.Zero
          } else {
            scale = 2.5f
          }
        })
      }
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false)
          do {
            val event = awaitPointerEvent()
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            if (event.changes.size > 1 || scale > 1f) {
              val newScale = (scale * zoom).coerceIn(1f, 5f)
              scale = newScale
              offset = if (newScale > 1f) {
                val maxX = size.width * (newScale - 1f) / 2f
                val maxY = size.height * (newScale - 1f) / 2f
                Offset(
                  (offset.x + pan.x).coerceIn(-maxX, maxX),
                  (offset.y + pan.y).coerceIn(-maxY, maxY)
                )
              } else {
                Offset.Zero
              }
              event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
          } while (event.changes.any { it.pressed })
        }
      }
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationX = offset.x
        translationY = offset.y
      }
  ) { content() }
}

// ---------- Small pieces ----------

@Composable
private fun LoadingIndicator() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = Color(0xFF8FA8FF))
  }
}

@Composable
private fun ViewerMessage(title: String, body: String) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFB6BDCB), modifier = Modifier.size(40.dp))
    Spacer(modifier = Modifier.height(12.dp))
    Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(6.dp))
    Text(body, color = Color(0xFFB6BDCB), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
  }
}

private fun openInAnotherApp(context: Context, attachment: AttachmentFile) {
  val uri = attachment.uriString?.let { Uri.parse(it) } ?: return
  val view = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, attachment.mimeType.ifBlank { if (attachment.isImage) "image/*" else "application/pdf" })
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  runCatching { context.startActivity(Intent.createChooser(view, null)) }
}
