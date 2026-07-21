package com.sms.textmessages.messenger.ui.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sms.textmessages.messenger.App

class GetStartedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🚫 Disable AppOpen while onboarding runs. App's ActivityLifecycleCallbacks
        // auto-clears this flag after skipping once, so LanguageActivity re-arms
        // it for itself; MainActivity is the first screen that doesn't, which is
        // what lets the app-open ad surface once, at Home.
        App.disableAppOpenAd = true

        setContent {
            GetStartedScreen()
        }
    }
}