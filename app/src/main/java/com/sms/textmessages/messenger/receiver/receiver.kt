package com.sms.textmessages.messenger.receiver

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "MMS_RECEIVER"
private const val EXTRA_FILE_URI = "mms_download_file_uri"
private const val EXTRA_TRANSACTION_ID = "mms_download_transaction_id"

// The system service that historically owns MMS transport on AOSP-based devices,
// and the target of the grantUriPermission call below. Unverified: on some OEM
// builds this package may differ, and there is no way to confirm it without a
// real device - see the implementation report for details.
private const val MMS_SERVICE_PACKAGE = "com.android.mms.service"

/**
 * Handles incoming MMS WAP push notifications (M-Notification.ind).
 *
 * As the default SMS/MMS app, this app is responsible for downloading and
 * inserting MMS content itself - Android does not do this automatically,
 * mirroring why SmsReceiver explicitly re-inserts SMS into content://sms.
 *
 * Flow: parse the notification PDU for transaction-id + content-location ->
 * ask the platform (SmsManager.downloadMultimediaMessage) to fetch the actual
 * MMS body over the carrier's MMS APN, which is not something a third-party
 * app can safely do by hand (APN/proxy resolution is not public API) -> the
 * platform notifies MmsDownloadCompleteReceiver when the raw M-Retrieve.conf
 * bytes are ready -> that receiver parses and inserts the message.
 *
 * NOTE: this assumes the platform sends the required M-NotifyResp.ind
 * acknowledgement back to the MMSC itself as part of the
 * downloadMultimediaMessage transaction (this is the entire premise of that
 * public API - it exists specifically so apps don't have to hand-roll the
 * WAP/HTTP transport handshake, which would additionally require carrier APN
 * proxy details this app has no public-API way to obtain). This has not been
 * verified against a real MMSC - see the implementation report.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val pduBytes = intent.getByteArrayExtra("data")
        if (pduBytes == null) {
            Log.e(TAG, "WAP_PUSH_DELIVER received with no PDU 'data' extra, action=${intent.action}")
            return
        }

        Log.d(TAG, "WAP push received, ${pduBytes.size} bytes")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleNotification(context, pduBytes)
            } catch (e: Exception) {
                Log.e(TAG, "Unhandled exception processing incoming MMS notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleNotification(context: Context, pduBytes: ByteArray) {
        val notification = MmsPduParser.parseNotificationInd(pduBytes)
        if (notification == null) {
            Log.e(TAG, "Failed to parse M-Notification.ind - dropping incoming MMS (no transaction-id/content-location)")
            return
        }

        Log.d(
            TAG,
            "Parsed M-Notification.ind: transactionId=${notification.transactionId} " +
                "contentLocation=${notification.contentLocation} from=${notification.from} " +
                "size=${notification.messageSize}"
        )

        requestDownload(context, notification)
    }

    private fun requestDownload(context: Context, notification: MmsNotificationInd) {
        val safeName = notification.transactionId.replace(Regex("[^A-Za-z0-9]"), "_")
        val downloadsDir = File(context.cacheDir, "mms_downloads").apply { mkdirs() }
        val file = File(downloadsDir, "mms_${safeName}_${System.currentTimeMillis()}.dat")

        val fileUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.mmsfileprovider", file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create FileProvider uri for MMS download temp file (txId=${notification.transactionId})", e)
            return
        }

        context.grantUriPermission(
            MMS_SERVICE_PACKAGE,
            fileUri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val downloadCompleteIntent = Intent(context, MmsDownloadCompleteReceiver::class.java).apply {
            putExtra(EXTRA_FILE_URI, fileUri.toString())
            putExtra(EXTRA_TRANSACTION_ID, notification.transactionId)
        }

        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notification.transactionId.hashCode(),
            downloadCompleteIntent,
            piFlags
        )

        try {
            SmsManager.getDefault().downloadMultimediaMessage(
                context,
                notification.contentLocation,
                fileUri,
                Bundle(),
                pendingIntent
            )
            Log.d(TAG, "Requested MMS download for txId=${notification.transactionId} from=${notification.contentLocation}")
        } catch (e: Exception) {
            Log.e(TAG, "downloadMultimediaMessage threw for txId=${notification.transactionId}", e)
        }
    }
}

/**
 * Receives the completion callback from SmsManager.downloadMultimediaMessage(),
 * parses the downloaded M-Retrieve.conf PDU, and inserts the message into
 * content://mms so MmsRepository.loadMmsAttachments() can find it.
 *
 * Registered with no intent-filter - it is only ever targeted explicitly via
 * the PendingIntent built in MmsReceiver, so it does not need to be exported
 * or matched by action/data.
 */
class MmsDownloadCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val downloadResultCode = resultCode
        val fileUriString = intent.getStringExtra(EXTRA_FILE_URI)
        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID)

        if (fileUriString == null) {
            Log.e(TAG, "MMS download-complete broadcast missing file uri extra (txId=$transactionId)")
            return
        }

        val fileUri = Uri.parse(fileUriString)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (downloadResultCode != Activity.RESULT_OK) {
                    // resultCode is one of SmsManager.MMS_ERROR_* on failure, per
                    // SmsManager#downloadMultimediaMessage's documented contract.
                    Log.e(TAG, "MMS download failed resultCode=$downloadResultCode txId=$transactionId")
                    return@launch
                }

                val bytes = try {
                    context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read downloaded MMS file txId=$transactionId", e)
                    null
                }

                if (bytes == null || bytes.isEmpty()) {
                    Log.e(TAG, "Downloaded MMS file empty/unreadable txId=$transactionId")
                    return@launch
                }

                Log.d(TAG, "Downloaded MMS body: ${bytes.size} bytes, txId=$transactionId")

                val retrieveConf = MmsPduParser.parseRetrieveConf(bytes)
                if (retrieveConf == null) {
                    Log.e(TAG, "Failed to parse M-Retrieve.conf txId=$transactionId")
                    return@launch
                }

                Log.d(
                    TAG,
                    "Parsed M-Retrieve.conf: from=${retrieveConf.from} to=${retrieveConf.to} " +
                        "parts=${retrieveConf.parts.size} subject=${retrieveConf.subject} txId=$transactionId"
                )

                val msgId = MmsProvider.insertRetrievedMessage(context, retrieveConf)
                if (msgId == null) {
                    Log.e(TAG, "Failed to insert retrieved MMS into content://mms txId=$transactionId")
                } else {
                    Log.d(TAG, "MMS fully processed: msgId=$msgId txId=$transactionId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unhandled exception processing downloaded MMS txId=$transactionId", e)
            } finally {
                try {
                    context.contentResolver.delete(fileUri, null, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clean up MMS download temp file txId=$transactionId", e)
                }
                pendingResult.finish()
            }
        }
    }
}
