/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.icons

import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.util.Log
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.gestures.ui.LawnchairShortcutActivity
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ComponentKey

/**
 * Per-shortcut icon and label overrides for pinned deep shortcuts.
 *
 * These reuse the storage that already backs per-app overrides: [ShortcutKey] is a [ComponentKey]
 * whose class name is the shortcut id, so a shortcut override is just another row keyed by the
 * publisher package and that id.
 */
object ShortcutIconOverrides {

    private const val TAG = "ShortcutIconOverrides"

    fun keyFor(shortcutInfo: ShortcutInfo): ComponentKey = ShortcutKey.fromInfo(shortcutInfo)

    /**
     * Whether [itemInfo] can carry an override at all.
     *
     * Shortcut ids are chosen by the publishing app, so unlike an activity class name one may
     * contain the separators [ComponentKey.toString] builds on. Both the override table and the
     * icon picker route pass keys around in that string form, so a key that does not survive the
     * round trip is not one we can store against.
     */
    fun supportsOverrides(itemInfo: ItemInfo): Boolean {
        val key = try {
            ShortcutKey.fromItemInfo(itemInfo)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to derive a shortcut key for $itemInfo", t)
            return false
        }
        return ComponentKey.fromString(key.toString()) == key
    }

    /** Returns the user-chosen icon for [shortcutInfo], or null when none is set. */
    fun getIcon(context: Context, shortcutInfo: ShortcutInfo, iconDpi: Int): Drawable? {
        val item = IconOverrideRepository.INSTANCE.get(context)
            .overridesMap[keyFor(shortcutInfo)] ?: return null
        return IconPackProvider.INSTANCE.get(context)
            .getDrawable(item.toIconEntry(), iconDpi, shortcutInfo.userHandle)
    }

    fun hasIconOverride(context: Context, shortcutInfo: ShortcutInfo): Boolean {
        return IconOverrideRepository.INSTANCE.get(context)
            .overridesMap
            .containsKey(keyFor(shortcutInfo))
    }

    /**
     * A chosen icon replaces the publisher's icon outright, so the publisher badge that would sit
     * on top of it goes too — otherwise a web app picked out of an icon pack still wears the
     * browser's mark. Lawnchair 2's Customize behaved the same way.
     */
    fun shouldSkipBadge(context: Context, shortcutInfo: ShortcutInfo): Boolean {
        return LawnchairShortcutActivity.shouldSkipShortcutBadge(context, shortcutInfo) ||
            hasIconOverride(context, shortcutInfo)
    }

    /** Returns the user-chosen label for [shortcutInfo], or null when none is set. */
    fun getLabel(context: Context, shortcutInfo: ShortcutInfo): CharSequence? {
        return PreferenceManager.getInstance(context).customAppName[keyFor(shortcutInfo)]
    }
}
