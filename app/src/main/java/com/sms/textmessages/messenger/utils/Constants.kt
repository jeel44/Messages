package com.sms.textmessages.messenger.utils

// Shared by SettingsScreen and GetStartedScreen so there's one place to fix this.
const val PRIVACY_POLICY_URL = "https://sites.google.com/view/messagesappspolicy/home"

// Master switch for the call-end screen and everything it needs (call screener
// role, overlay / phone-state / call-log permissions, call-log disclosure,
// OEM battery prompt, the "Calling" settings). All code is kept; with this
// false nothing is shown or requested. To bring it back: set this to true and
// restore the call-end block in AndroidManifest.xml (see "CALL-END FEATURE
// (DISABLED)" there).
const val CALL_END_FEATURE_ENABLED = false
