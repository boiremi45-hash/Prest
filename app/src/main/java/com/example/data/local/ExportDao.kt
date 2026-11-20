package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportDao {
    @Query("SELECT * FROM exports ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<ExportRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExportRecordEntity)

    @Query("DELETE FROM exports WHERE id = :id")
    suspend fun deleteById(id: String)
}
