package com.example.andbook.ui.main

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import android.graphics.Bitmap
import android.net.Uri
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.example.andbook.Reader
import com.example.andbook.Settings
import com.example.andbook.data.*
import com.example.andbook.reader.BookCoverHelper
import com.example.andbook.reader.CbzParser
import com.example.andbook.reader.EpubParser
import com.example.andbook.reader.PdfRendererHelper
import com.example.andbook.theme.*
import com.example.andbook.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { DataRepository(context.applicationContext) }
    
    val settings by repository.settings.collectAsStateWithLifecycle()
    val history by repository.history.collectAsStateWithLifecycle()
    val library by repository.library.collectAsStateWithLifecycle()
    val scannedBooks by repository.scannedBooks.collectAsStateWithLifecycle()
    val isScanning by repository.isScanning.collectAsStateWithLifecycle()

    val systemFont = FontMap[settings.systemFontName] ?: NyghtSerifFontFamily

    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(MainTab.HISTORY) }
    val historyListState = rememberLazyListState()
    
    val collapseFraction by remember {
        derivedStateOf {
            if (currentTab == MainTab.HISTORY && historyListState.firstVisibleItemIndex == 0) {
                (historyListState.firstVisibleItemScrollOffset / 150f).coerceIn(0f, 1f)
            } else if (currentTab == MainTab.HISTORY) {
                1f
            } else {
                0f
            }
        }
    }
    
    var selectedHistoryItemForDetail by remember { mutableStateOf<HistoryItem?>(null) }
    var selectedFormatFilter by remember { mutableStateOf<BookFormat?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // Onboarding folder launcher
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

    val importBookLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                scope.launch {
                    val book = repository.getBookFromUri(uri)
                    val name = book.name.lowercase()
                    if (name.endsWith(".epub") || name.endsWith(".pdf") || name.endsWith(".cbz")) {
                        repository.addToLibrary(book)
                    } else {
                        android.widget.Toast.makeText(context, "Unsupported file format. Please choose an EPUB, PDF, or CBZ file.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Auto-scan on startup if folder is configured
    LaunchedEffect(settings.booksFolderUri) {
        if (settings.booksFolderUri != null && scannedBooks.isEmpty()) {
            repository.scanBooksFolder()
        }
    }

    // Filtered lists based on format chip
    val filteredHistory = remember(history, selectedFormatFilter) {
        if (selectedFormatFilter == null) history else history.filter { it.book.format == selectedFormatFilter }
    }
    val filteredLibrary = remember(library, selectedFormatFilter) {
        if (selectedFormatFilter == null) library else library.filter { it.format == selectedFormatFilter }
    }
    val filteredScanned = remember(scannedBooks, selectedFormatFilter) {
        if (selectedFormatFilter == null) scannedBooks else scannedBooks.filter { it.format == selectedFormatFilter }
    }

        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (settings.booksFolderUri == null && library.isEmpty()) {
                // --- Onboarding Flow ---
                OnboardingScreen(
                    onSelectFolder = { folderLauncher.launch(null) },
                    onImportFile = { importBookLauncher.launch(arrayOf("*/*")) }
                )
            } else {
                // --- Main Application Layout ---
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (currentTab != MainTab.QUOTES) {
                            // Top Editorial Header
                            HeaderBar(
                                booksFolderName = settings.booksFolderName ?: "Books Folder",
                                currentTab = currentTab,
                                isScanning = isScanning,
                                onScanRequested = { scope.launch { repository.scanBooksFolder() } },
                                onShowSettings = { onItemClick(Settings) },
                                onChangeFolder = { folderLauncher.launch(null) },
                                collapseFraction = collapseFraction,
                                onImportClick = { importBookLauncher.launch(arrayOf("*/*")) }
                            )

                            // Format Filter Chips
                            FilterChipsBar(
                                selectedFilter = selectedFormatFilter,
                                onFilterSelected = { selectedFormatFilter = it }
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                        } else {
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Content List based on active tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when (currentTab) {
                                MainTab.HISTORY -> {
                                    HistoryTab(
                                        historyList = filteredHistory,
                                        onCoverClick = { selectedHistoryItemForDetail = it },
                                        onTitleClick = { item -> onItemClick(Reader(item.book.uri)) },
                                        onDeleteBook = { uri -> scope.launch { repository.deleteFromHistory(uri) } },
                                        onClearAll = { showClearHistoryDialog = true },
                                        systemFont = systemFont,
                                        listState = historyListState
                                    )
                                }
                                MainTab.LIBRARY -> {
                                     LibraryTab(
                                         libraryList = filteredLibrary,
                                         onBookClick = { book -> onItemClick(Reader(book.uri)) },
                                         onDeleteBook = { book -> scope.launch { repository.removeFromLibrary(book.uri) } },
                                         onImportClick = { importBookLauncher.launch(arrayOf("*/*")) },
                                         systemFont = systemFont
                                     )
                                 }
                                 MainTab.BROWSE -> {
                                     BrowseTab(
                                         scannedBooks = filteredScanned,
                                         libraryList = library,
                                         hasFolderSelected = settings.booksFolderUri != null,
                                         onBookClick = { book -> onItemClick(Reader(book.uri)) },
                                         onToggleLibrary = { book, isSaved ->
                                             scope.launch {
                                                 if (isSaved) {
                                                     repository.removeFromLibrary(book.uri)
                                                 } else {
                                                     repository.addToLibrary(book)
                                                 }
                                             }
                                         },
                                         onSelectFolderClick = { folderLauncher.launch(null) },
                                         systemFont = systemFont
                                     )
                                }
                                MainTab.QUOTES -> {
                                    QuotesTab(
                                        repository = repository,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        // Bottom Spacer for Capsule Nav
                        Spacer(modifier = Modifier.height(90.dp))
                    }

                    // Floating Capsule Navigation (Thin, Icon-only)
                    FloatingCapsuleNavBar(
                        currentTab = currentTab,
                        onTabSelected = { currentTab = it },
                        onShowSettings = { onItemClick(Settings) },
                        onResumeLastBook = {
                            if (history.isNotEmpty()) {
                                onItemClick(Reader(history.first().book.uri))
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // --- Book Details Dialog ---
                selectedHistoryItemForDetail?.let { item ->
                    val isSaved = library.any { it.uri == item.book.uri }
                    BookDetailDialog(
                        item = item,
                        isSavedInLibrary = isSaved,
                        onToggleLibrary = {
                            scope.launch {
                                if (isSaved) {
                                    repository.removeFromLibrary(item.book.uri)
                                } else {
                                    repository.addToLibrary(item.book)
                                }
                            }
                        },
                        onDismiss = { selectedHistoryItemForDetail = null },
                        onResume = { onItemClick(Reader(item.book.uri)) },
                        onDeleteFromHistory = { scope.launch { repository.deleteFromHistory(item.book.uri) } },
                        systemFont = systemFont
                    )
                }

                // --- Clear History Dialog ---
                if (showClearHistoryDialog) {
                    ConfirmationDialog(
                        title = "Clear History",
                        message = "Are you sure you want to clear your recently read book history? This action cannot be undone.",
                        onConfirm = {
                            scope.launch { repository.clearHistory() }
                            showClearHistoryDialog = false
                        },
                        onDismiss = { showClearHistoryDialog = false },
                        systemFont = systemFont
                    )
                }
            }
        }
    }

// --- Custom Animated Book Component ---
@Composable
fun AnimatedBookLogo(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "book_page_flip")
    
    val pageAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "page_angle"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outline
    val backgroundColor = MaterialTheme.colorScheme.background

    Canvas(
        modifier = modifier
            .size(120.dp)
            .graphicsLayer {
                scaleX = breathingScale
                scaleY = breathingScale
            }
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val pageW = width * 0.42f
        val pageH = height * 0.72f
        val topY = height * 0.14f
        val bottomY = topY + pageH
        
        // Static Left Page
        val leftPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(centerX, topY)
            quadraticTo(centerX - pageW * 0.5f, topY - 10f, centerX - pageW, topY)
            lineTo(centerX - pageW, bottomY)
            quadraticTo(centerX - pageW * 0.5f, bottomY - 10f, centerX, bottomY)
            close()
        }
        drawPath(leftPath, color = primaryColor.copy(alpha = 0.05f))
        drawPath(leftPath, color = outlineColor, style = Stroke(width = 3f))

        // Static Right Page
        val rightPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(centerX, topY)
            quadraticTo(centerX + pageW * 0.5f, topY - 10f, centerX + pageW, topY)
            lineTo(centerX + pageW, bottomY)
            quadraticTo(centerX + pageW * 0.5f, bottomY - 10f, centerX, bottomY)
            close()
        }
        drawPath(rightPath, color = primaryColor.copy(alpha = 0.05f))
        drawPath(rightPath, color = outlineColor, style = Stroke(width = 3f))

        // Spine
        drawLine(
            color = tertiaryColor,
            start = Offset(centerX, topY - 8f),
            end = Offset(centerX, bottomY + 8f),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )

        // Turning Page
        val angleRad = Math.toRadians(pageAngle.toDouble())
        val cosW = Math.cos(angleRad).toFloat()
        
        if (pageAngle > 0f && pageAngle < 180f) {
            val turningPagePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX, topY)
                quadraticTo(centerX + pageW * cosW * 0.5f, topY - 10f - (12f * Math.sin(angleRad).toFloat()), centerX + pageW * cosW, topY - (8f * Math.sin(angleRad).toFloat()))
                lineTo(centerX + pageW * cosW, bottomY - (8f * Math.sin(angleRad).toFloat()))
                quadraticTo(centerX + pageW * cosW * 0.5f, bottomY - 10f - (12f * Math.sin(angleRad).toFloat()), centerX, bottomY)
                close()
            }
            
            drawPath(turningPagePath, color = backgroundColor)
            drawPath(
                turningPagePath, 
                color = tertiaryColor.copy(alpha = 0.8f), 
                style = Stroke(width = 2.5f)
            )
        }
    }
}

// --- Onboarding Screen ---
@Composable
fun OnboardingScreen(onSelectFolder: () -> Unit, onImportFile: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1B1613) // Exact video background color
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // The 4K 120 FPS video animation container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF332A24), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val uri = Uri.parse("android.resource://${ctx.packageName}/${com.example.andbook.R.raw.onboarding_animation}")
                            setVideoURI(uri)
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setOnPreparedListener { mediaPlayer ->
                                mediaPlayer.isLooping = true
                                mediaPlayer.start()
                            }
                        }
                    }
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Select Folder Button (Cocoa Dark)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1F1511))
                        .border(1.dp, Color(0xFF332A24), RoundedCornerShape(4.dp))
                        .clickable { onSelectFolder() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color(0xFFEFE6DD),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Books Directory",
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEFE6DD)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Import Single File Button (Neutral Outlined)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF241D1A)) // Dark warm espresso card matching theme
                        .border(1.dp, Color(0xFF332A24), RoundedCornerShape(4.dp))
                        .clickable { onImportFile() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFFE6DFD5),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Import Book File",
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6DFD5)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}


