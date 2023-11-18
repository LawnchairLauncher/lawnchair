package app.lawnchair.smartspace.model

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle

data class SmartspaceAction(
    val id: String,
    val icon: Icon? = null,
    val title: CharSequence,
    val subtitle: CharSequence? = null,
    val contentDescription: CharSequence? = null,
    val pendingIntent: PendingIntent? = null,
    val intent: Intent? = null,
    val onClick: Runnable? = null,
    val extras: Bundle? = null,
)

val SmartspaceAction?.hasIntent get() = this != null && (intent != null || pendingIntent != null || onClick != null)
