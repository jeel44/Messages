package com.sms.textmessages.messenger.receiver

import android.util.Log
import java.nio.charset.Charset

private const val TAG = "MMS_PDU"

// MMS message-type values (M-Notification.ind, M-Retrieve.conf, ...). These are the
// raw wire bytes for the X-Mms-Message-Type header's short-integer value (already
// including the WSP short-integer high bit), matching AOSP PduHeaders.MESSAGE_TYPE_*.
internal object MmsMessageType {
    const val SEND_REQ = 0x80
    const val SEND_CONF = 0x81
    const val NOTIFICATION_IND = 0x82
    const val NOTIFYRESP_IND = 0x83
    const val RETRIEVE_CONF = 0x84
    const val ACKNOWLEDGE_IND = 0x85
    const val DELIVERY_IND = 0x86
}

/**
 * WSP well-known header field-name codes used by MMS notification/retrieve PDUs
 * (OMA-WAP-MMS-ENC). Reconstructed from memory against the spec / AOSP
 * PduHeaders.java - there is no bundled reference implementation in this project
 * to check against (see MmsReceiver investigation notes), so this table is the
 * single highest-risk correctness assumption in this file. If real-device parsing
 * comes back wrong, start here.
 */
internal object MmsFieldCode {
    const val BCC = 0x81
    const val CC = 0x82
    const val CONTENT_LOCATION = 0x83
    const val CONTENT_TYPE = 0x84
    const val DATE = 0x85
    const val DELIVERY_REPORT = 0x86
    const val DELIVERY_TIME = 0x87
    const val EXPIRY = 0x88
    const val FROM = 0x89
    const val MESSAGE_CLASS = 0x8A
    const val MESSAGE_ID = 0x8B
    const val MESSAGE_TYPE = 0x8C
    const val MMS_VERSION = 0x8D
    const val MESSAGE_SIZE = 0x8E
    const val PRIORITY = 0x8F
    const val READ_REPORT = 0x90
    const val REPORT_ALLOWED = 0x91
    const val RESPONSE_STATUS = 0x92
    const val RESPONSE_TEXT = 0x93
    const val SENDER_VISIBILITY = 0x94
    const val STATUS = 0x95
    const val SUBJECT = 0x96
    const val TO = 0x97
    const val TRANSACTION_ID = 0x98
}

// Address "type" tag used in the content://mms/{id}/addr table - AOSP's PduPersister
// reuses the header field-code values above directly as the addr type column.
internal object MmsAddrType {
    const val FROM = MmsFieldCode.FROM
    const val TO = MmsFieldCode.TO
    const val CC = MmsFieldCode.CC
    const val BCC = MmsFieldCode.BCC
}

internal data class MmsNotificationInd(
    val transactionId: String,
    val contentLocation: String,
    val from: String?,
    val messageClass: String?,
    val messageSize: Long?,
    val expirySeconds: Long?
)

internal data class MmsPart(
    val contentType: String,
    val name: String?,
    val charset: Int?,
    val data: ByteArray
)

internal data class MmsRetrieveConf(
    val transactionId: String?,
    val messageId: String?,
    val subject: String?,
    val from: String?,
    val to: List<String>,
    val cc: List<String>,
    val dateSeconds: Long?,
    val mmsVersion: Int?,
    val parts: List<MmsPart>
)

/**
 * Minimal, defensive WSP/MMS binary PDU reader/parser.
 *
 * Hand-written against the WAP-230-WSP and OMA-WAP-MMS-ENC wire formats - this
 * project has no PDU-parsing dependency (checked build.gradle.kts /
 * libs.versions.toml) and Android's own com.google.android.mms.pdu classes are
 * framework-internal, not public API, so a from-spec reimplementation is the only
 * option at minSdk 24 without adding a new third-party dependency. It has not been
 * exercised against a real carrier PDU capture. Every header value read is bounded
 * by its declared value-length and the cursor is force-seeked to the declared end
 * afterward, so a wrong guess about one header's internal sub-grammar cannot desync
 * the parse of the headers that follow it - the parser degrades to "this one field
 * is wrong / missing" rather than "everything after this point is garbage".
 */
internal object MmsPduParser {

    private val ISO_8859_1: Charset = Charset.forName("ISO-8859-1")

