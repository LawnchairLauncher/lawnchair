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

package com.android.launcher3.util

import android.content.Context
import android.content.ContextParams
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.os.UserHandle
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.util.LauncherModelHelper.SandboxModelContext
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Sandbox application where created [Context] instances are still sandboxed within it.
 *
 * Tests can declare this application as a [Rule], so that it is set up and destroyed automatically.
 * Alternatively, they can call [init] and [onDestroy] directly. Either way, these need to be called
 * for it to work and avoid leaks from created singletons.
 *
 * The create [Context] APIs construct a `ContextImpl`, which resets the application to the true
 * application, thus leaving the sandbox. This implementation wraps the created contexts to
 * propagate this application (see [SandboxApplicationWrapper]).
 */
class SandboxApplication private constructor(private val base: SandboxApplicationWrapper) :
    SandboxModelContext(base), TestRule {

    @JvmOverloads
    constructor(
        base: Context = ApplicationProvider.getApplicationContext()
    ) : this(SandboxApplicationWrapper(base))

    /**
     * Initializes the sandbox application propagation logic.
     *
     * This function either needs to be called manually or automatically through using [Rule].
     */
    fun init() {
        base.app = this@SandboxApplication
    }

    /** Returns `this` if [init] was called, otherwise crashes the test. */
    override fun getApplicationContext(): Context = base.applicationContext

    override fun shouldCleanUpOnDestroy(): Boolean {
        // Defer to the true application to decide whether to clean up. For instance, we do not want
        // to cleanup under Robolectric.
        val app = ApplicationProvider.getApplicationContext<Context>()
        return (app as? SandboxContext)?.shouldCleanUpOnDestroy() ?: true
    }

    override fun apply(statement: Statement, description: Description): Statement {
        return object : ExternalResource() {
                override fun before() = init()

                override fun after() = onDestroy()
            }
            .apply(statement, description)
    }
}

private class SandboxApplicationWrapper(base: Context, var app: Context? = null) :
    ContextWrapper(base) {

    override fun getApplicationContext(): Context {
        return checkNotNull(app) { "SandboxApplication accessed before #init() was called." }
    }

    override fun createPackageContext(packageName: String?, flags: Int): Context {
        return SandboxApplicationWrapper(super.createPackageContext(packageName, flags), app)
    }

    override fun createPackageContextAsUser(
        packageName: String,
        flags: Int,
        user: UserHandle,
    ): Context {
        return SandboxApplicationWrapper(
            super.createPackageContextAsUser(packageName, flags, user),
            app,
        )
    }

    override fun createContextAsUser(user: UserHandle, flags: Int): Context {
        return SandboxApplicationWrapper(super.createContextAsUser(user, flags), app)
    }

    override fun createApplicationContext(application: ApplicationInfo?, flags: Int): Context {
        return SandboxApplicationWrapper(super.createApplicationContext(application, flags), app)
    }

    override fun createContextForSdkInSandbox(sdkInfo: ApplicationInfo, flags: Int): Context {
        return SandboxApplicationWrapper(super.createContextForSdkInSandbox(sdkInfo, flags), app)
    }

    override fun createContextForSplit(splitName: String?): Context {
        return SandboxApplicationWrapper(super.createContextForSplit(splitName), app)
    }

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        return SandboxApplicationWrapper(
            super.createConfigurationContext(overrideConfiguration),
            app,
        )
    }

    override fun createDisplayContext(display: Display): Context {
        return SandboxApplicationWrapper(super.createDisplayContext(display), app)
    }

    override fun createDeviceContext(deviceId: Int): Context {
        return SandboxApplicationWrapper(super.createDeviceContext(deviceId), app)
    }

    override fun createWindowContext(type: Int, options: Bundle?): Context {
        return SandboxApplicationWrapper(super.createWindowContext(type, options), app)
    }

    override fun createWindowContext(display: Display, type: Int, options: Bundle?): Context {
        return SandboxApplicationWrapper(super.createWindowContext(display, type, options), app)
    }

    override fun createContext(contextParams: ContextParams): Context {
        return SandboxApplicationWrapper(super.createContext(contextParams), app)
    }

    override fun createAttributionContext(attributionTag: String?): Context {
        return SandboxApplicationWrapper(super.createAttributionContext(attributionTag), app)
    }

    override fun createCredentialProtectedStorageContext(): Context {
        return SandboxApplicationWrapper(super.createCredentialProtectedStorageContext(), app)
    }

    override fun createDeviceProtectedStorageContext(): Context {
        return SandboxApplicationWrapper(super.createDeviceProtectedStorageContext(), app)
    }

    override fun createTokenContext(token: IBinder, display: Display): Context {
        return SandboxApplicationWrapper(super.createTokenContext(token, display), app)
    }
}
