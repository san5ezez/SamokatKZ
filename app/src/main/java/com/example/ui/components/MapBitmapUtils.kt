package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.example.model.RoadHazardType

object MapBitmapUtils {

  fun createDestinationPin(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (44 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val radius = 14f * density

    // Shadow
    val shadowPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44000000
      }
    canvas.drawCircle(cx, size - 4f * density, 6f * density, shadowPaint)

    // Pin body (Yandex red)
    val pinPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF3B30.toInt()
      }
    val path =
      Path().apply {
        moveTo(cx, size - 5f * density)
        lineTo(cx - radius * 0.85f, cx + 2f * density)
        lineTo(cx + radius * 0.85f, cx + 2f * density)
        close()
      }
    canvas.drawPath(path, pinPaint)
    canvas.drawCircle(cx, cx - 2f * density, radius, pinPaint)

    // Inner white dot
    val innerPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
      }
    canvas.drawCircle(cx, cx - 2f * density, 5.5f * density, innerPaint)
    return bitmap
  }

  fun createOriginScooterPin(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (38 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f

    val outerGlow =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFCC00
      }
    canvas.drawCircle(cx, cx, 17f * density, outerGlow)

    val whiteRing =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
      }
    canvas.drawCircle(cx, cx, 12f * density, whiteRing)

    val yellowCore =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFCC00.toInt()
      }
    canvas.drawCircle(cx, cx, 9f * density, yellowCore)

    val darkCenter =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF16181D.toInt()
      }
    canvas.drawCircle(cx, cx, 3.5f * density, darkCenter)
    return bitmap
  }

  fun createHazardPin(context: Context, type: RoadHazardType): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (34 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f

    val bgColor =
      when (type) {
        RoadHazardType.ACCIDENT -> 0xFFFF3B30.toInt()
        RoadHazardType.ROAD_CLOSURE -> 0xFFD92D20.toInt()
        RoadHazardType.ROAD_WORKS -> 0xFFFF9500.toInt()
        RoadHazardType.HEAVY_TRAFFIC -> 0xFFE53935.toInt()
      }
    val symbol =
      when (type) {
        RoadHazardType.ACCIDENT -> "!"
        RoadHazardType.ROAD_CLOSURE -> "×"
        RoadHazardType.ROAD_WORKS -> "▲"
        RoadHazardType.HEAVY_TRAFFIC -> "≈"
      }

    val borderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
      }
    canvas.drawCircle(cx, cx, 15f * density, borderPaint)

    val fillPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
      }
    canvas.drawCircle(cx, cx, 12.5f * density, fillPaint)

    val textPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
      }
    val yOffset = cx - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(symbol, cx, yOffset, textPaint)
    return bitmap
  }
}
