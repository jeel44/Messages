package com.sms.textmessages.messenger.ui.splash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import com.sms.textmessages.messenger.App
import com.sms.textmessages.messenger.MainActivity
import com.sms.textmessages.messenger.ads.RemoteConfigManager
import com.sms.textmessages.messenger.ads.SplashAdManager
import com.sms.textmessages.messenger.ui.onboarding.GetStartedActivity
import com.sms.textmessages.messenger.utils.LocaleManager
import com.sms.textmessages.messenger.utils.PreferenceManager

class SplashActivity : ComponentActivity() {

    // Single source of truth for splash timing. animateTo() on this instance
    // must run inside a Composable (it needs Compose's MonotonicFrameClock,
    // which a plain Activity coroutine scope does not have) - see
    // SplashScreenUI's LaunchedEffect. This still drives both the progress
    // bar's visual state and the real dismiss point: the LaunchedEffect
    // awaits animateTo() and then reports back via onProgressComplete, so
    // there remains exactly one Animatable / one timer, not two.
    private val splashProgress = Animatable(0f)

    override fun attachBaseContext(newBase: Context) {

        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "en")!!

        val context = LocaleManager.setLocale(newBase, lang)

        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🚫 Disable AppOpen from Application while splash runs
        App.disableAppOpenAd = true

        // If coming from background, skip splash
        if (!isTaskRoot &&
            intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true &&
            intent?.action == Intent.ACTION_MAIN
        ) {
            finish()
            return
        }

        if (intent.action != Intent.ACTION_MAIN) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            SplashScreenUI(
                progress = splashProgress,
                onProgressComplete = { onSplashProgressComplete() }
            )
        }

        startSplashLogic()
    }

    private fun startSplashLogic() {

        RemoteConfigManager.init {
            SplashAdManager.loadAds(this)
        }
    }

    // Called from SplashScreenUI's LaunchedEffect once splashProgress's
    // animateTo() finishes - the progress bar reaching 100% is what gates
    // showing/skipping the ad, same as before the crash fix, just signaled
    // back from Compose instead of being awaited directly in this class.
    private fun onSplashProgressComplete() {

        SplashAdManager.showAdIfAvailable(
            activity = this,
            onAdDismissed = {
                goToHome()
            },
            onAdFailed = {
                goToHome()
            }
        )
    }

    private fun goToHome() {

        // ✅ Re-enable AppOpen for future background launches
        App.disableAppOpenAd = false

        if (PreferenceManager.isFirstLaunch(this)) {

            startActivity(
                Intent(this, GetStartedActivity::class.java)
            )

        } else {

            startActivity(
                Intent(this, MainActivity::class.java)
            )
        }

        finish()
    }
}

