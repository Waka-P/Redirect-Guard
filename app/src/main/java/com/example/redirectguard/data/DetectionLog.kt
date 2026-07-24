package com.example.redirectguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detection_logs")
data class DetectionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val targetPackage: String,
    val elapsedMs: Long,
    val actionTaken: String,
    val falsePositive: Boolean = false
)
