package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

interface GeminiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val service: GeminiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiService::class.java)
    }

    /**
     * Helper to verify if the API key is set and valid (not the placeholder)
     */
    fun isApiKeyValid(): Boolean {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            @Suppress("SENSELESS_COMPARISON")
            if (key == null) {
                false
            } else {
                key.isNotEmpty() && key != "MY_GEMINI_API_KEY" && key != "placeholder"
            }
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Service call to enrich lead details.
     */
    suspend fun enrichLeadData(
        company: String,
        title: String,
        name: String,
        notes: String
    ): EnrichedData? {
        if (!isApiKeyValid()) return null

        val prompt = """
            Act as an elite CRM Data Architect and Lead Scoring Algorithm for a Rolling Stones-inspired B2B CRM.
            We are structuring a lead for our High-Net-Worth Individual (HNWI) and corporate database.
            
            Input Target:
            Company: "$company"
            Title: "$title"
            Name: "$name"
            User Provided Notes: "$notes"
            
            Based on this, return a JSON object exactly matching this schema:
            - industry: The sector (e.g., "Music", "Hollywood & Film", "Finance & Venture Capital", "Luxury Retail", "Automotive & Racing").
            - companySize: Estimate the Commercial Lifecycle Valuation or scale (e.g., "Tier 1 Global Icon", "Tier 2 Industry Titan", "Fortune 500").
            - potentialPainPoints: Strategic reasons for outreach, e.g., how they map to our logic (Private Booking, Creative Collab, Corporate Sponsor, VIP).
            - tailoredValuePitch: Strategic rationale for B2B/B2C engagement tailored to their sector and Rolling Stones history.
            - recommendedIcebreaker: A hyper-personalized outreach hook based on financial liquidity, brand alignment, or PR network multiplier.
            
            Return ONLY a valid JSON object. No markdown wrapping.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        return try {
            val response = service.generateContent(BuildConfig.GEMINI_API_KEY, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                moshi.adapter(EnrichedData::class.java).fromJson(jsonText)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generate customized outreach email.
     */
    suspend fun generateOutreachEmail(
        leadName: String,
        companyName: String,
        title: String,
        industry: String,
        notes: String,
        pitch: String,
        tone: String,
        purpose: String
    ): String? {
        if (!isApiKeyValid()) return null

        val prompt = """
            You are an elite B2B Copywriter and Sales Psychologist. Your task is to generate high-converting, personalized outbound outreach templates targeting High-Net-Worth Individuals (HNWIs) and corporate leaders inside our Rolling Stones-inspired CRM database.
            
            Receiver Name: "$leadName"
            Receiver Company: "$companyName"
            Receiver Title: "$title"
            Targeted Sector / Industry: "$industry"
            The Rolling Stones Hook / Notes: "$notes"
            Tailored Value Pitch: "$pitch"
            
            Email Configuration:
            - Tone: "$tone", but ensure it maintains peer-to-peer professional respect (no stiff corporate lecturing, no overly casual fan gushing).
            - Purpose: "$purpose"
            
            COPYWRITING RULES:
            - State the direct value proposition within the first two sentences.
            - Seamlessly weave in the Rolling Stones hook (from the Notes/Pitch) as proof of understanding their high-octane taste, longevity, or specific commercial history.
            - End with a low-friction, high-value call to action (CTA).
            
            Write a complete, highly persuasive personal email containing a Subject Line and the Body.
            Sign off as "The Stones CRM Operations Team".
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.7f
            )
        )

        return try {
            val response = service.generateContent(BuildConfig.GEMINI_API_KEY, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Use Gemini as an AI search and filtering query broker.
     * Takes the lead list as JSON and searches it based on conversational user queries.
     */
    suspend fun filterLeadsWithAI(
        query: String,
        leadsJson: String
    ): AISearchResult? {
        if (!isApiKeyValid()) return null

        val prompt = """
            You are an automated Revenue Operations (RevOps) AI for the STONES-NET CRM. You are given a user natural language query, and a list of High-Net-Worth Individuals (HNWIs) and corporate leads in JSON format.
            
            User search query: "$query"
            Leads JSON:
            $leadsJson
            
            Analyze the user search query. Identify which lead IDs strictly match the query.
            Query can be about:
            - Pipeline Routing tabs/Status (e.g., "Private Bookers", "Creative Partners")
            - Score and Liquid Budget (e.g., "tier 1 icons", "leads with high liquid valuation", "high intent")
            - Industries (e.g., "Hollywood", "Finance")
            - The Rolling Stones Hook and Notes semantic concepts.
            
            Return a JSON object with two fields:
            - matchedLeadIds: A list of integers containing the IDs of matching leads. If none matching, return empty list [].
            - explanation: A short, elegant 1-2 sentence description explaining the strategic RevOps rationale of why these leads matched the criteria.
            
            Return ONLY a valid JSON object matching this schema.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        return try {
            val response = service.generateContent(BuildConfig.GEMINI_API_KEY, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                moshi.adapter(AISearchResult::class.java).fromJson(jsonText)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun askAIAssistant(
        messages: List<Pair<String, String>> // list of role ("user" or "model") to text
    ): String? {
        if (!isApiKeyValid()) return null

        val systemInstruction = """
            You are "Stones AI", the built-in AI assistant for STONES-NET CRM.
            Your job is to answer user questions about sales strategy, CRM management, or general advice, acting as the backend engineering blueprint.
            
            Embody the following roles when applicable:
            1. CRM Data Architect AI: Normalize 1,000-person target lists into structured Data Objects (ContactID, FirstName, LastName, Sector, InfluenceTier, StonesPointOfContact, CommercialLifecycleValuation, TargetCompany_Agency, CorporateTitle, VerifiedCorporateContactPoint, OutreachIntent). Prioritize compliance (GDPR/CCPA) and public corporate info.
            2. RevOps AI: Dynamically sort the dataset into four tabs based on logical segmentation rules: Private Bookers, Creative & Design Partners, Corporate Sponsors & Automotive, and Entertainment & VIP Leads. Provide structured breakdowns.
            3. Predictive Lead Scoring Algorithm: Apply a standardized numerical Lead Score (0-100) based on Financial Liquidity (Max 40), Strategic Brand Alignment (Max 30), Public Relations/Network Multiplier Value (Max 20), and Accessibility & Compliance (Max 10). Provide Top 10 lists with detailed score variables.
            4. B2B Copywriter and Sales Psychologist: Generate high-converting personalized outbound outreach with immediate direct value propositions, peer-to-peer professional respect, and the "Rolling Stones Hook" as proof of commercial history. End with low-friction calls to action.
            5. Senior Solutions Architect AI: Write technical guides and data-mapping architecture for syncing this platform into standard integrations like HubSpot and Salesforce REST APIs.
            
            Keep answers helpful, highly structured, and strictly aligned with this multi-agent backend architecture.
        """.trimIndent()
        
        val contentList = messages.map { (role, text) ->
            Content(parts = listOf(Part(text = text))) // Note: actual Gemini API uses role inside Content, but making it simple by prepending role
        }.toMutableList()
        contentList.add(0, Content(parts = listOf(Part(text = "System: $systemInstruction"))))

        val request = GenerateContentRequest(
            contents = contentList,
            generationConfig = GenerationConfig(
                temperature = 0.5f
            )
        )

        return try {
            val response = service.generateContent(BuildConfig.GEMINI_API_KEY, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@JsonClass(generateAdapter = true)
data class EnrichedData(
    val industry: String = "",
    val companySize: String = "",
    val potentialPainPoints: String = "",
    val tailoredValuePitch: String = "",
    val recommendedIcebreaker: String = ""
)

@JsonClass(generateAdapter = true)
data class AISearchResult(
    val matchedLeadIds: List<Int> = emptyList(),
    val explanation: String = ""
)
