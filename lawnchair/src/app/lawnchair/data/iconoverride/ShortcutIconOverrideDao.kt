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
interface ShortcutIconOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ShortcutIconOverride)

    @Query("DELETE FROM shortcuticonoverride WHERE target = :target")
    suspend fun delete(target: ComponentKey)

    @Query("SELECT * FROM shortcuticonoverride")
    fun observeAll(): Flow<List<ShortcutIconOverride>>

    @Query("SELECT * FROM shortcuticonoverride WHERE target = :target")
    fun observeTarget(target: ComponentKey): Flow<ShortcutIconOverride?>

    @Query("SELECT COUNT(target) FROM shortcuticonoverride")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM shortcuticonoverride")
    suspend fun deleteAll()

    @RawQuery
    suspend fun checkpoint(supportSQLiteQuery: SupportSQLiteQuery): Int
}
