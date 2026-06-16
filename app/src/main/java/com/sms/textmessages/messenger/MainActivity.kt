package com.sms.textmessages.messenger

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.sms.textmessages.messenger.ui.navigation.AppNavigation

class MainActivity : ComponentActivity() {

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
        }

    // Request contacts permission
    private val contactPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                println("CONTACT PERMISSION GRANTED")
            } else {
                println("CONTACT PERMISSION DENIED")
            }
        }

    // Request notification permission
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                println("NOTIFICATION PERMISSION GRANTED")
            } else {
                println("NOTIFICATION PERMISSION DENIED")
            }
        }

    // Launcher for default SMS role
    private val smsRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestSmsPermissions()
        requestContactsPermission()
        requestNotificationPermission()
        requestDefaultRole()

        setContent {

            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val language = prefs.getString("app_lang", null)
            val openChatSender = intent.getStringExtra("open_chat_sender")

            if (language == null) {
                // First launch → Language screen
                com.sms.textmessages.messenger.ui.language.LanguageScreen()
            } else {
                // Returning user → Normal app navigation
                AppNavigation(
                    onRequestDefault = {
                        requestDefaultRole()
                    },
                    openChatSender = openChatSender
                )
            }
        }
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
        }
    }

    private fun requestContactsPermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
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
            }
        }
    }

    private fun requestDefaultRole() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val roleManager = getSystemService(RoleManager::class.java)

            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            ) {

                val intent =
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)

                smsRoleLauncher.launch(intent)
            }
        }
    }
}

