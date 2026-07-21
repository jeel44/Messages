package com.sms.textmessages.messenger.ui.language

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.sms.textmessages.messenger.App

class LanguageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🚫 Still onboarding - keep AppOpen suppressed until Home is reached
        App.disableAppOpenAd = true

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            LanguageScreen()
        }
    }
}