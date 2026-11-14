package com.example.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.components.SquirclePathBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class RenderedSquircleIcon(
  val bitmap: ImageBitmap,
  val dominantColor: Color,
  val secondaryColor: Color = dominantColor,
)

/**
 * Renders any Android app icon into a 2x supersampled, realistically shaded Apple squircle
 * ImageBitmap and extracts its vibrant dominant color off the main thread.
 */
class SquircleIconRenderer(private val context: Context) {

  private val memoryCache = LruCache<String, RenderedSquircleIcon>(256)

  fun getCached(componentKey: String, sizePx: Int): RenderedSquircleIcon? {
    val bucketPx = quantizeBucketPx(sizePx)
    return memoryCache.get("$componentKey@$bucketPx")
  }

  suspend fun loadOrRender(
    packageName: String,
    activityName: String,
    componentKey: String,
    targetSizePx: Int,
  ): RenderedSquircleIcon? {
    val bucketPx = quantizeBucketPx(targetSizePx)
    val cacheKey = "$componentKey@$bucketPx"
    memoryCache.get(cacheKey)?.let { return it }

    return withContext(Dispatchers.Default) {
      memoryCache.get(cacheKey)?.let { return@withContext it }

      val drawable =
        runCatching {
          context.packageManager.getActivityIcon(ComponentName(packageName, activityName))
        }.getOrElse {
          runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        } ?: return@withContext null

      val renderedBitmap = renderSupersampledAppleSquircle(drawable, bucketPx)
      val (dominantColor, secondaryColor) = extractIconPalette(renderedBitmap)
      val asset = RenderedSquircleIcon(
        bitmap = renderedBitmap.asImageBitmap(),
        dominantColor = dominantColor,
        secondaryColor = secondaryColor,
      )
      memoryCache.put(cacheKey, asset)
      asset
    }
  }

  suspend fun renderAppSelfIcon(targetSizePx: Int): RenderedSquircleIcon? {
    val bucketPx = quantizeBucketPx(targetSizePx)
    val cacheKey = "__self__/${context.packageName}@$bucketPx"
    memoryCache.get(cacheKey)?.let { return it }

    return withContext(Dispatchers.Default) {
      memoryCache.get(cacheKey)?.let { return@withContext it }

      val drawable =
        runCatching { context.packageManager.getApplicationIcon(context.packageName) }.getOrNull()
          ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
          ?: return@withContext null

      val renderedBitmap = renderSupersampledAppleSquircle(drawable, bucketPx)
      val (dominantColor, secondaryColor) = extractIconPalette(renderedBitmap)
      val asset = RenderedSquircleIcon(
        bitmap = renderedBitmap.asImageBitmap(),
        dominantColor = dominantColor,
        secondaryColor = secondaryColor,
      )
      memoryCache.put(cacheKey, asset)
      asset
    }
  }

  fun evictPackage(packageName: String) {
    val snapshotKeys = memoryCache.snapshot().keys
    for (key in snapshotKeys) {
      if (key.startsWith("$packageName/")) {
        memoryCache.remove(key)
      }
    }
  }

  private fun quantizeBucketPx(sizePx: Int): Int {
    return (((sizePx.coerceIn(96, 384) + 8) / 16) * 16).coerceAtLeast(96)
  }

  private fun renderSupersampledAppleSquircle(drawable: Drawable, targetPx: Int): Bitmap {
    // Render at 2x supersampled resolution so diagonal squircle curves downsample silky-smooth
    val superPx = (targetPx * 2).coerceAtMost(576)
    val rawBitmap = Bitmap.createBitmap(superPx, superPx, Bitmap.Config.ARGB_8888)
    val rawCanvas = Canvas(rawBitmap)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
      val expand = (superPx * 0.21f).roundToInt()
      val bg = drawable.background ?: ColorDrawable(AndroidColor.WHITE)
      bg.setBounds(-expand, -expand, superPx + expand, superPx + expand)
      bg.draw(rawCanvas)

      drawable.foreground?.let { fg ->
        fg.setBounds(-expand, -expand, superPx + expand, superPx + expand)
        fg.draw(rawCanvas)
      }
    } else {
      renderSmartLegacyIcon(drawable, rawCanvas, superPx)
    }

