/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.flags

import android.util.Log
import org.json.JSONException
import org.json.JSONObject

private const val FIELD_VALUE = "value"
private const val FIELD_TYPE = "type"
private const val TYPE_BOOLEAN = "boolean"
private const val TYPE_STRING = "string"
private const val TYPE_INT = "int"

private const val TAG = "FlagSerializer"

abstract class FlagSerializer<T>(
    private val type: String,
    private val setter: (JSONObject, String, T) -> Unit,
    private val getter: (JSONObject, String) -> T
) {
    fun toSettingsData(value: T): String? {
        return try {
            JSONObject()
                .put(FIELD_TYPE, type)
                .also { setter(it, FIELD_VALUE, value) }
                .toString()
        } catch (e: JSONException) {
            Log.w(TAG, "write error", e)
            null
        }
    }

    /**
     * @throws InvalidFlagStorageException
     */
    fun fromSettingsData(data: String?): T? {
        if (data == null || data.isEmpty()) {
            return null
        }
        try {
            val json = JSONObject(data)
            return if (json.getString(FIELD_TYPE) == type) {
                getter(json, FIELD_VALUE)
            } else {
                null
            }
        } catch (e: JSONException) {
            Log.w(TAG, "read error", e)
            throw InvalidFlagStorageException()
        }
    }
}

object BooleanFlagSerializer : FlagSerializer<Boolean>(
    TYPE_BOOLEAN,
    JSONObject::put,
    JSONObject::getBoolean
)

object StringFlagSerializer : FlagSerializer<String>(
    TYPE_STRING,
    JSONObject::put,
    JSONObject::getString
)

object IntFlagSerializer : FlagSerializer<Int>(
    TYPE_INT,
    JSONObject::put,
    JSONObject::getInt
)

class InvalidFlagStorageException : Exception("Data found but is invalid")
