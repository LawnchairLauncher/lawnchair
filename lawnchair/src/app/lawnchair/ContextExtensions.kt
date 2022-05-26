package app.lawnchair

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

fun Context.resolveIntent(intent: Intent): ResolveInfo? = packageManager.resolveActivity(intent, 0)
