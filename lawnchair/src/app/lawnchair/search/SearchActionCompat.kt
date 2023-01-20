/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.app.PendingIntent
import android.app.search.SearchAction
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Parcelable
import android.os.UserHandle
import java.util.Objects
import kotlinx.parcelize.Parcelize

/**
 * Represents a searchable action info that can be called from another process
 * or within the client process.
 *
 * @hide
 */
@Parcelize
data class SearchActionCompat(
    /**
     * Returns the unique id of this object.
     */
    val id: String,
    /**
     * Returns a title representing the action.
     */
    val title: CharSequence,
    /**
     * Returns an icon representing the action.
     */
    val icon: Icon?,
    /**
     * Returns a subtitle representing the action.
     */
    val subtitle: CharSequence?,
    /**
     * Returns a content description representing the action.
     */
    val contentDescription: CharSequence?,
    /**
     * Returns the action intent.
     */
    val pendingIntent: PendingIntent?,
    /**
     * Returns the intent.
     */
    val intent: Intent?,
    /**
     * Returns the user handle.
     */
    val userHandle: UserHandle?,
    /**
     * Returns the extra bundle for this object.
     */
    val extras: Bundle?,
) : Parcelable {

    init {
        check(!(this.pendingIntent == null && this.intent == null)) { "At least one type of intent should be available." }
        check(!(this.pendingIntent != null && this.intent != null)) { "Only one type of intent should be available." }
    }

    private constructor(from: SearchAction) : this(
        from.getId(),
        from.getTitle(),
        from.getIcon(),
        from.getSubtitle(),
        from.getContentDescription(),
        from.getPendingIntent(),
        from.getIntent(),
        from.getUserHandle(),
        from.getExtras()
    )

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is SearchActionCompat -> false
        else -> id == other.id && title == other.title
    }

    override fun hashCode(): Int = Objects.hash(id, title)

    /**
     * A builder for search action object.
     *
     * @hide
     */
    class Builder(private val id: String, private val title: String) {
        private var icon: Icon? = null
        private var subtitle: CharSequence? = null
        private var contentDescription: CharSequence? = null
        private var pendingIntent: PendingIntent? = null
        private var intent: Intent? = null
        private var userHandle: UserHandle? = null
        private var extras: Bundle? = null

        /**
         * Sets the subtitle.
         */
        fun setIcon(icon: Icon?) = apply {
            this.icon = icon
        }

        /**
         * Sets the subtitle.
         */
        fun setSubtitle(subtitle: CharSequence?) = apply {
            this.subtitle = subtitle
        }

        /**
         * Sets the content description.
         */
        fun setContentDescription(contentDescription: CharSequence?) = apply {
            this.contentDescription = contentDescription
        }

        /**
         * Sets the pending intent.
         */
        fun setPendingIntent(pendingIntent: PendingIntent?) = apply {
            this.pendingIntent = pendingIntent
        }

        /**
         * Sets the user handle.
         */
        fun setUserHandle(userHandle: UserHandle?) = apply {
            this.userHandle = userHandle
        }

        /**
         * Sets the intent.
         */
        fun setIntent(intent: Intent?) = apply {
            this.intent = intent
        }

        /**
         * Sets the extra.
         */
        fun setExtras(extras: Bundle?) = apply {
            this.extras = extras
        }

        /**
         * Builds a new SearchActionCompat instance.
         */
        fun build(): SearchActionCompat = SearchActionCompat(
            id,
            title,
            icon,
            subtitle,
            contentDescription,
            pendingIntent,
            intent,
            userHandle,
            extras
        )
    }

    companion object {
        fun wrap(action: SearchAction): SearchActionCompat = SearchActionCompat(action)
    }
}
