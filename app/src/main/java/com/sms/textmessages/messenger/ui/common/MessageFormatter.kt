package com.sms.textmessages.messenger.ui.common

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.sms.textmessages.messenger.ui.home.copyCodeToClipboard
import com.sms.textmessages.messenger.ui.home.extractCopyableCode

// Regex for Amounts: Matches Rs, INR, $, ₹ followed by numbers, optionally with commas and decimals.
// Also matches numbers followed by INR, rupees, dollars, or amounts after debited/credited.
private val amountRegex = Regex("""(?i)(?:rs\.?|inr|usd|\$|₹)\s*[\d,]+(?:\.\d+)?|\b[\d,]+(?:\.\d+)?\s*(?:inr|rupees|dollars|usd)\b|(?i)(?:debited|credited|deducted|received|paid)\s*(?:by|for|from|with)?\s*(?:rs\.?|inr|usd|\$|₹)?\s*([\d,]+(?:\.\d+)?)""")

// Regex for Dates: Matches 12/04/24, 12-04-2024, 12 Jan 2024, 12th Jan, etc.
private val dateRegex = Regex("""\b(?:0?[1-9]|[12][0-9]|3[01])[./-](?:0?[1-9]|1[0-2])[./-](?:19|20)?\d{2}\b|\b(?:0?[1-9]|[12][0-9]|3[01])(?:st|nd|rd|th)?\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s*(?:19|20)?\d{2}\b""", RegexOption.IGNORE_CASE)

// Regex for Account Numbers: a/c no, acct ending in 1234, etc. Or just masked patterns like xx1234.
private val accountRegex = Regex("""(?i)(?:a/c|acct|account)[\s.]*(?:no\.?)?[\s\-:]*([x*\d]+)|\b[a-z]{0,4}[x*]{2,}\d{3,4}\b""")

// Regex for Offers/Discounts (Percentages, Promo Codes, Cashbacks)
private val offerRegex = Regex("""(?i)\b\d+(?:\.\d+)?\s*%\s*(?:off|cashback|discount)?\b|(?i)\b(?:flat|upto|save)\s*(?:rs\.?|inr|usd|\$|₹)?\s*[\d,]+(?:\.\d+)?\b""")
private val promoCodeRegex = Regex("""(?i)(?:code|coupon|use|apply)[\s:-]+([A-Z0-9]{4,15})\b""")

fun formatMessageText(
    context: Context,
    text: String,
    linkColor: Color,
    boldColor: Color? = null
): AnnotatedString {
    return buildAnnotatedString {
        append(text)

        // 1. Standard Linkify (URLs, Phone Numbers, Emails)
        val spannable = android.text.SpannableString(text)
        android.text.util.Linkify.addLinks(
            spannable,
            android.text.util.Linkify.WEB_URLS or
            android.text.util.Linkify.PHONE_NUMBERS or
            android.text.util.Linkify.EMAIL_ADDRESSES
        )
        val spans = spannable.getSpans(0, text.length, android.text.style.URLSpan::class.java)

        for (span in spans) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            addStyle(
                style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                ),
                start = start,
                end = end
            )
            addLink(
                LinkAnnotation.Url(span.url),
                start = start,
                end = end
            )
        }

        // Helper to add highlights for regex matches
        fun highlightMatches(regex: Regex) {
            regex.findAll(text).forEach { matchResult ->
                // If there are capturing groups (like the amount after 'debited by'), highlight just the group
                val groupToHighlight = if (matchResult.groups.size > 1 && matchResult.groups[1] != null) {
                    matchResult.groups[1]
                } else if (matchResult.groups.size > 2 && matchResult.groups[2] != null) {
                    matchResult.groups[2]
                } else {
                    matchResult.groups[0]
                }
                
                if (groupToHighlight == null) return@forEach

                val start = groupToHighlight.range.first
                val end = groupToHighlight.range.last + 1
                
                // Only highlight if not already part of a link (to avoid overlapping styles)
                val isOverlap = spans.any { 
                    val spanStart = spannable.getSpanStart(it)
                    val spanEnd = spannable.getSpanEnd(it)
                    start < spanEnd && end > spanStart 
                }
                
                if (!isOverlap) {
                    addStyle(
                        style = SpanStyle(
                            color = linkColor,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = start,
                        end = end
                    )
                }
            }
        }

        // 2. Custom Highlights for Transactions (Amounts, Dates, Accounts)
        highlightMatches(amountRegex)
        highlightMatches(dateRegex)
        highlightMatches(accountRegex)
        highlightMatches(offerRegex)
        highlightMatches(promoCodeRegex)

        // 3. Highlight and underline the copyable code (OTP/Ref ID)
        val copyableCode = extractCopyableCode(text)
        if (copyableCode != null) {
            val start = text.indexOf(copyableCode)
            if (start >= 0) {
                val end = start + copyableCode.length
                addStyle(
                    style = SpanStyle(
                        color = boldColor ?: linkColor,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = start,
                    end = end
                )
                // Make it clickable if they tap on it
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "copyable_code",
                        linkInteractionListener = {
                            copyCodeToClipboard(context, copyableCode)
                        }
                    ),
                    start = start,
                    end = end
                )
            }
        }
    }
}
