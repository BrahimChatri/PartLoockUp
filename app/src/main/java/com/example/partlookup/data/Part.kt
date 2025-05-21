package com.example.partlookup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parts")
data class Part(
    @PrimaryKey
    val partNumber: String,
    val description: String,
    val location: String,
    val quantity: Int = 0
) 