/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.plugins

import android.os.IBinder
import android.view.View
import com.android.systemui.plugins.annotations.ProvidesInterface

/**
 * Plugin for experimental "Contextual Auth" features.
 *
 * These plugins will get raw access to low-level events about the user's environment, such as
 * moving in/out of trusted locations, connection status of trusted devices, auth attempts, etc.
 * They will also receive callbacks related to system events & transitions to enable prototypes on
 * sensitive surfaces like lock screen and BiometricPrompt.
 *
 * Note to rebuild the plugin jar run: m PluginDummyLib
 */
@ProvidesInterface(action = AuthContextPlugin.ACTION, version = AuthContextPlugin.VERSION)
interface AuthContextPlugin : Plugin {

    /**
     * Called in the background when the plugin is enabled.
     *
     * This is a good time to ask your friendly [saucier] to cook up something special. The
     * [Plugin.onCreate] can also be used for initialization.
     */
    fun activated(saucier: Saucier)

    /**
     * Called when a [SensitiveSurface] is first shown.
     *
     * This may be called repeatedly if the state of the surface changes after it is shown. For
     * example, [SensitiveSurface.BiometricPrompt.isCredential] will change if the user falls back
     * to a credential-based auth method.
     */
    fun onShowingSensitiveSurface(surface: SensitiveSurface)

    /**
     * Called when a [SensitiveSurface] sensitive surface is hidden.
     *
     * This method may still be called without [onShowingSensitiveSurface] in cases of rapid
     * dismissal and plugins implementations should typically be idempotent.
     */
    fun onHidingSensitiveSurface(surface: SensitiveSurface)

    companion object {
        /** Plugin action. */
        const val ACTION = "com.android.systemui.action.PLUGIN_AUTH_CONTEXT"
        /** Plugin version. */
        const val VERSION = 1
    }

    /** Information about a sensitive surface in the framework, which the Plugin may augment. */
    sealed interface SensitiveSurface {

        /** Information about the BiometricPrompt that is being shown to the user. */
        data class BiometricPrompt(val view: View? = null, val isCredential: Boolean = false) :
            SensitiveSurface

        /** Information about bouncer. */
        data class LockscreenBouncer(val view: View? = null) : SensitiveSurface
    }

    /** Ask for the special. */
    interface Saucier {

        /** What [flavor] would you like? */
        fun getSauce(flavor: String): IBinder?
    }
}
