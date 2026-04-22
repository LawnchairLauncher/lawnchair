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

package com.android.quickstep.recents.di

import android.util.Log

class RecentsDependenciesScope(
    val scopeId: RecentsScopeId,
    private val dependencies: MutableMap<String, Any> = mutableMapOf(),
    private val scopeIds: MutableList<RecentsScopeId> = mutableListOf()
) {
    val scopeIdsLinked: List<RecentsScopeId>
        get() = scopeIds.toList()

    operator fun get(identifier: String): Any? {
        log("get $identifier")
        return dependencies[identifier]
    }

    operator fun set(key: String, value: Any) {
        synchronized(this) {
            log("set $key")
            dependencies[key] = value
        }
    }

    fun remove(key: String): Any? {
        synchronized(this) {
            log("remove $key")
            return dependencies.remove(key)
        }
    }

    fun linkTo(scope: RecentsDependenciesScope) {
        log("linking to ${scope.scopeId}")
        scopeIds += scope.scopeId
    }

    fun close() {
        log("reset")
        synchronized(this) { dependencies.clear() }
    }

    private fun log(message: String) {
        if (DEBUG) Log.d(TAG, "[scopeId=$scopeId] $message")
    }

    override fun toString(): String =
        "scopeId: $scopeId" +
            "\n dependencies: ${dependencies.map { "${it.key}=${it.value}" }.joinToString(", ")}" +
            "\n linked to: ${scopeIds.joinToString(", ")}"

    private companion object {
        private const val TAG = "RecentsDependenciesScope"
        private const val DEBUG = false
    }
}
