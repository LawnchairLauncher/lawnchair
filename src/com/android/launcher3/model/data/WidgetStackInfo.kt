/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.launcher3.model.data

import android.content.ComponentName
import com.android.launcher3.LauncherSettings
import com.android.launcher3.util.ContentWriter

/**
 * A stack of app widgets occupying a single cell region on the workspace, with only one member
 * visible at a time (cycled via [activeIndex]). Unlike [FolderInfo], stack members are not
 * separate Favorites rows: this row's own [LauncherSettings.Favorites.INTENT] column holds a
 * serialized list of member widget ids/providers, so this class does not need the
 * [CollectionInfo] placeholder-reconstruction machinery the model loader uses for folders.
 */
class WidgetStackInfo : ItemInfo() {

    /** One widget belonging to this stack. Not a separate Favorites row. */
    data class Member(val appWidgetId: Int, val provider: ComponentName)

    val members: MutableList<Member> = mutableListOf()

    /** Index into [members] of the widget currently shown. */
    var activeIndex: Int = 0

    init {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_WIDGET_STACK
    }

    fun addMember(appWidgetId: Int, provider: ComponentName) {
        members.add(Member(appWidgetId, provider))
    }

    fun removeMember(appWidgetId: Int) {
        members.removeAll { it.appWidgetId == appWidgetId }
        if (activeIndex >= members.size) {
            activeIndex = (members.size - 1).coerceAtLeast(0)
        }
    }

    override fun onAddToDatabase(writer: ContentWriter) {
        super.onAddToDatabase(writer)
        writer.put(LauncherSettings.Favorites.INTENT, serialize())
    }

    private fun serialize(): String {
        val membersPart =
            members.joinToString(separator = MEMBER_SEPARATOR) {
                "${it.appWidgetId}$FIELD_SEPARATOR${it.provider.flattenToString()}"
            }
        return "$activeIndex$MEMBER_SEPARATOR$membersPart"
    }

    companion object {
        private const val FIELD_SEPARATOR = ","
        private const val MEMBER_SEPARATOR = ";"

        /** Parses the serialized form written by [serialize], populating [members]/[activeIndex]. */
        @JvmStatic
        fun deserializeInto(stack: WidgetStackInfo, serialized: String?) {
            if (serialized.isNullOrEmpty()) return
            val parts = serialized.split(MEMBER_SEPARATOR)
            stack.activeIndex = parts.getOrNull(0)?.toIntOrNull() ?: 0
            for (i in 1 until parts.size) {
                val fields = parts[i].split(FIELD_SEPARATOR, limit = 2)
                if (fields.size != 2) continue
                val appWidgetId = fields[0].toIntOrNull() ?: continue
                val provider = ComponentName.unflattenFromString(fields[1]) ?: continue
                stack.members.add(Member(appWidgetId, provider))
            }
            if (stack.activeIndex !in stack.members.indices) {
                stack.activeIndex = 0
            }
        }
    }
}
