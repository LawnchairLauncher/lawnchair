package app.lawnchair.data.iconoverride

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortcutBadgeOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ShortcutBadgeOverride)

    @Query("DELETE FROM shortcutbadgeoverride WHERE target = :target")
    suspend fun delete(target: ComponentKey)

    @Query("SELECT * FROM shortcutbadgeoverride")
    fun observeAll(): Flow<List<ShortcutBadgeOverride>>

    @Query("SELECT * FROM shortcutbadgeoverride WHERE target = :target")
    fun observeTarget(target: ComponentKey): Flow<ShortcutBadgeOverride?>

    @Query("DELETE FROM shortcutbadgeoverride")
    suspend fun deleteAll()

    @RawQuery
    suspend fun checkpoint(supportSQLiteQuery: SupportSQLiteQuery): Int
}
