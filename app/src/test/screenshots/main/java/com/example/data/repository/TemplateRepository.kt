package com.example.data.repository

import com.example.data.db.TemplateDao
import com.example.data.model.OutreachTemplate
import kotlinx.coroutines.flow.Flow

class TemplateRepository(private val templateDao: TemplateDao) {
    val allTemplates: Flow<List<OutreachTemplate>> = templateDao.getAllTemplates()

    suspend fun insertTemplate(template: OutreachTemplate): Long {
        return templateDao.insertTemplate(template)
    }

    suspend fun deleteTemplate(template: OutreachTemplate) {
        templateDao.deleteTemplate(template)
    }
}
