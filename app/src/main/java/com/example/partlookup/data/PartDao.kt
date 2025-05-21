package com.example.partlookup.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PartDao {
    @Query("SELECT * FROM parts WHERE partNumber = :partNumber")
    suspend fun getPartByNumber(partNumber: String): Part?

    @Query("SELECT * FROM parts")
    fun getAllParts(): Flow<List<Part>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: Part)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<Part>)

    @Delete
    suspend fun deletePart(part: Part)

    @Query("DELETE FROM parts")
    suspend fun deleteAllParts()
} 