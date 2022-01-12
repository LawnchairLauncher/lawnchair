package app.lawnchair.data.iconoverride

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IconOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: IconOverride)

    @Delete
    suspend fun delete(item: IconOverride)

    @Query("SELECT * from iconoverride")
    fun getAll(): Flow<List<IconOverride>>
}
