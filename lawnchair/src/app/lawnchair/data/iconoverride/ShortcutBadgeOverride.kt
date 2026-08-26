package app.lawnchair.data.iconoverride

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.android.launcher3.util.ComponentKey

/**
 * Marks a pinned shortcut whose work/clone-profile badge the user has chosen to hide — presence
 * of a row means "hidden", no other columns needed. Kept in Room (rather than a DataStore
 * preference) so the in-memory repository map updates synchronously on toggle, the same way
 * [ShortcutIconOverride] does; a DataStore-backed preference raced with the immediate
 * onAppIconChanged refresh that follows a toggle.
 */
@Entity(tableName = "shortcutbadgeoverride")
data class ShortcutBadgeOverride(
    @PrimaryKey val target: ComponentKey,
)
