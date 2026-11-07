package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import com.example.data.repository.WalletRoutingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android SMS Collector Companion
 * Listens for incoming SMS from payment providers (Vodafone Cash, InstaPay, Etisalat, Orange)
 * and automatically updates wallet received balances in real-time.
 */
class WalletSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val fullBody = StringBuilder()
            var sender = ""

            for (sms in messages) {
                if (sms != null) {
                    sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: ""
                    fullBody.append(sms.displayMessageBody ?: sms.messageBody ?: "")
                }
            }

            val body = fullBody.toString()
            Log.d(TAG, "SMS Received from: $sender | Body: $body")

            val parseResult = parseSms(body)
            if (parseResult != null) {
                val (amount, reference) = parseResult
                Log.i(TAG, "Parsed incoming payment amount: $amount EGP (ref: $reference)")

                // Ensure repository initialized
                WalletRoutingRepository.init(context)
                val wallets = WalletRoutingRepository.wallets.value

                // Try to match wallet by phone number if mentioned in SMS
                val matchedByPhone = wallets.firstOrNull { w ->
                    w.phoneNumber.isNotBlank() && body.contains(w.phoneNumber.takeLast(8))
                }
                val targetWallet = matchedByPhone
                    ?: wallets.firstOrNull { it.status == "ACTIVE" }
                    ?: wallets.firstOrNull()

                if (targetWallet != null) {
                    WalletRoutingRepository.recordTransaction(
                        context = context,
                        walletId = targetWallet.id,
                        amount = amount,
                        referenceText = "SMS تلقائي [$sender]: $reference"
                    ) { success ->
                        if (success) {
                            Toast.makeText(
                                context,
                                "تم تسجيل تحويل $amount ج.م تلقائياً لمحفظة (${targetWallet.name})",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    // Optional: Dispatch to remote Backend API if configured
                    val remoteBackendUrl = context.getSharedPreferences("albaraa_wallets_prefs", Context.MODE_PRIVATE)
                        .getString("backend_api_url", "") ?: ""
                    if (remoteBackendUrl.isNotBlank()) {
                        dispatchToRemoteBackend(remoteBackendUrl, sender, body, targetWallet.phoneNumber, amount)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in WalletSmsReceiver", e)
        }
    }

    private fun dispatchToRemoteBackend(apiUrl: String, sender: String, body: String, recipientPhone: String, amount: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}api/wallet/sms-auto-sync" else "$apiUrl/api/wallet/sms-auto-sync"
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val payload = JSONObject().apply {
                    put("sender", sender)
                    put("messageBody", body)
                    put("recipientSim", recipientPhone)
                    put("amount", amount)
                    put("timestamp", System.currentTimeMillis())
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.i(TAG, "Remote backend sync response: $code")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to send SMS sync to remote backend: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "WalletSmsReceiver"

        fun normalizeDigits(input: String): String {
            val arabicChars = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
            var result = input
            for (i in arabicChars.indices) {
                result = result.replace(arabicChars[i], ('0' + i))
            }
            return result
        }

        fun parseSms(rawBody: String): Pair<Double, String>? {
            val text = normalizeDigits(rawBody)

            // Pattern 1: Vodafone Cash Arabic
            // "تم استلام مبلغ 500.00 جنيه من 01012345678" or "تم تحويل مبلغ 1,000 ج.م"
            val vfArabicRegex = Regex(
                """(?:تم\s*استلام\s*مبلغ|تم\s*تحويل\s*مبلغ|استلمت\s*مبلغ|إيداع\s*بمبلغ)\s*([\d,.]+)\s*(?:جنيه|ج\.?م|EGP|LE)?""",
                RegexOption.IGNORE_CASE
            )
            val matchVf = vfArabicRegex.find(text)
            if (matchVf != null) {
                val amountStr = matchVf.groupValues[1].replace(",", "").trim()
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    return Pair(amount, matchVf.value)
                }
            }

            // Pattern 2: English Cash / Transfer
            val vfEnglishRegex = Regex(
                """(?:received|transferred|deposited)\s*(?:EGP|LE)?\s*([\d,.]+)\s*(?:EGP|LE)?""",
                RegexOption.IGNORE_CASE
            )
            val matchEng = vfEnglishRegex.find(text)
            if (matchEng != null) {
                val amountStr = matchEng.groupValues[1].replace(",", "").trim()
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    return Pair(amount, matchEng.value)
                }
            }

            // Pattern 3: InstaPay Arabic
            val instaArabicRegex = Regex(
                """(?:تم\s*تحويل|تمت\s*إضافة)\s*([\d,.]+)\s*(?:جم|ج\.م|جنيه|EGP)""",
                RegexOption.IGNORE_CASE
            )
            val matchInsta = instaArabicRegex.find(text)
            if (matchInsta != null) {
                val amountStr = matchInsta.groupValues[1].replace(",", "").trim()
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    return Pair(amount, matchInsta.value)
                }
            }

            // Pattern 4: Generic Fallback with number + currency indicator
            val genericRegex = Regex(
                """([\d,.]+)\s*(?:جنيه|ج\.?م|EGP|LE)\s*(?:من|بنجاح|في\s*محفظتك)""",
                RegexOption.IGNORE_CASE
            )
            val matchGeneric = genericRegex.find(text)
            if (matchGeneric != null) {
                val amountStr = matchGeneric.groupValues[1].replace(",", "").trim()
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    return Pair(amount, matchGeneric.value)
                }
            }

            return null
        }
    }
}
