package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.AISearchResult
import com.example.data.api.EnrichedData
import com.example.data.api.GeminiClient
import com.example.data.db.AppDatabase
import com.example.data.model.Lead
import com.example.data.model.OutreachTemplate
import com.example.data.repository.LeadRepository
import com.example.data.repository.TemplateRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.content.Context
import android.net.Uri
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchLead(
    val id: Int,
    val name: String,
    val company: String,
    val title: String,
    val status: String,
    val score: Int,
    val industry: String,
    val notes: String
)

class CRMViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LeadRepository
    private val templateRepository: TemplateRepository
    
    val allLeads: StateFlow<List<Lead>>
    val allTemplates: StateFlow<List<OutreachTemplate>>

    // Selected lead for detail preview or edits
    private val _selectedLead = MutableStateFlow<Lead?>(null)
    val selectedLead: StateFlow<Lead?> = _selectedLead.asStateFlow()

    // AI Action States
    private val _isEnriching = MutableStateFlow(false)
    val isEnriching: StateFlow<Boolean> = _isEnriching.asStateFlow()

    private val _isGeneratingEmail = MutableStateFlow(false)
    val isGeneratingEmail: StateFlow<Boolean> = _isGeneratingEmail.asStateFlow()

    private val _generatedEmail = MutableStateFlow<String?>(null)
    val generatedEmail: StateFlow<String?> = _generatedEmail.asStateFlow()

    fun setGeneratedEmail(email: String?) {
        _generatedEmail.value = email
    }

    // AI & Normal Search States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isAISearching = MutableStateFlow(false)
    val isAISearching: StateFlow<Boolean> = _isAISearching.asStateFlow()

    private val _aiSearchExplanation = MutableStateFlow<String?>(null)
    val aiSearchExplanation: StateFlow<String?> = _aiSearchExplanation.asStateFlow()

    private val _matchedLeadIds = MutableStateFlow<List<Int>?>(null)
    val matchedLeadIds: StateFlow<List<Int>??> = _matchedLeadIds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // General app alerts
    private val _alertMessage = MutableStateFlow<String?>(null)
    val alertMessage: StateFlow<String?> = _alertMessage.asStateFlow()

    // Chat Assistant states
    private val _chatMessages = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val chatMessages: StateFlow<List<Pair<String, String>>> = _chatMessages.asStateFlow()

    private val _isChatAgentTyping = MutableStateFlow(false)
    val isChatAgentTyping: StateFlow<Boolean> = _isChatAgentTyping.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = LeadRepository(database.leadDao())
        templateRepository = TemplateRepository(database.templateDao())
        
        allLeads = repository.allLeads.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        allTemplates = templateRepository.allTemplates.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Prepopulate with rich sample deals on first launch if DB is empty
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = repository.allLeads.first()
            if (currentList.isEmpty()) {
                prepopulateSampleData()
            }
            
            val currentTemplates = templateRepository.allTemplates.first()
            if (currentTemplates.isEmpty()) {
                prepopulateSampleTemplates()
            }
        }
    }

    private suspend fun prepopulateSampleTemplates() {
        val sampleTemplates = listOf(
            OutreachTemplate(
                name = "VIP Backend Invite",
                persona = "Music & Recording Icons",
                angle = "Exclusive backend tour access",
                subjectLine = "Exclusive Invitation: Stage Operations for [Band_Name]",
                bodyContent = "Hi [Name],\n\nWe know you are a huge supporter, and we want to take you behind the scenes. Come see how the magic happens at the next show, plus secure VIP parking and green room access."
            ),
            OutreachTemplate(
                name = "Hollywood Collaboration",
                persona = "Hollywood Actors & Filmmakers",
                angle = "Production Synergies",
                subjectLine = "Collaboration Opportunities with [Brand_Name]",
                bodyContent = "Hello [Name],\n\nLoved your recent work! We believe there is a unique synergy between your upcoming projects and our latest campaign. Let's discuss a secure, private collaboration."
            ),
            OutreachTemplate(
                name = "Tech Leadership Intro",
                persona = "Aerospace, Tech & Venture Capital",
                angle = "Enterprise Strategy",
                subjectLine = "Scaling Operations alongside your Portfolio",
                bodyContent = "Dear [Name],\n\nScaling an enterprise requires precision. We provide robust, cloud-agnostic tools that we believe would be highly beneficial for your current ecosystem of tools."
            ),
            OutreachTemplate(
                name = "Sports Management Strategy",
                persona = "Sports Icons & Athletes",
                angle = "Brand deals & sponsorship",
                subjectLine = "Maximizing Sponsorship ROI Off-Season",
                bodyContent = "Hi [Name],\n\nYour off-season is just as important as your time on the field. We specialize in managing global brand appearances and seamless travel logistics."
            )
        )
        for (template in sampleTemplates) {
            templateRepository.insertTemplate(template)
        }
    }

    private suspend fun prepopulateSampleData() {
        val samples = mutableListOf<Lead>()
        try {
            val inputStream = getApplication<Application>().assets.open("rolling_stones_fans.txt")
            val reader = inputStream.bufferedReader()
            val lines = reader.readLines()
            var currentCategory = "Entertainment / Business / Sports"
            for ((index, name) in lines.withIndex()) {
                val trimmedName = name.trim()
                if (trimmedName.isBlank()) continue
                
                // If it looks like a header, update the category and skip
                if (trimmedName.contains("Icons") || trimmedName.contains("Actors") || 
                    trimmedName.contains("Personalities") || trimmedName.contains("Leaders") || 
                    trimmedName.contains("Culinary") || trimmedName.contains("Fine Art") || 
                    trimmedName.contains("Literature") || trimmedName.contains("Politics") || 
                    trimmedName.contains("Aerospace") || trimmedName.contains("Wall Street") || 
                    trimmedName.contains("Executives") || trimmedName.contains("Stage") || 
                    trimmedName.contains("Fashion") || trimmedName.contains("Real Estate") || 
                    trimmedName.contains("Songwriting") || trimmedName.contains("Beverage") || 
                    trimmedName.contains("Automotive") || trimmedName.contains("E-Commerce") || 
                    trimmedName.contains("Advertising")
                ) {
                    currentCategory = trimmedName
                    continue
                }

                val painPoints = when {
                    currentCategory.contains("Music") || currentCategory.contains("Songwriting") -> "Needs secure backstage access management, tour logistics efficiency."
                    currentCategory.contains("Hollywood") || currentCategory.contains("Television") -> "Requires high-privacy scheduling, secure script distribution."
                    currentCategory.contains("Sports") -> "Seeking brand deals management, off-season travel arrangements."
                    currentCategory.contains("Politics") || currentCategory.contains("Statecraft") -> "Needs secure communication channels, donor tracking."
                    currentCategory.contains("Business") || currentCategory.contains("Tech") || currentCategory.contains("Venture Capital") -> "Looking for scalable enterprise solutions, executive networking."
                    currentCategory.contains("Culinary") -> "Requires scalable supply chain management, premium reservations handling."
                    currentCategory.contains("Automotive") -> "Needs global shipping logistics, high-net-worth client tracking."
                    currentCategory.contains("Real Estate") -> "Needs VIP property showings, secure document workflows."
                    currentCategory.contains("Finance") || currentCategory.contains("Wall Street") -> "Looking for compliant, encrypted communication tools."
                    else -> "Needs reliable event ticketing and VIP concierge services."
                }

                val pitch = when {
                    currentCategory.contains("Music") || currentCategory.contains("Songwriting") -> "We provide end-to-end tour management software used by top-tier acts."
                    currentCategory.contains("Hollywood") || currentCategory.contains("Television") -> "Our encrypted collaboration suite keeps your projects leak-proof."
                    currentCategory.contains("Sports") -> "Our brand management platform maximizes ROI on your sponsorships."
                    currentCategory.contains("Politics") || currentCategory.contains("Statecraft") -> "A secure CRM to manage constituents and donors effortlessly."
                    currentCategory.contains("Business") || currentCategory.contains("Tech") || currentCategory.contains("Venture Capital") -> "Executive relationship management designed for scale."
                    currentCategory.contains("Culinary") -> "Streamline your reservations and supplier relations in one dashboard."
                    currentCategory.contains("Automotive") -> "Manage custom builds and global tracking securely."
                    currentCategory.contains("Real Estate") -> "The ultimate CRM for ultra-luxury property management."
                    currentCategory.contains("Finance") || currentCategory.contains("Wall Street") -> "A FinTech-ready CRM with end-to-end encryption and compliance."
                    else -> "Premium concierge software tailored to your specific needs."
                }

                // Create a generic lead out of the fan
                samples.add(
                    Lead(
                        name = trimmedName,
                        email = "${trimmedName.replace(" ", ".").lowercase().replace(Regex("[^a-z.]"), "")}@rollingstonesfan.com",
                        phone = "+1 (555) ${index.toString().padStart(4, '0')}",
                        company = currentCategory.take(30),
                        title = "Devoted Fan / VIP",
                        status = if (index % 5 == 0) "QUALIFIED" else if (index % 3 == 0) "CONTACTED" else "NEW",
                        score = (50..100).random(),
                        website = "https://${trimmedName.replace(" ", "").lowercase().replace(Regex("[^a-z]"), "")}.com",
                        notes = "Mega-famous Rolling Stones fan identified from the 1000-fan roster.",
                        enrichedIndustry = currentCategory,
                        enrichedSize = "VIP Individual / Enterprise",
                        enrichedPainPoints = painPoints,
                        enrichedPitch = pitch,
                        enrichedIcebreaker = "Hey $trimmedName, saw you at the last Stones tour! Let's connect."
                    )
                )
            }
            reader.close()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Insert all parsed leads
        for (item in samples) {
            repository.insertLead(item)
        }
    }

    fun selectLead(lead: Lead?) {
        _selectedLead.value = lead
        _generatedEmail.value = null // Reset compiled email when switching target
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            clearSearchFilter()
        }
    }

    fun clearSearchFilter() {
        _matchedLeadIds.value = null
        _aiSearchExplanation.value = null
    }

    fun dismissAlert() {
        _alertMessage.value = null
    }

    // --- Action Methods ---

    fun insertLead(
        name: String,
        email: String,
        phone: String,
        company: String,
        title: String,
        status: String,
        score: Int,
        website: String,
        notes: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val newLead = Lead(
                name = name,
                email = email,
                phone = phone,
                company = company,
                title = title,
                status = status,
                score = score,
                website = website,
                notes = notes
            )
            val id = repository.insertLead(newLead)
            
            // Auto enrich if key is valid
            if (GeminiClient.isApiKeyValid()) {
                enrichLeadLocally(newLead.copy(id = id.toInt()))
            }
        }
    }

    fun updateLead(lead: Lead) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLead(lead)
            if (_selectedLead.value?.id == lead.id) {
                _selectedLead.value = lead
            }
        }
    }

    fun deleteLead(lead: Lead) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteLead(lead)
            if (_selectedLead.value?.id == lead.id) {
                _selectedLead.value = null
            }
        }
    }

    /**
     * Data Enrichment with Gemini API
     */
    fun enrichLead(lead: Lead) {
        viewModelScope.launch {
            _isEnriching.value = true
            val success = enrichLeadLocally(lead)
            _isEnriching.value = false
            if (!success) {
                if (!GeminiClient.isApiKeyValid()) {
                    showMockEnrichment(lead)
                } else {
                    _alertMessage.value = "AI enrichment failed. Please check your internet connection."
                }
            }
        }
    }

    private suspend fun enrichLeadLocally(lead: Lead): Boolean {
        val enriched = withContext(Dispatchers.IO) {
            GeminiClient.enrichLeadData(
                company = lead.company,
                title = lead.title,
                name = lead.name,
                notes = lead.notes
            )
        }
        if (enriched != null) {
            val updated = lead.copy(
                enrichedIndustry = enriched.industry,
                enrichedSize = enriched.companySize,
                enrichedPainPoints = enriched.potentialPainPoints,
                enrichedPitch = enriched.tailoredValuePitch,
                enrichedIcebreaker = enriched.recommendedIcebreaker
            )
            withContext(Dispatchers.IO) {
                repository.updateLead(updated)
            }
            if (_selectedLead.value?.id == lead.id) {
                _selectedLead.value = updated
            }
            return true
        }
        return false
    }

    private fun showMockEnrichment(lead: Lead) {
        // Mock fallback to make sure UI is fully functional during review if API key is not yet set
        val industry = if (lead.company.contains("Tech", true) || lead.company.contains("labs", true)) "Software Engineering" else "Professional Services"
        val size = "Mid-Size (100 - 400 employees)"
        val painPoints = "• Customer churn risk\n• Disconnected outbound analytics\n• Overloaded customer onboarding"
        val pitch = "Your company, ${lead.company}, can optimize conversion rates by 22% using automated pipeline routers."
        val icebreaker = "Hi ${lead.name}, watched your team's panel discussion on CRM analytics last month—stellar work!"

        val updated = lead.copy(
            enrichedIndustry = industry,
            enrichedSize = size,
            enrichedPainPoints = painPoints,
            enrichedPitch = pitch,
            enrichedIcebreaker = icebreaker
        )
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLead(updated)
        }
        _selectedLead.value = updated
        _alertMessage.value = "Using high-fidelity mockup enrichment (Configure GEMINI_API_KEY in the Secrets panel to activate live AI)"
    }

    /**
     * AI Contact search
     */
    fun executeAISearch(query: String) {
        if (query.trim().isEmpty()) {
            clearSearchFilter()
            return
        }

        viewModelScope.launch {
            _isAISearching.value = true
            val leads = allLeads.value
            
            try {
                if (!GeminiClient.isApiKeyValid()) {
                    // Mock search query filter to show functional preview
                    simulateMockSearch(query, leads)
                    return@launch
                }

                // Map leads to simple compact schema for Gemini to save tokens
                val compactLeads = leads.map {
                    SearchLead(
                        id = it.id,
                        name = it.name,
                        company = it.company,
                        title = it.title,
                        status = it.status,
                        score = it.score,
                        industry = it.enrichedIndustry,
                        notes = it.notes
                    )
                }

                val moshi = Moshi.Builder()
                    .addLast(KotlinJsonAdapterFactory())
                    .build()
                val listType = Types.newParameterizedType(List::class.java, SearchLead::class.java)
                val jsonAdapter = moshi.adapter<List<SearchLead>>(listType)
                val jsonString = jsonAdapter.toJson(compactLeads)

                val searchResult = withContext(Dispatchers.IO) {
                    GeminiClient.filterLeadsWithAI(query, jsonString)
                }

                if (searchResult != null) {
                    _matchedLeadIds.value = searchResult.matchedLeadIds
                    _aiSearchExplanation.value = searchResult.explanation
                } else {
                    _alertMessage.value = "AI Search was unable to connect. Falling back to local search matching."
                    simulateMockSearch(query, leads)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _alertMessage.value = "An error occurred during AI search: ${e.localizedMessage}. Falling back to local search."
                simulateMockSearch(query, leads)
            } finally {
                _isAISearching.value = false
            }
        }
    }

    private fun simulateMockSearch(query: String, leads: List<Lead>) {
        val lowercaseQuery = query.lowercase()
        val matches = leads.filter {
            it.name.lowercase().contains(lowercaseQuery) ||
            it.company.lowercase().contains(lowercaseQuery) ||
            it.title.lowercase().contains(lowercaseQuery) ||
            it.status.lowercase().contains(lowercaseQuery) ||
            it.notes.lowercase().contains(lowercaseQuery) ||
            it.enrichedIndustry.lowercase().contains(lowercaseQuery) ||
            (lowercaseQuery.contains("high") && it.score >= 80) ||
            (lowercaseQuery.contains("cold") && it.score < 50) ||
            (lowercaseQuery.contains("warm") && it.score in 50..79) ||
            (lowercaseQuery.contains("won") && it.status == "WON") ||
            (lowercaseQuery.contains("new") && it.status == "NEW")
        }

        _matchedLeadIds.value = matches.map { it.id }
        _aiSearchExplanation.value = "AI Search (Simulation Mode): Found ${matches.size} lead(s) matching '$query'. (Enter your real Gemini API key in AI Studio Secrets tab to trigger deep natural language reasoning!)"
    }

    /**
     * Compose sales email with customizable Tone & Purpose using Gemini AI
     */
    fun compileOutreachEmail(lead: Lead, tone: String, purpose: String) {
        viewModelScope.launch {
            _isGeneratingEmail.value = true
            
            if (!GeminiClient.isApiKeyValid()) {
                simulateMockEmail(lead, tone, purpose)
                _isGeneratingEmail.value = false
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                GeminiClient.generateOutreachEmail(
                    leadName = lead.name,
                    companyName = lead.company,
                    title = lead.title,
                    industry = lead.enrichedIndustry.ifEmpty { "Business Scaling" },
                    notes = lead.notes,
                    pitch = lead.enrichedPitch.ifEmpty { "scaling strategic conversions" },
                    tone = tone,
                    purpose = purpose
                )
            }

            if (result != null) {
                _generatedEmail.value = result
            } else {
                _alertMessage.value = "Could not generate email. Please check your internet connection."
                simulateMockEmail(lead, tone, purpose)
            }
            _isGeneratingEmail.value = false
        }
    }

    private fun simulateMockEmail(lead: Lead, tone: String, purpose: String) {
        val subject = when (purpose) {
            "Meeting Request" -> "Quick request from The Sales Team regarding ${lead.company}'s pipeline"
            "Solution Intro" -> "Innovative outreach helper to scale ${lead.company}"
            "Friendly Follow-up" -> "Following up on your interest in generative acceleration"
            else -> "Exclusive intro: Let's optimize ${lead.company} together"
        }

        val greeting = "Hi ${lead.name},"
        val opening = when (tone) {
            "Professional" -> "I am writing to see how your team is managing current optimization bottlenecks. We noticed ${lead.company} is heavily scaling its operations."
            "Warm" -> "Hope your week is off to an incredible start! I saw that you are working as ${lead.title} at ${lead.company} and wanted to reach out direct."
            "Direct" -> "Let's cut right to the chase—we help teams under ${lead.title} structures streamline outbound funnels."
            else -> "Great sync last week! Eager to follow up on your scaling targets."
        }

        val body = if (lead.enrichedPitch.isNotEmpty()) {
            "Here's our focus: ${lead.enrichedPitch}\n\nWe would love to set up a 10-minute demo this coming Tuesday."
        } else {
            "Based on your notes: '${lead.notes}', we would love to collaborate on customized strategies built specifically for ${lead.company}.\n\nWhen is best to talk?"
        }

        val closing = "Warmly,\n\nThe Sales Growth Team"

        _generatedEmail.value = "Subject: $subject\n\n$greeting\n\n$opening\n\n$body\n\n$closing"
        _alertMessage.value = "Using generated high-fidelity email template (Configure your GEMINI_API_KEY for custom deep AI drafting!)"
    }

    /**
     * Send a message to the AI Chat Assistant
     */
    fun sendChatMessage(message: String) {
        val currentContext = _chatMessages.value.toMutableList()
        currentContext.add("user" to message)
        _chatMessages.value = currentContext
        
        viewModelScope.launch {
            _isChatAgentTyping.value = true
            
            if (!GeminiClient.isApiKeyValid()) {
                // Mock reply
                kotlinx.coroutines.delay(1000)
                val mockReply = "I am Stones AI Assistant. Setup your Gemini API Key in the AI Studio Secrets tab to unlock full conversational reasoning. You asked: '$message'"
                val updated = _chatMessages.value.toMutableList()
                updated.add("model" to mockReply)
                _chatMessages.value = updated
                _isChatAgentTyping.value = false
                return@launch
            }

            val result: String? = withContext(Dispatchers.IO) {
                // Combine recent messages for context
                val conversation: List<Pair<String, String>> = currentContext.takeLast(10)
                GeminiClient.askAIAssistant(conversation)
            }

            val updated = _chatMessages.value.toMutableList()
            if (result != null) {
                updated.add(Pair("model", result))
            } else {
                updated.add(Pair("model", "Sorry, I couldn't connect. Please try again."))
            }
            _chatMessages.value = updated
            _isChatAgentTyping.value = false
        }
    }

    fun importCsv(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val reader = inputStream.bufferedReader()
                    val lines = reader.readLines()
                    if (lines.isNotEmpty()) {
                        val regex = Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*\$)")
                        for (i in 1 until lines.size) {
                            val row = lines[i]
                            if (row.isBlank()) continue
                            val columns = row.split(regex).map { it.removeSurrounding("\"").replace("\"\"", "\"") }
                            if (columns.size >= 7) {
                                val lead = Lead(
                                    name = columns.getOrNull(0) ?: "",
                                    email = columns.getOrNull(1) ?: "",
                                    phone = columns.getOrNull(2) ?: "",
                                    company = columns.getOrNull(3) ?: "",
                                    title = columns.getOrNull(4) ?: "",
                                    status = columns.getOrNull(5) ?: "NEW",
                                    score = columns.getOrNull(6)?.toIntOrNull() ?: 50,
                                    website = "",
                                    notes = "Imported from CSV",
                                    enrichedIndustry = columns.getOrNull(7) ?: "",
                                    enrichedSize = columns.getOrNull(8) ?: "",
                                    enrichedPainPoints = columns.getOrNull(9) ?: "",
                                    enrichedPitch = columns.getOrNull(10) ?: "",
                                    enrichedIcebreaker = columns.getOrNull(11) ?: ""
                                )
                                repository.insertLead(lead)
                            }
                        }
                        _alertMessage.value = "CSV imported successfully"
                    }
                    reader.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _alertMessage.value = "Failed to import CSV: ${e.message}"
            }
        }
    }

    fun exportCsv(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val leads = allLeads.value
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    val writer = outputStream.bufferedWriter()
                    writer.write("name,email,phone,company,title,status,score,industry,size,painPoints,pitch,icebreaker\n")
                    for (lead in leads) {
                        val row = listOf(
                            lead.name, lead.email, lead.phone, lead.company, lead.title,
                            lead.status, lead.score.toString(), lead.enrichedIndustry, lead.enrichedSize,
                            lead.enrichedPainPoints, lead.enrichedPitch, lead.enrichedIcebreaker
                        ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
                        writer.write(row + "\n")
                    }
                    writer.flush()
                    writer.close()
                    _alertMessage.value = "CSV exported successfully"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _alertMessage.value = "Failed to export CSV: ${e.message}"
            }
        }
    }
}
