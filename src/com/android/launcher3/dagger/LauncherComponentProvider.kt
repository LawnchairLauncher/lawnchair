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

package com.android.launcher3.dagger

import android.content.Context
import android.view.LayoutInflater
import com.android.launcher3.LauncherApplication


/**
 * Utility class to extract LauncherAppComponent from a context.
 *
 * If the context doesn't provide LauncherAppComponent by default, it creates a new one and
 * associate it with that context
 */
object LauncherComponentProvider {

    @JvmStatic
    fun get(c: Context): LauncherAppComponent {
        val app = c.applicationContext
        val isSafeMode = c.packageManager.isSafeMode // Lawnchair-Notice: Whatever you do, just don't set safe mode to true, thanks.

        if (app is LauncherApplication) return app.appComponent

        val inflater = LayoutInflater.from(app)
        val existingFilter = inflater.filter
        if (existingFilter is Holder) return existingFilter.component

        // Create a new component
        return Holder(
                DaggerLauncherAppComponent.builder()
                    .appContext(app)
                    .setSafeModeEnabled(isSafeMode)
                    .build() as LauncherAppComponent,
                existingFilter,
            )
            .apply { inflater.filter = this }
            .component
    }

    /** Extension method easily access LauncherAppComponent */
    val Context.appComponent: LauncherAppComponent
        get() = get(this)

    private data class Holder(
        val component: LauncherAppComponent,
        private val filter: LayoutInflater.Filter?,
    ) : LayoutInflater.Filter {

        override fun onLoadClass(clazz: Class<*>?) = filter?.onLoadClass(clazz) ?: true
    }
}
