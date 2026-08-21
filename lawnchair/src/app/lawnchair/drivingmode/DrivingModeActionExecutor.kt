package app.lawnchair.drivingmode

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.DrivingMode

/** Executes a [DrivingModeSpecialAction] tile. */
object DrivingModeActionExecutor {

    fun execute(context: Context, action: DrivingModeSpecialAction, onExit: () -> Unit) {
        when (action) {
            DrivingModeSpecialAction.EXIT -> onExit()
            DrivingModeSpecialAction.SETTINGS ->
                context.startActivity(PreferenceActivity.createIntent(context, DrivingMode))
            // Implicit intents rather than hardcoded packages, so this always follows whatever
            // the user actually has set as their default handler.
            DrivingModeSpecialAction.NAVIGATION ->
                launchSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))
            DrivingModeSpecialAction.MUSIC ->
                launchSafely(context, Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER))
            DrivingModeSpecialAction.PHONE ->
                launchSafely(context, Intent(Intent.ACTION_DIAL))
            DrivingModeSpecialAction.CONTACTS ->
                launchSafely(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
            // Not launchable - the tile just displays the current speed.
            DrivingModeSpecialAction.SPEEDOMETER -> {}
        }
    }

    private fun launchSafely(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }
}
