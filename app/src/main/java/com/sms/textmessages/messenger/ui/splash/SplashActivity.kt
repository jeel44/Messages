package com.sms.textmessages.messenger.ui.splash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.sms.textmessages.messenger.App
import com.sms.textmessages.messenger.MainActivity
import com.sms.textmessages.messenger.ui.onboarding.GetStartedActivity
import com.sms.textmessages.messenger.utils.LocaleManager
import com.sms.textmessages.messenger.utils.PreferenceManager

/**
 * Launcher entry that routes immediately to GetStarted or MainActivity.
 * No splash UI, ads, or Remote Config wait — inbox must open without delay.
 */
class SplashActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {

        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "en")!!

        val context = LocaleManager.setLocale(newBase, lang)

        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate: action=${intent?.action} isTaskRoot=$isTaskRoot")

        // If coming from background, skip — existing task already has the real screen
        if (!isTaskRoot &&
            intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true &&
            intent?.action == Intent.ACTION_MAIN
        ) {
            Log.d(TAG, "onCreate: skipping - coming from background")
            finish()
            return
        }

        if (intent.action != Intent.ACTION_MAIN) {
            Log.d(TAG, "onCreate: non-MAIN action - routing to MainActivity")
            // Suppress home app-open on this cold entry so messages stay visible
            App.disableAppOpenAd = true
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Suppress home app-open on first activity after launch; App clears
        // the flag after skipping once so later background returns still work.
        App.disableAppOpenAd = true
        routeNext()
    }

    private fun routeNext() {
        Log.d(TAG, "routeNext: isFirstLaunch=${PreferenceManager.isFirstLaunch(this)}")

        if (PreferenceManager.isFirstLaunch(this)) {
            startActivity(Intent(this, GetStartedActivity::class.java))
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }

        finish()
    }

    companion object {
        private const val TAG = "SPLASH_DEBUG"
    }
}
