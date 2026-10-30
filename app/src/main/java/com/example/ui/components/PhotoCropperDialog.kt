package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.util.ImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Interactive Photoshop/Gallery style image cropper dialog.
 * Allows the user to freely drag 4 corner handles and reposition the crop frame
 * to isolate the PCB board from the background table, tools, or wires.
 */
@Composable
fun PhotoCropperDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
    onCropped: (newCroppedPath: String) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Normalized Crop Window coordinates (0f..1f relative to displayed image)
    var cropLeftNorm by remember { mutableFloatStateOf(0.05f) }
    var cropTopNorm by remember { mutableFloatStateOf(0.15f) }
    var cropRightNorm by remember { mutableFloatStateOf(0.95f) }
    var cropBottomNorm by remember { mutableFloatStateOf(0.85f) }

    // Load source bitmap
    LaunchedEffect(imageUrl) {
        isLoading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                val imageLoader = Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(ImageHelper.getCoilData(imageUrl))
                    .allowHardware(false)
                    .build()
                val result = (imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable
                if (result != null && result.bitmap != null) {
                    sourceBitmap = result.bitmap
                } else {
                    errorMessage = "تعذر تحميل الصورة لتنفيذ القص"
                }
            } catch (e: Exception) {
                errorMessage = "خطأ أثناء قراءة ملف الصورة: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0F172A),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0284C7)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0284C7).copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Crop, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "قص وتحديد كارتة التحكم (Crop Tool)",
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "اسحب الزوايا الأربعة لتحديد أبعاد الكارتة وعزل الخلفية",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إلغاء", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Canvas Area with Crop Overlay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("جارٍ تحميل الصورة...", color = Color.White)
                        }
                    } else if (sourceBitmap != null) {
                        val bmp = sourceBitmap!!
                        val bmpRatio = bmp.width.toFloat() / bmp.height.toFloat()

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val containerWidthPx = constraints.maxWidth.toFloat()
                            val containerHeightPx = constraints.maxHeight.toFloat()
                            val containerRatio = containerWidthPx / containerHeightPx

                            val displayedWidthPx: Float
                            val displayedHeightPx: Float
                            if (bmpRatio > containerRatio) {
                                displayedWidthPx = containerWidthPx
                                displayedHeightPx = containerWidthPx / bmpRatio
                            } else {
                                displayedHeightPx = containerHeightPx
                                displayedWidthPx = containerHeightPx * bmpRatio
                            }

                            val displayedWidthDp = with(density) { displayedWidthPx.toDp() }
                            val displayedHeightDp = with(density) { displayedHeightPx.toDp() }

                            // Box strictly enclosing the displayed image
                            Box(
                                modifier = Modifier
                                    .size(displayedWidthDp, displayedHeightDp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                // 1. The Image Bitmap
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawImage(
                                        image = bmp.asImageBitmap(),
                                        dstSize = androidx.compose.ui.unit.IntSize(
                                            displayedWidthPx.roundToInt(),
                                            displayedHeightPx.roundToInt()
                                        )
                                    )
                                }

                                // 2. Dimmed Mask + Crop Border Canvas
                                val cropLeftPx = cropLeftNorm * displayedWidthPx
                                val cropTopPx = cropTopNorm * displayedHeightPx
                                val cropRightPx = cropRightNorm * displayedWidthPx
                                val cropBottomPx = cropBottomNorm * displayedHeightPx
                                val cropWidthPx = cropRightPx - cropLeftPx
                                val cropHeightPx = cropBottomPx - cropTopPx

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    // Top dim
                                    drawRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset.Zero, size = Size(displayedWidthPx, cropTopPx))
                                    // Bottom dim
                                    drawRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset(0f, cropBottomPx), size = Size(displayedWidthPx, displayedHeightPx - cropBottomPx))
                                    // Left dim
                                    drawRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset(0f, cropTopPx), size = Size(cropLeftPx, cropHeightPx))
                                    // Right dim
                                    drawRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset(cropRightPx, cropTopPx), size = Size(displayedWidthPx - cropRightPx, cropHeightPx))

                                    // Crop Rectangle Glowing Border
                                    drawRect(
                                        color = Color(0xFF38BDF8),
                                        topLeft = Offset(cropLeftPx, cropTopPx),
                                        size = Size(cropWidthPx, cropHeightPx),
                                        style = Stroke(width = 2.dp.toPx())
                                    )

                                    // Rule-of-thirds grid lines inside crop window
                                    val thirdW = cropWidthPx / 3f
                                    val thirdH = cropHeightPx / 3f
                                    drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeftPx + thirdW, cropTopPx), Offset(cropLeftPx + thirdW, cropBottomPx), strokeWidth = 1f)
                                    drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeftPx + 2 * thirdW, cropTopPx), Offset(cropLeftPx + 2 * thirdW, cropBottomPx), strokeWidth = 1f)
                                    drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeftPx, cropTopPx + thirdH), Offset(cropRightPx, cropTopPx + thirdH), strokeWidth = 1f)
                                    drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeftPx, cropTopPx + 2 * thirdH), Offset(cropRightPx, cropTopPx + 2 * thirdH), strokeWidth = 1f)
                                }

                                // 3. Pan Whole Crop Window Gesture
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(cropLeftPx.toInt(), cropTopPx.toInt()) }
                                        .size(with(density) { cropWidthPx.toDp() }, with(density) { cropHeightPx.toDp() })
                                        .pointerInput(displayedWidthPx, displayedHeightPx) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val deltaNormX = dragAmount.x / displayedWidthPx
                                                val deltaNormY = dragAmount.y / displayedHeightPx
                                                val currentWidthNorm = cropRightNorm - cropLeftNorm
                                                val currentHeightNorm = cropBottomNorm - cropTopNorm

                                                val newLeft = (cropLeftNorm + deltaNormX).coerceIn(0f, 1f - currentWidthNorm)
                                                val newTop = (cropTopNorm + deltaNormY).coerceIn(0f, 1f - currentHeightNorm)
                                                cropLeftNorm = newLeft
                                                cropTopNorm = newTop
                                                cropRightNorm = newLeft + currentWidthNorm
                                                cropBottomNorm = newTop + currentHeightNorm
                                            }
                                        }
                                )

                                // 4. Interactive 4-Corner Draggable Handles
                                val handleRadiusDp = 16.dp
                                val handleRadiusPx = with(density) { handleRadiusDp.toPx() }

                                // Top-Left Handle
                                CropCornerHandle(
                                    xPx = cropLeftPx,
                                    yPx = cropTopPx,
                                    onDragDelta = { dx, dy ->
                                        val newLeft = (cropLeftNorm + (dx / displayedWidthPx)).coerceIn(0f, cropRightNorm - 0.15f)
                                        val newTop = (cropTopNorm + (dy / displayedHeightPx)).coerceIn(0f, cropBottomNorm - 0.15f)
                                        cropLeftNorm = newLeft
                                        cropTopNorm = newTop
                                    }
                                )

                                // Top-Right Handle
                                CropCornerHandle(
                                    xPx = cropRightPx,
                                    yPx = cropTopPx,
                                    onDragDelta = { dx, dy ->
                                        val newRight = (cropRightNorm + (dx / displayedWidthPx)).coerceIn(cropLeftNorm + 0.15f, 1f)
                                        val newTop = (cropTopNorm + (dy / displayedHeightPx)).coerceIn(0f, cropBottomNorm - 0.15f)
                                        cropRightNorm = newRight
                                        cropTopNorm = newTop
                                    }
                                )

                                // Bottom-Left Handle
                                CropCornerHandle(
                                    xPx = cropLeftPx,
                                    yPx = cropBottomPx,
                                    onDragDelta = { dx, dy ->
                                        val newLeft = (cropLeftNorm + (dx / displayedWidthPx)).coerceIn(0f, cropRightNorm - 0.15f)
                                        val newBottom = (cropBottomNorm + (dy / displayedHeightPx)).coerceIn(cropTopNorm + 0.15f, 1f)
                                        cropLeftNorm = newLeft
                                        cropBottomNorm = newBottom
                                    }
                                )

                                // Bottom-Right Handle
                                CropCornerHandle(
                                    xPx = cropRightPx,
                                    yPx = cropBottomPx,
                                    onDragDelta = { dx, dy ->
                                        val newRight = (cropRightNorm + (dx / displayedWidthPx)).coerceIn(cropLeftNorm + 0.15f, 1f)
                                        val newBottom = (cropBottomNorm + (dy / displayedHeightPx)).coerceIn(cropTopNorm + 0.15f, 1f)
                                        cropRightNorm = newRight
                                        cropBottomNorm = newBottom
                                    }
                                )
                            }
                        }
                    } else {
                        Text(text = errorMessage ?: "حدث خطأ غير معروف", color = Color(0xFFEF4444))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            cropLeftNorm = 0.05f
                            cropTopNorm = 0.15f
                            cropRightNorm = 0.95f
                            cropBottomNorm = 0.85f
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعادة ضبط الإطار")
                    }

                    Button(
                        onClick = {
                            val bmp = sourceBitmap
                            if (bmp != null) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val xPixel = (cropLeftNorm * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                                        val yPixel = (cropTopNorm * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                                        val wPixel = ((cropRightNorm - cropLeftNorm) * bmp.width).toInt().coerceIn(1, bmp.width - xPixel)
                                        val hPixel = ((cropBottomNorm - cropTopNorm) * bmp.height).toInt().coerceIn(1, bmp.height - yPixel)

                                        val croppedBitmap = Bitmap.createBitmap(bmp, xPixel, yPixel, wPixel, hPixel)

                                        val outFile = File(context.filesDir, "cropped_board_${System.currentTimeMillis()}.jpg")
                                        FileOutputStream(outFile).use { out ->
                                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                        }

                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "تم قص وتحديث صورة الكارتة بنجاح!", Toast.LENGTH_SHORT).show()
                                            onCropped(outFile.absolutePath)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "فشل حفظ الصورة المقصوصة: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = sourceBitmap != null,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("✂️ تطبيق القص وحفظ الصورة", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Movable Corner Handle for the photo cropper frame.
 */
@Composable
private fun CropCornerHandle(
    xPx: Float,
    yPx: Float,
    onDragDelta: (dx: Float, dy: Float) -> Unit
) {
    val density = LocalDensity.current
    val handleSize = 32.dp

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (xPx - with(density) { 16.dp.toPx() }).toInt(),
                    (yPx - with(density) { 16.dp.toPx() }).toInt()
                )
            }
            .size(handleSize)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF38BDF8),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
            shadowElevation = 8.dp,
            modifier = Modifier.size(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
            )
        }
    }
}
