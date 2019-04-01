/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.config.FeatureFlags

class FeedBridge(private val context: Context) {

    private val bridgePackages by lazy { listOf(
            PixelBridgeInfo("com.google.android.apps.nexuslauncher", R.integer.bridge_signature_hash),
            BridgeInfo("ch.deletescape.lawnchair.lawnfeed", R.integer.lawnfeed_signature_hash)) }

    fun resolveBridge(): BridgeInfo? {
        if (!useBridge) return null
        return bridgePackages.firstOrNull { it.isAvailable() }
    }

    fun isInstalled(): Boolean {
        return !useBridge || bridgePackages.any { it.isAvailable() }
    }

    fun resolveSmartspace(): String {
        return bridgePackages.firstOrNull { it.supportsSmartspace }?.packageName ?: "com.google.android.googlequicksearchbox"
    }

    open inner class BridgeInfo(val packageName: String, signatureHashRes: Int) {

        private val signatureHash = context.resources.getInteger(signatureHashRes)

        open val supportsSmartspace = false

        fun isAvailable(): Boolean {
            val info = context.packageManager.resolveService(Intent(overlayAction)
                    .setPackage(packageName)
                    .setData(Uri.parse(StringBuilder(packageName.length + 18)
                            .append("app://")
                            .append(packageName)
                            .append(":")
                            .append(Process.myUid())
                            .toString())
                            .buildUpon()
                            .appendQueryParameter("v", Integer.toString(7))
                            .appendQueryParameter("cv", Integer.toString(9))
                            .build()), 0)
            return info != null && isSigned()
        }

        private fun isSigned(): Boolean {
            if (BuildConfig.FLAVOR_build.equals("dev"))
                return true // Skip signature checks for dev builds
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                if (signingInfo.hasMultipleSigners()) return false
                return signingInfo.signingCertificateHistory.any { it.hashCode() == signatureHash }
            } else {
                val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                info.signatures.forEach {
                    if (it.hashCode() != signatureHash) return false
                }
                return info.signatures.isNotEmpty()
            }
        }
    }

    private inner class PixelBridgeInfo(packageName: String, signatureHashRes: Int) : BridgeInfo(packageName, signatureHashRes) {

        override val supportsSmartspace get() = isAvailable()
    }

    companion object : SingletonHolder<FeedBridge, Context>(ensureOnMainThread(
            useApplicationContext(::FeedBridge))) {

        private const val TAG = "FeedBridge"
        private const val overlayAction = "com.android.launcher3.WINDOW_OVERLAY"

        @JvmStatic
        val useBridge = FeatureFlags.FORCE_FEED_BRIDGE || !BuildConfig.DEBUG
    }
}
