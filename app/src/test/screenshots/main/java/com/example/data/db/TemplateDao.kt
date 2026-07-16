package com.example.data.db

import androidx.room.*
import com.example.data.model.OutreachTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<OutreachTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: OutreachTemplate): Long

    @Delete
    suspend fun deleteTemplate(template: OutreachTemplate)
}
