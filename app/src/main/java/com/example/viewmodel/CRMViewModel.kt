package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.AISearchResult
import com.example.data.api.EnrichedData
import com.example.data.api.GeminiClient
import com.example.data.db.AppDatabase
import com.example.data.model.Lead
import com.example.data.repository.LeadRepository
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
    val allLeads: StateFlow<List<Lead>>

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
        allLeads = repository.allLeads.stateIn(
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
        }
    }

    private suspend fun prepopulateSampleData() {
        val samples = listOf(
            Lead(
                name = "Elena Rostova",
                email = "elena.rostova@vertexlabs.io",
                phone = "+1 (555) 234-5678",
                company = "Vertex Labs",
                title = "Director of Product Engineering",
                status = "NEW",
                score = 85,
                website = "https://vertexlabs.io",
                notes = "Expressed high interest in scaling their generative AI pipeline but struggles with GPU orchestration latency.",
                enrichedIndustry = "GenAI & Computing",
                enrichedSize = "Mid-market (150-500)",
                enrichedPainPoints = "• High GPU cloud infrastructure costs\n• Underutilized instances on AWS\n• Deployment delivery lag",
                enrichedPitch = "Vertex Labs would highly benefit from our managed model routers, slicing pipeline latency by 35% with dynamic allocation.",
                enrichedIcebreaker = "Elena, saw your recent talk on scalable inferences. Fantastic insights on optimizing GPU thread pools!"
            ),
            Lead(
                name = "Marcus Vance",
                email = "m.vance@stellarretail.com",
                phone = "+1 (555) 876-5432",
                company = "Stellar Retail",
                title = "VP of Omnichannel Growth",
                status = "CONTACTED",
                score = 64,
                website = "https://stellarretail.com",
                notes = "Met at Retail Leaders Summit. Trying to unite point-of-sale customer data with digital checkout streams.",
                enrichedIndustry = "E-Commerce & Retail",
                enrichedSize = "Enterprise (5,000+)",
                enrichedPainPoints = "• Disconnected retail registers\n• Siloed purchase triggers\n• Missed personalized checkout window",
                enrichedPitch = "We offer Stellar Retail an instant live event broker syncing cashiers and cart state in <20ms for instant reward triggers.",
                enrichedIcebreaker = "Marcus! Loved Stellar's expansion into downtown hubs. How is the physical-to-digital inventory bridge coming along?"
            ),
            Lead(
                name = "Takahiro Sato",
                email = "sato@orionfintech.jp",
                phone = "+81 3-1234-5678",
                company = "Orion Fintech",
                title = "Chief Security Officer",
                status = "QUALIFIED",
                score = 92,
                website = "https://orionfintech.jp",
                notes = "Evaluating enterprise security controls and encrypted credential wallets. Multi-tenant isolation is critical.",
                enrichedIndustry = "Financial Tech & Security",
                enrichedSize = "Enterprise (1,200+)",
                enrichedPainPoints = "• Strict Japanese financial regulatory compliance\n• Complex self-managed HSM logs\n• Multi-region key leaks",
                enrichedPitch = "Orion Fintech can gain isolated cold-storage enclaves with hardware verification keys meeting FIPS 140-3 regulations natively.",
                enrichedIcebreaker = "Takahiro-san, congratulations on Orion's F-Type banking audit clearance. Outstanding achievement!"
            ),
            Lead(
                name = "Sarah Jenkins",
                email = "sarah.j@greenhousemedia.co",
                phone = "+1 (555) 432-1098",
                company = "Greenhouse Media",
                title = "Founder & CEO",
                status = "PROPOSAL_SENT",
                score = 45,
                website = "https://greenhousemedia.co",
                notes = "Content studio eager to automate long-video highlight generation for Instagram Reels and YouTube Shorts.",
                enrichedIndustry = "Digital Media Production",
                enrichedSize = "Boutique/Startup (10-30)",
                enrichedPainPoints = "• High manual editor wages\n• Slow turnaround time for short-form clips\n• No structured engagement scores",
                enrichedPitch = "We can help Greenhouse elevate throughput by 10x with auto-frame face-tracking summarizers.",
                enrichedIcebreaker = "Sarah, checked out Greenhouse's latest short doc on sustainability—visually spectacular project!"
            )
        )
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
}
