package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.TechnicianJobRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL

data class ReceiptCustomization(
    val includeDate: Boolean = true,
    val includePhone: Boolean = true,
    val includeDeviceType: Boolean = true,
    val includeFault: Boolean = true,
    val includeCost: Boolean = true,
    val includeReplacedParts: Boolean = true,
    val includeLocation: Boolean = true,
    val includeOtherDetails: Boolean = true,
    val includeStatus: Boolean = true,
    val includeImage: Boolean = true,
    val includeCustomFields: Boolean = true,
    val includeStamp: Boolean = true
)

object JobReceiptShareHelper {

    fun generateReceiptBitmap(
        context: Context,
        job: TechnicianJobRecord,
        config: ReceiptCustomization
    ): Bitmap {
        val width = 1080
        val padding = 48f
        val contentWidth = width - (padding * 2)

        // Pre-calculate items to render
        val rows = mutableListOf<Pair<String, String>>()
        if (config.includeDate && (job.date.isNotBlank() || job.dayOfWeek.isNotBlank())) {
            rows.add("تاريخ ويوم الفحص:" to "${job.dayOfWeek}  ${job.date}")
        }
        if (config.includePhone && job.clientPhone.isNotBlank()) {
            rows.add("رقم هاتف العميل:" to job.clientPhone)
        }
        if (config.includeLocation && job.clientLocation.isNotBlank()) {
            rows.add("مكان / عنوان العميل:" to job.clientLocation)
        }
        if (config.includeDeviceType && job.deviceType.isNotBlank()) {
            rows.add("نوع الجهاز / الكارتة:" to job.deviceType)
        }
        if (config.includeFault && job.faultDescription.isNotBlank()) {
            rows.add("توصيف العطل المُبلّغ:" to job.faultDescription)
        }
        if (config.includeReplacedParts && job.replacedParts.isNotBlank()) {
            rows.add("قطع الغيار المبدلة:" to job.replacedParts)
        }
        if (config.includeCost && job.cost > 0) {
            rows.add("إجمالي التكلفة / الحساب:" to "${String.format("%.0f", job.cost)} ج.م (EGP)")
        }
        if (config.includeStatus && job.status.isNotBlank()) {
            rows.add("حالة الشغل الحالية:" to job.status)
        }
        if (config.includeOtherDetails && job.otherDetails.isNotBlank()) {
            rows.add("ملاحظات الفني وتفاصيل:" to job.otherDetails)
        }
        if (config.includeCustomFields && job.customFields.isNotEmpty()) {
            job.customFields.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) {
                    rows.add("$k:" to v)
                }
            }
        }

        // Try load card image if needed
        var cardBitmap: Bitmap? = null
        if (config.includeImage && job.imageUrl.isNotBlank()) {
            try {
                if (job.imageUrl.startsWith("http://") || job.imageUrl.startsWith("https://")) {
                    val input: InputStream = URL(job.imageUrl).openStream()
                    cardBitmap = BitmapFactory.decodeStream(input)
                } else if (job.imageUrl.startsWith("content://") || job.imageUrl.startsWith("file://")) {
                    val uri = Uri.parse(job.imageUrl)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        cardBitmap = BitmapFactory.decodeStream(stream)
                    }
                } else {
                    val file = File(job.imageUrl)
                    if (file.exists()) {
                        cardBitmap = BitmapFactory.decodeFile(file.absolutePath)
                    }
                }
            } catch (_: Throwable) {}
        }

        // Height estimation
        var totalHeight = 320f // Top header + margin
        val rowHeight = 90f
        totalHeight += rows.size * rowHeight + 40f
        if (cardBitmap != null) {
            totalHeight += 480f // Image section
        }
        if (config.includeStamp) {
            totalHeight += 380f // Official Stamp & footer section
        } else {
            totalHeight += 120f
        }

        val bitmap = Bitmap.createBitmap(width, totalHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Deep luxury navy/slate background
        canvas.drawColor(Color.parseColor("#0B1329"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top decorative gradient bar
        val barShader = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            intArrayOf(Color.parseColor("#0284C7"), Color.parseColor("#10B981"), Color.parseColor("#F59E0B")),
            null,
            Shader.TileMode.CLAMP
        )
        paint.shader = barShader
        canvas.drawRect(0f, 0f, width.toFloat(), 18f, paint)
        paint.shader = null

        // Header Background Card
        paint.color = Color.parseColor("#131D38")
        val headerRect = RectF(padding, 40f, width - padding, 260f)
        canvas.drawRoundRect(headerRect, 28f, 28f, paint)

        // Border around Header
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(headerRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        // Header App Logo (if available)
        try {
            val logoBm = BitmapFactory.decodeResource(context.resources, R.drawable.logo_albaraa)
            if (logoBm != null) {
                val logoSize = 150
                val scaledLogo = Bitmap.createScaledBitmap(logoBm, logoSize, logoSize, true)
                canvas.drawBitmap(scaledLogo, width - padding - logoSize - 20f, 75f, null)
            }
        } catch (_: Throwable) {}

        // Header Title
        paint.color = Color.WHITE
        paint.textSize = 42f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("م. البراء إلكترونيك", padding + 30f, 105f, paint)

        // Subtitle
        paint.color = Color.parseColor("#38BDF8") // Cyan
        paint.textSize = 28f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("تقرير صيانة كارتة • إيصال رسمي معتمد", padding + 30f, 150f, paint)

        // Tagline & Date
        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("صيانة وبرمجة كروت الغسالات والثلاجات والأجهزة المنزلية", padding + 30f, 190f, paint)
        canvas.drawText("رقم الإيصال: #${job.id.take(6).uppercase()} | ${job.date}", padding + 30f, 225f, paint)

        // 2. Details Table Card
        var currentY = 285f
        val tableHeight = (rows.size * rowHeight) + 30f
        val tableRect = RectF(padding, currentY, width - padding, currentY + tableHeight)

        paint.color = Color.parseColor("#111C36")
        canvas.drawRoundRect(tableRect, 24f, 24f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#1E293B")
        canvas.drawRoundRect(tableRect, 24f, 24f, paint)
        paint.style = Paint.Style.FILL

        var rowY = currentY + 55f
        for ((idx, row) in rows.withIndex()) {
            val (label, value) = row

            // Alternate subtle row highlight
            if (idx % 2 == 1) {
                paint.color = Color.parseColor("#152342")
                canvas.drawRect(padding + 8f, rowY - 40f, width - padding - 8f, rowY + 30f, paint)
            }

            // Bullet indicator
            paint.color = if (label.contains("تكلفة") || label.contains("حساب")) Color.parseColor("#10B981")
            else if (label.contains("عطل")) Color.parseColor("#EF4444")
            else Color.parseColor("#38BDF8")
            canvas.drawCircle(width - padding - 36f, rowY - 8f, 7f, paint)

            // Label (Right-aligned in RTL)
            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 27f
            paint.typeface = Typeface.DEFAULT_BOLD
            val labelX = width - padding - 65f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(label, labelX, rowY, paint)

            // Value (Left-aligned or positioned with clear space)
            paint.color = if (label.contains("تكلفة") || label.contains("حساب")) Color.parseColor("#34D399")
            else if (label.contains("حالة")) Color.parseColor("#FBBF24")
            else Color.WHITE
            paint.textSize = 27f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(value, padding + 30f, rowY, paint)

            paint.textAlign = Paint.Align.LEFT
            rowY += rowHeight
        }

        currentY += tableHeight + 25f

        // 3. Card Image Section (if present)
        if (cardBitmap != null) {
            val imgCardRect = RectF(padding, currentY, width - padding, currentY + 450f)
            paint.color = Color.parseColor("#111C36")
            canvas.drawRoundRect(imgCardRect, 24f, 24f, paint)

            paint.color = Color.parseColor("#38BDF8")
            paint.textSize = 26f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("📷 صورة الكارتة أثناء الفحص والصيانة:", padding + 25f, currentY + 45f, paint)

            // Scaled Card Bitmap
            val maxBmWidth = (contentWidth - 50).toInt()
            val maxBmHeight = 360
            val aspect = cardBitmap.width.toFloat() / cardBitmap.height.toFloat()
            val targetW: Int
            val targetH: Int
            if (aspect > 1f) {
                targetW = maxBmWidth
                targetH = (maxBmWidth / aspect).toInt().coerceAtMost(maxBmHeight)
            } else {
                targetH = maxBmHeight
                targetW = (maxBmHeight * aspect).toInt().coerceAtMost(maxBmWidth)
            }

            try {
                val scaled = Bitmap.createScaledBitmap(cardBitmap, targetW, targetH, true)
                val left = padding + 25f + ((maxBmWidth - targetW) / 2f)
                val top = currentY + 70f + ((maxBmHeight - targetH) / 2f)
                canvas.drawBitmap(scaled, left, top, null)
            } catch (_: Throwable) {}

            currentY += 475f
        }

        // 4. Official App Stamp and Signature Footer
        if (config.includeStamp) {
            val stampCardRect = RectF(padding, currentY, width - padding, currentY + 340f)
            paint.color = Color.parseColor("#0F1A33")
            canvas.drawRoundRect(stampCardRect, 24f, 24f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.parseColor("#F59E0B").apply { /* Golden accent */ }
            canvas.drawRoundRect(stampCardRect, 24f, 24f, paint)
            paint.style = Paint.Style.FILL

            // Draw Official Golden Circular Seal on the Left
            val stampCenterX = padding + 130f
            val stampCenterY = currentY + 160f
            val stampRadius = 100f

            // Outer Seal Ring
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = Color.parseColor("#F59E0B")
            canvas.drawCircle(stampCenterX, stampCenterY, stampRadius, paint)

            // Inner Ring
            paint.strokeWidth = 2f
            canvas.drawCircle(stampCenterX, stampCenterY, stampRadius - 12f, paint)
            paint.style = Paint.Style.FILL

            // Seal Center Text
            paint.color = Color.parseColor("#F59E0B")
            paint.textSize = 21f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("البراء إلكترونيك", stampCenterX, stampCenterY - 20f, paint)
            canvas.drawText("★ معتمد رسمياً ★", stampCenterX, stampCenterY + 12f, paint)
            paint.textSize = 17f
            canvas.drawText("جودة • ضمان • صيانة", stampCenterX, stampCenterY + 40f, paint)

            // Right side of Stamp: Legal and Guarantee Note
            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.parseColor("#F8FAFC")
            paint.textSize = 28f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("مركز م. البراء إلكترونيك لصيانة الكروت والأجهزة", width - padding - 35f, currentY + 65f, paint)

            paint.color = Color.parseColor("#10B981")
            paint.textSize = 23f
            canvas.drawText("✓ تم الفحص وتأكيد عمل الكارتة والأطراف بنجاح", width - padding - 35f, currentY + 115f, paint)

            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 22f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText("• قطع غيار أصلية وضمان شامل على عمليات الصيانة", width - padding - 35f, currentY + 160f, paint)
            canvas.drawText("• للتواصل والاستفسار: ${job.userEmail.ifBlank { "م. البراء" }}", width - padding - 35f, currentY + 205f, paint)

            // Bottom Ribbon
            paint.color = Color.parseColor("#0284C7")
            paint.textSize = 20f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("نشكركم لثقتكم واختياركم مركزنا المعتمد", width / 2f, currentY + 295f, paint)

            paint.textAlign = Paint.Align.LEFT
        }

        return bitmap
    }

    fun shareToWhatsApp(
        context: Context,
        job: TechnicianJobRecord,
        config: ReceiptCustomization,
        phone: String
    ) {
        Toast.makeText(context, "جاري إعداد صورة الإيصال للواتساب...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = generateReceiptBitmap(context, job, config)
                val imageUri = saveBitmapToCache(context, bitmap, "receipt_${job.id.take(8)}.png")

                val cleanPhone = phone.filter { it.isDigit() }
                val targetPhone = if (cleanPhone.startsWith("0")) "20$cleanPhone" else cleanPhone

                val text = "السلام عليكم، تفاصيل وتقرير فحص صيانة جهازك / كارتتك لدى م. البراء إلكترونيك:\n" +
                        "الجهاز: ${job.deviceType}\n" +
                        "العطل: ${job.faultDescription}\n" +
                        if (job.cost > 0) "التكلفة: ${String.format("%.0f", job.cost)} ج.م\n" else "" +
                        "مرفق صورة الإيصال المعتمد."

                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        putExtra(Intent.EXTRA_TEXT, text)
                        if (targetPhone.isNotBlank()) {
                            putExtra("jid", "$targetPhone@s.whatsapp.net")
                        }
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    try {
                        context.startActivity(intent)
                    } catch (_: Throwable) {
                        // WhatsApp not installed directly or fallback to chooser
                        val fallback = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, imageUri)
                            putExtra(Intent.EXTRA_TEXT, text)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(fallback, "إرسال صورة الإيصال للعميل")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "خطأ في إنشاء صورة الإيصال: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun shareGeneric(
        context: Context,
        job: TechnicianJobRecord,
        config: ReceiptCustomization
    ) {
        Toast.makeText(context, "جاري تجهيز الصورة للمشاركة...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = generateReceiptBitmap(context, job, config)
                val imageUri = saveBitmapToCache(context, bitmap, "receipt_${job.id.take(8)}.png")

                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "تقرير صيانة كارتة معتمد - م. البراء إلكترونيك\nالجهاز: ${job.deviceType} | التاريخ: ${job.date}"
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(intent, "مشاركة صورة الإيصال والتقرير")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveToGallery(
        context: Context,
        job: TechnicianJobRecord,
        config: ReceiptCustomization
    ) {
        Toast.makeText(context, "جاري حفظ الإيصال في المعرض...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = generateReceiptBitmap(context, job, config)
                val filename = "Albaraa_Receipt_${job.id.take(6)}_${System.currentTimeMillis()}.png"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AlbaraaElectronics")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val appDir = File(imagesDir, "AlbaraaElectronics")
                    if (!appDir.exists()) appDir.mkdirs()
                    val file = File(appDir, filename)
                    val fos = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush()
                    fos.close()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "تم حفظ صورة الإيصال بنجاح في مجلد الصور 📸", Toast.LENGTH_LONG).show()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "فشل الحفظ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val cachePath = File(context.cacheDir, "receipt_shares")
        if (!cachePath.exists()) cachePath.mkdirs()
        val file = File(cachePath, fileName)
        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
        fos.close()

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
