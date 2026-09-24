package com.fazzdev.offlineedgeportal.pkg.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent preferences for PS4 PKG Sender settings (Target IP, etc.).
 * Ensures user configuration persists across app restarts.
 */
object PkgPreferences {
    private const val PREFS_NAME = "pkg_sender_prefs"
    private const val KEY_TARGET_IP = "ps4_target_ip"
    private const val DEFAULT_IP = "192.168.43.100"

    fun getTargetIp(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TARGET_IP, DEFAULT_IP) ?: DEFAULT_IP
    }

    fun setTargetIp(context: Context, ip: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TARGET_IP, ip.trim()).apply()
    }
}
