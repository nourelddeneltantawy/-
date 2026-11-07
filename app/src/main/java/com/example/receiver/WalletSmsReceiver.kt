package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import com.example.data.repository.WalletRoutingRepository

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

                // Match wallet by active state or highest priority
                val wallets = WalletRoutingRepository.wallets.value
                val matchedWallet = wallets.firstOrNull { it.status == "ACTIVE" } ?: wallets.firstOrNull()

                if (matchedWallet != null) {
                    WalletRoutingRepository.recordTransaction(
                        context = context,
                        walletId = matchedWallet.id,
                        amount = amount,
                        referenceText = "SMS تلقائي [$sender]: $reference"
                    ) { success ->
                        if (success) {
                            Toast.makeText(
                                context,
                                "تم تسجيل تحويل $amount ج.م تلقائياً لمحفظة (${matchedWallet.name})",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in WalletSmsReceiver", e)
        }
    }

    companion object {
        private const val TAG = "WalletSmsReceiver"

        /**
         * Normalizes Arabic numerals (٠١٢٣٤٥٦٧٨٩) to standard ASCII digits (0-9)
         */
        fun normalizeDigits(input: String): String {
            val arabicChars = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
            var result = input
            for (i in arabicChars.indices) {
                result = result.replace(arabicChars[i], ('0' + i))
            }
            return result
        }

        /**
         * Parses Vodafone Cash, InstaPay, Etisalat Cash, or Orange Cash confirmation SMS.
         * Returns Pair(amount, reference) or null if not a matching receipt SMS.
         */
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
            // "You have received EGP 500.00 from 010..." or "received 500 EGP"
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
            // "تم تحويل 250.00 جم بنجاح إلى حسابك عبر انستاباي"
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
