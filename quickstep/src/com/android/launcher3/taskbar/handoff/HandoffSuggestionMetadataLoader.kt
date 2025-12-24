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

package com.android.launcher3.taskbar.handoff

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.Log

/**
 * Loads metadata for [HandoffSuggestion] instances.
 *
 * This class is responsible for loading the label and icon for a [HandoffSuggestion]. It uses a
 * [Handler] to schedule the icon loading on the main thread, and it notifies a callback when the
 * metadata is loaded.
 */
class HandoffSuggestionMetadataLoader(private val context: Context, private val handler: Handler) {

    /** Callback used when retrieving app icons from cache. */
    fun interface OnHandoffSuggestionMetadataLoadedCallback {
        /** Called when metadata is loaded for a [HandoffSuggestion]. */
        fun onLoaded(handoffSuggestion: HandoffSuggestion)
    }

    /**
     * Loads metadata for the given suggestions. If a suggestion already has metadata, it will not
     * be loaded again.
     */
    fun loadMetadata(
        suggestions: List<HandoffSuggestion>,
        onLoad: OnHandoffSuggestionMetadataLoadedCallback,
    ) {
        // Cancel any pending metadata load requests.
        cancelPendingLoads()

        // Load metadata for each suggestion that doesn't already have it.
        for (suggestion in suggestions) {
            loadMetadataForSuggestion(suggestion, onLoad)
        }
    }

    /** Cancels any pending metadata load requests. */
    fun cancelPendingLoads() {
        Log.v(TAG, "cancelPendingLoads")
        handler.removeCallbacksAndMessages(null)
    }

    private fun loadMetadataForSuggestion(
        suggestion: HandoffSuggestion,
        onLoad: OnHandoffSuggestionMetadataLoadedCallback,
    ) {
        // If the suggestion already has metadata, don't load it again.
        if (suggestion.metadata != null) {
            Log.v(TAG, "loadMetadataForSuggestion: suggestion already has metadata")
            return
        }

        val icon = suggestion.remoteTask.icon ?: return
        Log.v(TAG, "loadMetadataForSuggestion: requesting drawable")
        icon.loadDrawableAsync(
            context,
            { drawable ->
                if (drawable != null) {
                    Log.v(TAG, "loadMetadataForSuggestion: drawable loaded")
                    onDrawableLoaded(suggestion, drawable, onLoad)
                } else {
                    Log.v(TAG, "loadMetadataForSuggestion: drawable is null")
                }
            },
            handler,
        )
    }

    private fun onDrawableLoaded(
        suggestion: HandoffSuggestion,
        drawable: Drawable?,
        callback: OnHandoffSuggestionMetadataLoadedCallback,
    ) {
        if (drawable != null) {
            Log.v(TAG, "onDrawableLoaded: drawable loaded")
            suggestion.metadata = HandoffSuggestion.Metadata(suggestion.remoteTask.label, drawable)
            callback.onLoaded(suggestion)
        } else {
            Log.v(TAG, "onDrawableLoaded: drawable is null")
        }
    }

    private companion object {
        const val TAG = "HandoffSuggestionMetadataLoader"
    }
}
