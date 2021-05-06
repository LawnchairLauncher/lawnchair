package app.lawnchair

import android.app.admin.DeviceAdminReceiver
import android.content.Intent

class DeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: android.content.Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: android.content.Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}