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

    /**
     * The storage key for [shortcutInfo]. Only meaningful for a shortcut [supportsOverrides] has
     * accepted, which is the only way an override gets written in the first place.
     */
    private fun keyFor(shortcutInfo: ShortcutInfo): ComponentKey = ShortcutKey.fromInfo(shortcutInfo)

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

    /** Whether an icon has been chosen for [shortcutInfo], regardless of whether it still resolves. */
    private fun hasIconOverride(context: Context, shortcutInfo: ShortcutInfo): Boolean {
        return IconOverrideRepository.INSTANCE.get(context)
            .overridesMap
            .containsKey(keyFor(shortcutInfo))
    }

    /**
     * Whether the publisher badge should be left off: either this is one of Lawnchair's own gesture
     * shortcuts, which has no meaningful publisher, or the user has chosen a replacement icon.
     *
     * A chosen icon replaces the publisher's outright, so the badge that identified the publisher
     * goes with it — otherwise a web app picked out of an icon pack still wears the browser's mark.
     * Lawnchair 2's Customize behaved the same way. This asks only whether an icon was chosen, not
     * whether it still resolves, so a shortcut whose icon pack has since been uninstalled keeps its
     * badge off while falling back to the publisher's icon.
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