// --- Header Bar ---
@Composable
fun HeaderBar(
    booksFolderName: String,
    currentTab: MainTab,
    isScanning: Boolean,
    onScanRequested: () -> Unit,
    onShowSettings: () -> Unit,
    onChangeFolder: () -> Unit,
    collapseFraction: Float = 0f,
    onImportClick: (() -> Unit)? = null
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600

    val greetingAnim = remember { Animatable(0f) }
    val subtitleAnim = remember { Animatable(0f) }
    val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

    LaunchedEffect(currentTab) {
        if (currentTab == MainTab.HISTORY) {
            greetingAnim.snapTo(0f)
            subtitleAnim.snapTo(0f)
            launch {
                greetingAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 600, easing = EaseOutCubic)
                )
            }
            launch {
                delay(120)
                subtitleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 600, easing = EaseOutCubic)
                )
            }
        }
    }

    val paddingTop = (24 - 16 * collapseFraction).dp
    val paddingBottom = (12 - 6 * collapseFraction).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = paddingTop, start = 24.dp, end = 24.dp, bottom = paddingBottom),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val sectionText = when (currentTab) {
                MainTab.HISTORY -> "— &BOOK / VOL. 01 ·"
                MainTab.LIBRARY -> "— &BOOK / LIBRARY ·"
                MainTab.BROWSE -> "— &BOOK / BROWSE ·"
                else -> "— &BOOK ·"
            }
            
            Text(
                text = sectionText,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = if (isTablet) 13.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (currentTab == MainTab.HISTORY) {
                val startSize = if (isTablet) 56f else 44f
                val endSize = if (isTablet) 32f else 26f
                val fontSize = (startSize - (startSize - endSize) * collapseFraction).sp

                Text(
                    text = androidx.compose.ui.text.buildAnnotatedString {
                        append("Hey, ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.tertiary))
                        append("Reader!")
                        pop()
                    },
                    fontFamily = NyghtSerifFontFamily,
                    fontSize = fontSize,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer {
                        alpha = greetingAnim.value
                        translationY = 12.dp.toPx() * (1f - greetingAnim.value)
                    }
                )

                val subtitleAlpha = (1f - collapseFraction) * subtitleAnim.value
                val baseHeight = if (isTablet) 42.dp else 32.dp
                val subtitleHeight = baseHeight * (1f - collapseFraction)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(subtitleHeight)
                        .graphicsLayer {
                            alpha = subtitleAlpha
                            translationY = 10.dp.toPx() * (1f - subtitleAnim.value)
                        }
                        .padding(top = (4 * (1f - collapseFraction)).dp)
                        .clipToBounds(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = androidx.compose.ui.text.buildAnnotatedString {
                            append("What will you read ")
                            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground))
                            append("today?")
                            pop()
                        },
                        fontFamily = NyghtSerifFontFamily,
                        fontSize = if (isTablet) 32.sp else 24.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            } else {
                val tabTitle = when (currentTab) {
                    MainTab.LIBRARY -> "My Library"
                    MainTab.BROWSE -> "Browse Files"
                    else -> ""
                }
                Text(
                    text = tabTitle,
                    fontFamily = NyghtSerifFontFamily,
                    fontSize = if (isTablet) 36.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (currentTab == MainTab.BROWSE) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onChangeFolder() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Change source folder",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Source: $booksFolderName",
                            fontFamily = InterFontFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onScanRequested, modifier = Modifier.size(36.dp)) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        if (currentTab == MainTab.LIBRARY && onImportClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onImportClick() }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import book file",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Import",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

// --- Scrolling Filter Chips Bar ---
@Composable
fun FilterChipsBar(
    selectedFilter: BookFormat?,
    onFilterSelected: (BookFormat?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChipItem(
            text = "All",
            isSelected = selectedFilter == null,
            onClick = { onFilterSelected(null) }
        )
        FilterChipItem(
            text = "EPUB",
            isSelected = selectedFilter == BookFormat.EPUB,
            onClick = { onFilterSelected(BookFormat.EPUB) }
        )
        FilterChipItem(
            text = "PDF",
            isSelected = selectedFilter == BookFormat.PDF,
            onClick = { onFilterSelected(BookFormat.PDF) }
        )
        FilterChipItem(
            text = "CBZ",
            isSelected = selectedFilter == BookFormat.CBZ,
            onClick = { onFilterSelected(BookFormat.CBZ) }
        )
    }
}

@Composable
fun FilterChipItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        Color(0xFF1F1511) // Dark cocoa
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        Color(0xFFEFE6DD) // Light foam cream
    } else {
        MaterialTheme.colorScheme.primary
    }
    val borderModifier = if (isSelected) {
        Modifier.border(1.dp, Color(0xFF1F1511), RoundedCornerShape(4.dp))
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .then(borderModifier)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = contentColor
        )
    }
}

