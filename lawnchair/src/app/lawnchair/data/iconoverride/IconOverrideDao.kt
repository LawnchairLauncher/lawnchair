package app.lawnchair.data.iconoverride

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.flow.Flow


@Dao
interface IconOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: IconOverride)

    @Query("DELETE FROM iconoverride WHERE target = :target")
    suspend fun delete(target: ComponentKey)

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM iconoverride")
    fun observeAll(): Flow<List<IconOverride>>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM iconoverride WHERE target = :target")
    fun observeTarget(target: ComponentKey): Flow<IconOverride?>

    @Query("SELECT COUNT(target) FROM iconoverride")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM iconoverride")
    suspend fun deleteAll()

    @RawQuery
    suspend fun checkpoint(supportSQLiteQuery: SupportSQLiteQuery): Int
}
