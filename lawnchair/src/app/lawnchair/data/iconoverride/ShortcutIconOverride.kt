package app.lawnchair.data.iconoverride

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.lawnchair.icons.picker.IconPickerItem
import com.android.launcher3.util.ComponentKey

/**
 * Icon override for one specific pinned shortcut (e.g. a single Gmail
 * label), keyed by [ShortcutKey][com.android.launcher3.shortcuts.ShortcutKey]
 * (itself a [ComponentKey] encoding package+shortcutId+user) — deliberately
 * a separate table from [IconOverride], which is keyed per app component and
 * shared across every instance of that app.
 */
@Entity(tableName = "shortcuticonoverride")
data class ShortcutIconOverride(
    @PrimaryKey val target: ComponentKey,
    @Embedded val iconPickerItem: IconPickerItem,
)
