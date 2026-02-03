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
import android.util.Slog
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/** Updates data in App-to-Web's education datastore. */
class AppToWebEducationDatastoreRepository
@VisibleForTesting
constructor(private val dataStore: DataStore<WindowingEducationProto>) {
    constructor(
        context: Context
    ) : this(
        DataStoreFactory.create(
            serializer = WindowingEducationProtoSerializer,
            produceFile = { context.dataStoreFile(APP_TO_WEB_EDUCATION_DATASTORE_FILEPATH) },
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
                Slog.e(
                    TAG,
                    "Error in reading App-to-Web education related data from datastore," +
                        "data is stored in a file named" +
                        "$APP_TO_WEB_EDUCATION_DATASTORE_FILEPATH",
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
     * Updates [WindowingEducationProto.featureUsedTimestampMillis_] field in datastore with current
     * timestamp if [isViewed] is true, if not then clears the field.
     */
    suspend fun updateFeatureUsedTimestampMillis(isViewed: Boolean) {
        dataStore.updateData { preferences ->
            if (isViewed) {
                preferences
                    .toBuilder()
                    .setFeatureUsedTimestampMillis(System.currentTimeMillis())
                    .build()
            } else {
                preferences.toBuilder().clearFeatureUsedTimestampMillis().build()
            }
        }
    }

    /** Increases [AppToWebEducation.educationShownCount] field by one. */
    suspend fun updateEducationShownCount() {
        val currentAppHandleProto = windowingEducationProto().appToWebEducation.toBuilder()
        currentAppHandleProto.setEducationShownCount(
            currentAppHandleProto.getEducationShownCount() + 1
        )
        dataStore.updateData { preferences ->
            preferences.toBuilder().setAppToWebEducation(currentAppHandleProto).build()
        }
    }

    companion object {
        private const val TAG = "AppToWebEducationDatastoreRepository"
        private const val APP_TO_WEB_EDUCATION_DATASTORE_FILEPATH = "app_to_web_education.pb"

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
