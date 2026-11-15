package app.lawnchair.ui.preferences.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lawnchair.icons.shape.IconShape
import com.android.launcher3.R
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CropIconDialog(
    bitmap: Bitmap,
    iconShape: IconShape,
    onDismissRequest: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    var userScale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var activeBoxSizePx by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = R.string.crop_icon),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.crop_icon_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val boxSizeDp = minOf(maxWidth - 16.dp, 280.dp)
                    val density = LocalDensity.current
                    val boxSizePx = with(density) { boxSizeDp.toPx() }

                    SideEffect {
                        activeBoxSizePx = boxSizePx
                    }

                    val imgW = bitmap.width.toFloat()
                    val imgH = bitmap.height.toFloat()
                    val baseScale = maxOf(boxSizePx / imgW, boxSizePx / imgH)

                    val curScale = baseScale * userScale
                    val dispW = imgW * curScale
                    val dispH = imgH * curScale

                    val scaledComposePath = remember(iconShape, boxSizePx) {
                        val androidPath = iconShape.getMaskPath()
                        val matrix = Matrix().apply {
                            postScale(boxSizePx / 100f, boxSizePx / 100f)
                        }
                        androidPath.transform(matrix)
                        androidPath.asComposePath()
                    }

                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                    val primaryColor = MaterialTheme.colorScheme.primary

                    Box(
                        modifier = Modifier
                            .size(boxSizeDp)
                            .clipToBounds()
                            .background(Color.Black)
                            .pointerInput(boxSizePx, bitmap) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (userScale * zoom).coerceIn(0.5f, 5f)
                                    userScale = newScale

                                    val currentDispW = imgW * (baseScale * newScale)
                                    val currentDispH = imgH * (baseScale * newScale)
                                    val allowedOx = abs(currentDispW - boxSizePx) / 2f
                                    val allowedOy = abs(currentDispH - boxSizePx) / 2f

                                    offsetX = (offsetX + pan.x).coerceIn(-allowedOx, allowedOx)
                                    offsetY = (offsetY + pan.y).coerceIn(-allowedOy, allowedOy)
                                }
                            },
                    ) {
                        Canvas(modifier = Modifier.size(boxSizeDp)) {
                            val imgLeft = (size.width - dispW) / 2f + offsetX
                            val imgTop = (size.height - dispH) / 2f + offsetY

                            // Draw image
                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                                dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt()),
                            )

                            // Scrim outside user-defined icon shape template
                            clipPath(scaledComposePath, clipOp = ClipOp.Difference) {
                                drawRect(Color.Black.copy(alpha = 0.58f))
                            }

                            // User-defined icon shape template outline
                            drawPath(
                                path = scaledComposePath,
                                color = primaryColor,
                                style = Stroke(width = 2.5.dp.toPx()),
                            )

                            // 1:1 square guide border indicating the preserved square boundary
                            drawRect(
                                color = Color.White.copy(alpha = 0.35f),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)),
                                ),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(id = android.R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val boxPx = if (activeBoxSizePx > 0f) activeBoxSizePx else 500f
                            val imgW = bitmap.width.toFloat()
                            val imgH = bitmap.height.toFloat()
                            val baseScale = maxOf(boxPx / imgW, boxPx / imgH)
                            val effectiveScale = baseScale * userScale
                            val dispW = imgW * effectiveScale
                            val dispH = imgH * effectiveScale

                            val imgLeft = (boxPx - dispW) / 2f + offsetX
                            val imgTop = (boxPx - dispH) / 2f + offsetY

                            val finalSquareBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                            val cropCanvas = AndroidCanvas(finalSquareBitmap)
                            val cropPaint = AndroidPaint(
                                AndroidPaint.ANTI_ALIAS_FLAG or
                                    AndroidPaint.FILTER_BITMAP_FLAG or
                                    AndroidPaint.DITHER_FLAG,
                            )
                            val ratio = 512f / boxPx
                            val matrix = Matrix().apply {
                                postScale(effectiveScale * ratio, effectiveScale * ratio)
                                postTranslate(imgLeft * ratio, imgTop * ratio)
                            }
                            cropCanvas.drawBitmap(bitmap, matrix, cropPaint)

                            onConfirm(finalSquareBitmap)
                        },
                    ) {
                        Text(text = stringResource(id = R.string.crop_icon_save))
                    }
                }
            }
        }
    }
}
