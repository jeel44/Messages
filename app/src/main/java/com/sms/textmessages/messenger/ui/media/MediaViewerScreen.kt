package com.sms.textmessages.messenger.ui.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

private val AccentBlue = Color(0xFF3E6AE1)

////////////////////////////////////////////////////////
// 🔵 MEDIA VIEWER SCREEN
////////////////////////////////////////////////////////

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    attachments: List<MediaAttachment>,
    initialIndex: Int,
    onBack: () -> Unit
) {

    if (attachments.isEmpty()) {
        // Nothing resolved yet (or the thread has no media) - callers building
        // this route reload attachments asynchronously, so this can render
        // once before that finishes.
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, attachments.lastIndex),
        pageCount = { attachments.size }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->

            val attachment = attachments[page]

            if (attachment.mimeType.startsWith("video/")) {
                VideoPage(uri = attachment.uri)
            } else {
                ZoomableImagePage(uri = attachment.uri)
            }
        }

        // Top overlay: close (left), share + download (right).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            Row {
                IconButton(onClick = {
                    shareAttachment(context, attachments[pagerState.currentPage])
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                }

                IconButton(onClick = {
                    downloadAttachment(context, attachments[pagerState.currentPage])
                }) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom filmstrip.
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(attachments) { index, attachment ->

                val isCurrent = index == pagerState.currentPage

                MmsThumbnail(
                    attachment = attachment,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (isCurrent)
                                Modifier.border(2.dp, AccentBlue, RoundedCornerShape(6.dp))
                            else
                                Modifier
                        )
                        .clickable {
                            scope.launch { pagerState.scrollToPage(index) }
                        }
                )
            }
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 ZOOMABLE IMAGE PAGE
////////////////////////////////////////////////////////

@Composable
private fun ZoomableImagePage(uri: Uri) {

    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(uri) { mutableStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(uri) {
        bitmap = loadMmsBitmap(context, uri, sampleSize = 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(uri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            },
        contentAlignment = Alignment.Center
    ) {

        val bmp = bitmap

        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 VIDEO PAGE
////////////////////////////////////////////////////////

@Composable
private fun VideoPage(uri: Uri) {

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(uri)
                    setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                    setOnPreparedListener { player -> player.isLooping = false }
                }
            }
        )
    }
}

////////////////////////////////////////////////////////
// 🔵 SHARED THUMBNAIL (reused by SharedMediaScreen's grid, the filmstrip
// above, and ChatScreen.kt's MMS bubble rendering)
////////////////////////////////////////////////////////

@Composable
fun MmsThumbnail(
    attachment: MediaAttachment,
    modifier: Modifier = Modifier,
    sampleSize: Int = 4,
    contentScale: ContentScale = ContentScale.Crop
) {

    val context = LocalContext.current
    var bitmap by remember(attachment.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(attachment.uri) {
        bitmap = loadMediaThumbnail(context, attachment, sampleSize)
    }

    Box(
        modifier = modifier.background(Color(0xFFEDEDED)),
        contentAlignment = Alignment.Center
    ) {

        val bmp = bitmap

        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (attachment.mimeType.startsWith("video/")) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 SHARE / DOWNLOAD
////////////////////////////////////////////////////////

private fun shareAttachment(context: Context, attachment: MediaAttachment) {

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = attachment.mimeType
        putExtra(Intent.EXTRA_STREAM, attachment.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Share"))
}

// Saves via MediaStore's ContentResolver insert, which needs no storage
// permission on API 29+ (scoped storage). AndroidManifest.xml declares no
// WRITE_EXTERNAL_STORAGE permission anywhere else in this app, so this
// intentionally doesn't add legacy-storage handling for API < 29 either -
// the insert/openOutputStream calls below will simply fail there, which is
// caught and surfaced as a toast rather than crashing.
private fun downloadAttachment(context: Context, attachment: MediaAttachment) {

    try {

        val resolver = context.contentResolver
        val isVideo = attachment.mimeType.startsWith("video/")
        val displayName = "MMS_${attachment.messageId}_${System.currentTimeMillis()}"

        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, attachment.mimeType)
        }

        val destUri = resolver.insert(collection, values)

        if (destUri == null) {
            Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
            return
        }

        resolver.openOutputStream(destUri)?.use { out ->
            resolver.openInputStream(attachment.uri)?.use { input ->
                input.copyTo(out)
            }
        }

        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()

    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
