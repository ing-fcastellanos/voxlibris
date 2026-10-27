package com.fcastellanos.voxlibris.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tamaños de portada del sistema (`vx-cover--mini`, `--small`, `--medium`, `--large` y la fluida). */
enum class CoverSize(val width: Dp?, val corner: Dp, val padding: Dp, val titleSize: TextUnit?) {
    Mini(40.dp, VoxDimens.radiusS, 4.dp, null),
    Small(56.dp, VoxDimens.radiusM, 6.dp, 10.sp),
    Medium(96.dp, VoxDimens.radiusL, 10.dp, 15.sp),
    Large(150.dp, VoxDimens.radiusXl, 16.dp, 22.sp),
    Fill(null, VoxDimens.radiusL, 10.dp, 16.sp),
}

/**
 * Portada de un libro: la imagen del EPUB si existe; si no, la portada generada con su color,
 * su forma y el título abajo a la izquierda. Es decorativa: el título del libro se lee al lado.
 */
@Composable
fun BookCover(
    title: String,
    style: BookStyle,
    imageFile: File?,
    size: CoverSize,
    modifier: Modifier = Modifier,
) {
    val colors = style.tint.colors
    val shape = RoundedCornerShape(size.corner)
    val image by rememberCoverBitmap(imageFile)
    val sized = if (size.width != null) modifier.width(size.width) else modifier.fillMaxWidth()
    val edge = if (style.tint == BookTint.Night) {
        Modifier.border(1.dp, VoxLibrisTheme.colors.coverEdge, shape)
    } else {
        Modifier
    }
    Box(sized.aspectRatio(2f / 3f).clip(shape).background(colors.container).then(edge)) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) { drawCoverShape(style.shape, colors.shape) }
            val titleSize = size.titleSize
            if (titleSize != null && title.isNotBlank()) {
                Text(
                    text = title,
                    style = VoxLibrisTheme.typography.emphasis.copy(fontSize = titleSize, lineHeight = titleSize * 1.05f),
                    color = colors.content,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(size.padding),
                )
            }
        }
    }
}

@Composable
private fun rememberCoverBitmap(file: File?) = produceState<ImageBitmap?>(initialValue = null, key1 = file) {
    value = file?.let {
        withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
    }
}

/** Las formas de `vx-cover__shape--*`, con las mismas proporciones que en el sistema de diseño. */
private fun DrawScope.drawCoverShape(shape: CoverShape, color: Color) {
    val w = size.width
    val h = size.height
    when (shape) {
        CoverShape.Disc -> drawCircle(color, radius = 0.425f * w, center = Offset(0.855f * w, -0.18f * h + 0.425f * w))
        CoverShape.Orb -> drawCircle(color, radius = 0.4f * w, center = Offset(0.82f * w, 0.68f * h - 0.4f * w))
        CoverShape.HalfRound -> {
            val top = -0.22f * h
            val height = 0.45f * h
            val r = CornerRadius(min(w / 2f, height))
            val path = Path().apply {
                addRoundRect(RoundRect(Rect(0f, top, w, top + height), CornerRadius.Zero, CornerRadius.Zero, r, r))
            }
            drawPath(path, color)
        }
        CoverShape.Stripes -> {
            val bar = 0.07f * h
            listOf(0.8f, 0.64f, 0.48f).forEachIndexed { index, fraction ->
                drawRoundRect(
                    color,
                    topLeft = Offset(0.1f * w, 0.08f * h + index * 1.7f * bar),
                    size = Size(fraction * w, bar),
                    cornerRadius = CornerRadius(bar / 2f),
                )
            }
        }
        CoverShape.Diamond -> {
            val side = 0.5f * w
            val left = 0.25f * w
            val top = 0.14f * h
            rotate(45f, pivot = Offset(left + side / 2f, top + side / 2f)) {
                drawRoundRect(color, Offset(left, top), Size(side, side), CornerRadius(0.083f * w))
            }
        }
        CoverShape.Ring -> {
            val diameter = 0.66f * w
            val stroke = max(4.dp.toPx(), 0.083f * w)
            drawCircle(
                color,
                radius = diameter / 2f - stroke / 2f,
                center = Offset(0.16f * w + diameter / 2f, 0.12f * h + diameter / 2f),
                style = Stroke(stroke),
            )
        }
        CoverShape.Quarter -> {
            val side = 0.55f * w
            drawArc(color, 90f, 90f, useCenter = true, topLeft = Offset(w - side, -side), size = Size(2f * side, 2f * side))
        }
    }
}