// --- Sub-Tabs (History & Library as Vertical/Grid Layouts) ---

fun getRelativeTimeSpanString(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 0) return "Just now"
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "$minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
        hours < 24 -> "$hours ${if (hours == 1L) "hour" else "hours"} ago"
        days < 7 -> "$days ${if (days == 1L) "day" else "days"} ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

@Composable
fun HistoryBookRow(
    item: HistoryItem,
    onCoverClick: () -> Unit,
    onTitleClick: () -> Unit,
    onDelete: () -> Unit,
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily
) {
    val context = LocalContext.current
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(item.book.uri) {
        coverBitmap = withContext(Dispatchers.IO) {
            try {
                BookCoverHelper.getCover(context, item.book)
            } catch (e: Exception) {
                null
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTitleClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flat outlined small cover image
        Box(
            modifier = Modifier
                .size(72.dp, 108.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .clickable { onCoverClick() },
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap!!.asImageBitmap(),
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Book, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Book Details
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.book.title,
                fontFamily = systemFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = item.book.author,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Progress Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val progress = item.progress
                val pct = (progress.lastPage.toFloat() / (progress.totalPages - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                
                Text(
                    text = "${(pct * 100).toInt()}% read",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )

                Text(
                    text = "·",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                Text(
                    text = getRelativeTimeSpanString(progress.lastReadTime),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove history",
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun HistoryTab(
    historyList: List<HistoryItem>,
    onCoverClick: (HistoryItem) -> Unit,
    onTitleClick: (HistoryItem) -> Unit,
    onDeleteBook: (String) -> Unit,
    onClearAll: () -> Unit,
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    if (historyList.isEmpty()) {
        EmptyStateMessage("No reading history yet. Start reading from the Browse tab!", systemFont = systemFont)
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "/ HISTORY / RECENTS",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = "Recently Read",
                            fontFamily = systemFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                    Text(
                        text = "Clear All",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { onClearAll() }
                    )
                }
            }
            items(historyList, key = { it.book.uri }) { item ->
                HistoryBookRow(
                    item = item,
                    onCoverClick = { onCoverClick(item) },
                    onTitleClick = { onTitleClick(item) },
                    onDelete = { onDeleteBook(item.book.uri) },
                    systemFont = systemFont
                )
            }
        }
    }
}

@Composable
fun LibraryTab(
    libraryList: List<Book>,
    onBookClick: (Book) -> Unit,
    onDeleteBook: (Book) -> Unit,
    onImportClick: () -> Unit,
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily
) {
    if (libraryList.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmptyStateMessage(
                message = "Your library is empty. Save books from the Browse tab or import books manually!",
                modifier = Modifier.wrapContentSize(),
                systemFont = systemFont
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1F1511))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .clickable { onImportClick() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFFEFE6DD),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import Book File",
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEFE6DD)
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(libraryList, key = { it.uri }) { book ->
                    LibraryBookCard(
                        book = book,
                        onBookClick = { onBookClick(book) },
                        onDelete = { onDeleteBook(book) },
                        systemFont = systemFont
                    )
                }
            }
        }
    }
}

@Composable
fun BrowseTab(
    scannedBooks: List<Book>,
    libraryList: List<Book>,
    hasFolderSelected: Boolean,
    onBookClick: (Book) -> Unit,
    onToggleLibrary: (Book, Boolean) -> Unit,
    onSelectFolderClick: () -> Unit,
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily
) {
    if (!hasFolderSelected) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmptyStateMessage(
                message = "No books directory selected. You can select a folder to automatically scan and import all your books.",
                modifier = Modifier.wrapContentSize(),
                systemFont = systemFont
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1F1511))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .clickable { onSelectFolderClick() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFFEFE6DD),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Select Books Directory",
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEFE6DD)
                    )
                }
            }
        }
    } else if (scannedBooks.isEmpty()) {
        EmptyStateMessage("No books found in this folder. Make sure it contains .epub, .pdf, or .cbz files!", systemFont = systemFont)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(scannedBooks, key = { it.uri }) { book ->
                val isSaved = libraryList.any { it.uri == book.uri }
                BrowseBookRow(
                    book = book,
                    isSaved = isSaved,
                    onBookClick = { onBookClick(book) },
                    onToggleLibrary = { onToggleLibrary(book, isSaved) },
                    systemFont = systemFont
                )
            }
        }
    }
}

