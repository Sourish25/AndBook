package com.example.andbook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.andbook.data.*
import com.example.andbook.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    repository: DataRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle()
    val history by repository.history.collectAsStateWithLifecycle()

    val todayMins = (settings.readingTimeTodaySeconds / 60).toInt()
    val todaySecs = (settings.readingTimeTodaySeconds % 60).toInt()
    
    val totalHours = (settings.totalReadingTimeSeconds / 3600).toInt()
    val totalMins = ((settings.totalReadingTimeSeconds % 3600) / 60).toInt()

    // Calculate completed books count
    val completedBooksCount = remember(history) {
        history.count { it.progress.completionPercentage >= 0.98f || (it.progress.totalPages > 0 && it.progress.lastPage >= it.progress.totalPages - 1) }
    }

    val dailyProgress = if (settings.dailyReadingGoalMinutes > 0) {
        (todayMins.toFloat() / settings.dailyReadingGoalMinutes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Weekly history (last 7 days) computation using actual recorded history.
    val pastDays = remember(settings.dailyHistory, settings.readingTimeTodaySeconds) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val displaySdf = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
        
        val list = mutableListOf<Pair<String, Long>>()
        for (i in 6 downTo 0) {
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(calendar.time)
            val label = displaySdf.format(calendar.time)
            
            val timeSecs = if (i == 0) {
                settings.dailyHistory[dateStr] ?: settings.readingTimeTodaySeconds
            } else {
                settings.dailyHistory[dateStr] ?: 0L
            }
            list.add(Pair(label, timeSecs))
        }
        list
    }

    val weeklyTotalSeconds = remember(pastDays) { pastDays.sumOf { it.second } }
    val weeklyTotalHours = (weeklyTotalSeconds / 3600).toInt()
    val weeklyTotalMins = ((weeklyTotalSeconds % 3600) / 60).toInt()

    val weeklyAverageSeconds = weeklyTotalSeconds / 7
    val weeklyAverageMins = (weeklyAverageSeconds / 60).toInt()

    val dailyAverageSeconds = remember(pastDays, settings.totalReadingTimeSeconds) {
        val activeDays = pastDays.map { it.second }.filter { it > 0 }
        if (activeDays.isEmpty()) 0L else activeDays.average().toLong()
    }
    val dailyAverageMins = (dailyAverageSeconds / 60).toInt()
    val dailyAverageSecs = (dailyAverageSeconds % 60).toInt()

    AndBookTheme(theme = settings.theme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "/ SYSTEM / PERFORMANCE",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Reading Stats",
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

                // --- Today's Goal Progress Ring/Card ---
                Text(
                    text = "DAILY READING GOAL",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(140.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { dailyProgress },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 8.dp,
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$todayMins",
                                    fontFamily = NyghtSerifFontFamily,
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "of ${settings.dailyReadingGoalMinutes} min",
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = if (dailyProgress >= 1f) "Goal achieved today! Keep it up." else "Keep reading to reach your daily goal.",
                            fontFamily = NyghtSerifFontFamily,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- Goal Adjuster Slider ---
                var sliderValue by remember(settings.dailyReadingGoalMinutes) {
                    mutableStateOf(settings.dailyReadingGoalMinutes.toFloat())
                }

                val currentGoalMins = remember(sliderValue) {
                    ((sliderValue / 5f).roundToInt() * 5).coerceIn(5, 300)
                }

                val goalDisplay = remember(currentGoalMins) {
                    val h = currentGoalMins / 60
                    val m = currentGoalMins % 60
                    if (h >= 1) {
                        if (m > 0) "${h}h ${m}m (${currentGoalMins} mins)" else "${h}h (${currentGoalMins} mins)"
                    } else {
                        "${currentGoalMins} mins"
                    }
                }
                
                Text(
                    text = "SET GOAL: $goalDisplay",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { valMins ->
                        sliderValue = valMins
                        val rounded = ((valMins / 5f).roundToInt() * 5).coerceIn(5, 300)
                        if (rounded != settings.dailyReadingGoalMinutes) {
                            scope.launch {
                                repository.updateSettings { it.copy(dailyReadingGoalMinutes = rounded) }
                            }
                        }
                    },
                    valueRange = 5f..300f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        thumbColor = MaterialTheme.colorScheme.tertiary
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- Weekly Activity Chart ---
                WeeklyActivityChart(
                    pastDays = pastDays,
                    goalMinutes = settings.dailyReadingGoalMinutes
                )

                Spacer(modifier = Modifier.height(28.dp))

                // --- Section: General Stats ---
                Text(
                    text = "STATISTICS DETAILS",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cumulative time card
                    StatCard(
                        icon = Icons.Default.Timer,
                        label = "Total Time",
                        value = if (totalHours > 0) "${totalHours}h ${totalMins}m" else "${totalMins}m ${todaySecs}s",
                        modifier = Modifier.weight(1f)
                    )
                    // Daily average card
                    StatCard(
                        icon = Icons.Default.TrendingUp,
                        label = "Daily Avg",
                        value = if (dailyAverageMins > 0) "${dailyAverageMins}m ${dailyAverageSecs}s" else "${dailyAverageSecs}s",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Weekly total card
                    StatCard(
                        icon = Icons.Default.CalendarToday,
                        label = "Weekly Total",
                        value = if (weeklyTotalHours > 0) "${weeklyTotalHours}h ${weeklyTotalMins}m" else "${weeklyTotalMins}m",
                        modifier = Modifier.weight(1f)
                    )
                    // Weekly daily average card
                    StatCard(
                        icon = Icons.Default.HourglassEmpty,
                        label = "Weekly Avg",
                        value = "${weeklyAverageMins}m",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Books read card
                    StatCard(
                        icon = Icons.Default.AutoAwesome,
                        label = "Finished",
                        value = "$completedBooksCount ${if (completedBooksCount == 1) "Book" else "Books"}",
                        modifier = Modifier.weight(1f)
                    )
                    // Books in progress card
                    val inProgressCount = history.size - completedBooksCount
                    StatCard(
                        icon = Icons.Default.Book,
                        label = "In Progress",
                        value = "$inProgressCount ${if (inProgressCount == 1) "Book" else "Books"}",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

// Helper extension function to avoid complex logic inline
private fun AppSettings.dailyReadingReadingGoalHours(): Float {
    return dailyReadingGoalMinutes.toFloat() / 60f
}

@Composable
fun WeeklyActivityChart(
    pastDays: List<Pair<String, Long>>,
    goalMinutes: Int
) {
    val maxSeconds = pastDays.maxOfOrNull { it.second }?.coerceAtLeast(goalMinutes * 60L) ?: (goalMinutes * 60L)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "WEEKLY ACTIVITY",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                pastDays.forEachIndexed { index, dayData ->
                    val label = dayData.first
                    val seconds = dayData.second
                    val minutes = (seconds / 60).toInt()
                    
                    val heightRatio = if (maxSeconds > 0) seconds.toFloat() / maxSeconds.toFloat() else 0f
                    val isToday = index == pastDays.lastIndex
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Value label above the bar
                        Text(
                            text = if (minutes > 0) "${minutes}m" else "-",
                            fontFamily = InterFontFamily,
                            fontSize = 9.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // The bar itself inside a fixed-height container
                        Box(
                            modifier = Modifier
                                .height(80.dp)
                                .width(16.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(fraction = heightRatio.coerceIn(0.04f, 1f))
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.tertiary 
                                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                                    )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Day label (e.g. Mon)
                        Text(
                            text = label,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

// Small helper extension to avoid spelling error or undefined coerceAtIn
private fun Float.coerceAtIn(min: Float, max: Float): Float {
    return this.coerceIn(min, max)
}

@Composable
fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = NyghtSerifFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
