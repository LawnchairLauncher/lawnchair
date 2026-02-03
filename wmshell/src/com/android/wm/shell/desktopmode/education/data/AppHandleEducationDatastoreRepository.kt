/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode.education.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.android.framework.protobuf.InvalidProtocolBufferException
import com.android.internal.annotations.VisibleForTesting
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Manages interactions with the App Handle education datastore.
 *
 * This class provides a layer of abstraction between the UI/business logic and the underlying
 * DataStore.
 */
class AppHandleEducationDatastoreRepository
@VisibleForTesting
constructor(private val dataStore: DataStore<WindowingEducationProto>) {
    constructor(
        context: Context
    ) : this(
        DataStoreFactory.create(
            serializer = WindowingEducationProtoSerializer,
            produceFile = { context.dataStoreFile(APP_HANDLE_EDUCATION_DATASTORE_FILEPATH) },
            corruptionHandler =
                ReplaceFileCorruptionHandler(
                    produceNewData = { WindowingEducationProto.getDefaultInstance() }
                ),
        )
    )

    /** Provides dataStore.data flow and handles exceptions thrown during collection */
    val dataStoreFlow: Flow<WindowingEducationProto> =
        dataStore.data.catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                Log.e(
                    TAG,
                    "Error in reading app handle education related data from datastore, data is " +
                        "stored in a file named $APP_HANDLE_EDUCATION_DATASTORE_FILEPATH",
                    exception,
                )
            } else {
                throw exception
            }
        }

    /**
     * Reads and returns the [WindowingEducationProto] Proto object from the DataStore. If the
     * DataStore is empty or there's an error reading, it returns the default value of Proto.
     */
    suspend fun windowingEducationProto(): WindowingEducationProto = dataStoreFlow.first()

    /**
     * Updates [WindowingEducationProto.appHandleHintViewedTimestampMillis_] field in datastore with
     * current timestamp if [isViewed] is true, if not then clears the field.
     */
    suspend fun updateAppHandleHintViewedTimestampMillis(isViewed: Boolean) {
        dataStore.updateData { preferences ->
            if (isViewed) {
                preferences
                    .toBuilder()
                    .setAppHandleHintViewedTimestampMillis(System.currentTimeMillis())
                    .build()
            } else {
                preferences.toBuilder().clearAppHandleHintViewedTimestampMillis().build()
            }
        }
    }

    /**
     * Updates [WindowingEducationProto.enterDesktopModeHintViewedTimestampMillis_] field in
     * datastore with current timestamp if [isViewed] is true, if not then clears the field.
     */
    suspend fun updateEnterDesktopModeHintViewedTimestampMillis(isViewed: Boolean) {
        dataStore.updateData { preferences ->
            if (isViewed) {
                preferences
                    .toBuilder()
                    .setEnterDesktopModeHintViewedTimestampMillis(System.currentTimeMillis())
                    .build()
            } else {
                preferences.toBuilder().clearEnterDesktopModeHintViewedTimestampMillis().build()
            }
        }
    }

    /**
     * Updates [WindowingEducationProto.exitDesktopModeHintViewedTimestampMillis_] field in
     * datastore with current timestamp if [isViewed] is true, if not then clears the field.
     */
    suspend fun updateExitDesktopModeHintViewedTimestampMillis(isViewed: Boolean) {
        dataStore.updateData { preferences ->
            if (isViewed) {
                preferences
                    .toBuilder()
                    .setExitDesktopModeHintViewedTimestampMillis(System.currentTimeMillis())
                    .build()
            } else {
                preferences.toBuilder().clearExitDesktopModeHintViewedTimestampMillis().build()
            }
        }
    }

    /**
     * Updates [WindowingEducationProto.appHandleHintUsedTimestampMillis_] field in datastore with
     * current timestamp if [isViewed] is true, if not then clears the field.
     */
    suspend fun updateAppHandleHintUsedTimestampMillis(isViewed: Boolean) {
        dataStore.updateData { preferences ->
            if (isViewed) {
                preferences
                    .toBuilder()
                    .setAppHandleHintUsedTimestampMillis(System.currentTimeMillis())
                    .build()
            } else {
                preferences.toBuilder().clearAppHandleHintUsedTimestampMillis().build()
            }
        }
    }

    /**
     * Updates [AppHandleEducation.appUsageStats] and
     * [AppHandleEducation.appUsageStatsLastUpdateTimestampMillis] fields in datastore with
     * [appUsageStats] and [appUsageStatsLastUpdateTimestamp].
     */
    suspend fun updateAppUsageStats(
        appUsageStats: Map<String, Int>,
        appUsageStatsLastUpdateTimestamp: Duration,
    ) {
        val currentAppHandleProto = windowingEducationProto().appHandleEducation.toBuilder()
        currentAppHandleProto
            .putAllAppUsageStats(appUsageStats)
            .setAppUsageStatsLastUpdateTimestampMillis(appUsageStatsLastUpdateTimestamp.toMillis())
        dataStore.updateData { preferences: WindowingEducationProto ->
            preferences.toBuilder().setAppHandleEducation(currentAppHandleProto).build()
        }
    }

    companion object {
        private const val TAG = "AppHandleEducationDatastoreRepository"
        private const val APP_HANDLE_EDUCATION_DATASTORE_FILEPATH = "app_handle_education.pb"

        object WindowingEducationProtoSerializer : Serializer<WindowingEducationProto> {

            override val defaultValue: WindowingEducationProto =
                WindowingEducationProto.getDefaultInstance()

            override suspend fun readFrom(input: InputStream): WindowingEducationProto =
                try {
                    WindowingEducationProto.parseFrom(input)
                } catch (exception: InvalidProtocolBufferException) {
                    throw CorruptionException("Cannot read proto.", exception)
                }

            override suspend fun writeTo(
                windowingProto: WindowingEducationProto,
                output: OutputStream,
            ) = windowingProto.writeTo(output)
        }
    }
}
