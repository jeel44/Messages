package com.sms.textmessages.messenger.receiver

import com.sms.textmessages.messenger.ui.chat.isServiceSender

enum class NotificationCategory {
    PERSONAL, OTP, OFFER, TRANSACTION_DEBIT, TRANSACTION_CREDIT, SERVICE_DEFAULT
}

private val explicitOtpPhrase = Regex(
    """\b(otp|one[\s-]?time[\s-]?password)\b""",
    RegexOption.IGNORE_CASE
)

private val otpRelatedWord = Regex(
    """\b(otp|code|verification)\b""",
    RegexOption.IGNORE_CASE
)

// Matches a standalone 4-8 digit code, e.g. the "482913" in "482913 is your OTP" -
// same shape of code HomeScreen.extractCopyableCode looks for.
private val standaloneCodePattern = Regex("""\b\d{4,8}\b""")

private val amountPattern = Regex(
    """(?:rs\.?|inr|₹|\$|usd)\s?[\d,]+(?:\.\d+)?""",
    RegexOption.IGNORE_CASE
)

private val debitKeywords = listOf("debited", "spent", "paid", "withdrawn", "sent")
private val creditKeywords = listOf("credited", "received", "deposited", "refund")

private val offerPattern = Regex(
    """\b(off|sale|discount|cashback|offer)\b""",
    RegexOption.IGNORE_CASE
)

private fun isOtpMessage(body: String): Boolean {
    if (explicitOtpPhrase.containsMatchIn(body)) return true
    return standaloneCodePattern.containsMatchIn(body) && otpRelatedWord.containsMatchIn(body)
}

fun classifyNotification(sender: String, body: String): NotificationCategory {

    if (!isServiceSender(sender)) return NotificationCategory.PERSONAL

    if (isOtpMessage(body)) return NotificationCategory.OTP

    val hasAmount = amountPattern.containsMatchIn(body)

    if (hasAmount && debitKeywords.any { body.contains(it, ignoreCase = true) }) {
        return NotificationCategory.TRANSACTION_DEBIT
    }

    if (hasAmount && creditKeywords.any { body.contains(it, ignoreCase = true) }) {
        return NotificationCategory.TRANSACTION_CREDIT
    }

    if (offerPattern.containsMatchIn(body)) return NotificationCategory.OFFER

    return NotificationCategory.SERVICE_DEFAULT
}
