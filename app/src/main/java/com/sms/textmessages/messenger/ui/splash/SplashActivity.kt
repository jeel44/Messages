package com.sms.textmessages.messenger.ui.splash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.sms.textmessages.messenger.App
import com.sms.textmessages.messenger.MainActivity
import com.sms.textmessages.messenger.ads.RemoteConfigManager
import com.sms.textmessages.messenger.ads.SplashAdManager
import com.sms.textmessages.messenger.ui.onboarding.GetStartedActivity
import com.sms.textmessages.messenger.utils.LocaleManager
import com.sms.textmessages.messenger.utils.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : ComponentActivity() {

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
            SplashScreenUI()
        }

        startSplashLogic()
    }

    private fun startSplashLogic() {

        RemoteConfigManager.init {

            SplashAdManager.loadAds(this)

            lifecycleScope.launch {

                delay(4000)

                SplashAdManager.showAdIfAvailable(
                    activity = this@SplashActivity,
                    onAdDismissed = {
                        goToHome()
                    },
                    onAdFailed = {
                        goToHome()
                    }
                )
            }
        }
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

