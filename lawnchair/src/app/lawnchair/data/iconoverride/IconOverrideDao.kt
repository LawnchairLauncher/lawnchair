package app.lawnchair.data.iconoverride

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.flow.Flow

@Dao
interface IconOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: IconOverride)

    @Query("DELETE FROM iconoverride WHERE target = :target")
    suspend fun delete(target: ComponentKey)

    @Query("SELECT * FROM iconoverride")
    fun observeAll(): Flow<List<IconOverride>>

    @Query("SELECT * FROM iconoverride WHERE target = :target")
    fun observeTarget(target: ComponentKey): Flow<IconOverride?>

    @Query("DELETE FROM iconoverride")
    fun deleteAll()
}
