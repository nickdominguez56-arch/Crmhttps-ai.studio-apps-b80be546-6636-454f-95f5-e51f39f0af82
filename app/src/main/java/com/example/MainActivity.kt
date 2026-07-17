package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.api.GeminiClient
import com.example.data.model.Lead
import com.example.ui.theme.*
import com.example.viewmodel.CRMViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CRMMainApp()
            }
        }
    }
}

@Composable
fun CRMMainApp() {
    val viewModel: CRMViewModel = viewModel()
    val leads by viewModel.allLeads.collectAsStateWithLifecycle()
    val selectedLead by viewModel.selectedLead.collectAsStateWithLifecycle()
    val alertMessage by viewModel.alertMessage.collectAsStateWithLifecycle()
    
    // Bottom Navigation tab index
    var activeTab by remember { mutableIntStateOf(0) }
    
    // Add dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var showAIChatDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importCsv(context, it) }
    }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { viewModel.exportCsv(context, it) }
    }

    // Observe alert messages and make toast info
    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissAlert()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_app_scaffold"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CRMTopBar(
                onImportClick = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                onExportClick = { exportLauncher.launch("stones_crm_leads.csv") }
            )
        },
        bottomBar = {
            CRMBottomNav(
                activeIndex = activeTab,
                onTabSelected = { activeTab = it }
            )
        },
        floatingActionButton = {
            if (activeTab == 0) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FloatingActionButton(
                        onClick = { showAIChatDialog = true },
                        containerColor = TealSecondary,
                        contentColor = SlateDarkBg,
                        modifier = Modifier.testTag("ai_assistant_fab")
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = "AI Assistant")
                    }
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_lead_fab")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Lead")
                    }
                }
            } else {
                FloatingActionButton(
                    onClick = { showAIChatDialog = true },
                    containerColor = TealSecondary,
                    contentColor = SlateDarkBg,
                    modifier = Modifier.testTag("ai_assistant_fab")
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = "AI Assistant")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> LeadsDashboard(
                    viewModel = viewModel,
                    leads = leads,
                    onLeadClick = { viewModel.selectLead(it) }
                )
                1 -> AISmartSearch(
                    viewModel = viewModel,
                    leads = leads,
                    onLeadClick = { viewModel.selectLead(it) }
                )
                2 -> DataEnrichmentMatrix(
                    leads = leads
                )
                3 -> AnalyticsDashboard(
                    leads = leads
                )
            }

            // Lead Details modal overlay / dialog
            selectedLead?.let { lead ->
                LeadDetailsDialog(
                    lead = lead,
                    viewModel = viewModel,
                    onDismiss = { viewModel.selectLead(null) }
                )
            }

            // Add new lead form dialog
            if (showAddDialog) {
                AddLeadDialog(
                    onDismiss = { showAddDialog = false },
                    onSave = { name, email, phone, company, title, status, emailOpens, websiteVisits, customScore, website, notes ->
                        viewModel.insertLead(
                            name = name,
                            email = email,
                            phone = phone,
                            company = company,
                            title = title,
                            status = status,
                            website = website,
                            notes = notes,
                            emailOpens = emailOpens,
                            websiteVisits = websiteVisits,
                            customFieldScore = customScore
                        )
                        showAddDialog = false
                    }
                )
            }

            // AI Assistant Chat dialog
            if (showAIChatDialog) {
                AIChatAssistantDialog(
                    viewModel = viewModel,
                    onDismiss = { showAIChatDialog = false }
                )
            }
        }
    }
}

