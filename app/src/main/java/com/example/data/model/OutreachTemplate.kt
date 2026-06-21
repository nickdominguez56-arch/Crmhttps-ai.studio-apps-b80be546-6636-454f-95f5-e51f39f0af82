package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class OutreachTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val persona: String,
    val angle: String,
    val subjectLine: String,
    val bodyContent: String
)
