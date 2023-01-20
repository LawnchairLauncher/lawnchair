/*
 * Copyright (C) 2020 The Android Open Source Project
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
package app.lawnchair.search

import android.app.search.SearchTarget
import android.app.slice.SliceManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.os.UserHandle
import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import app.lawnchair.search.SearchTargetCompat.SearchResultType
import com.android.app.search.LayoutType.SearchLayoutType
import kotlinx.parcelize.Parcelize

/**
 * A representation of a search result. Search result can be expressed in one of the following:
 * app icon, shortcut, slice, widget, or a custom object using [SearchActionCompat]. While
 * app icon ([PackageManager], shortcut [ShortcutManager], slice [SliceManager],
 * or widget (@link AppWidgetManager} are published content backed by the system service,
 * [SearchActionCompat] is a custom object that the service can use to send search result to the
 * client.
 *
 * These various types of Android primitives could be defined as [SearchResultType]. Some
 * times, the result type can define the layout type that that this object can be rendered in.
 * (e.g., app widget). Most times, [.getLayoutType] assigned by the service
 * can recommend which layout this target should be rendered in.
 *
 * The service can also use fields such as [.getScore] to indicate
 * how confidence the search result is and [.isHidden] to indicate
 * whether it is recommended to be shown by default.
 *
 * Finally, [.getId] is the unique identifier of this search target and a single
 * search target is defined by being able to express a single launcheable item. In case the
 * service want to recommend how to combine multiple search target objects to render in a group
 * (e.g., same row), [.getParentId] can be assigned on the sub targets of the group
 * using the primary search target's identifier.
 *
 * @hide
 */
@Parcelize
data class SearchTargetCompat(
    /**
     * Retrieves the result type {@see SearchResultType}.
     */
    @get:SearchResultType
    val resultType: Int,
    /**
     * Retrieves the layout type.
     *
     * Constant to express how the group of [SearchTargetCompat] should be rendered on
     * the client side. (e.g., "icon", "icon_row", "short_icon_row")
     */
    @get:SearchLayoutType
    val layoutType: String,
    /**
     * Retrieves the id of the target.
     */
    val id: String,
    /**
     * Retrieves the parent id of the target.
     */
    val parentId: String?,
    /**
     * Retrieves the score of the target.
     */
    val score: Float,
    /**
     * Indicates whether this object should be hidden and shown only on demand.
     */
    val isHidden: Boolean,
    /**
     * Retrieves the package name of the target.
     */
    val packageName: String,
    /**
     * Retrieves the user handle of the target.
     */
    val userHandle: UserHandle,
    /**
     * Returns a search action.
     */
    val searchAction: SearchActionCompat?,
    /**
     * Retrieves the shortcut info of the target.
     */
    val shortcutInfo: ShortcutInfo?,
    /**
     * Returns a slice uri.
     */
    val sliceUri: Uri?,
    /**
     * Return a widget provider info.
     */
    val appWidgetProviderInfo: AppWidgetProviderInfo?,
    /**
     * Return extra bundle.
     */
    val extras: Bundle,
) : Parcelable {

    init {
        var published = 0
        if (searchAction != null) published++
        if (shortcutInfo != null) published++
        if (appWidgetProviderInfo != null) published++
        if (sliceUri != null) published++
        check(published <= 1) {
            "Only one of SearchAction, ShortcutInfo, AppWidgetProviderInfo, SliceUri can be assigned in a SearchTargetCompat."
        }
    }

    private constructor(from: SearchTarget) : this(
        from.getResultType(),
        from.getLayoutType(),
        from.getId(),
        from.getParentId(),
        from.getScore(),
        from.isHidden(),
        from.getPackageName(),
        from.getUserHandle(),
        SearchActionCompat.wrap(from.getSearchAction()),
        from.getShortcutInfo(),
        from.getSliceUri(),
        from.getAppWidgetProviderInfo(),
        from.getExtras()
    )

    /**
     * A builder for search target object.
     *
     * @hide
     */
    class Builder(
        @SearchResultType private val resultType: Int,
        @SearchLayoutType private val layoutType: String,
        private val id: String
    ) {
        private var parentId: String? = null
        private var score: Float = 1f
        private var hidden: Boolean = false
        private lateinit var packageName: String
        private lateinit var userHandle: UserHandle
        private var searchAction: SearchActionCompat? = null
        private var shortcutInfo: ShortcutInfo? = null
        private var sliceUri: Uri? = null
        private var appWidgetProviderInfo: AppWidgetProviderInfo? = null
        private lateinit var extras: Bundle

        /**
         * Sets the parent id.
         */
        fun setParentId(parentId: String) = apply {
            this.parentId = parentId
        }

        /**
         * Sets the package name.
         */
        fun setPackageName(packageName: String) = apply {
            this.packageName = packageName
        }

        /**
         * Sets the user handle.
         */
        fun setUserHandle(userHandle: UserHandle) = apply {
            this.userHandle = userHandle
        }

        /**
         * Sets the shortcut info.
         */
        fun setShortcutInfo(shortcutInfo: ShortcutInfo) = apply {
            this.shortcutInfo = shortcutInfo
            packageName = shortcutInfo.getPackage()
        }

        /**
         * Sets the app widget provider info.
         */
        fun setAppWidgetProviderInfo(appWidgetProviderInfo: AppWidgetProviderInfo) = apply {
            this.appWidgetProviderInfo = appWidgetProviderInfo
        }

        /**
         * Sets the slice URI.
         */
        fun setSliceUri(sliceUri: Uri) = apply {
            this.sliceUri = sliceUri
        }

        /**
         * Set the [SearchActionCompat] object to this target.
         */
        fun setSearchAction(searchAction: SearchActionCompat?) = apply {
            this.searchAction = searchAction
        }

        /**
         * Set any extra information that needs to be shared between service and the client.
         */
        fun setExtras(extras: Bundle) = apply {
            this.extras = extras
        }

        /**
         * Sets the score of the object.
         */
        fun setScore(@FloatRange(from = 0.0, to = 1.0) score: Float) = apply {
            this.score = score
        }

        /**
         * Sets whether the result should be hidden (e.g. not visible) by default inside client.
         */
        fun setHidden(hidden: Boolean) = apply {
            this.hidden = hidden
        }

        /**
         * Builds a new SearchTargetCompat instance.
         *
         * @throws IllegalStateException if no target is set
         */
        fun build(): SearchTargetCompat = SearchTargetCompat(
            resultType,
            layoutType,
            id,
            parentId,
            score,
            hidden,
            packageName,
            userHandle,
            searchAction,
            shortcutInfo,
            sliceUri,
            appWidgetProviderInfo,
            extras
        )
    }

    /**
     * @hide
     */
    @IntDef(value = [RESULT_TYPE_APPLICATION, RESULT_TYPE_SHORTCUT, RESULT_TYPE_SLICE, RESULT_TYPE_WIDGETS])
    @Retention(AnnotationRetention.SOURCE)
    annotation class SearchResultType

    companion object {
        const val RESULT_TYPE_APPLICATION = 1
        const val RESULT_TYPE_SHORTCUT = 1 shl 1
        const val RESULT_TYPE_SLICE = 1 shl 2
        const val RESULT_TYPE_WIDGETS = 1 shl 3

        fun wrap(target: SearchTarget): SearchTargetCompat = SearchTargetCompat(target)
    }
}
