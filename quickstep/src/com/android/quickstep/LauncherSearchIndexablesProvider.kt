/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep

import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.LauncherApps
import android.database.Cursor
import android.database.MatrixCursor
import android.os.Build
import android.provider.SearchIndexablesContract
import android.provider.SearchIndexablesProvider
import android.util.Xml
import androidx.core.content.withStyledAttributes
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.TYPE_TABLET
import com.android.launcher3.R
import com.android.launcher3.model.DeviceGridState
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

@TargetApi(Build.VERSION_CODES.O)
class LauncherSearchIndexablesProvider : SearchIndexablesProvider() {

    override fun onCreate(): Boolean = true

    override fun queryXmlResources(strings: Array<String>): Cursor {
        val cursor = MatrixCursor(SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS)
        val context = context ?: return cursor
        val settingsActivity =
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(context.packageName),
                0,
            )
        cursor.newRow().apply {
            add(
                SearchIndexablesContract.XmlResource.COLUMN_XML_RESID,
                R.xml.indexable_launcher_prefs,
            )
            add(
                SearchIndexablesContract.XmlResource.COLUMN_INTENT_ACTION,
                Intent.ACTION_APPLICATION_PREFERENCES,
            )
            add(
                SearchIndexablesContract.XmlResource.COLUMN_INTENT_TARGET_PACKAGE,
                context.packageName,
            )
            add(
                SearchIndexablesContract.XmlResource.COLUMN_INTENT_TARGET_CLASS,
                settingsActivity!!.activityInfo!!.name,
            )
        }

        return cursor
    }

    fun isDeviceTablet(): Boolean {
        return DeviceGridState(context).deviceType == TYPE_TABLET
    }

    override fun queryRawData(projection: Array<String>) =
        MatrixCursor(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS)

    override fun queryNonIndexableKeys(projection: Array<String>): Cursor {
        val cursor = MatrixCursor(SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS)
        if (Flags.oneGridSpecs() && !isDeviceTablet()) {
            cursor.addRow(arrayOf(ALLOW_ROTATION_KEY))
        } else {
            cursor.addRow(arrayOf(FIXED_LANDSCAPE_KEY))
        }
        if (!context!!.getSystemService(LauncherApps::class.java)?.hasShortcutHostPermission()!!) {
            // We are not the current launcher. Hide all preferences
            try {
                context!!.resources!!.getXml(R.xml.indexable_launcher_prefs).use { parser ->
                    val depth = parser.depth
                    val attrs = intArrayOf(android.R.attr.key)
                    var type: Int = parser.next()
                    while (
                        (type != XmlPullParser.END_TAG || parser.depth > depth) &&
                            type != XmlPullParser.END_DOCUMENT
                    ) {
                        if (type == XmlPullParser.START_TAG) {
                            context?.withStyledAttributes(Xml.asAttributeSet(parser), attrs) {
                                cursor.addRow(arrayOf(getString(0)))
                            }
                        }
                        type = parser.next()
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: XmlPullParserException) {
                throw RuntimeException(e)
            }
        }
        return cursor
    }

    companion object {
        private const val ALLOW_ROTATION_KEY = "pref_allowRotation"
        private const val FIXED_LANDSCAPE_KEY = "pref_fixed_landscape_mode"
    }
}
