package com.example.andbook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.andbook.data.AppSettings
import com.example.andbook.data.DataRepository
import com.example.andbook.data.ReaderTheme
import com.example.andbook.theme.*
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: DataRepository,
    onBack: () -> Unit,
    onNavigateToStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle()
    val history by repository.history.collectAsStateWithLifecycle()

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val folderName = uri.lastPathSegment ?: "Books"
                scope.launch {
                    repository.updateSettings {
                        it.copy(booksFolderUri = uri.toString(), booksFolderName = folderName)
                    }
                    repository.scanBooksFolder()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "/ SYSTEM / CONFIG",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Settings",
                                fontFamily = NyghtSerifFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // --- Section: Statistics ---
                SettingsSectionHeader("ANALYSIS & GOALS")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { onNavigateToStats() },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Reading Statistics",
                                    fontFamily = NyghtSerifFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "View analytics and adjust daily reading goals",
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // --- Section: Theme ---
                SettingsSectionHeader("THEME MODE")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsThemeButton(
                        text = "Light Coffee",
                        isSelected = settings.theme == ReaderTheme.LIGHT_COFFEE,
                        onClick = { scope.launch { repository.updateSettings { it.copy(theme = ReaderTheme.LIGHT_COFFEE) } } },
                        modifier = Modifier.weight(1f)
                    )
                    SettingsThemeButton(
                        text = "Dark Coffee",
                        isSelected = settings.theme == ReaderTheme.DARK_COFFEE,
                        onClick = { scope.launch { repository.updateSettings { it.copy(theme = ReaderTheme.DARK_COFFEE) } } },
                        modifier = Modifier.weight(1f)
                    )
                    SettingsThemeButton(
                        text = "Amoled",
                        isSelected = settings.theme == ReaderTheme.AMOLED,
                        onClick = { scope.launch { repository.updateSettings { it.copy(theme = ReaderTheme.AMOLED) } } },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // --- Section: EPUB Typography ---
                SettingsSectionHeader("DEFAULT EPUB FONT")
                var expandedFontDropdown by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedFontDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = FontDisplayNameMap[settings.fontName] ?: "Default",
                                fontFamily = FontMap[settings.fontName] ?: InterFontFamily,
                                fontSize = 15.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = expandedFontDropdown,
                        onDismissRequest = { expandedFontDropdown = false },
                        // Restrict height and make it scrollable to prevent the big list overflow
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 220.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    ) {
                        FontMap.keys.forEach { fontKey ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = FontDisplayNameMap[fontKey] ?: fontKey,
                                        fontFamily = FontMap[fontKey] ?: InterFontFamily,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    scope.launch { repository.updateSettings { it.copy(fontName = fontKey) } }
                                    expandedFontDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // --- Section: System Font ---
                SettingsSectionHeader("SYSTEM WIDE FONT")
                var expandedSystemFontDropdown by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedSystemFontDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = FontDisplayNameMap[settings.systemFontName] ?: "Nyght Serif",
                                fontFamily = FontMap[settings.systemFontName] ?: NyghtSerifFontFamily,
                                fontSize = 15.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = expandedSystemFontDropdown,
                        onDismissRequest = { expandedSystemFontDropdown = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 220.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    ) {
                        FontMap.keys.forEach { fontKey ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = FontDisplayNameMap[fontKey] ?: fontKey,
                                        fontFamily = FontMap[fontKey] ?: InterFontFamily,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    scope.launch { repository.updateSettings { it.copy(systemFontName = fontKey) } }
                                    expandedSystemFontDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // --- Section: EPUB Font Size ---
                SettingsSectionHeader("DEFAULT FONT SIZE: ${settings.fontSize.toInt()}sp")
                Slider(
                    value = settings.fontSize,
                    onValueChange = { size -> scope.launch { repository.updateSettings { it.copy(fontSize = size) } } },
                    valueRange = 14f..38f,
                    steps = 12,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        thumbColor = MaterialTheme.colorScheme.tertiary
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                // --- Section: Storage Directory ---
                SettingsSectionHeader("LIBRARY DIRECTORY")
                OutlinedButton(
                    onClick = { folderLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                    )
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (settings.booksFolderName != null) "Change: ${settings.booksFolderName}" else "Choose Directory",
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
fun SettingsThemeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = InterFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1
        )
    }
}