@Composable
fun CRMTopBar(
    onImportClick: () -> Unit = {},
    onExportClick: () -> Unit = {}
) {
    Surface(
        color = SlateDarkBg,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "👅 STONES-NET CRM",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Offwhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmeralPrimary.copy(alpha = 0.1f))
                                .border(1.dp, EmeralPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v2.0",
                                color = EmeralPrimary,
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = "High-Net-Worth Lead Generation & Relationship Matrix Dashboard",
                        fontSize = 12.sp,
                        color = CoolGreyText,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Global Actions Box
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onImportClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SlateCardBg),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SlateCardBg.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("📤 Import CSV", color = Offwhite, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onExportClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SlateCardBg),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SlateCardBg.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("📥 Export CSV", color = Offwhite, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CRMBottomNav(activeIndex: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = SlateDarkBg,
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("app_navigation_bar")
    ) {
        NavigationBarItem(
            selected = activeIndex == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Leads") },
            label = { Text("Contact Leads", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SlateDarkBg,
                selectedTextColor = EmeralPrimary,
                indicatorColor = EmeralPrimary,
                unselectedIconColor = CoolGreyText,
                unselectedTextColor = CoolGreyText
            ),
            modifier = Modifier.testTag("nav_leads_tab")
        )
        NavigationBarItem(
            selected = activeIndex == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "AI Search") },
            label = { Text("AI Agent Search", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SlateDarkBg,
                selectedTextColor = EmeralPrimary,
                indicatorColor = EmeralPrimary,
                unselectedIconColor = CoolGreyText,
                unselectedTextColor = CoolGreyText
            ),
            modifier = Modifier.testTag("nav_ai_search_tab")
        )
        NavigationBarItem(
            selected = activeIndex == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(imageVector = Icons.Default.Menu, contentDescription = "Data Matrix") },
            label = { Text("Enrichment Matrix", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SlateDarkBg,
                selectedTextColor = EmeralPrimary,
                indicatorColor = EmeralPrimary,
                unselectedIconColor = CoolGreyText,
                unselectedTextColor = CoolGreyText
            ),
            modifier = Modifier.testTag("nav_matrix_tab")
        )
        NavigationBarItem(
            selected = activeIndex == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(imageVector = Icons.Default.Info, contentDescription = "Analytics") },
            label = { Text("Analytics", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SlateDarkBg,
                selectedTextColor = EmeralPrimary,
                indicatorColor = EmeralPrimary,
                unselectedIconColor = CoolGreyText,
                unselectedTextColor = CoolGreyText
            ),
            modifier = Modifier.testTag("nav_analytics_tab")
        )
    }
}

// --- TAB 1: Leads list & dashboard meters ---
@Composable
fun LeadsDashboard(
    viewModel: CRMViewModel,
    leads: List<Lead>,
    onLeadClick: (Lead) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val matchedLeadIds by viewModel.matchedLeadIds.collectAsStateWithLifecycle()
    
    // Local filter by category chip selection index
    var selectedCategoryFilter by remember { mutableStateOf("All Verticals") }
    val categories = remember(leads) {
        listOf("All Verticals") + leads.map { it.enrichedIndustry.ifEmpty { "Unclassified" } }.distinct().sorted()
    }

    var selectedStatusFilter by remember { mutableStateOf("All Statuses") }
    val statuses = listOf("All Statuses", "NEW", "CONTACTED", "QUALIFIED", "PROPOSAL_SENT", "WON", "LOST")

    var sortColumn by remember { mutableStateOf("Name") }
    var sortAscending by remember { mutableStateOf(true) }

    // Filter leads locally based on both normal text query, AI subset selection, and category filters
    val filteredLeads = remember(leads, searchQuery, matchedLeadIds, selectedCategoryFilter, selectedStatusFilter, sortColumn, sortAscending) {
        val filtered = leads.filter { lead ->
            val matchText = searchQuery.isEmpty() ||
                    lead.name.contains(searchQuery, true) ||
                    lead.company.contains(searchQuery, true) ||
                    lead.title.contains(searchQuery, true) ||
                    lead.notes.contains(searchQuery, true)
            
            val matchAI = matchedLeadIds == null || matchedLeadIds!!.contains(lead.id)
            val matchCategory = selectedCategoryFilter == "All Verticals" || lead.enrichedIndustry == selectedCategoryFilter || lead.notes.contains(selectedCategoryFilter, ignoreCase = true)
            val matchStatus = selectedStatusFilter == "All Statuses" || lead.status == selectedStatusFilter
            
            matchText && matchAI && matchCategory && matchStatus
        }
        
        when (sortColumn) {
            "Name" -> if (sortAscending) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
            "Industry" -> if (sortAscending) filtered.sortedBy { it.enrichedIndustry.lowercase() } else filtered.sortedByDescending { it.enrichedIndustry.lowercase() }
            "Valuation" -> if (sortAscending) filtered.sortedBy { it.score } else filtered.sortedByDescending { it.score }
            else -> filtered
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBg)
    ) {
        // CRM Health Metrics Component
        CRMHealthMetricsDashboard(leads = leads)
        
        // Search Bar and Quick Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Filter by name, industry, title...", color = CoolGreyText) },
                prefix = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CoolGreyText) },
                suffix = {
                    if (searchQuery.isNotEmpty() || matchedLeadIds != null) {
                        IconButton(onClick = {
                            viewModel.updateSearchQuery("")
                            viewModel.clearSearchFilter()
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeralPrimary,
                    unfocusedBorderColor = SlateCardBg,
                    focusedContainerColor = SlateCardBg,
                    unfocusedContainerColor = SlateCardBg,
                    focusedTextColor = Offwhite,
                    unfocusedTextColor = Offwhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_text_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Vertical / Category filter row
            Text(text = "Verticals", color = CoolGreyText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = selectedCategoryFilter == category
                    val currentCount = if (category == "All Verticals") leads.size else leads.count { it.enrichedIndustry == category || it.notes.contains(category, true) }
                    
                    Box(
                        modifier = Modifier
                            .clickable { selectedCategoryFilter = category }
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) EmeralPrimary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(if (isSelected) EmeralPrimary.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = category,
                                color = if (isSelected) EmeralPrimary else CoolGreyText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SlateCardBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$currentCount",
                                    color = CoolGreyText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Status filter row
            Text(text = "Status", color = CoolGreyText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statuses.forEach { status ->
                    val isSelected = selectedStatusFilter == status
                    val currentCount = if (status == "All Statuses") leads.size else leads.count { it.status == status }
                    
                    val statusTheme = getStatusTheme(status)
                    val tintColor = if (status == "All Statuses") EmeralPrimary else statusTheme.color
                    
                    Box(
                        modifier = Modifier
                            .clickable { selectedStatusFilter = status }
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) tintColor else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(if (isSelected) tintColor.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (status == "All Statuses") "All Statuses" else statusTheme.label,
                                color = if (isSelected) tintColor else CoolGreyText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SlateCardBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$currentCount",
                                    color = CoolGreyText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active filters banner notice which can be reset
        if (matchedLeadIds != null) {
            AIAlertFilterNotice(viewModel = viewModel)
        }

        // Main Leads List with staggered entries
        if (filteredLeads.isEmpty()) {
            EmptyStateView()
        } else {
            // Table Header row for sorting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(62.dp)) // space for avatar

                SortableHeaderCell(
                    title = "Name",
                    currentSortColumn = sortColumn,
                    isAscending = sortAscending,
                    onClick = {
                        if (sortColumn == "Name") sortAscending = !sortAscending else { sortColumn = "Name"; sortAscending = true }
                    },
                    modifier = Modifier.weight(1f)
                )

                SortableHeaderCell(
                    title = "Industry",
                    currentSortColumn = sortColumn,
                    isAscending = sortAscending,
                    onClick = {
                        if (sortColumn == "Industry") sortAscending = !sortAscending else { sortColumn = "Industry"; sortAscending = true }
                    },
                    modifier = Modifier.weight(1f)
                )

                SortableHeaderCell(
                    title = "Valuation",
                    currentSortColumn = sortColumn,
                    isAscending = sortAscending,
                    onClick = {
                        if (sortColumn == "Valuation") sortAscending = !sortAscending else { sortColumn = "Valuation"; sortAscending = true }
                    },
                    modifier = Modifier.width(90.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredLeads, key = { it.id }) { lead ->
                    LeadCardItem(lead = lead, onClick = { onLeadClick(lead) })
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
                }
            }
        }
    }
}

@Composable
fun CRMHealthMetricsDashboard(leads: List<Lead>) {
    val activeLeads = leads.size
    val avgValuation = if (leads.isNotEmpty()) leads.map { it.score }.average() * 1.5 else 0.0
    val roundedValuation = Math.round(avgValuation * 10.0) / 10.0
    val formattedValuation = "$${roundedValuation}M"
    
    // progress bar calculation mock
    val targetValuation = 150.0 // abstract goal
    val progressToGoal = (avgValuation / targetValuation).coerceIn(0.0, 1.0).toFloat()
    
    val qualifiedLeads = leads.count { it.score >= 70 }
    val qualifiedRatio = if (activeLeads > 0) qualifiedLeads.toFloat() / activeLeads else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SlateCardBg.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = EmeralPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CRM HEALTH METRICS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CoolGreyText, letterSpacing = 1.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Metric 1: Active Leads
                Column(modifier = Modifier.weight(1f)) {
                    Text("Active Targets", color = CoolGreyText, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$activeLeads", color = Offwhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                
                // Metric 2: Avg Valuation
                Column(modifier = Modifier.weight(1f)) {
                    Text("Avg Asset Valuation", color = CoolGreyText, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(formattedValuation, color = EmeralPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Progress Bar: Portfolio Target Growth
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Portfolio Target Growth", color = CoolGreyText, fontSize = 11.sp)
                    Text("${(progressToGoal * 100).toInt()}%", color = Offwhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = progressToGoal,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = EmeralPrimary,
                    trackColor = SlateDarkBg
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress Bar: Qualified Target Density
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Qualified Target Density (>70 Score)", color = CoolGreyText, fontSize = 11.sp)
                    Text("${(qualifiedRatio * 100).toInt()}%", color = Offwhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = qualifiedRatio,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = CoralOrange,
                    trackColor = SlateDarkBg
                )
            }
        }
    }
}

@Composable
fun AIAlertFilterNotice(viewModel: CRMViewModel) {
    val explanation by viewModel.aiSearchExplanation.collectAsStateWithLifecycle()

    Card(
        colors = CardDefaults.cardColors(containerColor = EmeralPrimary.copy(alpha = 0.1f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .border(1.dp, EmeralPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Search insight",
                    tint = EmeralPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Smart Filter Enabled",
                    color = PremiumMint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { viewModel.clearSearchFilter() },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear Filter", tint = CoolGreyText, modifier = Modifier.size(14.dp))
                }
            }
            if (!explanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = explanation!!,
                    color = Offwhite,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun LeadCardItem(lead: Lead, onClick: () -> Unit) {
    val scoreColor = when {
        lead.score >= 80 -> CoralOrange
        lead.score >= 50 -> TealSecondary
        else -> CoolGreyText
    }

    val statusDetails = getStatusTheme(lead.status)

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateCardBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("lead_item_card_${lead.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Custom Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(scoreColor.copy(alpha = 0.15f))
                    .border(1.5.dp, scoreColor, CircleShape)
            ) {
                Text(
                    text = lead.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase(),
                    color = scoreColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lead.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Offwhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusDetails.color.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusDetails.label,
                            color = statusDetails.color,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "${lead.title} • ${lead.company}",
                    fontSize = 12.sp,
                    color = CoolGreyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress Thermal Score Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = lead.score / 100f,
                        color = scoreColor,
                        trackColor = SlateDarkBg,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "Score ${lead.score} (${lead.scoreCategory})",
                        fontSize = 10.sp,
                        color = scoreColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                // AI enriched badge tag if available
                if (lead.enrichedIndustry.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(PremiumMint.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✨ ENRICHED • ${lead.enrichedIndustry}",
                            color = PremiumMint,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = CoolGreyText.copy(alpha = 0.3f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Matching Leads",
            color = Offwhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Try adjusting your search criteria, clearing your status filters, or hitting the '+' icon to create a new lead in your CRM database.",
            color = CoolGreyText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun SortableHeaderCell(
    title: String,
    currentSortColumn: String,
    isAscending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = title == currentSortColumn
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isSelected) EmeralPrimary else CoolGreyText,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.width(2.dp))
        if (isSelected) {
            Icon(
                imageVector = if (isAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Sort Icon",
                tint = EmeralPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// --- TAB 2: AI Smart Conversational Search ---
@Composable
fun AISmartSearch(
    viewModel: CRMViewModel,
    leads: List<Lead>,
    onLeadClick: (Lead) -> Unit
) {
    var aiQueryInput by remember { mutableStateOf("") }
    val isSearching by viewModel.isAISearching.collectAsStateWithLifecycle()
    val matchedLeadIds by viewModel.matchedLeadIds.collectAsStateWithLifecycle()
    val explanation by viewModel.aiSearchExplanation.collectAsStateWithLifecycle()

    val suggestions = listOf(
        "leads with hot scores above 80",
        "contacts in GenAI industry who are qualified",
        "retail company employees in cold status",
        "potential partners that face orchestration issues"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // AI Header Promo Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCardBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, EmeralPrimary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(EmeralPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = PremiumMint, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Conversational Search Agent",
                        color = Offwhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Search, segment, and isolate leads naturally using conversational AI language filters mapping across lead metadata, custom user notes, and enriched intelligence profiles.",
                    color = CoolGreyText,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Search Input Field
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "What criteria are you looking for today?",
                    fontSize = 12.sp,
                    color = CoolGreyText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = aiQueryInput,
                    onValueChange = { aiQueryInput = it },
                    placeholder = { Text("e.g. leads interested in security with scores > 75...", color = CoolGreyText.copy(alpha = 0.5f)) },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeralPrimary,
                        unfocusedBorderColor = SlateDarkBg,
                        focusedContainerColor = SlateDarkBg,
                        unfocusedContainerColor = SlateDarkBg,
                        focusedTextColor = Offwhite,
                        unfocusedTextColor = Offwhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .testTag("ai_search_conversational_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Suggestion Pills
                Text(text = "SUGGESTED FILTERS", fontSize = 10.sp, color = CoolGreyText, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TealSecondary.copy(alpha = 0.6f))
                                .clickable { aiQueryInput = suggestion }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = suggestion,
                                color = EmeralPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Search Button with Loading Meter
                Button(
                    onClick = { viewModel.executeAISearch(aiQueryInput) },
                    enabled = !isSearching,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeralPrimary,
                        disabledContainerColor = CoolGreyText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("ai_match_query_button")
                ) {
                    if (isSearching) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = CharcoalBase, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Consulting AI...", color = CharcoalBase, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CharcoalBase)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Search & Filter with AI", color = CharcoalBase, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Results Display Panel
        if (matchedLeadIds != null) {
            val matchingLeads = leads.filter { matchedLeadIds!!.contains(it.id) }

            Text(
                text = "MATCHING LEADS (${matchingLeads.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CoolGreyText,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (explanation != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeralPrimary.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(1.dp, EmeralPrimary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(text = "AI AGENT EVALUATION BRIEF", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = PremiumMint)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = explanation!!, color = Offwhite, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }

            if (matchingLeads.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCardBg),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Text(
                        text = "No recorded contacts strictly match that description. Try broadening your query terms.",
                        color = CoolGreyText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matchingLeads.forEach { lead ->
                        LeadCardItem(lead = lead, onClick = { onLeadClick(lead) })
                    }
                }
            }
        }
    }
}

// --- CORE ACTION 3: Lead Profile Detail Dialog containing Overview, AI Enrichment, and Email Campaign Synthesis ---
@Composable
fun LeadDetailsDialog(
    lead: Lead,
    viewModel: CRMViewModel,
    onDismiss: () -> Unit
) {
    val isEnriching by viewModel.isEnriching.collectAsStateWithLifecycle()
    val isGeneratingEmail by viewModel.isGeneratingEmail.collectAsStateWithLifecycle()
    val generatedEmail by viewModel.generatedEmail.collectAsStateWithLifecycle()
    val allTemplates by viewModel.allTemplates.collectAsStateWithLifecycle()

    var detailTabState by remember { mutableIntStateOf(0) } // 0: Profile, 1: AI Insights, 2: Outreach Draft

    // Edit states
    var isEditMode by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(lead.name) }
    var editEmail by remember { mutableStateOf(lead.email) }
    var editPhone by remember { mutableStateOf(lead.phone) }
    var editCompany by remember { mutableStateOf(lead.company) }
    var editTitle by remember { mutableStateOf(lead.title) }
    var editStatus by remember { mutableStateOf(lead.status) }
    var editEmailOpens by remember { mutableIntStateOf(lead.emailOpens) }
    var editWebsiteVisits by remember { mutableIntStateOf(lead.websiteVisits) }
    var editCustomScore by remember { mutableIntStateOf(lead.customFieldScore) }
    var editNotes by remember { mutableStateOf(lead.notes) }

    // Email generator settings
    var emailTone by remember { mutableStateOf("Warm") }
    val emailTones = listOf("Warm", "Professional", "Direct", "Friendly")
    var emailPurpose by remember { mutableStateOf("Meeting Request") }
    val emailPurposes = listOf("Meeting Request", "Solution Intro", "Friendly Follow-up")

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            color = SlateDarkBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Offwhite)
                    }
                    Text(
                        text = "Lead Workspace",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Offwhite
                    )
                    IconButton(
                        onClick = {
                            if (isEditMode) {
                                // Save edit
                                viewModel.updateLead(
                                    lead.copy(
                                        name = editName,
                                        email = editEmail,
                                        phone = editPhone,
                                        company = editCompany,
                                        title = editTitle,
                                        status = editStatus,
                                        emailOpens = editEmailOpens,
                                        websiteVisits = editWebsiteVisits,
                                        customFieldScore = editCustomScore,
                                        notes = editNotes
                                    )
                                )
                                isEditMode = false
                            } else {
                                isEditMode = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Edit Toggle",
                            tint = if (isEditMode) EmeralPrimary else TealSecondary
                        )
                    }
                }

                // Main metadata display header card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (lead.enrichedIndustry.isNotEmpty()) lead.enrichedIndustry.uppercase() else "SECTOR TAG",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = EmeralPrimary,
                            modifier = Modifier
                                .background(EmeralPrimary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .border(1.dp, EmeralPrimary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(EmeralPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lead.name.split(" ").map { it.firstOrNull() ?: "" }.joinToString("").uppercase(),
                                    color = EmeralPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
    
                            Spacer(modifier = Modifier.width(16.dp))
    
                            Column {
                                Text(text = lead.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Offwhite)
                                Text(text = "${lead.title} @ ${lead.company}", fontSize = 13.sp, color = CoolGreyText)
                            }
                        }
                    }
                }

                // WORKSPACE MINI TABS: Profile, AI Insights, AI Email Draft
                TabRow(
                    selectedTabIndex = detailTabState,
                    containerColor = Color.Transparent,
                    contentColor = EmeralPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            color = EmeralPrimary,
                            modifier = Modifier.tabIndicatorOffset(tabPositions[detailTabState])
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = detailTabState == 0,
                        onClick = { detailTabState = 0 },
                        text = { Text("Profile", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = detailTabState == 1,
                        onClick = { detailTabState = 1 },
                        text = { Text("AI Insights", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = detailTabState == 2,
                        onClick = { detailTabState = 2 },
                        text = { Text("Email Outreach", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Workspace content switching
                when (detailTabState) {
                    0 -> { // PROFILE OVERVIEW AND EDIT FORM
                        if (isEditMode) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Lead Full Name", color = CoolGreyText) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editEmail,
                                    onValueChange = { editEmail = it },
                                    label = { Text("Email Address", color = CoolGreyText) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editPhone,
                                    onValueChange = { editPhone = it },
                                    label = { Text("Phone Number", color = CoolGreyText) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = editCompany,
                                        onValueChange = { editCompany = it },
                                        label = { Text("Company", color = CoolGreyText) },
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = editTitle,
                                        onValueChange = { editTitle = it },
                                        label = { Text("Title", color = CoolGreyText) },
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Text(
                                    text = "Email Opens: $editEmailOpens",
                                    fontSize = 12.sp,
                                    color = Offwhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = editEmailOpens.toFloat(),
                                    onValueChange = { editEmailOpens = it.toInt() },
                                    valueRange = 0f..20f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = EmeralPrimary,
                                        activeTrackColor = EmeralPrimary,
                                        inactiveTrackColor = SlateCardBg
                                    )
                                )

                                Text(
                                    text = "Website Visits: $editWebsiteVisits",
                                    fontSize = 12.sp,
                                    color = Offwhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = editWebsiteVisits.toFloat(),
                                    onValueChange = { editWebsiteVisits = it.toInt() },
                                    valueRange = 0f..20f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = EmeralPrimary,
                                        activeTrackColor = EmeralPrimary,
                                        inactiveTrackColor = SlateCardBg
                                    )
                                )

                                Text(
                                    text = "Custom Field Weight: $editCustomScore",
                                    fontSize = 12.sp,
                                    color = Offwhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = editCustomScore.toFloat(),
                                    onValueChange = { editCustomScore = it.toInt() },
                                    valueRange = 0f..50f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = EmeralPrimary,
                                        activeTrackColor = EmeralPrimary,
                                        inactiveTrackColor = SlateCardBg
                                    )
                                )

                                Text(text = "Interaction Status", fontSize = 12.sp, color = CoolGreyText, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("NEW", "CONTACTED", "QUALIFIED", "PROPOSAL_SENT", "WON", "LOST").forEach { statusOption ->
                                        val isSelected = editStatus == statusOption
                                        val statusTheme = getStatusTheme(statusOption)
                                        Box(
                                            modifier = Modifier
                                                .clickable { editStatus = statusOption }
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) statusTheme.color else SlateCardBg)
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = statusTheme.label,
                                                color = if (isSelected) CharcoalBase else Offwhite,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                                
                                OutlinedTextField(
                                    value = editNotes,
                                    onValueChange = { editNotes = it },
                                    label = { Text("Deal Notes", color = CoolGreyText) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 4
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        viewModel.updateLead(
                                            lead.copy(
                                                name = editName,
                                                email = editEmail,
                                                phone = editPhone,
                                                company = editCompany,
                                                title = editTitle,
                                                status = editStatus,
                                                emailOpens = editEmailOpens,
                                                websiteVisits = editWebsiteVisits,
                                                customFieldScore = editCustomScore,
                                                notes = editNotes
                                            )
                                        )
                                        isEditMode = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeralPrimary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save Changes", color = CharcoalBase, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Read-only static overview design
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SlateDarkBg),
                                    border = BorderStroke(1.dp, SlateCardBg),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column {
                                            Text(text = "CORPORATE HUB ROUTING", color = CoolGreyText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                            Text(text = lead.email, color = Offwhite, fontSize = 14.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                        }
                                        Column {
                                            Text(text = "COMMERCIAL FOOTPRINT ECOSYSTEM", color = CoolGreyText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                            Text(text = "Score: ${lead.score}/100 (${lead.scoreCategory})", color = EmeralPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                Column {
                                    Text(text = "TARGET ENGAGEMENT STRATEGY", color = CoolGreyText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SlateDarkBg.copy(alpha = 0.5f)),
                                        border = BorderStroke(1.dp, SlateCardBg),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = lead.notes.ifEmpty { "Target intentional strategic layout data currently unavailable. Edit lead to append insights." },
                                            color = Offwhite,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                OutlinedButton(
                                    onClick = {
                                        viewModel.deleteLead(lead)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralOrange),
                                    border = BorderStroke(1.dp, CoralOrange.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delete Lead from CRM")
                                }
                            }
                        }
                    }

                    1 -> { // AI DATA ENRICHMENT VIEW
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "AI DATA ENRICHMENT ENGINE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = PremiumMint,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            if (lead.enrichedIndustry.isEmpty() && !isEnriching) {
                                // Empty state for AI insights
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SlateCardBg, RoundedCornerShape(12.dp))
                                        .border(BorderStroke(1.dp, CoolGreyText.copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Deep Intelligence Missing",
                                            color = Offwhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Text(
                                            text = "Use Gemini 3.5 Flash to automatically discover structural business size, pain points, enterprise values, and tailored icebreaker lines based on prompt context.",
                                            color = CoolGreyText,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 17.sp,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )

                                        Button(
                                            onClick = { viewModel.enrichLead(lead) },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeralPrimary)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = CharcoalBase)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Enrich Data with AI", color = CharcoalBase, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Full structured AI enriched results card
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (isEnriching) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(120.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(color = EmeralPrimary)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text("Synthesizing market insights with Gemini...", color = CoolGreyText, fontSize = 12.sp)
                                            }
                                        }
                                    } else {
                                        // Industry & Company Size Badge
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            EnrichedStatTag(label = "INDUSTRY", value = lead.enrichedIndustry, modifier = Modifier.weight(1f))
                                            EnrichedStatTag(label = "EST. SIZE", value = lead.enrichedSize, modifier = Modifier.weight(1f))
                                        }

                                        // Pain Points
                                        AIInsightCard(title = "Hypothesis • Potential Pain Points", info = lead.enrichedPainPoints, icon = Icons.Default.Info)

                                        // Value Pitch
                                        AIInsightCard(title = "Tailored Enterprise Value Proposition", info = lead.enrichedPitch, icon = Icons.Default.Share)

                                        // Conversation Icebreaker
                                        AIInsightCard(title = "Personalized Connection Icebreaker", info = lead.enrichedIcebreaker, icon = Icons.Default.Person)

                                        Spacer(modifier = Modifier.height(10.dp))

                                        OutlinedButton(
                                            onClick = { viewModel.enrichLead(lead) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumMint),
                                            border = BorderStroke(1.dp, EmeralPrimary.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Refresh Enriched Insights")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> { // AI EMAIL OUTREACH GENERATION / WORKSPACE DRAFTING
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "EMAIL OUTREACH SYNTHESIZER",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = PremiumMint,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )

                            // Form selectors
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SlateCardBg),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Tone dropdown / pills
                                    Text(text = "CAMPAIGN OUTREACH TONE", fontSize = 11.sp, color = CoolGreyText, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        emailTones.forEach { tone ->
                                            val isSelected = emailTone == tone
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) EmeralPrimary else SlateDarkBg)
                                                    .clickable { emailTone = tone }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = tone,
                                                    color = if (isSelected) CharcoalBase else Offwhite,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }

                                    // Purpose dropdown / pills
                                    Text(text = "COMMUNICATION PURPOSE", fontSize = 11.sp, color = CoolGreyText, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        emailPurposes.forEach { purpose ->
                                            val isSelected = emailPurpose == purpose
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) EmeralPrimary else SlateDarkBg)
                                                    .clickable { emailPurpose = purpose }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = purpose,
                                                    color = if (isSelected) CharcoalBase else Offwhite,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Button(
                                        onClick = { viewModel.compileOutreachEmail(lead, emailTone, emailPurpose) },
                                        enabled = !isGeneratingEmail,
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeralPrimary),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isGeneratingEmail) {
                                            CircularProgressIndicator(color = CharcoalBase, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = CharcoalBase)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Draft Outreach Template with AI", color = CharcoalBase, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Reusable Templates Module
                            Text(text = "REUSABLE PITCH TEMPLATES", fontSize = 10.sp, color = PremiumMint, fontWeight = FontWeight.Bold)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SlateCardBg),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (allTemplates.isEmpty()) {
                                        Text("No templates available.", color = CoolGreyText, fontSize = 12.sp)
                                    } else {
                                        allTemplates.forEach { template ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(SlateDarkBg)
                                                    .clickable {
                                                        val body = template.bodyContent
                                                            .replace("[Name]", lead.name)
                                                            .replace("[Band_Name]", "The Rolling Stones")
                                                            .replace("[Brand_Name]", lead.company.ifEmpty { "your company" })
                                                        val subject = template.subjectLine
                                                            .replace("[Name]", lead.name)
                                                            .replace("[Band_Name]", "The Rolling Stones")
                                                            .replace("[Brand_Name]", lead.company.ifEmpty { "your company" })
                                                        
                                                        viewModel.setGeneratedEmail("SUBJECT: $subject\n\n$body")
                                                    }
                                                    .padding(12.dp)
                                            ) {
                                                Column {
                                                    Text(text = template.name, color = Offwhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    Text(text = "Persona: ${template.persona}", color = PremiumMint, fontSize = 11.sp, maxLines = 1)
                                                    Text(text = "Angle: ${template.angle}", color = CoolGreyText, fontSize = 11.sp, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Output email editor template drawer
                            if (generatedEmail != null && !isGeneratingEmail) {
                                Text(text = "CONSTRUCTED TEMPLATE OUTLINE", fontSize = 10.sp, color = PremiumMint, fontWeight = FontWeight.Bold)

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SlateCardBg),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, CoolGreyText.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Text(
                                            text = generatedEmail!!,
                                            color = Offwhite,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Copy & Send actions row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(generatedEmail!!))
                                                    Toast.makeText(context, "Copied template to clipboard!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Copy Text")
                                            }

                                            Button(
                                                onClick = {
                                                    // Extract subject line if possible
                                                    val lines = generatedEmail!!.lines()
                                                    val subject = lines.firstOrNull { it.startsWith("Subject:", true) }?.removePrefix("Subject:")?.trim() ?: "Sales Follow up"
                                                    val body = lines.filter { !it.startsWith("Subject:", true) }.joinToString("\n").trim()

                                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                        data = Uri.parse("mailto:") // only email apps should handle this
                                                        putExtra(Intent.EXTRA_EMAIL, arrayOf(lead.email))
                                                        putExtra(Intent.EXTRA_SUBJECT, subject)
                                                        putExtra(Intent.EXTRA_TEXT, body)
                                                    }
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "No supportive email client found on this device.", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeralPrimary),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = CharcoalBase)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Launch Client", color = CharcoalBase, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateCardBg, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = EmeralPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label.uppercase(), fontSize = 9.sp, color = CoolGreyText, fontWeight = FontWeight.Bold)
            Text(text = value, fontSize = 14.sp, color = Offwhite, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun EnrichedStatTag(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SlateCardBg, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(text = label, fontSize = 9.sp, color = PremiumMint, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value.ifEmpty { "Generating..." }, fontSize = 14.sp, color = Offwhite, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AIInsightCard(title: String, info: String, icon: ImageVector) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = PremiumMint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title.uppercase(), fontSize = 9.sp, color = CoolGreyText, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = info.ifEmpty { "Evaluating profile pointers..." },
                    color = Offwhite,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// --- ACTIONS 4: Dialog sheets to Add custom leads ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLeadDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, Int, Int, Int, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("NEW") }
    var emailOpens by remember { mutableIntStateOf(0) }
    var websiteVisits by remember { mutableIntStateOf(0) }
    var customFieldScore by remember { mutableIntStateOf(0) }
    var website by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val statuses = listOf("NEW", "CONTACTED", "QUALIFIED", "PROPOSAL_SENT", "WON", "LOST")

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateDarkBg),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, CoolGreyText.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .testTag("add_lead_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Contact Lead",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Offwhite
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("First and Last Name", color = CoolGreyText) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                    modifier = Modifier.fillMaxWidth().testTag("add_lead_name_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address", color = CoolGreyText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                    modifier = Modifier.fillMaxWidth().testTag("add_lead_email_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number", color = CoolGreyText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = company,
                        onValueChange = { company = it },
                        label = { Text("Company Name", color = CoolGreyText) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                        modifier = Modifier.weight(1f).testTag("add_lead_company_input")
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Work Title", color = CoolGreyText) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Dropdown mock for status choice (Simplistic horizontal badges)
                Text(text = "Initial Lifecycle Status", fontSize = 11.sp, color = CoolGreyText, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    statuses.forEach { curr ->
                        val isSelected = status == curr
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) EmeralPrimary else SlateCardBg)
                                .clickable { status = curr }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = curr.replace("_", " "),
                                color = if (isSelected) CharcoalBase else CoolGreyText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Lead Score rating
                // Engagement tracking sliders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Email Opens", fontSize = 11.sp, color = CoolGreyText, fontWeight = FontWeight.Bold)
                    Text(text = "$emailOpens", fontSize = 11.sp, color = EmeralPrimary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = emailOpens.toFloat(),
                    onValueChange = { emailOpens = it.toInt() },
                    valueRange = 0f..20f,
                    colors = SliderDefaults.colors(
                        thumbColor = EmeralPrimary,
                        activeTrackColor = EmeralPrimary,
                        inactiveTrackColor = SlateCardBg
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Website Visits", fontSize = 11.sp, color = CoolGreyText, fontWeight = FontWeight.Bold)
                    Text(text = "$websiteVisits", fontSize = 11.sp, color = EmeralPrimary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = websiteVisits.toFloat(),
                    onValueChange = { websiteVisits = it.toInt() },
                    valueRange = 0f..20f,
                    colors = SliderDefaults.colors(
                        thumbColor = EmeralPrimary,
                        activeTrackColor = EmeralPrimary,
                        inactiveTrackColor = SlateCardBg
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Custom Score Adjuster", fontSize = 11.sp, color = CoolGreyText, fontWeight = FontWeight.Bold)
                    Text(text = "$customFieldScore pts", fontSize = 11.sp, color = EmeralPrimary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = customFieldScore.toFloat(),
                    onValueChange = { customFieldScore = it.toInt() },
                    valueRange = 0f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = EmeralPrimary,
                        activeTrackColor = EmeralPrimary,
                        inactiveTrackColor = SlateCardBg
                    )
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Deal notes or connection source...", color = CoolGreyText) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeralPrimary, unfocusedBorderColor = SlateCardBg, focusedTextColor = Offwhite, unfocusedTextColor = Offwhite),
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, CoolGreyText.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoolGreyText),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (name.trim().isNotEmpty() && email.trim().isNotEmpty()) {
                                onSave(name, email, phone, company, title, status, emailOpens, websiteVisits, customFieldScore, website, notes)
                            }
                        },
                        enabled = name.trim().isNotEmpty() && email.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeralPrimary,
                            disabledContainerColor = CoolGreyText
                        ),
                        modifier = Modifier.weight(1f).testTag("save_lead_button")
                    ) {
                        Text("Save Lead", color = CharcoalBase, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- AI ASSISTANT CHAT DIALOG ---
@Composable
fun AIChatAssistantDialog(
    viewModel: CRMViewModel,
    onDismiss: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isTyping by viewModel.isChatAgentTyping.collectAsStateWithLifecycle()
    var currentInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // Allow filling screen
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SlateDarkBg),
            border = BorderStroke(1.dp, SlateCardBg)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateCardBg)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(EmeralPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = EmeralPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Stones AI Assistant", color = Offwhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Always active, ask anything.", color = PremiumMint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = CoolGreyText)
                    }
                }

                // Chat Messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = CoolGreyText, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Ask anything to your CRM Copilot", color = CoolGreyText, fontSize = 14.sp)
                            }
                        }
                    }

                    items(messages) { msg ->
                        val isUser = msg.first == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            if (!isUser) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(EmeralPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = EmeralPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isUser) 16.dp else 4.dp,
                                            bottomEnd = if (isUser) 4.dp else 16.dp
                                        )
                                    )
                                    .background(if (isUser) EmeralPrimary else SlateCardBg)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = msg.second,
                                    color = if (isUser) CharcoalBase else Offwhite,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    if (isTyping) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(EmeralPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = EmeralPrimary, modifier = Modifier.size(16.dp))
                                }
                                CircularProgressIndicator(color = EmeralPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Synthesizing response...", color = CoolGreyText, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Input Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateCardBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = currentInput,
                        onValueChange = { currentInput = it },
                        placeholder = { Text("Ask CRM copilot...", color = CoolGreyText, fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = SlateDarkBg,
                            unfocusedContainerColor = SlateDarkBg,
                            focusedTextColor = Offwhite,
                            unfocusedTextColor = Offwhite
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = {
                            if (currentInput.isNotBlank()) {
                                viewModel.sendChatMessage(currentInput)
                                currentInput = ""
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(EmeralPrimary)
                            .size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = CharcoalBase)
                    }
                }
            }
        }
    }
}

// --- TAB 3: Data Enrichment Matrix ---
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DataEnrichmentMatrix(leads: List<Lead>) {
    var sortColumn by remember { mutableStateOf("Name") }
    var sortAscending by remember { mutableStateOf(true) }

    val sortedLeads = remember(leads, sortColumn, sortAscending) {
        val filtered = leads.filter { it.enrichedIndustry.isNotEmpty() || it.enrichedSize.isNotEmpty() || it.enrichedPainPoints.isNotEmpty() || it.name.isNotEmpty() }
        when (sortColumn) {
            "Name" -> if (sortAscending) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
            "Industry" -> if (sortAscending) filtered.sortedBy { it.enrichedIndustry.lowercase() } else filtered.sortedByDescending { it.enrichedIndustry.lowercase() }
            "Valuation" -> if (sortAscending) filtered.sortedBy { it.enrichedSize.lowercase() } else filtered.sortedByDescending { it.enrichedSize.lowercase() }
            "Confidence" -> if (sortAscending) filtered.sortedBy { it.score } else filtered.sortedByDescending { it.score }
            else -> filtered
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Data Enrichment Matrix",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Offwhite,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Interactive, sortable table. Press & hold confidence scores for tooltips.",
                fontSize = 12.sp,
                color = CoolGreyText,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateCardBg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SortableHeaderCell(title = "Name", currentSortColumn = sortColumn, isAscending = sortAscending, onClick = {
                if (sortColumn == "Name") sortAscending = !sortAscending else { sortColumn = "Name"; sortAscending = true }
            }, modifier = Modifier.weight(1.5f))
            SortableHeaderCell(title = "Industry", currentSortColumn = sortColumn, isAscending = sortAscending, onClick = {
                if (sortColumn == "Industry") sortAscending = !sortAscending else { sortColumn = "Industry"; sortAscending = true }
            }, modifier = Modifier.weight(1.5f))
            SortableHeaderCell(title = "Valuation", currentSortColumn = sortColumn, isAscending = sortAscending, onClick = {
                if (sortColumn == "Valuation") sortAscending = !sortAscending else { sortColumn = "Valuation"; sortAscending = true }
            }, modifier = Modifier.weight(1.5f))
            SortableHeaderCell(title = "Confidence", currentSortColumn = sortColumn, isAscending = sortAscending, onClick = {
                if (sortColumn == "Confidence") sortAscending = !sortAscending else { sortColumn = "Confidence"; sortAscending = true }
            }, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortedLeads, key = { it.id }) { lead ->
                MatrixRowItem(lead = lead)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MatrixRowItem(lead: Lead) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateCardBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = lead.name, color = Offwhite, fontSize = 12.sp, modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = lead.enrichedIndustry.ifEmpty { "N/A" }, color = CoolGreyText, fontSize = 12.sp, modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = lead.enrichedSize.ifEmpty { "Unknown" }, color = CoolGreyText, fontSize = 12.sp, modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip(
                    containerColor = CharcoalBase,
                    contentColor = Offwhite
                ) {
                    Text("Score based on Lead Valuation and Activity", fontSize = 11.sp)
                }
            },
            state = rememberTooltipState(),
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val scoreColor = when {
                    lead.score >= 80 -> EmeralPrimary
                    lead.score >= 50 -> Color(0xFFFBBF24)
                    else -> Color(0xFFFB7185)
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(scoreColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "${lead.score}%", color = Offwhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
data class StatusTheme(val label: String, val color: Color)

fun getStatusTheme(status: String): StatusTheme {
    return when (status) {
        "NEW" -> StatusTheme("Targeted", Color(0xFF38BDF8))       // Cyan Light
        "CONTACTED" -> StatusTheme("Reached Out", Color(0xFFFB7185))  // Soft Pink
        "QUALIFIED" -> StatusTheme("Engaged", Color(0xFFFBBF24))  // Amber Gold
        "PROPOSAL_SENT" -> StatusTheme("Proposal", Color(0xFFC084FC)) // Lavender Purple
        "WON" -> StatusTheme("Won Deal", PremiumMint)              // Emerald Green
        "LOST" -> StatusTheme("Closed/Lost", CoolGreyText)         // Slate Muted
        else -> StatusTheme(status, Offwhite)
    }
}

// --- TAB 4: Dashboard ---
@Composable
fun AnalyticsDashboard(leads: List<Lead>) {
    val totalContacts = leads.size
    val averageScore = if (leads.isNotEmpty()) leads.map { it.score }.average().toInt() else 0
    val byVertical = leads.groupBy { it.enrichedIndustry.ifEmpty { "Unclassified" } }.mapValues { it.value.size }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(SlateDarkBg)
        .verticalScroll(rememberScrollState())
        .padding(24.dp)) {
        
        Text("CRM Summary Analytics", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Offwhite, modifier = Modifier.padding(bottom = 24.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DashboardMetricCard(title = "Total Contacts", value = totalContacts.toString(), modifier = Modifier.weight(1f))
            DashboardMetricCard(title = "Avg Confidence", value = "$averageScore%", modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Lead Distribution by Vertical", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Offwhite, modifier = Modifier.padding(bottom = 16.dp))
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SlateCardBg)
                .padding(16.dp)
        ) {
            byVertical.entries.sortedByDescending { it.value }.forEach { (vertical, count) ->
                VerticalStatsRow(vertical = vertical, count = count, total = totalContacts.coerceAtLeast(1))
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun DashboardMetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier
        .clip(RoundedCornerShape(12.dp))
        .background(SlateCardBg)
        .border(1.dp, SlateCardBg.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        .padding(20.dp)) {
        Column {
            Text(text = title, fontSize = 14.sp, color = CoolGreyText, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontSize = 32.sp, color = EmeralPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VerticalStatsRow(vertical: String, count: Int, total: Int) {
    val percentage = (count.toFloat() / total)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = vertical, fontSize = 14.sp, color = Offwhite, fontWeight = FontWeight.Medium)
            Text(text = "$count (${(percentage * 100).toInt()}%)", fontSize = 14.sp, color = CoolGreyText)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(SlateDarkBg)) {
            Box(modifier = Modifier.fillMaxWidth(percentage).fillMaxHeight().background(EmeralPrimary))
        }
    }
}
