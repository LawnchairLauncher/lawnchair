package app.lawnchair.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Log
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

object ThemedIconCompat {
    const val TAG = "ThemedIconCompat"

    fun getThemedIcon(
        context: Context,
        componentName: ComponentName,
    ): Drawable? {
        val activityInfo = resolveActivityInfo(context, componentName) ?: return null
        val drawable = getMonochromeIconResource(
            context,
            activityInfo,
        ) ?: return null

        return drawable
    }

    private fun resolveActivityInfo(context: Context, componentName: ComponentName): ActivityInfo? {
        return try {
            context.packageManager.getActivityInfo(
                componentName,
                0,
            )
        } catch (e: PackageManager.NameNotFoundException) {
            // Handle the case where the activity is not found
            null
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getMonochromeIconResource(context: Context, activityInfo: ActivityInfo): Drawable? {
        val iconResource = activityInfo.applicationInfo.icon

        val resources = try {
            context.packageManager.getResourcesForApplication(activityInfo.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, e.toString())
            return null
        }
        var xmlParser: XmlResourceParser? = null
        try {
            xmlParser = resources.getXml(iconResource)
            if (!xmlParser.skipToNextTag()) return null

            if (xmlParser.name != "adaptive-icon") {
                return null
            }

            while (xmlParser.skipToNextTag()) {
                if (xmlParser.name == "monochrome") {
                    val drawable = xmlParser.getAttributeResourceValue(
                        "http://schemas.android.com/apk/res/android",
                        "drawable",
                        0,
                    )
                    if (drawable == 0) return null

                    return resources.getDrawable(drawable, null)
                }
            }
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, e.toString())
            return null
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
            return null
        } catch (e: XmlPullParserException) {
            Log.e(TAG, e.toString())
            return null
        } finally {
            xmlParser?.close()
        }

        return null
    }
}

fun XmlPullParser.skipToNextTag(): Boolean {
    while (next() != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) return true
    }
    return false
}
