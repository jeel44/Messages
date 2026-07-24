package com.sms.textmessages.messenger

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.sms.textmessages.messenger.ui.navigation.AppNavigation
import com.sms.textmessages.messenger.ui.onboarding.CallLogDisclosureScreen
import com.sms.textmessages.messenger.utils.OverlayPermission
import com.sms.textmessages.messenger.utils.PreferenceManager

class MainActivity : ComponentActivity() {

    companion object {
        private val SMS_URI_SCHEMES = setOf("sms", "smsto", "mms", "mmsto")
    }

    private val _openChatSender = mutableStateOf<String?>(null)
    private val _openChatAutoFocus = mutableStateOf(false)
    private val _showCallLogDisclosure = mutableStateOf(false)

    // Request multiple SMS permissions
    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.forEach { (permission, granted) ->
                if (granted) {
                    println("$permission GRANTED")
                } else {
                    println("$permission DENIED")
                }
            }
            requestContactsPermission()
        }

    // Request contacts permission
    private val contactPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                println("CONTACT PERMISSION GRANTED")
            } else {
                println("CONTACT PERMISSION DENIED")
            }
            requestNotificationPermission()
        }

    // Request notification permission
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                println("NOTIFICATION PERMISSION GRANTED")
            } else {
                println("NOTIFICATION PERMISSION DENIED")
            }
            requestPhoneStatePermission()
        }

    // Launcher for default SMS role
    private val smsRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            android.util.Log.d("ROLE_DEBUG", "Role picker result received, calling requestRuntimePermissions()")
            requestRuntimePermissions()
        }

    // READ_PHONE_STATE for CallStateListener, the manifest-declared receiver
    // that drives the post-call overlay - requested here rather than bundled
    // into requestSmsPermissions() since it's functionally unrelated
    // (telephony state, not SMS content) even though both fire at startup.
    // No re-registration needed on grant: CallStateListener is a manifest
    // receiver, so it's active as soon as the permission is held.
    private val phoneStatePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                println("READ_PHONE_STATE GRANTED")
            } else {
                println("READ_PHONE_STATE DENIED")
            }
            if (!shouldShowCallLogDisclosure()) {
                requestCallLogPermission()
            } else {
                requestOverlayPermission()
            }
        }

    // READ_CALL_LOG - fallback source CallStateListener uses to resolve the
    // caller's number for an answered incoming call when the PHONE_STATE
    // broadcast's incoming_number extra comes back blank (the normal case on
    // API 29+ without this permission). Same launcher style and no
    // re-registration needed on grant, for the same reason as
    // phoneStatePermissionLauncher above: CallStateListener is a manifest
    // receiver, it just checks this permission fresh on every call.
    private val callLogPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                println("READ_CALL_LOG GRANTED")
            } else {
                println("READ_CALL_LOG DENIED")
            }
            requestOverlayPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        android.util.Log.d(
            "ROLE_DEBUG",
            "onCreate() called, instance hash: ${System.identityHashCode(this)}, savedInstanceState null: ${savedInstanceState == null}"
        )

        _openChatSender.value = extractSenderFromIntent(intent) ?: intent.getStringExtra("open_chat_sender")
        _openChatAutoFocus.value = intent.getBooleanExtra("open_chat_autofocus", false)

        requestDefaultRole()
        if (shouldShowCallLogDisclosure()) {
            _showCallLogDisclosure.value = true
        }

        setContent {

            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val language = prefs.getString("app_lang", null)
            val openChatSender = _openChatSender.value
            val openChatAutoFocus = _openChatAutoFocus.value

            if (_showCallLogDisclosure.value) {
                // Shown once ever, before the first READ_CALL_LOG request -
                // gated ahead of the language/nav split below since it mirrors
                // where requestCallLogPermission() used to fire unconditionally.
                CallLogDisclosureScreen(
                    onAllow = {
                        PreferenceManager.setCallLogDisclosureShown(this)
                        _showCallLogDisclosure.value = false
                        requestCallLogPermission()
                    },
                    onDismiss = {
                        PreferenceManager.setCallLogDisclosureShown(this)
                        _showCallLogDisclosure.value = false
                        requestOverlayPermission()
                    }
                )
            } else if (language == null) {
                // First launch → Language screen
                com.sms.textmessages.messenger.ui.language.LanguageScreen()
            } else {
                // Returning user → Normal app navigation
                AppNavigation(
                    onRequestDefault = {
                        requestDefaultRole()
                    },
                    openChatSender = openChatSender,
                    openChatAutoFocus = openChatAutoFocus,
                    onChatSenderConsumed = { _openChatSender.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _openChatSender.value = extractSenderFromIntent(intent) ?: intent.getStringExtra("open_chat_sender")
        _openChatAutoFocus.value = intent.getBooleanExtra("open_chat_autofocus", false)
    }

    // Pulls the target phone number out of an external sms:/smsto:/mms:/mmsto:
    // intent (e.g. tapping "Message" on a contact in the Contacts app), which
    // arrives via intent.data rather than the "open_chat_sender" custom extra
    // this app uses internally for notification/overlay taps.
    private fun extractSenderFromIntent(intent: Intent): String? {
        val data = intent.data

        if (data != null) {
            android.util.Log.d(
                "MAIN_INTENT_DEBUG",
                "action=${intent.action} data=$data extras=${intent.extras}"
            )
        }

        if (data == null || data.scheme !in SMS_URI_SCHEMES) {
            return null
        }

        val isRecognizedAction = intent.action == Intent.ACTION_SENDTO ||
            intent.action == Intent.ACTION_VIEW ||
            intent.action == android.telephony.TelephonyManager.ACTION_RESPOND_VIA_MESSAGE

        if (!isRecognizedAction) {
            return null
        }

        // schemeSpecificPart is the number for sms:/smsto:/mms:/mmsto: URIs;
        // smsto: URIs can carry a "?body=..." suffix which isn't part of the number.
        return data.schemeSpecificPart?.substringBefore("?")?.takeIf { it.isNotBlank() }
    }

    private fun requestSmsPermissions() {

        val permissions = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS
        )

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            smsPermissionLauncher.launch(notGranted.toTypedArray())
        } else {
            requestContactsPermission()
        }
    }

    private fun requestContactsPermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        } else {
            requestNotificationPermission()
        }
    }

    private fun requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
                return
            }
        }
        requestPhoneStatePermission()
    }

    private fun requestPhoneStatePermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        } else if (!shouldShowCallLogDisclosure()) {
            requestCallLogPermission()
        } else {
            requestOverlayPermission()
        }
    }

    private fun requestCallLogPermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        } else {
            requestOverlayPermission()
        }
    }

    // Gates CallLogDisclosureScreen: only ever shown once (call_log_disclosure_shown
    // flag), and only if the permission isn't already granted or already decided
    // via a previous denial - respects a choice the user already made.
    private fun shouldShowCallLogDisclosure(): Boolean {

        if (PreferenceManager.isCallLogDisclosureShown(this)) {
            return false
        }

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALL_LOG
        ) != PackageManager.PERMISSION_GRANTED
    }

    // Draw-over-other-apps permission for CategoryOverlayService's popup.
    // Contextual, one-shot: only prompts if not already granted, alongside
    // the app's other startup permission requests.
    private fun requestOverlayPermission() {
        android.util.Log.d(
            "ROLE_DEBUG",
            "requestOverlayPermission() called, canDrawOverlays=${OverlayPermission.canDrawOverlays(this)}"
        )
        if (!OverlayPermission.canDrawOverlays(this)) {
            OverlayPermission.requestOverlayPermission(this)
        }
    }

    // Requests the default-SMS-handler role before any runtime permission
    // dialog, since Play requires the role prompt to fully resolve first.
    // Runtime permissions are requested from smsRoleLauncher's callback once
    // the role prompt resolves, or directly below when there's no prompt to
    // precede (role unavailable/already held/pre-Q device).
    private fun requestDefaultRole() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val roleManager = getSystemService(RoleManager::class.java)

            android.util.Log.d(
                "ROLE_DEBUG",
                "isRoleAvailable=${roleManager?.isRoleAvailable(RoleManager.ROLE_SMS)}, isRoleHeld=${roleManager?.isRoleHeld(RoleManager.ROLE_SMS)}"
            )

            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            ) {

                val intent =
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)

                App.disableAppOpenAd = true
                android.util.Log.d("ROLE_DEBUG", "Launching role picker")
                smsRoleLauncher.launch(intent)
                return
            }
        }
        android.util.Log.d(
            "ROLE_DEBUG",
            "Skipping role picker, calling requestRuntimePermissions() directly - already default or role unavailable"
        )
        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {
        requestSmsPermissions()
    }
}

