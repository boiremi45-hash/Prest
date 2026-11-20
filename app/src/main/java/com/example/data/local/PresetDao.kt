package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY updatedAtMillis DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PresetEntity?

    @Query("SELECT COUNT(*) FROM presets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PresetEntity>)

    @Query("UPDATE presets SET isFavorite = :isFavorite, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean, updatedAt: Long)

    @Query("UPDATE presets SET usageCount = usageCount + 1, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun incrementUsage(id: String, updatedAt: Long)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deleteById(id: String)
}