    // Subtle realistic diagonal tonal color-gradience sheen & depth
    val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f,
        0f,
        superPx * 0.72f,
        superPx.toFloat(),
        intArrayOf(
          AndroidColor.argb(36, 255, 255, 255),
          AndroidColor.argb(8, 255, 255, 255),
          AndroidColor.argb(0, 0, 0, 0),
          AndroidColor.argb(28, 0, 0, 0),
        ),
        floatArrayOf(0f, 0.34f, 0.72f, 1f),
        Shader.TileMode.CLAMP,
      )
    }
    rawCanvas.drawRect(0f, 0f, superPx.toFloat(), superPx.toFloat(), glossPaint)

    // Mask cleanly into a high-precision squircle at 2x supersampled resolution
    val superSquircle = Bitmap.createBitmap(superPx, superPx, Bitmap.Config.ARGB_8888)
    val outCanvas = Canvas(superSquircle)
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    val squirclePath = SquirclePathBuilder.buildAndroidPath(superPx.toFloat(), superPx.toFloat())
    outCanvas.drawPath(squirclePath, maskPaint)
    maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    outCanvas.drawBitmap(rawBitmap, 0f, 0f, maskPaint)
    rawBitmap.recycle()

    // Realistic molded inner edge bevel highlight along the squircle contour
    val bevelStrokePx = (superPx * 0.014f).coerceAtLeast(2f)
    val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = bevelStrokePx
      shader = LinearGradient(
        0f,
        0f,
        0f,
        superPx.toFloat(),
        intArrayOf(
          AndroidColor.argb(64, 255, 255, 255),
          AndroidColor.argb(18, 255, 255, 255),
          AndroidColor.argb(26, 0, 0, 0),
        ),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
      )
    }
    outCanvas.drawPath(squirclePath, bevelPaint)

    // Bilinear downsample 2x -> 1x for ultra-smooth anti-aliased squircle edges
    val finalBitmap = Bitmap.createScaledBitmap(superSquircle, targetPx, targetPx, true)
    if (finalBitmap !== superSquircle) {
      superSquircle.recycle()
    }
    return finalBitmap
  }

  private fun renderSmartLegacyIcon(drawable: Drawable, targetCanvas: Canvas, sizePx: Int) {
    val probeBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val probeCanvas = Canvas(probeBitmap)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(probeCanvas)

    val analysis = analyzeIconBitmap(probeBitmap, sizePx)
    val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    if (analysis.isFullBleed) {
      val bleed = (sizePx * 0.03f).roundToInt()
      drawable.setBounds(-bleed, -bleed, sizePx + bleed, sizePx + bleed)
      drawable.draw(targetCanvas)
    } else {
      val (topColor, bottomColor) =
        if (analysis.averageLuminance > 180f) {
          AndroidColor.rgb(42, 52, 71) to AndroidColor.rgb(20, 27, 42)
        } else {
          AndroidColor.rgb(255, 255, 255) to AndroidColor.rgb(233, 238, 246)
        }

      val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f,
          0f,
          0f,
          sizePx.toFloat(),
          topColor,
          bottomColor,
          Shader.TileMode.CLAMP,
        )
      }
      targetCanvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), backdropPaint)

      val inset = (sizePx * 0.14f).roundToInt()
      val dst = android.graphics.Rect(inset, inset, sizePx - inset, sizePx - inset)
      targetCanvas.drawBitmap(probeBitmap, null, dst, filterPaint)
    }

    probeBitmap.recycle()
  }

  private fun analyzeIconBitmap(bitmap: Bitmap, sizePx: Int): LegacyIconAnalysis {
    val step = (sizePx / 10).coerceAtLeast(1)
    var opaqueEdgeCount = 0
    var totalEdgeSamples = 0
    var lumSum = 0f
    var opaquePixels = 0

    val margin = (sizePx * 0.12f).roundToInt().coerceAtLeast(1)
    val maxCoord = sizePx - 1 - margin

    for (y in step / 2 until sizePx step step) {
      for (x in step / 2 until sizePx step step) {
        val pixel = bitmap.getPixel(x, y)
        val alpha = AndroidColor.alpha(pixel)
        if (alpha > 200) {
          val r = AndroidColor.red(pixel)
          val g = AndroidColor.green(pixel)
          val b = AndroidColor.blue(pixel)
          lumSum += (0.299f * r + 0.587f * g + 0.114f * b)
          opaquePixels++
        }
        val isNearEdge = x <= margin || x >= maxCoord || y <= margin || y >= maxCoord
        if (isNearEdge) {
          totalEdgeSamples++
          if (alpha > 220) opaqueEdgeCount++
        }
      }
    }

    val edgeFillRatio =
      if (totalEdgeSamples > 0) opaqueEdgeCount.toFloat() / totalEdgeSamples else 0f
    val avgLum = if (opaquePixels > 0) lumSum / opaquePixels else 0f
    return LegacyIconAnalysis(
      isFullBleed = edgeFillRatio >= 0.80f,
      averageLuminance = avgLum,
    )
  }

  private data class LegacyIconAnalysis(
    val isFullBleed: Boolean,
    val averageLuminance: Float,
  )

  companion object {
    fun extractVibrantColor(bitmap: Bitmap): Color = extractIconPalette(bitmap).first

    fun extractIconPalette(bitmap: Bitmap): Pair<Color, Color> {
      val width = bitmap.width
      val height = bitmap.height
      if (width <= 0 || height <= 0) return Color(0xFF22D3EE) to Color(0xFF3B82F6)

      val stepX = (width / 10).coerceAtLeast(1)
      val stepY = (height / 10).coerceAtLeast(1)
      val hsv = FloatArray(3)

      var bestVibrantPixel = 0
      var bestScore = -1f
      var bestHue = 0f

      var secondVibrantPixel = 0
      var secondScore = -1f

      var sumR = 0L
      var sumG = 0L
      var sumB = 0L
      var count = 0L

      val candidates = ArrayList<Pair<Int, Float>>()

      for (y in stepY until height - stepY step stepY) {
        for (x in stepX until width - stepX step stepX) {
          val pixel = bitmap.getPixel(x, y)
          if (AndroidColor.alpha(pixel) < 200) continue

          val r = AndroidColor.red(pixel)
          val g = AndroidColor.green(pixel)
          val b = AndroidColor.blue(pixel)
          sumR += r
          sumG += g
          sumB += b
          count++

          AndroidColor.RGBToHSV(r, g, b, hsv)
          val sat = hsv[1]
          val value = hsv[2]
          if (sat > 0.18f && value > 0.18f) {
            val midValueBonus = 1f - abs(value - 0.65f)
            val score = (sat * 2.2f) + midValueBonus
            candidates.add(pixel to score)
            if (score > bestScore) {
              bestScore = score
              bestVibrantPixel = pixel
              bestHue = hsv[0]
            }
          }
        }
      }

      if (bestScore > 0f) {
        val primary = Color(bestVibrantPixel).copy(alpha = 1f)
        for ((pixel, score) in candidates) {
          AndroidColor.colorToHSV(pixel, hsv)
          val rawDiff = abs(hsv[0] - bestHue)
          val hueDist = minOf(rawDiff, 360f - rawDiff)
          if (hueDist >= 22f && score > secondScore) {
            secondScore = score
            secondVibrantPixel = pixel
          }
        }
        val secondary = if (secondScore > 0f) {
          Color(secondVibrantPixel).copy(alpha = 1f)
        } else {
          // Harmonious deeper shifted tone if icon is monochromatic vibrant
          AndroidColor.colorToHSV(bestVibrantPixel, hsv)
          hsv[0] = (hsv[0] + 24f) % 360f
          hsv[2] = (hsv[2] * 0.62f).coerceIn(0.14f, 0.85f)
          Color(AndroidColor.HSVToColor(hsv)).copy(alpha = 1f)
        }
        return primary to secondary
      }

      if (count > 0L) {
        val avgR = (sumR / count).toInt().coerceIn(0, 255)
        val avgG = (sumG / count).toInt().coerceIn(0, 255)
        val avgB = (sumB / count).toInt().coerceIn(0, 255)
        val primary = Color(avgR, avgG, avgB)
        val secondary = Color(
          red = (avgR * 0.58f).toInt().coerceIn(0, 255),
          green = (avgG * 0.64f).toInt().coerceIn(0, 255),
          blue = ((avgB * 0.82f) + 28).toInt().coerceIn(0, 255),
        )
        return primary to secondary
      }
      return Color(0xFF22D3EE) to Color(0xFF3B82F6)
    }
  }
}
