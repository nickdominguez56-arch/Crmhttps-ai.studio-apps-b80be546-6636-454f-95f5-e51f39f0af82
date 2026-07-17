package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "leads")
data class Lead(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val phone: String = "",
    val company: String = "",
    val title: String = "",
    val status: String = "NEW", // NEW, CONTACTED, QUALIFIED, PROPOSAL_SENT, WON, LOST
    val notes: String = "",
    val score: Int = 50, // Interest score 0-100
    val website: String = "",
    val linkedin: String = "",
    
    // AI Enriched Fields
    val enrichedIndustry: String = "",
    val enrichedSize: String = "",
    val enrichedPainPoints: String = "",
    val enrichedPitch: String = "",
    val enrichedIcebreaker: String = "",
    
    // Engagement Metrics
    val emailOpens: Int = 0,
    val websiteVisits: Int = 0,
    val customFieldScore: Int = 0, // custom weighting added by users
    
    val lastContacted: Long = System.currentTimeMillis()
) {
    val scoreCategory: String
        get() = when {
            score >= 80 -> "Hot"
            score >= 50 -> "Warm"
            else -> "Cold"
        }
}
