package com.sms.textmessages.messenger.ads

import android.content.Context
import com.sms.textmessages.messenger.R

/**
 * Ad unit IDs from [R.string] only — Remote Config is not used for unit IDs.
 * Call [init] from Application.onCreate before any ad work.
 */
object AdUnitIds {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun appOpen(): String = str(R.string.ad_unit_app_open)

    fun banner(): String = str(R.string.ad_unit_banner)

    /** Medium Rectangle (300×250) for call-end screen only. */
    fun callEndMrec(): String = str(R.string.ad_unit_call_end_mrec)

    fun interstitial(): String = str(R.string.ad_unit_interstitial)

    fun native(): String = str(R.string.ad_unit_native)

    fun forFormat(format: AdFormat): String = when (format) {
        AdFormat.APP_OPEN -> appOpen()
        AdFormat.INTERSTITIAL -> interstitial()
        AdFormat.NATIVE -> native()
        AdFormat.BANNER -> banner()
    }

    private fun str(resId: Int): String {
        val ctx = appContext
            ?: error("AdUnitIds.init() must run before reading ad unit IDs")
        return ctx.getString(resId)
    }
}
