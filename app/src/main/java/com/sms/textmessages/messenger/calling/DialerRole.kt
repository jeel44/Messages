package com.sms.textmessages.messenger.calling

import android.app.role.RoleManager
import android.content.Context
import android.os.Build

/** ROLE_DIALER helpers — used only as a POST_CALL dedup guard today. */
object DialerRole {

    fun isRoleApiAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun isHeld(context: Context): Boolean {
        if (!isRoleApiAvailable()) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    }
}
