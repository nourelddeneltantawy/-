package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.Motor
import com.example.ui.components.parseHexToColor
import com.example.ui.components.parseWireColorNameToColor
import com.example.ui.components.resolveWireColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object MotorShareHelper {

    fun shareMotorAsImage(context: Context, motor: Motor) {
        Toast.makeText(context, "جاري إعداد صورة المخطط للمشاركة...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = createMotorBitmap(context, motor)
                val cachePath = File(context.cacheDir, "shared_motors")
                if (!cachePath.exists()) cachePath.mkdirs()
                val imageFile = File(cachePath, "motor_${motor.id.take(8)}.png")
                val fos = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
                fos.close()

                val contentUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )

                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "مخطط وأطراف ماتور: ${motor.name}\n${motor.brand} ${motor.model}\nالبراء إلكترونيك"
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, "مشاركة مخطط الماتور كصورة")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "تعذر إنشاء صورة الماتور: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun createMotorBitmap(context: Context, motor: Motor): Bitmap {
        val width = 1080
        val pinouts = motor.pinouts

        // Calculate dynamic height based on pins and notes
        val headerHeight = 260
        val pinRowHeight = 110
        val pinsTotalHeight = (pinouts.size * pinRowHeight).coerceAtLeast(180)
        val notesHeight = if (motor.notes.isNotBlank()) 140 else 0
        val footerHeight = 120
        val totalHeight = headerHeight + pinsTotalHeight + notesHeight + footerHeight

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background: High-tech dark slate (#0F172A)
        canvas.drawColor(android.graphics.Color.parseColor("#0F172A"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top decorative accent bar (Cyan / Electric Blue)
        paint.color = android.graphics.Color.parseColor("#0284C7")
        canvas.drawRect(0f, 0f, width.toFloat(), 16f, paint)

        // Header Background Card
        paint.color = android.graphics.Color.parseColor("#1E293B")
        val headerRect = RectF(36f, 36f, (width - 36).toFloat(), (headerHeight - 20).toFloat())
        canvas.drawRoundRect(headerRect, 24f, 24f, paint)

        // Header Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = android.graphics.Color.parseColor("#334155")
        canvas.drawRoundRect(headerRect, 24f, 24f, paint)
        paint.style = Paint.Style.FILL

        // App Watermark / Category Badge
        paint.color = android.graphics.Color.parseColor("#38BDF8")
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        val categoryText = if (motor.category.isNotBlank()) "مخطط ماتور • ${motor.category}" else "مخطط أطراف الماتور"
        canvas.drawText(categoryText, (width - 70).toFloat(), 86f, paint)

        // Motor Title
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 46f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(motor.name, (width - 70).toFloat(), 148f, paint)

        // Brand & Model & Active Pin count
        paint.color = android.graphics.Color.parseColor("#94A3B8")
        paint.textSize = 28f
        paint.typeface = Typeface.DEFAULT
        val activePinsCount = pinouts.count { !it.isBlankPin }
        val metaText = "${motor.brand} • ${motor.model} • ${motor.motorType.ifBlank { "ماتور غسالة" }} ($activePinsCount أطراف)"
        canvas.drawText(metaText, (width - 70).toFloat(), 196f, paint)

        // Vertical Pins Section
        var currentY = headerHeight.toFloat() + 10f

        pinouts.forEachIndexed { index, pin ->
            val rowRect = RectF(36f, currentY, (width - 36).toFloat(), currentY + 92f)

            // Pin Row Background
            paint.color = android.graphics.Color.parseColor("#1E293B")
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rowRect, 18f, 18f, paint)

            // Pin border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = android.graphics.Color.parseColor("#334155")
            canvas.drawRoundRect(rowRect, 18f, 18f, paint)
            paint.style = Paint.Style.FILL

            // Terminal Socket Number Box (Right side in RTL)
            val socketRight = (width - 56).toFloat()
            val socketLeft = socketRight - 70f
            val socketRect = RectF(socketLeft, currentY + 14f, socketRight, currentY + 78f)
            paint.color = android.graphics.Color.parseColor("#0F172A")
            canvas.drawRoundRect(socketRect, 12f, 12f, paint)

            // Socket border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = android.graphics.Color.parseColor("#64748B")
            canvas.drawRoundRect(socketRect, 12f, 12f, paint)
            paint.style = Paint.Style.FILL

            // Pin Number Text
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 32f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${pin.pinNumber}", socketRect.centerX(), socketRect.centerY() + 11f, paint)

            // Wire Color Swatch Circle
            val (cPrimary, cSecondary) = resolveWireColors(
                colorHex = pin.colorHex,
                wireColorName = pin.wireColor,
                secondaryColorHex = pin.secondaryColorHex,
                secondaryColorName = pin.secondaryColorName
            )
            val circleCenterX = socketLeft - 44f
            val circleCenterY = socketRect.centerY()

            if (pin.isBlankPin) {
                // Blank NC Terminal symbol
                paint.color = android.graphics.Color.parseColor("#475569")
                canvas.drawCircle(circleCenterX, circleCenterY, 20f, paint)
                paint.color = android.graphics.Color.parseColor("#E2E8F0")
                paint.textSize = 24f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("∅", circleCenterX, circleCenterY + 8f, paint)
            } else {
                val primaryInt = android.graphics.Color.argb(
                    (cPrimary.alpha * 255).toInt(),
                    (cPrimary.red * 255).toInt(),
                    (cPrimary.green * 255).toInt(),
                    (cPrimary.blue * 255).toInt()
                )
                paint.color = primaryInt
                canvas.drawCircle(circleCenterX, circleCenterY, 20f, paint)

                if (cSecondary != null) {
                    val secondaryInt = android.graphics.Color.argb(
                        (cSecondary.alpha * 255).toInt(),
                        (cSecondary.red * 255).toInt(),
                        (cSecondary.green * 255).toInt(),
                        (cSecondary.blue * 255).toInt()
                    )
                    paint.color = secondaryInt
                    canvas.drawArc(
                        RectF(circleCenterX - 20f, circleCenterY - 20f, circleCenterX + 20f, circleCenterY + 20f),
                        45f,
                        90f,
                        true,
                        paint
                    )
                    canvas.drawArc(
                        RectF(circleCenterX - 20f, circleCenterY - 20f, circleCenterX + 20f, circleCenterY + 20f),
                        225f,
                        90f,
                        true,
                        paint
                    )
                }

                // White ring around color swatch
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = android.graphics.Color.parseColor("#CBD5E1")
                canvas.drawCircle(circleCenterX, circleCenterY, 20f, paint)
                paint.style = Paint.Style.FILL
            }

            // Pin Function Name
            paint.textAlign = Paint.Align.RIGHT
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 30f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val funcName = if (pin.isBlankPin) "مكان فاضي في السوكيت (NC)" else pin.name.ifBlank { "طرف توصيل" }
            canvas.drawText(funcName, circleCenterX - 36f, currentY + 42f, paint)

            // Pin Details (NO color names written as text!)
            paint.color = android.graphics.Color.parseColor("#94A3B8")
            paint.textSize = 22f
            paint.typeface = Typeface.DEFAULT
            val subDetails = if (pin.isBlankPin) {
                "خانة فارغة في السوكيت بدون توصيل"
            } else {
                pin.functionDesc
            }
            if (subDetails.isNotBlank()) {
                canvas.drawText(subDetails, circleCenterX - 36f, currentY + 74f, paint)
            }

            currentY += pinRowHeight.toFloat()
        }

        // Notes section if present
        if (motor.notes.isNotBlank()) {
            val notesRect = RectF(36f, currentY + 10f, (width - 36).toFloat(), currentY + 110f)
            paint.color = android.graphics.Color.parseColor("#1E293B")
            canvas.drawRoundRect(notesRect, 16f, 16f, paint)

            paint.color = android.graphics.Color.parseColor("#38BDF8")
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("ملاحظات الفحص والتشغيل:", (width - 66).toFloat(), currentY + 48f, paint)

            paint.color = android.graphics.Color.parseColor("#CBD5E1")
            paint.textSize = 22f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(motor.notes.take(80), (width - 66).toFloat(), currentY + 84f, paint)

            currentY += 125f
        }

        // Footer Branding (Simply "البراء إلكترونيك")
        paint.color = android.graphics.Color.parseColor("#94A3B8")
        paint.textSize = 34f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            "البراء إلكترونيك",
            (width / 2).toFloat(),
            totalHeight - 40f,
            paint
        )

        return bitmap
    }
}
