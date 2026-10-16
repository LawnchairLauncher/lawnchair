package app.lawnchair.omni.experience

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import app.lawnchair.LawnchairApp
import app.lawnchair.gestures.handlers.SleepMethodDeviceAdmin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uses provisioned Android enterprise roles. Installing this APK never grants an OEM role. */
object OemIntegration {
    data class Status(val systemApp: Boolean, val deviceOwner: Boolean, val profileOwner: Boolean, val quickstep: Boolean)
    suspend fun status(context: Context): Status = withContext(Dispatchers.IO) {
        val policy = context.getSystemService(DevicePolicyManager::class.java)
        Status(
            context.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            policy?.isDeviceOwnerApp(context.packageName) == true,
            policy?.isProfileOwnerApp(context.packageName) == true,
            LawnchairApp.isRecentsEnabled,
        )
    }
    suspend fun suspendApp(context: Context, packageName: String, suspended: Boolean): Boolean = withContext(Dispatchers.IO) {
        require(packageName != context.packageName && !packageName.startsWith("com.omnidev.workspace"))
        require(OmniAppInventory.get(context).visible().any { it.targetPackage == packageName })
        val state = status(context)
        check(state.deviceOwner || state.profileOwner) { "device_owner_required" }
        val policy = context.getSystemService(DevicePolicyManager::class.java)
        policy.setPackagesSuspended(ComponentName(context, SleepMethodDeviceAdmin.SleepDeviceAdmin::class.java), arrayOf(packageName), suspended).isEmpty()
    }
}
