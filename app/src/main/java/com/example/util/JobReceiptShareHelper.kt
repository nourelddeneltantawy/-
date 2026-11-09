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
    val includeLocation: Boolean = true,
    val includeDeviceType: Boolean = true,
    val includeFault: Boolean = true,
    val includeCost: Boolean = true,
    val includeReplacedParts: Boolean = true,
    val includeOtherDetails: Boolean = true,
    val includeStatus: Boolean = false,
    val includeImage: Boolean = true,
    val includeCustomFields: Boolean = true
)

object JobReceiptShareHelper {

    fun generateReceiptBitmap(
        context: Context,
        job: TechnicianJobRecord,
        config: ReceiptCustomization
    ): Bitmap {
        return generateMultiJobReceiptBitmap(context, listOf(job), config)
    }

    fun generateMultiJobReceiptBitmap(
        context: Context,
        jobs: List<TechnicianJobRecord>,
        config: ReceiptCustomization
    ): Bitmap {
        if (jobs.isEmpty()) {
            return Bitmap.createBitmap(1080, 400, Bitmap.Config.ARGB_8888)
        }

        val firstJob = jobs.first()
        val width = 1080
        val padding = 44f
        val contentWidth = width - (padding * 2)

        // Decode App Logo
        val logoBm: Bitmap? = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.logo_albaraa)
        } catch (_: Throwable) {
            null
        }

        // Calculate dynamic height based on single or multi-card
        var calculatedHeight = 270f // Header section

        // Client info section height
        var clientRows = 0
        if (config.includeDate) clientRows++
        if (config.includePhone && firstJob.clientPhone.isNotBlank()) clientRows++
        if (config.includeLocation && firstJob.clientLocation.isNotBlank()) clientRows++
        if (clientRows > 0) {
            calculatedHeight += (clientRows * 60f) + 40f
        }

        // Each card section height
        for (job in jobs) {
            calculatedHeight += 70f // Card header (Device title & cost)
            var cardFieldsCount = 0
            if (config.includeFault && job.faultDescription.isNotBlank()) cardFieldsCount++
            if (config.includeReplacedParts && job.replacedParts.isNotBlank()) cardFieldsCount++
            if (config.includeStatus && job.status.isNotBlank()) cardFieldsCount++
            if (config.includeOtherDetails && job.otherDetails.isNotBlank()) cardFieldsCount++
            if (config.includeCustomFields) cardFieldsCount += job.customFields.size

            calculatedHeight += (cardFieldsCount * 55f) + 30f

            if (config.includeImage && job.imageUrl.isNotBlank()) {
                calculatedHeight += 380f // photo block
            }
        }

        // Total cost block if multiple cards
        if (jobs.size > 1 && config.includeCost) {
            calculatedHeight += 120f
        }

        // Extra bottom padding (minimal, no cluttered footer as requested!)
        calculatedHeight += 70f

        val bitmap = Bitmap.createBitmap(width, calculatedHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Sleek, clean luxury dark background (#0E1626)
        canvas.drawColor(Color.parseColor("#0E1626"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top accent line (Cyan to Emerald)
        val topShader = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            intArrayOf(Color.parseColor("#0284C7"), Color.parseColor("#10B981")),
            null,
            Shader.TileMode.CLAMP
        )
        paint.shader = topShader
        canvas.drawRect(0f, 0f, width.toFloat(), 14f, paint)
        paint.shader = null

        // 2. Minimalist Header: Just Logo + Title + Subtitle ("إيصال صيانة")
        val headerCardRect = RectF(padding, 32f, width - padding, 220f)
        paint.color = Color.parseColor("#142038")
        canvas.drawRoundRect(headerCardRect, 22f, 22f, paint)

        // Draw Logo on the Right
        if (logoBm != null) {
            val logoSize = 130
            val scaledLogo = Bitmap.createScaledBitmap(logoBm, logoSize, logoSize, true)
            val logoLeft = width - padding - logoSize - 25f
            val logoTop = 60f
            canvas.drawBitmap(scaledLogo, logoLeft, logoTop, null)
        }

        // Header Titles on the Left
        paint.color = Color.WHITE
        paint.textSize = 38f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("م. البراء إلكترونيك", padding + 30f, 95f, paint)

        paint.color = Color.parseColor("#38BDF8") // Cyan
        paint.textSize = 28f
        paint.typeface = Typeface.DEFAULT_BOLD
        val receiptSubtitle = if (jobs.size > 1) "إيصال صيانة مجمع (${jobs.size} كروت)" else "إيصال صيانة"
        canvas.drawText(receiptSubtitle, padding + 30f, 140f, paint)

        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT
        val receiptId = "رقم الإيصال: #${firstJob.id.take(6).uppercase()} | ${firstJob.date}"
        canvas.drawText(receiptId, padding + 30f, 185f, paint)

        var currentY = 245f

        // 3. Customer Info Card (if phone/location/date selected)
        if (clientRows > 0) {
            val clientCardHeight = (clientRows * 58f) + 24f
            val clientRect = RectF(padding, currentY, width - padding, currentY + clientCardHeight)

            paint.color = Color.parseColor("#121B30")
            canvas.drawRoundRect(clientRect, 18f, 18f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.8f
            paint.color = Color.parseColor("#1E2D4A")
            canvas.drawRoundRect(clientRect, 18f, 18f, paint)
            paint.style = Paint.Style.FILL

            var clientRowY = currentY + 45f
            if (config.includeDate) {
                drawReceiptRow(canvas, paint, "تاريخ الاستلام واليوم:", "${firstJob.dayOfWeek} ${firstJob.date}", padding, width, clientRowY, Color.parseColor("#38BDF8"))
                clientRowY += 58f
            }
            if (config.includePhone && firstJob.clientPhone.isNotBlank()) {
                drawReceiptRow(canvas, paint, "رقم هاتف العميل:", firstJob.clientPhone, padding, width, clientRowY, Color.parseColor("#10B981"))
                clientRowY += 58f
            }
            if (config.includeLocation && firstJob.clientLocation.isNotBlank()) {
                drawReceiptRow(canvas, paint, "مكان وعنوان العميل:", firstJob.clientLocation, padding, width, clientRowY, Color.parseColor("#94A3B8"))
                clientRowY += 58f
            }

            currentY += clientCardHeight + 20f
        }

        // 4. Render Each Card
        var totalCost = 0.0
        for ((index, job) in jobs.withIndex()) {
            totalCost += job.cost

            // Pre-calculate card rows
            val cardRows = mutableListOf<Pair<String, String>>()
            if (config.includeFault && job.faultDescription.isNotBlank()) {
                cardRows.add("العطل المُبلّغ:" to job.faultDescription)
            }
            if (config.includeReplacedParts && job.replacedParts.isNotBlank()) {
                cardRows.add("قطع الغيار المبدلة:" to job.replacedParts)
            }
            if (config.includeStatus && job.status.isNotBlank()) {
                cardRows.add("حالة الشغل:" to job.status)
            }
            if (config.includeOtherDetails && job.otherDetails.isNotBlank()) {
                cardRows.add("ملاحظات:" to job.otherDetails)
            }
            if (config.includeCustomFields && job.customFields.isNotEmpty()) {
                job.customFields.forEach { (k, v) ->
                    if (k.isNotBlank() && v.isNotBlank()) {
                        cardRows.add("$k:" to v)
                    }
                }
            }

            // Image for this card
            val cardBm: Bitmap? = if (config.includeImage && job.imageUrl.isNotBlank()) {
                loadBitmapFromUriOrPath(context, job.imageUrl)
            } else null

            val cardBlockHeight = 70f + (cardRows.size * 55f) + 16f + (if (cardBm != null) 360f else 0f)
            val cardRect = RectF(padding, currentY, width - padding, currentY + cardBlockHeight)

            // Card background
            paint.color = Color.parseColor("#121E36")
            canvas.drawRoundRect(cardRect, 18f, 18f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.8f
            paint.color = Color.parseColor("#1E2E50")
            canvas.drawRoundRect(cardRect, 18f, 18f, paint)
            paint.style = Paint.Style.FILL

            // Card Header Bar inside the card
            val headerTopRect = RectF(padding, currentY, width - padding, currentY + 62f)
            paint.color = Color.parseColor("#1A2846")
            canvas.drawRoundRect(headerTopRect, 18f, 18f, paint)

            // Device Name
            val cardLabel = if (jobs.size > 1) "كارتة ${index + 1}: ${job.deviceType}" else job.deviceType
            paint.color = Color.WHITE
            paint.textSize = 28f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(cardLabel, width - padding - 25f, currentY + 42f, paint)

            // Cost Badge on the Left
            if (config.includeCost && job.cost > 0) {
                paint.color = Color.parseColor("#34D399") // Green
                paint.textSize = 26f
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("${String.format("%.0f", job.cost)} ج.م", padding + 25f, currentY + 42f, paint)
            }

            var itemRowY = currentY + 105f
            for (row in cardRows) {
                val (lbl, valStr) = row
                drawReceiptRow(canvas, paint, lbl, valStr, padding, width, itemRowY, Color.parseColor("#E2E8F0"))
                itemRowY += 55f
            }

            // Draw image if available
            if (cardBm != null) {
                try {
                    val maxW = (contentWidth - 60).toInt()
                    val maxH = 310
                    val aspect = cardBm.width.toFloat() / cardBm.height.toFloat()
                    val targetW = if (aspect > 1f) maxW else (maxH * aspect).toInt().coerceAtMost(maxW)
                    val targetH = if (aspect > 1f) (maxW / aspect).toInt().coerceAtMost(maxH) else maxH

                    val scaled = Bitmap.createScaledBitmap(cardBm, targetW, targetH, true)
                    val left = padding + 30f + ((maxW - targetW) / 2f)
                    val top = itemRowY
                    canvas.drawBitmap(scaled, left, top, null)
                } catch (_: Throwable) {}
            }

            currentY += cardBlockHeight + 18f
        }

        // 5. Total Summary Card (for multiple cards or single total)
        if (config.includeCost && (jobs.size > 1 || jobs.first().cost > 0)) {
            val totalRect = RectF(padding, currentY, width - padding, currentY + 80f)
            paint.color = Color.parseColor("#064E3B")
            canvas.drawRoundRect(totalRect, 16f, 16f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.parseColor("#10B981")
            canvas.drawRoundRect(totalRect, 16f, 16f, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.WHITE
            paint.textSize = 28f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.RIGHT
            val totalLabel = if (jobs.size > 1) "إجمالي الحساب (${jobs.size} كروت):" else "إجمالي الحساب المطلوب:"
            canvas.drawText(totalLabel, width - padding - 30f, currentY + 50f, paint)

            paint.color = Color.parseColor("#6EE7B7")
            paint.textSize = 32f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${String.format("%.0f", totalCost)} ج.م (EGP)", padding + 30f, currentY + 52f, paint)
        }

        // Zero cluttered footer! Clean, minimalist and elegant!
        return bitmap
    }

    private fun drawReceiptRow(
        canvas: Canvas,
        paint: Paint,
        label: String,
        value: String,
        padding: Float,
        width: Int,
        y: Float,
        valueColorInt: Int = Color.WHITE
    ) {
        // Label on Right
        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 23f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(label, width - padding - 30f, y, paint)

        // Value on Left
        paint.color = valueColorInt
        paint.textSize = 23f
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(value, padding + 30f, y, paint)
    }

    private fun loadBitmapFromUriOrPath(context: Context, pathOrUrl: String): Bitmap? {
        return try {
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                val input: InputStream = URL(pathOrUrl).openStream()
                BitmapFactory.decodeStream(input)
            } else if (pathOrUrl.startsWith("data:image/") && pathOrUrl.contains(";base64,")) {
                val base64Data = pathOrUrl.substringAfter(";base64,")
                val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } else if (pathOrUrl.startsWith("content://") || pathOrUrl.startsWith("file://")) {
                val uri = Uri.parse(pathOrUrl)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                val file = File(pathOrUrl)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun shareToWhatsApp(
        context: Context,
        jobs: List<TechnicianJobRecord>,
        config: ReceiptCustomization,
        phone: String
    ) {
        Toast.makeText(context, "جاري إعداد صورة الإيصال للواتساب...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = generateMultiJobReceiptBitmap(context, jobs, config)
                val imageUri = saveBitmapToCache(context, bitmap, "receipt_multi_${System.currentTimeMillis()}.png")

                val cleanPhone = phone.filter { it.isDigit() }
                val targetPhone = if (cleanPhone.startsWith("0")) "2$cleanPhone" else cleanPhone

                val cardSummary = if (jobs.size > 1) {
                    jobs.joinToString("\n") { "• ${it.deviceType}: ${it.faultDescription} (${String.format("%.0f", it.cost)} ج.م)" }
                } else {
                    "الجهاز: ${jobs.first().deviceType}\nالعطل: ${jobs.first().faultDescription}"
                }

                val totalCost = jobs.sumOf { it.cost }
                val text = "السلام عليكم، تفاصيل إيصال الصيانة لدى مركز م. البراء إلكترونيك:\n" +
                        cardSummary + "\n" +
                        (if (totalCost > 0) "إجمالي الحساب: ${String.format("%.0f", totalCost)} ج.م\n" else "") +
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
        jobs: List<TechnicianJobRecord>,
        config: ReceiptCustomization
    ) {
        Toast.makeText(context, "جاري تجهيز الصورة للمشاركة...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = generateMultiJobReceiptBitmap(context, jobs, config)
                val imageUri = saveBitmapToCache(context, bitmap, "receipt_share_${System.currentTimeMillis()}.png")

                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "إيصال صيانة - م. البراء إلكترونيك (${jobs.size} كروت)"
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(intent, "مشاركة صورة الإيصال")
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
        jobs: List<TechnicianJobRecord>,
        config: ReceiptCustomization
    ) {
        Toast.makeText(context, "جاري حفظ الإيصال في المعرض...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = generateMultiJobReceiptBitmap(context, jobs, config)
                val filename = "Albaraa_Receipt_${System.currentTimeMillis()}.png"

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
