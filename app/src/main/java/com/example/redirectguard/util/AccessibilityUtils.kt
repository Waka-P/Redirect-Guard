package com.example.redirectguard.util

import android.content.Context
import android.provider.Settings
import com.example.redirectguard.service.RedirectGuardService

object AccessibilityUtils {

    fun isServiceEnabled(context: Context): Boolean {
        val expectedComponentName = "${context.packageName}/${RedirectGuardService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any { it.equals(expectedComponentName, ignoreCase = true) }
    }
}
