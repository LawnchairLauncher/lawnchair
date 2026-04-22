/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.model

import android.app.blob.BlobHandle
import android.app.blob.BlobStoreManager
import android.content.Context
import android.content.res.Resources
import android.os.ParcelFileDescriptor.AutoCloseInputStream
import android.provider.Settings.Secure
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.util.Xml
import com.android.launcher3.AutoInstallsLayout
import com.android.launcher3.AutoInstallsLayout.SourceResources
import com.android.launcher3.DefaultLayoutParser
import com.android.launcher3.DefaultLayoutParser.RES_PARTNER_DEFAULT_LAYOUT
import com.android.launcher3.LauncherSettings.Settings
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.util.IOUtils
import com.android.launcher3.util.Partner
import com.android.launcher3.widget.LauncherWidgetHolder
import java.io.StringReader
import javax.inject.Inject

private const val TAG = "LayoutParserFactory"

/** Utility class for providing default layout parsers */
open class LayoutParserFactory
@Inject
constructor(@ApplicationContext private val context: Context) {

    open fun createExternalLayoutParser(
        widgetHolder: LauncherWidgetHolder,
        openHelper: DatabaseHelper,
    ): AutoInstallsLayout? {

        createWorkspaceLoaderFromAppRestriction(widgetHolder, openHelper)?.let {
            return it
        }
        AutoInstallsLayout.get(context, widgetHolder, openHelper)?.let {
            return it
        }

        val partner = Partner.get(context.packageManager)
        if (partner != null) {
            val workspaceResId = partner.getXmlResId(RES_PARTNER_DEFAULT_LAYOUT)
            if (workspaceResId != 0) {
                return DefaultLayoutParser(
                    context,
                    widgetHolder,
                    openHelper,
                    partner.resources,
                    workspaceResId,
                )
            }
        }
        return null
    }

    /**
     * Creates workspace loader from an XML resource listed in the app restrictions.
     *
     * @return the loader if the restrictions are set and the resource exists; null otherwise.
     */
    private fun createWorkspaceLoaderFromAppRestriction(
        widgetHolder: LauncherWidgetHolder,
        openHelper: DatabaseHelper,
    ): AutoInstallsLayout? {
        val systemLayoutProvider =
            Secure.getString(context.contentResolver, Settings.LAYOUT_PROVIDER_KEY)
        if (TextUtils.isEmpty(systemLayoutProvider)) {
            return null
        }

        // Try the blob store first
        val blobManager = context.getSystemService(BlobStoreManager::class.java)
        if (systemLayoutProvider.startsWith(Settings.BLOB_KEY_PREFIX) && blobManager != null) {
            val blobHandlerDigest = systemLayoutProvider.substring(Settings.BLOB_KEY_PREFIX.length)
            try {
                AutoCloseInputStream(
                        blobManager.openBlob(
                            BlobHandle.createWithSha256(
                                Base64.decode(
                                    blobHandlerDigest,
                                    Base64.NO_WRAP or Base64.NO_PADDING,
                                ),
                                Settings.LAYOUT_DIGEST_LABEL,
                                0,
                                Settings.LAYOUT_DIGEST_TAG,
                            )
                        )
                    )
                    .use {
                        return getAutoInstallsLayoutFromIS(
                            widgetHolder,
                            openHelper,
                            String(IOUtils.toByteArray(it)),
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting layout from blob handle", e)
                return null
            }
        }

        // Try contentProvider based provider
        val pm = context.packageManager
        val pi = pm.resolveContentProvider(systemLayoutProvider, 0)
        if (pi == null) {
            Log.e(TAG, "No provider found for authority $systemLayoutProvider")
            return null
        }
        val uri = ModelDbController.getLayoutUri(systemLayoutProvider, context)
        try {
            context.contentResolver.openInputStream(uri)?.use {
                Log.d(TAG, "Loading layout from $systemLayoutProvider")
                val res = pm.getResourcesForApplication(pi.applicationInfo)
                return getAutoInstallsLayoutFromIS(
                    widgetHolder,
                    openHelper,
                    String(IOUtils.toByteArray(it)),
                    SourceResources.wrap(res),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting layout stream from: $systemLayoutProvider", e)
        }
        return null
    }

    @Throws(Exception::class)
    protected fun getAutoInstallsLayoutFromIS(
        widgetHolder: LauncherWidgetHolder,
        openHelper: DatabaseHelper,
        xml: String,
        res: SourceResources = SourceResources.wrap(Resources.getSystem()),
    ): AutoInstallsLayout {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        return AutoInstallsLayout(
            context,
            widgetHolder,
            openHelper,
            res,
            { parser },
            AutoInstallsLayout.TAG_WORKSPACE,
        )
    }

    /** Layout parser factory with fixed xml */
    class XmlLayoutParserFactory(ctx: Context, private val xml: String) : LayoutParserFactory(ctx) {

        override fun createExternalLayoutParser(
            widgetHolder: LauncherWidgetHolder,
            openHelper: DatabaseHelper,
        ): AutoInstallsLayout? {
            try {
                return getAutoInstallsLayoutFromIS(widgetHolder, openHelper, xml)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting layout from provided xml", e)
                return super.createExternalLayoutParser(widgetHolder, openHelper)
            }
        }
    }
}
