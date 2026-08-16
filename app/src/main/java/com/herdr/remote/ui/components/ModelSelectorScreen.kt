package com.herdr.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.herdr.remote.data.model.OpenRouterModel
import com.herdr.remote.data.network.OpenRouterService
import com.herdr.remote.ui.theme.AccentCyan
import com.herdr.remote.ui.theme.AccentEmerald
import com.herdr.remote.ui.theme.AccentPrimary
import com.herdr.remote.ui.theme.AccentViolet
import com.herdr.remote.ui.theme.BackgroundDark
import com.herdr.remote.ui.theme.BorderHighlight
import com.herdr.remote.ui.theme.BorderSubtle
import com.herdr.remote.ui.theme.SurfaceCard
import com.herdr.remote.ui.theme.SurfaceDark
import com.herdr.remote.ui.theme.SurfaceElevated
import com.herdr.remote.ui.theme.TextMuted
import com.herdr.remote.ui.theme.TextPrimary
import com.herdr.remote.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Fullscreen Navigation 3 Destination for browsing and searching 400+ OpenRouter AI Models.
 */
@Composable
fun ModelSelectorScreen(
    selectedModelId: String,
    apiKey: String,
    onSelectModel: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val openRouterService = remember { OpenRouterService() }

    var models by remember { mutableStateOf<List<OpenRouterModel>>(OpenRouterModel.DEFAULT_MODELS) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    var benchmarkingModelId by remember { mutableStateOf<String?>(null) }
    var benchmarkResults by remember { mutableStateOf<Map<String, com.herdr.remote.data.network.ModelBenchmarkResult>>(emptyMap()) }

    fun testModel(modelId: String) {
        benchmarkingModelId = modelId
        scope.launch {
            val res = openRouterService.benchmarkModel(apiKey, modelId)
            benchmarkingModelId = null
            benchmarkResults = benchmarkResults + (modelId to res)
        }
    }

    fun loadModels() {
        isLoading = true
        scope.launch {
            val result = openRouterService.fetchModels(apiKey.ifBlank { null })
            isLoading = false
            result.onSuccess { list ->
                if (list.isNotEmpty()) {
                    models = list
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadModels()
    }

    val filterOptions = listOf("All", "Free 🎁", "Gemini", "DeepSeek", "Llama", "Claude", "Mistral", "Qwen")

    val filteredModels = remember(models, searchQuery, selectedFilter) {
        models.filter { model ->
            val matchesFilter = when (selectedFilter) {
                "Free 🎁" -> model.isFree
                "Gemini" -> model.id.contains("gemini", ignoreCase = true) || model.name.contains("gemini", ignoreCase = true)
                "DeepSeek" -> model.id.contains("deepseek", ignoreCase = true) || model.name.contains("deepseek", ignoreCase = true)
                "Llama" -> model.id.contains("llama", ignoreCase = true) || model.name.contains("llama", ignoreCase = true)
                "Claude" -> model.id.contains("claude", ignoreCase = true) || model.name.contains("claude", ignoreCase = true)
                "Mistral" -> model.id.contains("mistral", ignoreCase = true) || model.name.contains("mistral", ignoreCase = true)
                "Qwen" -> model.id.contains("qwen", ignoreCase = true) || model.name.contains("qwen", ignoreCase = true)
                else -> true
            }

            val matchesSearch = searchQuery.isBlank() ||
                    model.id.contains(searchQuery, ignoreCase = true) ||
                    model.name.contains(searchQuery, ignoreCase = true) ||
                    (model.description?.contains(searchQuery, ignoreCase = true) == true)

            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = BackgroundDark,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Browse AI Models",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = "${filteredModels.size} of ${models.size} models available",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentViolet
                        )
                    }

                    IconButton(onClick = { loadModels() }) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = AccentViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Models",
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name, provider, or ID...", color = TextMuted, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Clear",
                                    tint = AccentViolet,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SurfaceElevated,
                        unfocusedContainerColor = SurfaceElevated
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )

                // Category Filter Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterOptions.forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) AccentViolet else SurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) AccentViolet else BorderSubtle,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredModels, key = { it.id }) { model ->
                val isSelected = model.id == selectedModelId

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) SurfaceElevated else SurfaceCard)
                        .border(
                            1.5.dp,
                            if (isSelected) AccentViolet else BorderSubtle,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectModel(model.id) }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = if (isSelected) AccentViolet else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (model.isFree) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentEmerald.copy(alpha = 0.2f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "FREE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = AccentEmerald
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = model.id,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp
                                ),
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!model.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = model.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = TextMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SurfaceDark)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = model.formattedContext.ifBlank { "${model.contextLength ?: 0} ctx" },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = AccentCyan
                                    )
                                }

                                if (model.pricing != null) {
                                    Text(
                                        text = if (model.isFree) "Free inference" else "In: $${model.pricing.prompt} / 1M",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = TextMuted
                                    )
                                }
                            }

                            // Benchmark Speed Test Action & Result Display
                            val benchmarkResult = benchmarkResults[model.id]
                            val isBenchmarking = benchmarkingModelId == model.id

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isBenchmarking) SurfaceElevated else AccentViolet.copy(alpha = 0.15f))
                                        .border(1.dp, if (isBenchmarking) AccentViolet else AccentViolet.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable(enabled = !isBenchmarking) { testModel(model.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isBenchmarking) {
                                            CircularProgressIndicator(
                                                strokeWidth = 1.5.dp,
                                                color = AccentViolet,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text("Testing speed...", fontSize = 11.sp, color = AccentViolet)
                                        } else {
                                            Text("🧪 Test Speed", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentViolet)
                                        }
                                    }
                                }

                                if (benchmarkResult != null && benchmarkResult.isSuccess) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AccentEmerald.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "⚡ ${benchmarkResult.latencyMs}ms • 🚀 ${String.format("%.1f", benchmarkResult.tokensPerSecond)} t/s",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            color = AccentEmerald
                                        )
                                    }
                                } else if (benchmarkResult != null && !benchmarkResult.isSuccess) {
                                    Text(
                                        text = "Failed: ${benchmarkResult.errorMessage?.take(30)}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFEF5350)
                                    )
                                }
                            }

                            if (benchmarkResult != null && benchmarkResult.isSuccess && benchmarkResult.responseText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "💬 \"${benchmarkResult.responseText}\"",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(AccentViolet),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
