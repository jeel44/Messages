package com.sms.textmessages.messenger.utils

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

object CallScreeningRole {

    fun isRoleApiAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // Overlay auto-grant for call screeners starts on Android 11 (R).
    fun isOverlayAutoGrantApi(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun isAvailable(context: Context): Boolean {
        if (!isRoleApiAvailable()) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
    }

    fun isHeld(context: Context): Boolean {
        if (!isRoleApiAvailable()) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun createRequestIntent(context: Context): Intent? {
        if (!isAvailable(context) || isHeld(context)) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }
}
