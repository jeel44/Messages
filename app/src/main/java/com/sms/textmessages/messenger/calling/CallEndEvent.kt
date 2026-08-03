package com.sms.textmessages.messenger.calling

import android.os.Parcelable
import com.sms.textmessages.messenger.receiver.CallEndType
import kotlinx.parcelize.Parcelize

@Parcelize
data class CallEndEvent(
    val number: String?,
    val displayName: String,
    val type: CallEndType,
    val durationMs: Long,
    val timestampMs: Long = System.currentTimeMillis(),
    val wasBlocked: Boolean = false,
    val disconnectCause: Int = DISCONNECT_UNKNOWN,
    val source: Source = Source.PHONE_STATE
) : Parcelable {

    enum class Source { PHONE_STATE, POST_CALL, CALL_LOG }

    companion object {
        const val DISCONNECT_UNKNOWN = -1
    }

    fun dedupeKey(): String =
        "${number.orEmpty()}|${type.name}|${timestampMs / 10_000}"
}
