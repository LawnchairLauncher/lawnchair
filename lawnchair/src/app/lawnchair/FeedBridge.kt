/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
import android.content.pm.ApplicationInfo.FLAG_SYSTEM
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.util.Log
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.SingletonHolder
import app.lawnchair.util.ensureOnMainThread
import app.lawnchair.util.getSignatureHash
import app.lawnchair.util.useApplicationContext
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants

class FeedBridge(private val context: Context) {

    private val shouldUseFeed = context.applicationInfo.flags and (FLAG_DEBUGGABLE or FLAG_SYSTEM) == 0
    private val prefs by lazy { PreferenceManager.getInstance(context) }
    private val bridgePackages by lazy {
        listOf(
            PixelBridgeInfo("com.google.android.apps.nexuslauncher", R.integer.bridge_signature_hash),
            BridgeInfo("app.lawnchair.lawnfeed", R.integer.lawnfeed_signature_hash),
        )
    }

    @JvmOverloads
    fun resolveBridge(customPackage: String = prefs.feedProvider.get()): BridgeInfo? {
        val customBridge = customBridgeOrNull(customPackage)
        val feedProvider = customPackage.toBoolean()
        return when {
            customBridge != null -> customBridge
            !shouldUseFeed && !feedProvider -> null
            else -> bridgePackages.firstOrNull { it.isAvailable() }
        }
    }

    private fun customBridgeOrNull(customPackage: String = prefs.feedProvider.get()): CustomBridgeInfo? {
        return if (customPackage.isNotBlank()) {
            val bridge = CustomBridgeInfo(customPackage)
            if (bridge.isAvailable()) bridge else null
        } else {
            null
        }
    }

    private fun customBridgeAvailable() = customBridgeOrNull()?.isAvailable() == true

    fun isInstalled(): Boolean {
        return customBridgeAvailable() || !shouldUseFeed || bridgePackages.any { it.isAvailable() }
    }

    fun resolveSmartspace(): String {
        return bridgePackages.firstOrNull { it.supportsSmartspace }?.packageName
            ?: "com.google.android.googlequicksearchbox"
    }

    open inner class BridgeInfo(val packageName: String, signatureHashRes: Int) {
        protected open val signatureHash =
            if (signatureHashRes > 0) context.resources.getInteger(signatureHashRes) else 0

        open val supportsSmartspace = false

        fun isAvailable(): Boolean {
            val info = context.packageManager.resolveService(
                Intent(OVERLAY_ACTION)
                    .setPackage(packageName)
                    .setData(
                        Uri.parse(
                            StringBuilder(packageName.length + 18)
                                .append("app://")
                                .append(packageName)
                                .append(":")
                                .append(Process.myUid())
                                .toString(),
                        )
                            .buildUpon()
                            .appendQueryParameter("v", 7.toString())
                            .appendQueryParameter("cv", 9.toString())
                            .build(),
                    ),
                0,
            )
            return info != null && isSigned()
        }

        open fun isSigned(): Boolean {
            when {
                BuildConfig.DEBUG -> return true

                Utilities.ATLEAST_P -> {
                    val info =
                        context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    val signingInfo = info.signingInfo
                    if (signingInfo!!.hasMultipleSigners()) return false
                    return signingInfo.signingCertificateHistory.any { it.hashCode() == signatureHash }
                }

                else -> {
                    val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    return if (info.signatures!!.any { it.hashCode() != signatureHash }) false else info.signatures!!.isNotEmpty()
                }
            }
        }
    }

    private inner class CustomBridgeInfo(packageName: String) : BridgeInfo(packageName, 0) {
        override val signatureHash = whitelist[packageName]?.toInt() ?: -1
        val ignoreWhitelist = prefs.ignoreFeedWhitelist.get()
        override fun isSigned(): Boolean {
            if (signatureHash == -1 && Utilities.ATLEAST_P) {
                val info = context.packageManager
                    .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                if (signingInfo!!.hasMultipleSigners()) return false
                signingInfo.signingCertificateHistory.forEach {
                    val hash = Integer.toHexString(it.hashCode())
                    Log.d(TAG, "Feed provider $packageName(0x$hash) isn't whitelisted")
                }
            }
            return ignoreWhitelist || signatureHash != -1 && super.isSigned()
        }
    }

    private inner class PixelBridgeInfo(packageName: String, signatureHashRes: Int) : BridgeInfo(packageName, signatureHashRes) {
        override val supportsSmartspace get() = isAvailable()
    }

    companion object : SingletonHolder<FeedBridge, Context>(
        ensureOnMainThread(
            useApplicationContext(::FeedBridge),
        ),
    ) {
        private const val TAG = "FeedBridge"
        private const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"

        private val whitelist = mutableMapOf<String, Long?>()

        fun initializeWhitelist(context: Context) {
            whitelist["com.saulhdev.neofeed"] = getSignatureHash(context, "com.saulhdev.neofeed")
            whitelist["ua.itaysonlab.homefeeder"] = 0x887456ed
            whitelist["launcher.libre.dev"] = 0x2e9dbab5
            whitelist[SmartspacerConstants.SMARTSPACER_PACKAGE_NAME] = 0x15c6e36f
            whitelist["amirz.aidlbridge"] = 0xb662cc2f
            whitelist["com.google.android.googlequicksearchbox"] = 0xe3ca78d8
            whitelist["com.google.android.apps.nexuslauncher"] = 0xb662cc2f
        }

        fun getAvailableProviders(context: Context) = context.packageManager
            .queryIntentServices(
                Intent(OVERLAY_ACTION).setData(Uri.parse("app://${context.packageName}")),
                PackageManager.GET_META_DATA,
            )
            .asSequence()
            .map { it.serviceInfo.applicationInfo }
            .distinct()
            .filter { getInstance(context).CustomBridgeInfo(it.packageName).isSigned() }

        @JvmStatic
        fun useBridge(context: Context) = getInstance(context).let { it.shouldUseFeed || it.customBridgeAvailable() }
    }

    init {
        initializeWhitelist(context)
    }
}
