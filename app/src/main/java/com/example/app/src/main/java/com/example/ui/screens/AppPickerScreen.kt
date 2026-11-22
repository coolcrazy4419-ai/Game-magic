package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.InstalledAppInfo
import com.example.ui.components.darkNeonGlow
import com.example.ui.theme.*
import com.example.ui.viewmodel.TurboViewModel

private enum class PickerFilter(val label: String) {
    ALL("All Apps"),
    GAMES("Games"),
    ADDED("Added")
}

@Composable
fun AppPickerScreen(
    viewModel: TurboViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val allApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(PickerFilter.ALL) }

    val gamesCount = remember(allApps) { allApps.count { it.isGame } }
    val addedCount = remember(allApps, settings.selectedPackages) {
        allApps.count { settings.selectedPackages.contains(it.packageName) }
    }

    val filteredApps = remember(allApps, searchQuery, activeFilter, settings.selectedPackages) {
        val query = searchQuery.trim()
        val base = if (query.isNotEmpty()) {
            allApps.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            when (activeFilter) {
                PickerFilter.ALL -> allApps
                PickerFilter.GAMES -> allApps.filter { it.isGame }
                PickerFilter.ADDED -> allApps.filter { settings.selectedPackages.contains(it.packageName) }
            }
        }
        base.sortedWith(
            compareByDescending<InstalledAppInfo> { settings.selectedPackages.contains(it.packageName) }
                .thenByDescending { it.isGame }
                .thenBy { it.name.lowercase() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = ObsidianCard,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(44.dp)
                        .darkNeonGlow(EmeraldGlow, cornerRadius = 22.dp, glowAlpha = 0.28f, strokeWidth = 0.85.dp)
                        .testTag("app_picker_back_button")
                        .clickable { onBack() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = EmeraldGlow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "ADD GAMES & APPS",
                        style = MaterialTheme.typography.titleLarge,
                        color = MagicTextPrimary
                    )
                    Text(
                        text = "${allApps.size} installed apps • Tap to arm sidebar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MagicTextSecondary
                    )
                }
            }

            IconButton(
                onClick = { viewModel.loadApps() },
                modifier = Modifier.testTag("app_picker_refresh_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = EmeraldGlow)
            }
        }

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    "Search all installed apps...",
                    color = MagicTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = EmeraldGlow)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = MagicTextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldGlow,
                unfocusedBorderColor = ObsidianDivider,
                focusedContainerColor = ObsidianCard.copy(alpha = 0.94f),
                unfocusedContainerColor = ObsidianCard.copy(alpha = 0.88f),
                focusedTextColor = MagicTextPrimary,
                unfocusedTextColor = MagicTextPrimary,
                cursorColor = EmeraldGlow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .darkNeonGlow(
                    glowColor = if (searchQuery.isNotEmpty()) EmeraldGlow else EmeraldDeepGlow,
                    cornerRadius = 14.dp,
                    glowAlpha = if (searchQuery.isNotEmpty()) 0.28f else 0.14f,
                    strokeWidth = 0.85.dp
                )
                .testTag("app_picker_search_input")
        )

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PickerFilter.entries.forEach { filter ->
                val selected = activeFilter == filter && searchQuery.isBlank()
                val count = when (filter) {
                    PickerFilter.ALL -> allApps.size
                    PickerFilter.GAMES -> gamesCount
                    PickerFilter.ADDED -> addedCount
                }
                val accent = when (filter) {
                    PickerFilter.ALL -> EmeraldGlow
                    PickerFilter.GAMES -> HeavyPurpleGlow
                    PickerFilter.ADDED -> BalancedCyanGlow
                }
                val mod = if (selected) {
                    Modifier
                        .weight(1f)
                        .darkNeonGlow(accent, cornerRadius = 10.dp, glowAlpha = 0.28f, strokeWidth = 0.85.dp)
                } else {
                    Modifier.weight(1f)
                }
                Surface(
                    color = if (selected) accent.copy(alpha = 0.18f) else ObsidianCard,
                    shape = RoundedCornerShape(10.dp),
                    border = if (!selected) BorderStroke(0.75.dp, ObsidianDivider) else null,
                    modifier = mod
                        .testTag("picker_filter_${filter.name.lowercase()}")
                        .clickable {
                            searchQuery = ""
                            activeFilter = filter
                        }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${filter.label} ($count)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White else MagicTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Apps List
        if (appsLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EmeraldGlow, strokeWidth = 2.5.dp)
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No app matches \"$searchQuery\"" else "No apps found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MagicTextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 6.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isAdded = settings.selectedPackages.contains(app.packageName)
                    PickerAppRow(
                        app = app,
                        isAdded = isAdded,
                        onToggle = {
                            if (isAdded) viewModel.removeAppFromMagic(app.packageName, app.name)
                            else viewModel.addAppToMagic(app.packageName, app.name)
                        }
                    )
                }
            }
        }

        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGlow),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .darkNeonGlow(EmeraldGlow, cornerRadius = 12.dp, glowAlpha = 0.30f, strokeWidth = 0.9.dp)
                .testTag("app_picker_done_button")
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ObsidianGreenBg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "DONE • ${settings.selectedPackages.size} IN GAME MAGIC",
                style = MaterialTheme.typography.labelMedium,
                color = ObsidianGreenBg,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PickerAppRow(
    app: InstalledAppInfo,
    isAdded: Boolean,
    onToggle: () -> Unit
) {
    val iconBitmap = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }

    val mod = if (isAdded) {
        Modifier
            .fillMaxWidth()
            .darkNeonGlow(EmeraldGlow, cornerRadius = 14.dp, glowAlpha = 0.24f, strokeWidth = 0.85.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    Surface(
        color = if (isAdded) ObsidianElevated.copy(alpha = 0.94f) else ObsidianCard.copy(alpha = 0.9f),
        shape = RoundedCornerShape(14.dp),
        border = if (!isAdded) BorderStroke(0.75.dp, ObsidianDivider) else null,
        modifier = mod.clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(ObsidianGreenBg),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = app.name,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(9.dp))
                    )
                } else {
                    Icon(
                        imageVector = if (app.isGame) Icons.Rounded.SportsEsports else Icons.Rounded.Apps,
                        contentDescription = app.name,
                        tint = EmeraldGlow,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MagicTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (app.isGame) {
                        Text(
                            text = "GAME",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeavyVioletAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(HeavyFillDark)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = if (isAdded) "Added to Game Magic" else "Tap to add",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAdded) EmeraldGlow else MagicTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (isAdded) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Added",
                    tint = EmeraldGlow,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Surface(
                    color = EmeraldDeepGlow.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(0.8.dp, EmeraldGlow)
                ) {
                    Text(
                        text = "+ Add",
                        style = MaterialTheme.typography.labelSmall,
                        color = MagicTextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
