package com.android.launcher3

import android.content.Context
import android.content.SharedPreferences

object LauncherPrefs {

    @JvmStatic
    fun getPrefs(context: Context): SharedPreferences {
        // Use application context for shared preferences, so that we use a single cached instance
        return context.applicationContext.getSharedPreferences(
                LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun getDevicePrefs(context: Context): SharedPreferences {
        // Use application context for shared preferences, so that we use a single cached instance
        return context.applicationContext.getSharedPreferences(
                LauncherFiles.DEVICE_PREFERENCES_KEY, Context.MODE_PRIVATE)
    }}