// --- Sub-Cards / Sub-Rows ---

@Composable
fun HistoryBookCard(
    item: HistoryItem,
    onCoverClick: () -> Unit,
    onTitleClick: () -> Unit,
    onDelete: () -> Unit,
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily
) {
    val context = LocalContext.current
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(item.book.uri) {
        coverBitmap = withContext(Dispatchers.IO) {
            try {
                BookCoverHelper.getCover(context, item.book)
            } catch (e: Exception) {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onTitleClick() }
    ) {
        // Flat outlined cover image
        Box(
            modifier = Modifier
                .size(140.dp, 210.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .clickable { onCoverClick() },
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap!!.asImageBitmap(),
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Book, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title
        Text(
            text = item.book.title,
            fontFamily = systemFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Author
        Text(
            text = item.book.author,
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Progress Details (Typewriter style)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            val progress = item.progress
            val pct = (progress.lastPage.toFloat() / (progress.totalPages - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            
            Text(
                text = "${(pct * 100).toInt()}% read",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove history",
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun LibraryBookCard(
    book: Book,
    onBookClick: () -> Unit,
    onDelete: () -> Unit,
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily
) {
    val context = LocalContext.current
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(book.uri) {
        coverBitmap = withContext(Dispatchers.IO) {
            try {
                BookCoverHelper.getCover(context, book)
            } catch (e: Exception) {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBookClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap!!.asImageBitmap(),
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Book, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title
        Text(
            text = book.title,
            fontFamily = systemFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Author
        Text(
            text = book.author,
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = book.format.name,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete book",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun BrowseBookRow(
    book: Book,
    isSaved: Boolean,
    onBookClick: () -> Unit,
    onToggleLibrary: () -> Unit,
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat!
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBookClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (book.format) {
                BookFormat.PDF -> Icons.Default.PictureAsPdf
                BookFormat.EPUB -> Icons.Default.MenuBook
                BookFormat.CBZ -> Icons.Default.PhotoLibrary
            }
            Icon(
                imageVector = icon,
                contentDescription = book.format.name,
                tint = MaterialTheme.colorScheme.tertiary, // Caramel Gold accent
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontFamily = systemFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleLibrary, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.LibraryAddCheck else Icons.Default.LibraryAdd,
                    contentDescription = "Library Status",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyStateMessage(
    message: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily
) {
    Box(
        modifier = modifier
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontFamily = systemFont,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 26.sp
        )
    }
}

// --- Settings Dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onUpdateTheme: (ReaderTheme) -> Unit,
    onUpdateFont: (String) -> Unit,
    onUpdateFontSize: (Float) -> Unit,
    onChangeFolder: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column {
                    Text(
                        text = "/ SYSTEM / CONFIG",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = "Settings",
                        fontFamily = NyghtSerifFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Theme selection
                Text(
                    text = "Theme Mode",
                    fontFamily = NyghtSerifFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeButton(
                        text = "Light Coffee",
                        isSelected = settings.theme == ReaderTheme.LIGHT_COFFEE,
                        onClick = { onUpdateTheme(ReaderTheme.LIGHT_COFFEE) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeButton(
                        text = "Dark Coffee",
                        isSelected = settings.theme == ReaderTheme.DARK_COFFEE,
                        onClick = { onUpdateTheme(ReaderTheme.DARK_COFFEE) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeButton(
                        text = "Amoled",
                        isSelected = settings.theme == ReaderTheme.AMOLED,
                        onClick = { onUpdateTheme(ReaderTheme.AMOLED) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Font selection
                Text(
                    text = "Default EPUB Font",
                    fontFamily = NyghtSerifFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                var expandedFontDropdown by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedFontDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
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
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        FontMap.keys.forEach { fontKey ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = FontDisplayNameMap[fontKey] ?: fontKey,
                                        fontFamily = FontMap[fontKey] ?: InterFontFamily,
                                        fontSize = 16.sp
                                    )
                                },
                                onClick = {
                                    onUpdateFont(fontKey)
                                    expandedFontDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Font Size selection
                Text(
                    text = "Default Font Size: ${settings.fontSize.toInt()}sp",
                    fontFamily = NyghtSerifFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Slider(
                    value = settings.fontSize,
                    onValueChange = onUpdateFontSize,
                    valueRange = 14f..38f,
                    steps = 12,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                        thumbColor = MaterialTheme.colorScheme.tertiary
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Actions divider
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onChangeFolder,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change Books Folder", fontFamily = InterFontFamily, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", fontFamily = InterFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ThemeButton(
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
            .padding(horizontal = 4.dp, vertical = 10.dp),
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

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "/ SYSTEM / CONFIRM",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = title,
                        fontFamily = systemFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    fontFamily = InterFontFamily,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel", fontFamily = InterFontFamily, fontSize = 14.sp)
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Clear All", fontFamily = InterFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

