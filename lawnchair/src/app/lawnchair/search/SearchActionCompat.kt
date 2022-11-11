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
import kotlinx.parcelize.Parcelize
import java.util.Objects

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

    override fun equals(o: Any?): Boolean = when {
        this === o -> true
        o !is SearchActionCompat -> false
        else -> id == o.id && title == o.title
    }

    override fun hashCode(): Int {
        return Objects.hash(id, title)
    }

    /**
     * A builder for search action object.
     *
     * @hide
     */
    class Builder(private val id: String, private val title: String) {
        private var mIcon: Icon? = null
        private var mSubtitle: CharSequence? = null
        private var mContentDescription: CharSequence? = null
        private var mPendingIntent: PendingIntent? = null
        private var mIntent: Intent? = null
        private var mUserHandle: UserHandle? = null
        private var mExtras: Bundle? = null

        /**
         * Sets the subtitle.
         */
        fun setIcon(icon: Icon?) = apply {
            mIcon = icon
        }

        /**
         * Sets the subtitle.
         */
        fun setSubtitle(subtitle: CharSequence?) = apply {
            mSubtitle = subtitle
        }

        /**
         * Sets the content description.
         */
        fun setContentDescription(contentDescription: CharSequence?) = apply {
            mContentDescription = contentDescription
        }

        /**
         * Sets the pending intent.
         */
        fun setPendingIntent(pendingIntent: PendingIntent?) = apply {
            mPendingIntent = pendingIntent
        }

        /**
         * Sets the user handle.
         */
        fun setUserHandle(userHandle: UserHandle?) = apply {
            mUserHandle = userHandle
        }

        /**
         * Sets the intent.
         */
        fun setIntent(intent: Intent?) = apply {
            mIntent = intent
        }

        /**
         * Sets the extra.
         */
        fun setExtras(extras: Bundle?) = apply {
            mExtras = extras
        }

        /**
         * Builds a new SearchActionCompat instance.
         */
        fun build(): SearchActionCompat = SearchActionCompat(
            id,
            title,
            mIcon,
            mSubtitle,
            mContentDescription,
            mPendingIntent,
            mIntent,
            mUserHandle,
            mExtras
        )
    }

    companion object {
        fun wrap(action: SearchAction): SearchActionCompat = SearchActionCompat(action)
    }
}
