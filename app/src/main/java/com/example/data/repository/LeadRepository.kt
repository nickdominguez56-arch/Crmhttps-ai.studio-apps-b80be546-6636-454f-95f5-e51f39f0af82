package com.example.data.repository

import com.example.data.db.LeadDao
import com.example.data.model.Lead
import kotlinx.coroutines.flow.Flow

class LeadRepository(private val leadDao: LeadDao) {
    val allLeads: Flow<List<Lead>> = leadDao.getAllLeads()

    suspend fun getLeadById(id: Int): Lead? {
        return leadDao.getLeadById(id)
    }

    suspend fun insertLead(lead: Lead): Long {
        return leadDao.insertLead(lead)
    }

    suspend fun updateLead(lead: Lead) {
        leadDao.updateLead(lead)
    }

    suspend fun deleteLead(lead: Lead) {
        leadDao.deleteLead(lead)
    }

    suspend fun clearAll() {
        leadDao.clearAll()
    }
}