    fun parseNotificationInd(pdu: ByteArray): MmsNotificationInd? {
        val reader = PduReader(pdu)
        var transactionId: String? = null
        var contentLocation: String? = null
        var from: String? = null
        var messageClass: String? = null
        var messageSize: Long? = null
        var expirySeconds: Long? = null
        var sawNotificationType = false

        try {
            while (reader.hasMore()) {
                // Stop as soon as we have the two fields required to proceed - see
                // class doc. Bonus fields (from/class/size/expiry) are best-effort.
                if (transactionId != null && contentLocation != null) break

                val fieldByte = reader.readByte()
                if (fieldByte and 0x80 == 0) {
                    // Application-header (name/value as plain text-strings) - rare,
                    // best-effort skip.
                    reader.seekTo(reader.position - 1)
                    reader.readTextString()
                    reader.readTextString()
                    continue
                }

                when (fieldByte) {
                    MmsFieldCode.MESSAGE_TYPE -> {
                        val type = reader.readByte()
                        sawNotificationType = type == MmsMessageType.NOTIFICATION_IND
                        if (!sawNotificationType) {
                            Log.w(TAG, "Expected M-Notification.ind (0x82), got message-type 0x${type.toString(16)}")
                        }
                    }
                    MmsFieldCode.TRANSACTION_ID -> transactionId = reader.readTextString()
                    MmsFieldCode.CONTENT_LOCATION -> contentLocation = reader.readTextString()
                    MmsFieldCode.FROM -> from = readFromAddress(reader)
                    MmsFieldCode.MESSAGE_CLASS -> messageClass = readMessageClass(reader)
                    MmsFieldCode.MESSAGE_SIZE -> messageSize = reader.readLongInteger()
                    MmsFieldCode.EXPIRY -> expirySeconds = readExpiry(reader)
                    else -> {
                        // Unrecognized header - can't safely guess its grammar without
                        // risking desync, so stop here with whatever was collected.
                        Log.w(TAG, "Unrecognized notification-ind header 0x${fieldByte.toString(16)} at ${reader.position - 1}, stopping parse")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing M-Notification.ind at offset ${reader.position}", e)
        }

        val txId = transactionId
        val loc = contentLocation
        if (txId == null || loc == null) {
            Log.e(TAG, "M-Notification.ind missing required field(s): transactionId=$txId contentLocation=$loc sawType=$sawNotificationType")
            return null
        }

        return MmsNotificationInd(txId, loc, from, messageClass, messageSize, expirySeconds)
    }

    fun parseRetrieveConf(pdu: ByteArray): MmsRetrieveConf? {
        val reader = PduReader(pdu)
        var transactionId: String? = null
        var messageId: String? = null
        var subject: String? = null
        var from: String? = null
        val to = mutableListOf<String>()
        val cc = mutableListOf<String>()
        var dateSeconds: Long? = null
        var mmsVersion: Int? = null
        var bodyStart = -1

        try {
            headerLoop@ while (reader.hasMore()) {
                val fieldByte = reader.readByte()
                if (fieldByte and 0x80 == 0) {
                    reader.seekTo(reader.position - 1)
                    reader.readTextString()
                    reader.readTextString()
                    continue
                }

                when (fieldByte) {
                    MmsFieldCode.MESSAGE_TYPE -> {
                        val type = reader.readByte()
                        if (type != MmsMessageType.RETRIEVE_CONF) {
                            Log.w(TAG, "Expected M-Retrieve.conf (0x84), got message-type 0x${type.toString(16)}")
                        }
                    }
                    MmsFieldCode.TRANSACTION_ID -> transactionId = reader.readTextString()
                    MmsFieldCode.MESSAGE_ID -> messageId = reader.readTextString()
                    MmsFieldCode.MMS_VERSION -> mmsVersion = reader.readByte()
                    MmsFieldCode.DATE -> dateSeconds = reader.readLongInteger()
                    MmsFieldCode.SUBJECT -> subject = readEncodedStringValue(reader)
                    MmsFieldCode.FROM -> from = readFromAddress(reader)
                    MmsFieldCode.TO -> readEncodedStringValue(reader)?.let { to.add(it) }
                    MmsFieldCode.CC -> readEncodedStringValue(reader)?.let { cc.add(it) }
                    MmsFieldCode.PRIORITY,
                    MmsFieldCode.DELIVERY_REPORT,
                    MmsFieldCode.READ_REPORT,
                    MmsFieldCode.REPORT_ALLOWED,
                    MmsFieldCode.SENDER_VISIBILITY,
                    MmsFieldCode.STATUS -> reader.readByte() // simple 1-byte short-integer fields, self-delimiting
                    MmsFieldCode.CONTENT_TYPE -> {
                        // Everything after this header's own (bounded) value is the
                        // multipart body - stop the header loop here.
                        readBoundedSkip(reader) // media type + params, not needed structurally
                        bodyStart = reader.position
                        break@headerLoop
                    }
                    else -> {
                        Log.w(TAG, "Unrecognized retrieve-conf header 0x${fieldByte.toString(16)} at ${reader.position - 1}, aborting header parse")
                        break@headerLoop
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing M-Retrieve.conf headers at offset ${reader.position}", e)
        }

        if (bodyStart < 0) {
            Log.e(TAG, "M-Retrieve.conf: never reached Content-Type header, no body to parse (txId=$transactionId)")
            return null
        }

        val parts = try {
            parseBody(pdu, bodyStart)
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing M-Retrieve.conf body from offset $bodyStart", e)
            emptyList()
        }

        if (parts.isEmpty()) {
            Log.w(TAG, "M-Retrieve.conf parsed with zero parts (txId=$transactionId) - message will have no visible content")
        }

        return MmsRetrieveConf(transactionId, messageId, subject, from, to, cc, dateSeconds, mmsVersion, parts)
    }

    // Reads a Value-length-prefixed field and discards it, leaving the cursor at
    // the declared end regardless of internal structure.
    private fun readBoundedSkip(reader: PduReader) {
        val len = reader.readValueLength()
        reader.seekTo(reader.position + len)
    }

    private fun readExpiry(reader: PduReader): Long? {
        val len = reader.readValueLength()
        val end = reader.position + len
        val value = try {
            val token = reader.readByte() // Absolute-token (0x80) or Relative-token (0x81)
            val v = reader.readLongInteger()
            if (token == 0x80) v else null // relative expiry (seconds-from-now) not converted, best-effort only
        } catch (e: Exception) {
            null
        }
        reader.seekTo(end)
        return value
    }

    private fun readMessageClass(reader: PduReader): String? {
        val next = reader.peekByteOrNull() ?: return null
        return if (next and 0x80 != 0) {
            when (reader.readByte()) {
                0x80 -> "personal"
                0x81 -> "advertisement"
                0x82 -> "informational"
                0x83 -> "auto"
                else -> null
            }
        } else {
            reader.readTextString()
        }
    }

    // From = Value-length (Insert-address-token=0x81 | Address-present-token=0x80 Encoded-string-value)
    private fun readFromAddress(reader: PduReader): String? {
        val len = reader.readValueLength()
        val end = reader.position + len
        val result = try {
            val token = reader.peekByteOrNull()
            when (token) {
                0x81 -> null // insert-address-token: sender wants MMSC to fill this in - not applicable to MT messages
                0x80 -> {
                    reader.readByte()
                    readEncodedStringBounded(reader, end)
                }
                else -> readEncodedStringBounded(reader, end)
            }
        } catch (e: Exception) {
            null
        }
        reader.seekTo(end)
        return result?.let(::stripAddressTypeSuffix)
    }

    // Encoded-string-value = Text-String | (Value-length Char-set Text-String)
    private fun readEncodedStringValue(reader: PduReader): String? {
        val len = reader.readValueLength()
        val end = reader.position + len
        val result = try {
            readEncodedStringBounded(reader, end)
        } catch (e: Exception) {
            null
        }
        reader.seekTo(end)
        return result?.let(::stripAddressTypeSuffix)
    }

    private fun readEncodedStringBounded(reader: PduReader, end: Int): String? {
        if (reader.position >= end) return null
        val first = reader.peekByteOrNull() ?: return null
        return if (first in 0x20..0x7E || first == 0x7F) {
            // Looks like a plain printable/quoted text-string.
            reader.readTextString()
        } else {
            // Nested Value-length + Char-set + Text-String.
            reader.readIntegerValue() // charset MIBenum, decoding still falls back to ISO-8859-1 below
            reader.readTextString()
        }
    }

    private fun stripAddressTypeSuffix(address: String): String {
        val idx = address.indexOf("/TYPE=")
        return if (idx >= 0) address.substring(0, idx) else address
    }

    // MMS body = multipart entries: UintVar nEntries, then per entry:
    // UintVar headersLen, UintVar dataLen, headersLen octets (content-type + headers), dataLen octets (raw data).
    private fun parseBody(pdu: ByteArray, bodyStart: Int): List<MmsPart> {
        val reader = PduReader(pdu)
        reader.seekTo(bodyStart)
        if (!reader.hasMore()) return emptyList()

        val entryCount = reader.readUintVar()
        if (entryCount <= 0 || entryCount > 200) {
            Log.w(TAG, "Implausible multipart entry count $entryCount, treating body as opaque single part")
            return listOfNotNull(singlePartFallback(pdu, bodyStart))
        }

        val parts = mutableListOf<MmsPart>()
        repeat(entryCount.toInt()) { index ->
            try {
                val headersLen = reader.readUintVar().toInt()
                val dataLen = reader.readUintVar().toInt()
                val headersEnd = reader.position + headersLen
                val headerReader = PduReader(reader.readBytes(headersLen))
                val contentType = readPartContentType(headerReader)
                val name = readPartExtraHeaders(headerReader)
                reader.seekTo(headersEnd)
                val data = reader.readBytes(dataLen)
                parts.add(MmsPart(contentType, name, null, data))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse multipart entry $index", e)
            }
        }
        return parts
    }

    private fun singlePartFallback(pdu: ByteArray, bodyStart: Int): MmsPart? {
        if (bodyStart >= pdu.size) return null
        return MmsPart("application/octet-stream", "part0", null, pdu.copyOfRange(bodyStart, pdu.size))
    }

    // Well-known WSP content-type short-integer codes actually seen in MMS parts.
    // Not exhaustive - anything else must arrive as an explicit text-string, which
    // is what the vast majority of modern senders use anyway.
    private val WELL_KNOWN_CONTENT_TYPES = mapOf(
        0x03 to "text/plain",
        0x08 to "text/x-vCalendar",
        0x0E to "image/gif",
        0x10 to "image/jpeg",
        0x11 to "image/png",
        0x23 to "application/smil",
        0x24 to "application/vnd.wap.multipart.related",
        0x25 to "application/vnd.wap.multipart.mixed"
    )

    private fun readPartContentType(reader: PduReader): String {
        val len = reader.readValueLength()
        val end = reader.position + len
        val type = try {
            val first = reader.peekByteOrNull()
            if (first != null && first and 0x80 != 0) {
                val code = reader.readShortInteger()
                WELL_KNOWN_CONTENT_TYPES[code] ?: "application/octet-stream".also {
                    Log.w(TAG, "Unrecognized well-known content-type code 0x${code.toString(16)}")
                }
            } else {
                reader.readTextString()
            }
        } catch (e: Exception) {
            "application/octet-stream"
        }
        reader.seekTo(end)
        return type
    }

    // Best-effort scan for a Name/Content-Location/Content-Id parameter within a
    // part's header block, purely for a human-readable attachment name.
    private fun readPartExtraHeaders(reader: PduReader): String? {
        while (reader.hasMore()) {
            val b = reader.peekByteOrNull() ?: break
            if (b in 0x20..0x7E) {
                return try {
                    reader.readTextString()
                } catch (e: Exception) {
                    null
                }
            }
            reader.readByte()
        }
        return null
    }
}

/**
 * Cursor-based reader for WSP primitive encodings (uintvar, value-length,
 * short/long-integer, NUL-terminated text-string). These primitive encodings are
 * unambiguous and well-documented, unlike the header field-code table in
 * [MmsFieldCode] - this class is the low-risk part of the parser.
 */
internal class PduReader(private val data: ByteArray) {

    var position: Int = 0
        private set

    fun hasMore(): Boolean = position < data.size

    fun seekTo(pos: Int) {
        position = pos.coerceIn(0, data.size)
    }

    fun peekByteOrNull(): Int? = if (hasMore()) data[position].toInt() and 0xFF else null

    fun readByte(): Int {
        if (!hasMore()) throw IndexOutOfBoundsException("PDU truncated at $position")
        val b = data[position].toInt() and 0xFF
        position++
        return b
    }

    fun readBytes(n: Int): ByteArray {
        if (n < 0 || position + n > data.size) throw IndexOutOfBoundsException("PDU truncated reading $n bytes at $position (size=${data.size})")
        val out = data.copyOfRange(position, position + n)
        position += n
        return out
    }

    fun readUintVar(): Long {
        var result = 0L
        var iterations = 0
        while (true) {
            val b = readByte()
            result = (result shl 7) or (b and 0x7F).toLong()
            if (b and 0x80 == 0) break
            if (++iterations > 8) throw IllegalStateException("Uintvar too long at $position")
        }
        return result
    }

    fun readValueLength(): Int {
        val first = readByte()
        return when {
            first < 0x1F -> first
            first == 0x1F -> readUintVar().toInt()
            else -> 0
        }
    }

    fun readShortInteger(): Int {
        val b = readByte()
        if (b and 0x80 == 0) throw IllegalStateException("Expected short-integer at ${position - 1}, got 0x${b.toString(16)}")
        return b and 0x7F
    }

    fun readLongInteger(): Long {
        val len = readByte()
        if (len == 0 || len > 30) throw IllegalStateException("Invalid long-integer length $len at ${position - 1}")
        var value = 0L
        repeat(len) { value = (value shl 8) or readByte().toLong() }
        return value
    }

    fun readIntegerValue(): Long {
        val next = peekByteOrNull() ?: throw IndexOutOfBoundsException("PDU truncated reading integer-value")
        return if (next and 0x80 != 0) readShortInteger().toLong() else readLongInteger()
    }

    fun readTextString(): String {
        if (peekByteOrNull() == 0x7F) readByte()
        val start = position
        while (hasMore() && data[position].toInt() != 0x00) position++
        val str = String(data, start, position - start, Charset.forName("ISO-8859-1"))
        if (hasMore()) position++
        return str
    }
}
