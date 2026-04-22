package app.lawnchair.gestures.handlers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import app.lawnchair.LawnchairLauncher

class OpenAssistantHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        try {
            val component = getCurrentlySelectedDefaultAssistant(context)
            Log.d(TAG, "Detected assistant: $component")

            when {
                component == null -> noValidAssistantFound()
                isActivity(component) -> launchIntent(Intent(Intent.ACTION_ASSIST).setComponent(component))
                isService(component) -> launchIntent(Intent(Intent.ACTION_VOICE_COMMAND))
                isInstalled(component.packageName) -> launchIntent(context.packageManager.getLaunchIntentForPackage(component.packageName))
                else -> noValidAssistantFound()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching assistant", e)
            noValidAssistantFound()
        }
    }

    private fun launchIntent(intent: Intent?) {
        intent?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            runCatching { context.startActivity(this) }
                .onFailure { Log.e(TAG, "Failed to launch intent: $intent", it) }
        } ?: Log.e(TAG, "Intent is null, cannot launch.")
    }

    private fun noValidAssistantFound() {
        Log.e(TAG, "No valid assistant found, opening voice input settings.")
        launchIntent(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
    }

    private fun getCurrentlySelectedDefaultAssistant(context: Context): ComponentName? = Settings.Secure.getString(context.contentResolver, "assistant")
        ?.takeIf { it.isNotEmpty() }
        ?.let(ComponentName::unflattenFromString)
        ?: context.packageManager.resolveActivity(Intent(Intent.ACTION_ASSIST), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.let { ComponentName(it.packageName, it.name) }

    private fun isActivity(component: ComponentName) = context.packageManager.queryIntentActivities(Intent().setComponent(component), PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()

    private fun isService(component: ComponentName) = context.packageManager.getInstalledPackages(PackageManager.GET_SERVICES)
        .any { it.packageName == component.packageName && it.services?.any { service -> service.name == component.className } == true }

    private fun isInstalled(packageName: String) = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    companion object {
        private const val TAG = "OpenAssistantHandler"
    }
}
