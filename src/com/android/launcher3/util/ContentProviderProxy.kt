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

package com.android.launcher3.util

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/** Wrapper around [ContentProvider] which allows delegating all calls to an interface */
abstract class ContentProviderProxy : ContentProvider() {

    override fun onCreate() = true

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        checkGetProxy()?.delete(uri, selection, selectionArgs) ?: 0

    /** Do not route this call through proxy as it doesn't generally require initializing objects */
    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        checkGetProxy()?.insert(uri, values)

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = checkGetProxy()?.query(uri, projection, selection, selectionArgs, sortOrder)

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = checkGetProxy()?.update(uri, values, selection, selectionArgs) ?: 0

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? =
        checkGetProxy()?.call(method, arg, extras)

    private fun checkGetProxy(): ProxyProvider? = context?.let { getProxy(it) }

    abstract fun getProxy(ctx: Context): ProxyProvider?

    /** Interface for handling the actual content provider calls */
    interface ProxyProvider {

        fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        fun insert(uri: Uri, values: ContentValues?): Uri? = null

        fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        fun call(method: String, arg: String?, extras: Bundle?): Bundle? = null
    }
}
