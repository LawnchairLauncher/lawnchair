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

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.Context
import android.content.ContextParams
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.os.UserHandle
import android.provider.Settings.Global
import android.provider.Settings.Secure
import android.provider.Settings.System
import android.test.mock.MockContentResolver
import android.util.ArrayMap
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.dagger.LauncherBaseAppComponent.Builder
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

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
    SandboxContext(base), TestRule {

    private val mockResolver = MockContentResolver()
    private val spiedServices = ArrayMap<String, Any>()
    private val packageManager = spy(baseContext.packageManager)
    private val dbDir = File(cacheDir, UUID.randomUUID().toString())

    private var lockModelThreadOnDestroy = false

    @JvmOverloads
    constructor(
        base: Context = ApplicationProvider.getApplicationContext()
    ) : this(SandboxApplicationWrapper(base))

    init {
        // System settings cache content provider. Ensure that they are statically initialized
        Secure.getString(base.contentResolver, "test")
        System.getString(base.contentResolver, "test")
        Global.getString(base.contentResolver, "test")
    }

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

    override fun getDatabasePath(name: String) = File(dbDir.apply { if (!exists()) mkdirs() }, name)

    override fun getContentResolver(): ContentResolver = mockResolver

    override fun cleanUpObjects() {
        // When destroying the context, make sure that the model thread is blocked, so that no
        // new jobs get posted while we are cleaning up
        val modelLock = CountDownLatch(1)
        val modelRelease = CountDownLatch(1)
        if (lockModelThreadOnDestroy) {
            Executors.MODEL_EXECUTOR.execute {
                modelLock.countDown()
                modelRelease.await()
            }
            modelLock.await()
        }
        if (deleteContents(dbDir)) {
            dbDir.delete()
        }
        super.cleanUpObjects()
        modelRelease.countDown()
    }

    private fun deleteContents(dir: File): Boolean {
        var success = true
        dir.listFiles()?.forEach {
            if (it.isDirectory) success = success and deleteContents(it)
            if (!it.delete()) success = false
        }
        return success
    }

    override fun initDaggerComponent(componentBuilder: Builder) {
        super.initDaggerComponent(componentBuilder.iconsDbName(null))
    }

    override fun getPackageManager(): PackageManager = packageManager

    override fun getSystemService(name: String): Any? =
        spiedServices[name] ?: super.getSystemService(name)

    fun <T> spyService(tClass: Class<T>): T {
        val name = getSystemServiceName(tClass)
        val service = spiedServices[name]
        if (service != null) return service as T

        val result = spy(getSystemService(tClass))
        spiedServices[name] = result
        return result
    }

    fun setupProvider(authority: String, provider: ContentProvider) {
        val providerInfo = ProviderInfo()
        providerInfo.authority = authority
        providerInfo.applicationInfo = applicationInfo
        provider.attachInfo(this, providerInfo)
        mockResolver.addProvider(providerInfo.authority, provider)
        doReturn(providerInfo)
            .whenever(packageManager)
            .resolveContentProvider(eq(authority), any<Int>())
    }

    /**
     * Notifies the rule to lock the model thread during cleanup operation so that no new tasks get
     * posted
     */
    fun withModelDependency() = this.apply { lockModelThreadOnDestroy = true }

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
