package com.sms.textmessages.messenger.ui.ads

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ads.RemoteConfigManager
import android.net.Uri
import android.provider.ContactsContract
import android.content.Context
import com.sms.textmessages.messenger.ui.chat.sendSms
import android.widget.Button
import android.widget.ImageView



class OverlayAdService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Remove previous popup safely
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e("SMS_OVERLAY", "Remove overlay error")
        }

        val phone = intent?.getStringExtra("sender") ?: "Unknown"
        val sender = getContactName(this, phone)
        val message = intent?.getStringExtra("message") ?: ""

        Log.d("SMS_OVERLAY", "Overlay showing $sender : $message")

        val inflater = LayoutInflater.from(this)

        overlayView = inflater.inflate(
            R.layout.native_overlay_ad,
            null
        )

        // =============================
        // FIND VIEWS
        // =============================

        val avatar = overlayView!!.findViewById<TextView>(R.id.popup_avatar)
        val senderView = overlayView!!.findViewById<TextView>(R.id.popup_sender)
        val messageView = overlayView!!.findViewById<TextView>(R.id.popup_message)
        val closeBtn = overlayView!!.findViewById<ImageView>(R.id.popup_close)

        val yesBtn = overlayView!!.findViewById<Button>(R.id.reply_yes)
        val okBtn = overlayView!!.findViewById<Button>(R.id.reply_ok)
        val howBtn = overlayView!!.findViewById<Button>(R.id.reply_how)

        // =============================
        // SET SMS DATA
        // =============================

        avatar.text = sender.firstOrNull()?.uppercase() ?: "?"
        senderView.text = sender
        messageView.text = message

        // =============================
        // CLOSE BUTTON
        // =============================

        closeBtn.setOnClickListener {
            removePopup()
        }

        // =============================
        // CLICK ANYWHERE TO OPEN CHAT
        // =============================

        overlayView!!.setOnClickListener {
            removePopup()
        }

        // =============================
        // QUICK REPLIES
        // =============================

        yesBtn.setOnClickListener {
            sendSms(this, phone, "Yes")
            removePopup()
        }

        okBtn.setOnClickListener {
            sendSms(this, phone, "Ok")
            removePopup()
        }

        howBtn.setOnClickListener {
            sendSms(this, phone, "How are you?")
            removePopup()
        }

        // =============================
        // LOAD NATIVE AD
        // =============================

        // Native Ad
        val nativeAdView =
            overlayView!!.findViewById<NativeAdView>(R.id.nativeAdView)

        val headline =
            overlayView!!.findViewById<TextView>(R.id.ad_headline)

        val body =
            overlayView!!.findViewById<TextView>(R.id.ad_body)

        val icon =
            overlayView!!.findViewById<ImageView>(R.id.ad_icon)

        val cta =
            overlayView!!.findViewById<Button>(R.id.ad_call_to_action)

        val adUnit = RemoteConfigManager.notificationNativeId()

        val adLoader = AdLoader.Builder(this, adUnit)
            .forNativeAd { nativeAd: NativeAd ->

                headline.text = nativeAd.headline
                nativeAdView.headlineView = headline

                nativeAd.body?.let {
                    body.text = it
                    nativeAdView.bodyView = body
                }

                nativeAd.icon?.let {
                    icon.setImageDrawable(it.drawable)
                    nativeAdView.iconView = icon
                }

                nativeAd.callToAction?.let {
                    cta.text = it
                    nativeAdView.callToActionView = cta
                }

                nativeAdView.setNativeAd(nativeAd)
            }
            .build()

        adLoader.loadAd(AdRequest.Builder().build())

        adLoader.loadAd(AdRequest.Builder().build())

        // =============================
        // WINDOW PARAMS
        // =============================

        val windowType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        // Get screen width
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

// Convert 16dp margin to pixels
        val margin = (16 * displayMetrics.density).toInt()

// Window params
        val params = WindowManager.LayoutParams(
            screenWidth - (margin * 2), // creates left & right space
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 10

        windowManager.addView(overlayView, params)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // =============================
    // REMOVE POPUP
    // =============================

    private fun removePopup() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e("SMS_OVERLAY", "Remove popup error")
        }

        stopSelf()
    }

    // =============================
    // GET CONTACT NAME
    // =============================

    private fun getContactName(context: Context, phone: String): String {

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phone)
        )

        val cursor = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )

        cursor?.use {

            if (it.moveToFirst()) {

                val index =
                    it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)

                if (index != -1) {
                    return it.getString(index)
                }
            }
        }

        return phone
    }
}