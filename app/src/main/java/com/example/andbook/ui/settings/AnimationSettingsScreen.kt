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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.andbook.data.DataRepository
import com.example.andbook.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationSettingsScreen(
    repository: DataRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "/ SYSTEM / MOTION",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Motion & Animations",
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

            // --- Theme Transition Speed ---
            val themePercent = (settings.themeAnimationSpeedMultiplier * 100).toInt()
            SettingsSectionHeader("THEME REVEAL TRANSITION: ${themePercent}%")
            
            Text(
                text = "Controls the duration of the circular sweep transition when changing color schemes.",
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = settings.themeAnimationSpeedMultiplier,
                onValueChange = { multiplier ->
                    scope.launch {
                        repository.updateSettings { it.copy(themeAnimationSpeedMultiplier = multiplier) }
                    }
                },
                valueRange = 0.1f..3.0f,
                steps = 28,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.tertiary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    thumbColor = MaterialTheme.colorScheme.tertiary
                )
            )
            
            Spacer(modifier = Modifier.height(28.dp))

            // --- Zoom Springs Speed ---
            val zoomPercent = (settings.zoomAnimationSpeedMultiplier * 100).toInt()
            SettingsSectionHeader("PAGE ZOOM SPRINGS: ${zoomPercent}%")
            
            Text(
                text = "Adjusts the stiffness of physical spring animations when double-tapping to zoom pages.",
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = settings.zoomAnimationSpeedMultiplier,
                onValueChange = { multiplier ->
                    scope.launch {
                        repository.updateSettings { it.copy(zoomAnimationSpeedMultiplier = multiplier) }
                    }
                },
                valueRange = 0.2f..3.0f,
                steps = 27,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.tertiary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    thumbColor = MaterialTheme.colorScheme.tertiary
                )
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
