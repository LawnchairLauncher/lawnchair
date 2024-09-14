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

package com.android.launcher3

import android.view.View
import android.view.ViewGroup
import android.view.ViewParent

object UtilitiesKt {

    /**
     * Modify [ViewGroup]'s attribute with type [T]. The overridden attribute is saved by calling
     * [View.setTag] and can be later restored by [View.getTag].
     *
     * @param <T> type of [ViewGroup] attribute. For example, [T] is [Boolean] if modifying
     *   [ViewGroup.setClipChildren]
     */
    abstract class ViewGroupAttrModifier<T>(
        private val targetAttrValue: T,
        private val tagKey: Int
    ) {
        /**
         * If [targetAttrValue] is different from existing view attribute returned from
         * [getAttribute], this method will save existing attribute by calling [ViewGroup.setTag].
         * Then call [setAttribute] to set attribute with [targetAttrValue].
         */
        fun saveAndChangeAttribute(viewGroup: ViewGroup) {
            val oldAttrValue = getAttribute(viewGroup)
            if (oldAttrValue !== targetAttrValue) {
                viewGroup.setTag(tagKey, oldAttrValue)
                setAttribute(viewGroup, targetAttrValue)
            }
        }

        /** Restore saved attribute in [saveAndChangeAttribute] by calling [ViewGroup.getTag]. */
        @Suppress("UNCHECKED_CAST")
        fun restoreAttribute(viewGroup: ViewGroup) {
            val oldAttrValue: T = viewGroup.getTag(tagKey) as T ?: return
            setAttribute(viewGroup, oldAttrValue)
            viewGroup.setTag(tagKey, null)
        }

        /** Subclass will override this method to decide how to get [ViewGroup] attribute. */
        abstract fun getAttribute(viewGroup: ViewGroup): T

        /** Subclass will override this method to decide how to set [ViewGroup] attribute. */
        abstract fun setAttribute(viewGroup: ViewGroup, attr: T)
    }

    /** [ViewGroupAttrModifier] to call [ViewGroup.setClipChildren] to false. */
    @JvmField
    val CLIP_CHILDREN_FALSE_MODIFIER: ViewGroupAttrModifier<Boolean> =
        object : ViewGroupAttrModifier<Boolean>(false, R.id.saved_clip_children_tag_id) {
            override fun getAttribute(viewGroup: ViewGroup): Boolean {
                return viewGroup.clipChildren
            }

            override fun setAttribute(viewGroup: ViewGroup, clipChildren: Boolean) {
                viewGroup.clipChildren = clipChildren
            }
        }

    /** [ViewGroupAttrModifier] to call [ViewGroup.setClipToPadding] to false. */
    @JvmField
    val CLIP_TO_PADDING_FALSE_MODIFIER: ViewGroupAttrModifier<Boolean> =
        object : ViewGroupAttrModifier<Boolean>(false, R.id.saved_clip_to_padding_tag_id) {
            override fun getAttribute(viewGroup: ViewGroup): Boolean {
                return viewGroup.clipToPadding
            }

            override fun setAttribute(viewGroup: ViewGroup, clipToPadding: Boolean) {
                viewGroup.clipToPadding = clipToPadding
            }
        }

    /**
     * Recursively call [ViewGroupAttrModifier.saveAndChangeAttribute] from [View] to its parent
     * (direct or indirect) inclusive.
     *
     * [ViewGroupAttrModifier.saveAndChangeAttribute] will save the existing attribute value on each
     * view with [View.setTag], which can be restored in [restoreAttributesOnViewTree].
     *
     * Note that if parent is null or not a parent of the view, this method will be applied all the
     * way to root view.
     *
     * @param v child view
     * @param parent direct or indirect parent of child view
     * @param modifiers list of [ViewGroupAttrModifier] to modify view attribute
     */
    @JvmStatic
    fun modifyAttributesOnViewTree(
        v: View?,
        parent: ViewParent?,
        vararg modifiers: ViewGroupAttrModifier<*>
    ) {
        if (v == null) {
            return
        }
        if (v is ViewGroup) {
            for (modifier in modifiers) {
                modifier.saveAndChangeAttribute(v)
            }
        }
        if (v === parent) {
            return
        }
        if (v.parent is View) {
            modifyAttributesOnViewTree(v.parent as View, parent, *modifiers)
        }
    }

    /**
     * Recursively call [ViewGroupAttrModifier.restoreAttribute]} to restore view attributes
     * previously saved in [ViewGroupAttrModifier.saveAndChangeAttribute] on view to its parent
     * (direct or indirect) inclusive.
     *
     * Note that if parent is null or not a parent of the view, this method will be applied all the
     * way to root view.
     *
     * @param v child view
     * @param parent direct or indirect parent of child view
     * @param modifiers list of [ViewGroupAttrModifier] to restore view attributes
     */
    @JvmStatic
    fun restoreAttributesOnViewTree(
        v: View?,
        parent: ViewParent?,
        vararg modifiers: ViewGroupAttrModifier<*>
    ) {
        if (v == null) {
            return
        }
        if (v is ViewGroup) {
            for (modifier in modifiers) {
                modifier.restoreAttribute(v)
            }
        }
        if (v === parent) {
            return
        }
        if (v.parent is View) {
            restoreAttributesOnViewTree(v.parent as View, parent, *modifiers)
        }
    }
}
