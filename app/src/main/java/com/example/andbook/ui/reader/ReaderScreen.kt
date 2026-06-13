package com.example.andbook.ui.reader

import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.border
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.andbook.data.*
import com.example.andbook.reader.*
import com.example.andbook.theme.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
@Composable
fun rememberTime(use12Hour: Boolean): String {
    var time by remember { mutableStateOf("") }
    LaunchedEffect(use12Hour) {
        val pattern = if (use12Hour) "h:mm a" else "HH:mm"
        val formatter = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        while (true) {
            time = formatter.format(java.util.Date())
            kotlinx.coroutines.delay(10000) // update every 10s
        }
    }
    return time
}

@Composable
fun rememberBatteryLevel(context: Context): String {
    var batteryPercent by remember { mutableStateOf("--") }
    LaunchedEffect(Unit) {
        val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        while (true) {
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                batteryPercent = "${(level * 100 / scale.toFloat()).toInt()}%"
            }
            kotlinx.coroutines.delay(30000) // update every 30s
        }
    }
    return batteryPercent
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookUri: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { DataRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val settings by repository.settings.collectAsStateWithLifecycle()
    val history by repository.history.collectAsStateWithLifecycle()
    val highlights by repository.highlights.collectAsStateWithLifecycle()

    val systemFont = remember(settings.systemFontName) {
        FontMap[settings.systemFontName] ?: NyghtSerifFontFamily
    }

    var book by remember { mutableStateOf<Book?>(null) }
    var epubBook by remember { mutableStateOf<EpubBook?>(null) }
    var cbzPages by remember { mutableStateOf<List<String>>(emptyList()) }
    var pdfPageCount by remember { mutableStateOf(0) }
    var isLoadingBook by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var epubChapterIndex by remember { mutableStateOf(0) }
    var epubChapterPages by remember { mutableStateOf<List<String>>(emptyList()) }
    var epubChapterTitle by remember { mutableStateOf("") }
    var isPaginating by remember { mutableStateOf(false) }
    var showTocDialog by remember { mutableStateOf(false) }
    var showFontSettingsDialog by remember { mutableStateOf(false) }
    var pendingAnchorId by remember { mutableStateOf<String?>(null) }


    // TTS state
    val isTtsActive by TtsService.isServiceRunning.collectAsStateWithLifecycle()
    val isTtsPlaying by TtsService.isPlaying.collectAsStateWithLifecycle()
    val ttsCurrentWordRange by TtsService.currentWordRange.collectAsStateWithLifecycle()
    var showTtsBanner by remember { mutableStateOf(false) }

    LaunchedEffect(isTtsActive) {
        if (isTtsActive) {
            showTtsBanner = true
        }
    }

    // Navigation and layout parameters
    var showControls by remember { mutableStateOf(false) }
    var isSelectionActive by remember { mutableStateOf(false) }
    var isPagerZoomed by remember { mutableStateOf(false) }
    
    val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    val statusBarHeightDp = remember {
        if (resourceId > 0) {
            val px = context.resources.getDimensionPixelSize(resourceId)
            (px / context.resources.displayMetrics.density).dp
        } else {
            24.dp
        }
    }
    
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val view = androidx.compose.ui.platform.LocalView.current
    val window = (context as? android.app.Activity)?.window

    val activeThemeColor = MaterialTheme.colorScheme.background
    val isLightTheme = settings.theme == ReaderTheme.LIGHT_COFFEE

    // Hide status bar when controls are hidden, set correct theme color when shown
    LaunchedEffect(showControls, settings.theme) {
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showControls) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                window.statusBarColor = activeThemeColor.toArgb()
                insetsController.isAppearanceLightStatusBars = isLightTheme
            } else {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
        }
    }

    // Restore status bar on exit
    DisposableEffect(Unit) {
        onDispose {
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // Daily and Total reading time tracking
    LaunchedEffect(Unit) {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        while (true) {
            kotlinx.coroutines.delay(5000) // update every 5 seconds
            val todayStr = dateFormat.format(java.util.Date())
            repository.updateSettings { currentSettings ->
                val isNewDay = currentSettings.lastReadDateStr != todayStr
                val todayProgress = if (isNewDay) 5L else currentSettings.readingTimeTodaySeconds + 5L
                
                val updatedHistory = currentSettings.dailyHistory.toMutableMap()
                updatedHistory[todayStr] = todayProgress
                if (updatedHistory.size > 30) {
                    val sortedKeys = updatedHistory.keys.sorted()
                    for (i in 0 until (sortedKeys.size - 30)) {
                        updatedHistory.remove(sortedKeys[i])
                    }
                }

                currentSettings.copy(
                    readingTimeTodaySeconds = todayProgress,
                    totalReadingTimeSeconds = currentSettings.totalReadingTimeSeconds + 5L,
                    lastReadDateStr = todayStr,
                    dailyHistory = updatedHistory
                )
            }
        }
    }

    // Load book metadata and structure
    LaunchedEffect(bookUri) {
        isLoadingBook = true
        loadError = null
        epubBook = null
        epubChapterPages = emptyList()
        epubChapterTitle = ""
        epubChapterIndex = 0
        try {
            withContext(Dispatchers.IO) {
                val historyItem = history.firstOrNull { it.book.uri == bookUri }
                var resolvedBook = historyItem?.book 
                    ?: repository.library.value.firstOrNull { it.uri == bookUri } 
                    ?: repository.scannedBooks.value.firstOrNull { it.uri == bookUri }
                
                if (resolvedBook == null) {
                    resolvedBook = getBookFromUri(context, bookUri)
                }
                
                book = resolvedBook
                when (resolvedBook.format) {
                    BookFormat.PDF -> {
                        val count = PdfRendererHelper.getPageCount(context, resolvedBook.uri)
                        if (count <= 0) throw Exception("PDF file has 0 pages or is unreadable")
                        pdfPageCount = count
                    }
                    BookFormat.CBZ -> {
                        val pages = CbzParser.getPages(context, resolvedBook.uri)
                        if (pages.isEmpty()) throw Exception("CBZ archive contains no readable images")
                        cbzPages = pages
                    }
                    BookFormat.EPUB -> {
                        epubBook = EpubParser.parse(context, Uri.parse(resolvedBook.uri))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loadError = e.localizedMessage ?: "Failed to read file contents"
        } finally {
            isLoadingBook = false
        }
    }


    // Clean up and restore status bar
    DisposableEffect(Unit) {
        onDispose {
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
            TtsService.stopPlayback()
        }
    }

    BackHandler {
        TtsService.stopPlayback()
        onBack()
    }

    val activeBook = book

    if (isLoadingBook || activeBook == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
        }
        return
    }

    if (loadError != null) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                     Text(
                        text = "Unable to open book",
                        fontFamily = NyghtSerifFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = loadError ?: "The file format may be unsupported, corrupted, or permission was denied by the system.",
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1F1511))
                            .border(1.dp, Color(0xFF1F1511), RoundedCornerShape(12.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Go Back", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, color = Color(0xFFEFE6DD))
                    }
                }
            }
        return
    }

    // Fetch last read position from history using stable coordinates
    val initialPageInfo = remember(bookUri, history, epubBook) {
        val historyItem = history.firstOrNull { it.book.uri == bookUri }
        val lastPageSaved = historyItem?.progress?.lastPage ?: 0
        val lastCharOffsetSaved = historyItem?.progress?.lastCharOffset ?: 0
        val chaptersCount = epubBook?.chapters?.size ?: 0
        
        if (epubBook != null) {
            if (lastPageSaved < chaptersCount && lastCharOffsetSaved >= 0) {
                Pair(lastPageSaved, lastCharOffsetSaved)
            } else {
                // Fallback from old legacy global page coordinate
                val totalPagesSaved = historyItem?.progress?.totalPages ?: 1
                val ratio = lastPageSaved.toFloat() / totalPagesSaved.coerceAtLeast(1).toFloat()
                val estimatedCh = (ratio * chaptersCount).toInt().coerceIn(0, (chaptersCount - 1).coerceAtLeast(0))
                Pair(estimatedCh, 0)
            }
        } else {
            Pair(0, 0)
        }
    }
    
    val initialChapterIndex = initialPageInfo.first
    val initialPageIndexInChapter = initialPageInfo.second

    LaunchedEffect(epubBook) {
        if (epubBook != null) {
            epubChapterIndex = initialChapterIndex
        }
    }


        val customTextToolbar = remember {
            object : TextToolbar {
                override fun showMenu(
                    rect: Rect,
                    onCopyRequested: (() -> Unit)?,
                    onPasteRequested: (() -> Unit)?,
                    onCutRequested: (() -> Unit)?,
                    onSelectAllRequested: (() -> Unit)?
                ) {
                    // Do nothing to completely block the system popups
                }

                override fun hide() {
                    // Do nothing
                }

                override val status: TextToolbarStatus
                    get() = TextToolbarStatus.Hidden
            }
        }

        CompositionLocalProvider(LocalTextToolbar provides customTextToolbar) {
            BoxWithConstraints(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
            val widthPx = with(density) { (maxWidth - 48.dp).roundToPx() }
            val heightPx = with(density) { (maxHeight - 176.dp - statusBarHeightDp).roundToPx() }

            val textStyle = TextStyle(
                fontFamily = FontMap[settings.fontName] ?: LoraFontFamily,
                fontSize = settings.fontSize.sp,
                lineHeight = (settings.fontSize * settings.readerLineHeight).sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = when (settings.readerTextAlignment) {
                    1 -> androidx.compose.ui.text.style.TextAlign.Left
                    2 -> androidx.compose.ui.text.style.TextAlign.Center
                    else -> androidx.compose.ui.text.style.TextAlign.Justify
                }
            )

            val headerFontSize = when {
                maxWidth > 800.dp -> 36.sp
                maxWidth > 600.dp -> 32.sp
                maxWidth > 400.dp -> 28.sp
                else -> 24.sp
            }

            val isTablet = maxWidth > 600.dp
            val placeholderButtonFontSize = if (isTablet) 18.sp else 12.sp
            val placeholderButtonPadding = if (isTablet) Modifier.padding(horizontal = 28.dp, vertical = 16.dp) else Modifier.padding(horizontal = 16.dp, vertical = 10.dp)

            // EPUB Pagination: Paginate ONLY the current chapter (takes < 50ms!)
            LaunchedEffect(epubBook, epubChapterIndex, settings.fontName, settings.fontSize, settings.readerLineHeight, settings.readerTextAlignment, widthPx, heightPx) {
                val currentBook = epubBook
                if (currentBook == null || widthPx <= 0 || heightPx <= 0) {
                    epubChapterPages = emptyList()
                    return@LaunchedEffect
                }
                isPaginating = true
                try {
                    val ch = currentBook.chapters[epubChapterIndex]
                    epubChapterTitle = ch.title
                    
                    // Lazy load chapter content if empty
                    val rawContent = if (ch.content.isEmpty() && ch.zipPath.isNotEmpty()) {
                        val result = withContext(Dispatchers.IO) {
                            EpubParser.loadChapterContent(context, Uri.parse(activeBook.uri), ch.zipPath)
                        }
                        ch.content = result.content.replace("\r", "")
                        ch.links = result.links
                        ch.anchors = result.anchors
                        ch.content
                    } else {
                        ch.content.replace("\r", "")
                    }
                    val content = rawContent
                    
                    val pages = withContext(Dispatchers.Default) {
                        BookPaginator.paginate(content, textMeasurer, textStyle, widthPx, heightPx)
                    }
                    val rawPages = if (pages.isEmpty()) listOf(content) else pages.map { it.replace("\r", "") }
                    epubChapterPages = if (epubChapterIndex == 0 && currentBook.cover != null) {
                        if (content.trim().isEmpty()) {
                            listOf("[COVER_IMAGE]")
                        } else {
                            listOf("[COVER_IMAGE]") + rawPages
                        }
                    } else {
                        rawPages
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val ch = currentBook.chapters[epubChapterIndex]
                    val sanitizedContent = ch.content.replace("\r", "")
                    epubChapterPages = if (epubChapterIndex == 0 && currentBook.cover != null) {
                        listOf("[COVER_IMAGE]")
                    } else {
                        listOf(sanitizedContent)
                    }
                } finally {
                    isPaginating = false
                }
            }

            val pageOffsets = remember(epubChapterPages, epubChapterIndex, epubBook) {
                val currentBook = epubBook
                if (currentBook == null || epubChapterIndex >= currentBook.chapters.size || epubChapterPages.isEmpty()) {
                    emptyList()
                } else {
                    val ch = currentBook.chapters[epubChapterIndex]
                    var searchIndex = 0
                    epubChapterPages.map { pageText ->
                        if (pageText == "[COVER_IMAGE]") {
                            Pair(-1, -1)
                        } else {
                            val start = ch.content.indexOf(pageText, searchIndex)
                            if (start != -1) {
                                searchIndex = start + pageText.length
                                Pair(start, searchIndex)
                            } else {
                                Pair(-1, -1)
                            }
                        }
                    }
                }
            }

            val epubPagerPageCount = remember(epubChapterPages, epubChapterIndex, epubBook) {
                val chaptersCount = epubBook?.chapters?.size ?: 0
                var count = epubChapterPages.size
                if (epubChapterIndex > 0) count++
                if (epubChapterIndex < chaptersCount - 1) count++
                count
            }

            val finalPageCount = when (activeBook.format) {
                BookFormat.PDF -> pdfPageCount
                BookFormat.CBZ -> cbzPages.size
                BookFormat.EPUB -> epubPagerPageCount
            }

            val pagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { finalPageCount }
            )

            LaunchedEffect(pagerState.currentPage) {
                isPagerZoomed = false
            }

            // Initialize TTS service when requested
            val startTtsEngine: () -> Unit = {
                val actualPageIndex = if (epubChapterIndex > 0) pagerState.currentPage - 1 else pagerState.currentPage
                val currentPageText = (epubChapterPages.getOrNull(actualPageIndex) ?: "").replace("\r", "")
                TtsService.start(
                    context = context,
                    bookUri = bookUri,
                    bookTitle = activeBook.title,
                    chapterTitle = epubChapterTitle,
                    pageText = currentPageText,
                    speed = settings.ttsSpeed,
                    pitch = settings.ttsPitch,
                    cover = epubBook?.cover
                )
                showTtsBanner = true
            }

            var lastLoadedChapterIndex by remember { mutableStateOf(-1) }
            var transitionDirection by remember { mutableStateOf(0) } // -1 for prev, 1 for next, 0 for initial
            var hasScrolledToInitialPage by remember(bookUri) { mutableStateOf(false) }

            // Scroll to target chapter page when pages load
            LaunchedEffect(epubChapterPages) {
                if (activeBook.format == BookFormat.EPUB && epubChapterPages.isNotEmpty()) {
                    val anchorId = pendingAnchorId
                    if (anchorId != null) {
                        val targetOffset = epubBook?.chapters?.getOrNull(epubChapterIndex)?.anchors?.get(anchorId) ?: 0
                        val targetPage = pageOffsets.indexOfFirst { (start, end) ->
                            targetOffset in start..end
                        }
                        val finalScrollPage = if (targetPage != -1) {
                            if (epubChapterIndex > 0) targetPage + 1 else targetPage
                        } else {
                            if (epubChapterIndex > 0) 1 else 0
                        }
                        pagerState.scrollToPage(finalScrollPage.coerceIn(0, epubPagerPageCount - 1))
                        pendingAnchorId = null
                        lastLoadedChapterIndex = epubChapterIndex
                        hasScrolledToInitialPage = true
                        transitionDirection = 0
                    } else if (lastLoadedChapterIndex == -1) {
                        val targetPage = if (epubChapterIndex > 0) initialPageIndexInChapter + 1 else initialPageIndexInChapter
                        pagerState.scrollToPage(targetPage.coerceIn(0, epubPagerPageCount - 1))
                        lastLoadedChapterIndex = epubChapterIndex
                        hasScrolledToInitialPage = true
                    } else if (transitionDirection == 1) {
                        val targetPage = if (epubChapterIndex > 0) 1 else 0
                        pagerState.scrollToPage(targetPage)
                        lastLoadedChapterIndex = epubChapterIndex
                        transitionDirection = 0
                    } else if (transitionDirection == -1) {
                        val targetPage = if (epubChapterIndex > 0) epubChapterPages.size else epubChapterPages.size - 1
                        pagerState.scrollToPage(targetPage)
                        lastLoadedChapterIndex = epubChapterIndex
                        transitionDirection = 0
                    }
                } else if (activeBook.format != BookFormat.EPUB && finalPageCount > 0 && !hasScrolledToInitialPage) {
                    // Non-EPUB formats use normal scrolling
                    val historyItem = history.firstOrNull { it.book.uri == bookUri }
                    val initialPage = historyItem?.progress?.lastPage ?: 0
                    pagerState.scrollToPage(initialPage.coerceIn(0, finalPageCount - 1))
                    hasScrolledToInitialPage = true
                }
            }

            // Update reading history as user turns pages
            LaunchedEffect(pagerState.currentPage, epubChapterPages, epubChapterIndex, hasScrolledToInitialPage) {
                if (hasScrolledToInitialPage) {
                    when (activeBook.format) {
                        BookFormat.PDF, BookFormat.CBZ -> {
                            if (finalPageCount > 0) {
                                repository.updateBookProgress(activeBook) { currentProgress ->
                                    ReadingProgress(
                                        bookUri = activeBook.uri,
                                        lastPage = pagerState.currentPage,
                                        totalPages = finalPageCount,
                                        lastReadTime = System.currentTimeMillis()
                                    )
                                }
                            }
                        }
                        BookFormat.EPUB -> {
                            if (epubChapterPages.isNotEmpty() && !isPaginating) {
                                val actualPageIndex = if (epubChapterIndex > 0) pagerState.currentPage - 1 else pagerState.currentPage
                                val totalChapters = epubBook?.chapters?.size ?: 1
                                if (actualPageIndex in epubChapterPages.indices) {
                                    repository.updateBookProgress(activeBook) { currentProgress ->
                                        ReadingProgress(
                                            bookUri = activeBook.uri,
                                            lastPage = epubChapterIndex,
                                            lastCharOffset = actualPageIndex,
                                            totalPages = totalChapters,
                                            lastReadTime = System.currentTimeMillis()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sync current page with TTS service on page or active text change
            LaunchedEffect(pagerState.currentPage, epubChapterIndex, epubChapterPages, isTtsActive) {
                if (isTtsActive && activeBook.format == BookFormat.EPUB && epubChapterPages.isNotEmpty()) {
                    val actualPageIndex = if (epubChapterIndex > 0) pagerState.currentPage - 1 else pagerState.currentPage
                    val currentPageText = (epubChapterPages.getOrNull(actualPageIndex) ?: "").replace("\r", "")
                    TtsService.updateTrackInfo(
                        bookUri = bookUri,
                        bookTitle = activeBook.title,
                        chapterTitle = epubChapterTitle,
                        pageText = currentPageText
                    )
                }
            }

            // Sync TTS speed from app settings to the service (avoiding loops)
            LaunchedEffect(settings.ttsSpeed) {
                if (isTtsActive && TtsService.ttsSpeed.value != settings.ttsSpeed) {
                    TtsService.instance?.updateSpeed(settings.ttsSpeed)
                }
            }

            // Listen to control actions from the background service
            LaunchedEffect(Unit) {
                TtsService.controlActions.collect { action ->
                    when (action) {
                        is TtsControlAction.SkipNext -> {
                            val chaptersCount = epubBook?.chapters?.size ?: 0
                            if (pagerState.currentPage < epubPagerPageCount - 1) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            } else if (settings.autoPlayNextChapter && epubChapterIndex < chaptersCount - 1) {
                                isPaginating = true
                                transitionDirection = 1
                                epubChapterIndex++
                            } else {
                                TtsService.instance?.pauseSpeaking()
                            }
                        }
                        is TtsControlAction.SkipPrev -> {
                            if (pagerState.currentPage > 0) {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            } else if (epubChapterIndex > 0) {
                                isPaginating = true
                                transitionDirection = -1
                                epubChapterIndex--
                            }
                        }
                        is TtsControlAction.SetSpeed -> {
                            repository.updateSettings { it.copy(ttsSpeed = action.speed) }
                        }
                        else -> {}
                    }
                }
            }

            // Helper to seek to a specific progress in the current chapter
            val seekToChapterProgress: (Float) -> Unit = { progress ->
                if (epubChapterPages.isNotEmpty()) {
                    val totalLength = epubChapterPages.sumOf { it.length }
                    val targetChar = (progress * totalLength).toInt().coerceIn(0, totalLength)
                    
                    var accumulated = 0
                    var targetPage = 0
                    var offsetInPage = 0
                    for (i in epubChapterPages.indices) {
                        val len = epubChapterPages[i].length
                        if (accumulated + len >= targetChar) {
                            targetPage = i
                            offsetInPage = targetChar - accumulated
                            break
                        }
                        accumulated += len
                        if (i == epubChapterPages.lastIndex) {
                            targetPage = i
                            offsetInPage = len
                        }
                    }
                    
                    val targetPagerPage = if (epubChapterIndex > 0) targetPage + 1 else targetPage
                    scope.launch {
                        pagerState.scrollToPage(targetPagerPage)
                        val currentPageText = epubChapterPages[targetPage].replace("\r", "")
                        TtsService.updateTrackInfo(
                            bookUri = bookUri,
                            bookTitle = activeBook.title,
                            chapterTitle = epubChapterTitle,
                            pageText = currentPageText
                        )
                        TtsService.instance?.speakFromOffset(offsetInPage)
                    }
                }
            }

            val resolveRelativePath: (String, String) -> String = { basePath, relativePath ->
                if (relativePath.startsWith("/")) {
                    relativePath.substring(1)
                } else {
                    val baseDir = if (basePath.contains("/")) basePath.substringBeforeLast("/") + "/" else ""
                    val combined = baseDir + relativePath
                    val segments = combined.split("/")
                    val cleanSegments = mutableListOf<String>()
                    for (seg in segments) {
                        if (seg == "..") {
                            if (cleanSegments.isNotEmpty()) {
                                cleanSegments.removeAt(cleanSegments.size - 1)
                            }
                        } else if (seg != "." && seg.isNotEmpty()) {
                            cleanSegments.add(seg)
                        }
                    }
                    cleanSegments.joinToString("/")
                }
            }

            val handleHyperlinkClick: (String) -> Unit = { targetUrl ->
                val decodedUrl = Uri.decode(targetUrl).trim()
                val targetChapterFile = if (decodedUrl.contains("#")) decodedUrl.substringBefore("#") else decodedUrl
                val anchorId = if (decodedUrl.contains("#")) decodedUrl.substringAfter("#") else null
                
                val currentBook = epubBook
                if (currentBook != null) {
                    val currentChapter = currentBook.chapters.getOrNull(epubChapterIndex)
                    val resolvedZipPath = if (targetChapterFile.isNotEmpty() && currentChapter != null) {
                        resolveRelativePath(currentChapter.zipPath, targetChapterFile)
                    } else {
                        currentChapter?.zipPath ?: ""
                    }
                    
                    val targetIndex = currentBook.chapters.indexOfFirst {
                        it.zipPath == resolvedZipPath || it.zipPath.endsWith(targetChapterFile)
                    }
                    
                    if (targetIndex != -1) {
                        if (targetIndex == epubChapterIndex) {
                            if (anchorId != null) {
                                val targetOffset = currentBook.chapters[epubChapterIndex].anchors[anchorId] ?: 0
                                val targetPage = pageOffsets.indexOfFirst { (start, end) ->
                                    targetOffset in start..end
                                }
                                if (targetPage != -1) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(if (epubChapterIndex > 0) targetPage + 1 else targetPage)
                                    }
                                }
                            }
                        } else {
                            pendingAnchorId = anchorId
                            isPaginating = true
                            transitionDirection = 1
                            epubChapterIndex = targetIndex
                        }
                    }
                }
            }

            // Main Content Area
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .align(Alignment.TopCenter)
                    .pointerInput(epubBook, epubChapterIndex, epubChapterPages) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (activeBook.format == BookFormat.EPUB) {
                                    val screenWidth = size.width
                                    val tapX = offset.x
                                    when {
                                        tapX < screenWidth * 0.2f -> {
                                            scope.launch {
                                                if (pagerState.currentPage > 0) {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                } else if (activeBook.format == BookFormat.EPUB && epubChapterIndex > 0) {
                                                    isPaginating = true
                                                    transitionDirection = -1
                                                    epubChapterIndex--
                                                }
                                            }
                                        }
                                        tapX > screenWidth * 0.8f -> {
                                            scope.launch {
                                                val maxPage = pagerState.pageCount - 1
                                                if (pagerState.currentPage < maxPage) {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                } else {
                                                    val chaptersCount = epubBook?.chapters?.size ?: 0
                                                    if (activeBook.format == BookFormat.EPUB && epubChapterIndex < chaptersCount - 1) {
                                                        isPaginating = true
                                                        transitionDirection = 1
                                                        epubChapterIndex++
                                                    }
                                                }
                                            }
                                        }
                                        else -> {
                                            showControls = !showControls
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                 if (finalPageCount == 0) {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Text("This book contains no readable content.", fontFamily = systemFont, fontSize = 20.sp)
                     }
                  } else {
                     val isCurrentPagePlaceholder = remember(pagerState.currentPage, epubChapterPages, epubChapterIndex, epubBook) {
                         val chaptersCount = epubBook?.chapters?.size ?: 0
                         val isPrev = epubChapterIndex > 0 && pagerState.currentPage == 0
                         val nextPageTrigger = if (epubChapterIndex > 0) epubChapterPages.size + 1 else epubChapterPages.size
                         val isNext = epubChapterIndex < chaptersCount - 1 && pagerState.currentPage == nextPageTrigger
                         isPrev || isNext
                     }

                     // 1. Static Chapter Title Header (outside Pager)
                     if (activeBook.format == BookFormat.EPUB && epubChapterPages.isNotEmpty() && !isPaginating) {
                         Box(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .padding(start = 24.dp, end = 24.dp, top = 16.dp + statusBarHeightDp, bottom = 4.dp)
                         ) {
                             if (!isCurrentPagePlaceholder) {
                                 Row(
                                     modifier = Modifier.fillMaxWidth(),
                                     horizontalArrangement = Arrangement.SpaceBetween,
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     Text(
                                         text = epubChapterTitle,
                                         fontFamily = systemFont,
                                         fontWeight = FontWeight.Medium,
                                         fontStyle = FontStyle.Italic,
                                         fontSize = headerFontSize,
                                         color = MaterialTheme.colorScheme.tertiary,
                                         maxLines = 1,
                                         overflow = TextOverflow.Ellipsis,
                                         modifier = Modifier.weight(1f)
                                     )
                                     if (settings.showReaderTime) {
                                         val currentTime = rememberTime(settings.use12HourClockFormat)
                                         Spacer(modifier = Modifier.width(8.dp))
                                         Text(
                                             text = currentTime,
                                             fontFamily = JetBrainsMonoFontFamily,
                                             fontSize = 12.sp,
                                             fontWeight = FontWeight.Bold,
                                             color = MaterialTheme.colorScheme.secondary
                                         )
                                     }
                                 }
                             } else {
                                 // Keep height identical on placeholder pages to prevent upward layout shift
                                 Text(
                                     text = " ",
                                     fontFamily = systemFont,
                                     fontSize = headerFontSize,
                                     maxLines = 1
                                 )
                             }
                         }
                     }

                     // 2. Horizontal Pager
                     HorizontalPager(
                         state = pagerState,
                         modifier = Modifier
                             .weight(1f)
                             .fillMaxWidth(),
                         beyondViewportPageCount = 2,
                         contentPadding = PaddingValues(0.dp),
                         userScrollEnabled = !isSelectionActive && !isPagerZoomed
                     ) { pageIndex ->
                         Box(
                             modifier = Modifier
                                 .fillMaxSize()
                                 .then(
                                     if (activeBook.format == BookFormat.EPUB) {
                                         Modifier.padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 16.dp)
                                     } else {
                                         Modifier
                                     }
                                 ),
                             contentAlignment = Alignment.Center
                         ) {
                             when (activeBook.format) {
                                 BookFormat.PDF -> {
                                     PdfPageRenderer(
                                         uriString = activeBook.uri,
                                         pageIndex = pageIndex,
                                         systemFont = systemFont,
                                         onZoomChanged = { isPagerZoomed = it },
                                         onToggleControls = { showControls = !showControls },
                                         onNavigateBack = {
                                             scope.launch {
                                                 if (pagerState.currentPage > 0) {
                                                     pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                 }
                                             }
                                         },
                                         onNavigateForward = {
                                             scope.launch {
                                                 if (pagerState.currentPage < finalPageCount - 1) {
                                                     pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                 }
                                             }
                                         }
                                     )
                                 }
                                 BookFormat.CBZ -> {
                                     CbzPageRenderer(
                                         uriString = activeBook.uri,
                                         entryName = cbzPages.getOrNull(pageIndex) ?: "",
                                         systemFont = systemFont,
                                         onZoomChanged = { isPagerZoomed = it },
                                         onToggleControls = { showControls = !showControls },
                                         onNavigateBack = {
                                             scope.launch {
                                                 if (pagerState.currentPage > 0) {
                                                     pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                 }
                                             }
                                         },
                                         onNavigateForward = {
                                             scope.launch {
                                                 if (pagerState.currentPage < finalPageCount - 1) {
                                                     pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                 }
                                             }
                                         }
                                     )
                                 }
                                 BookFormat.EPUB -> {
                                     val isPrevPlaceholder = epubChapterIndex > 0 && pageIndex == 0
                                     val isNextPlaceholder = pageIndex == (if (epubChapterIndex > 0) epubChapterPages.size + 1 else epubChapterPages.size)
                                     
                                      if (isPrevPlaceholder) {
                                          val prevTitle = epubBook?.chapters?.getOrNull(epubChapterIndex - 1)?.title ?: "Previous Chapter"
                                          Column(
                                              modifier = Modifier
                                                  .fillMaxSize()
                                                  .pointerInput(pagerState.currentPage, epubChapterIndex) {
                                                      detectTapGestures(
                                                          onTap = { offset ->
                                                              val screenWidth = size.width
                                                              val tapX = offset.x
                                                              scope.launch {
                                                                  if (tapX < screenWidth * 0.2f) {
                                                                      if (pagerState.currentPage > 0) {
                                                                          pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                                      }
                                                                  } else if (tapX > screenWidth * 0.8f) {
                                                                      val maxPage = pagerState.pageCount - 1
                                                                      if (pagerState.currentPage < maxPage) {
                                                                          pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                                      }
                                                                  } else {
                                                                      showControls = !showControls
                                                                  }
                                                              }
                                                          }
                                                      )
                                                  },
                                              verticalArrangement = Arrangement.Center,
                                              horizontalAlignment = Alignment.CenterHorizontally
                                          ) {
                                              Text(
                                                  text = "— PREVIOUS CHAPTER ·",
                                                  fontFamily = JetBrainsMonoFontFamily,
                                                  fontSize = if (isTablet) 16.sp else 11.sp,
                                                  fontWeight = FontWeight.Bold,
                                                  color = MaterialTheme.colorScheme.tertiary,
                                                  modifier = Modifier.padding(bottom = 8.dp)
                                              )
                                              Text(
                                                  text = prevTitle,
                                                  fontFamily = NyghtSerifFontFamily,
                                                  fontStyle = FontStyle.Italic,
                                                  fontWeight = FontWeight.Normal,
                                                  fontSize = if (isTablet) 44.sp else 32.sp,
                                                  lineHeight = if (isTablet) 52.sp else 38.sp,
                                                  color = MaterialTheme.colorScheme.onBackground,
                                                  textAlign = TextAlign.Center,
                                                  modifier = Modifier.padding(horizontal = 24.dp)
                                              )
                                              Spacer(modifier = Modifier.height(24.dp))
                                              Box(
                                                  modifier = Modifier
                                                      .clip(RoundedCornerShape(4.dp))
                                                      .background(Color.Transparent)
                                                      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                                      .clickable {
                                                          isPaginating = true
                                                          transitionDirection = -1
                                                          epubChapterIndex--
                                                      }
                                                      .then(placeholderButtonPadding),
                                                  contentAlignment = Alignment.Center
                                              ) {
                                                  Text(
                                                      text = "← READ PREVIOUS",
                                                      fontFamily = JetBrainsMonoFontFamily,
                                                      fontWeight = FontWeight.Bold,
                                                      fontSize = placeholderButtonFontSize,
                                                      color = MaterialTheme.colorScheme.onBackground
                                                  )
                                              }
                                          }
                                      } else if (isNextPlaceholder) {
                                          val nextTitle = epubBook?.chapters?.getOrNull(epubChapterIndex + 1)?.title ?: "Next Chapter"
                                          Column(
                                              modifier = Modifier
                                                  .fillMaxSize()
                                                  .pointerInput(pagerState.currentPage, epubChapterIndex) {
                                                      detectTapGestures(
                                                          onTap = { offset ->
                                                              val screenWidth = size.width
                                                              val tapX = offset.x
                                                              scope.launch {
                                                                  if (tapX < screenWidth * 0.2f) {
                                                                      if (pagerState.currentPage > 0) {
                                                                          pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                                      }
                                                                  } else if (tapX > screenWidth * 0.8f) {
                                                                      val maxPage = pagerState.pageCount - 1
                                                                      if (pagerState.currentPage < maxPage) {
                                                                          pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                                      }
                                                                  } else {
                                                                      showControls = !showControls
                                                                  }
                                                              }
                                                          }
                                                      )
                                                  },
                                              verticalArrangement = Arrangement.Center,
                                              horizontalAlignment = Alignment.CenterHorizontally
                                          ) {
                                              Text(
                                                  text = "— NEXT CHAPTER ·",
                                                  fontFamily = JetBrainsMonoFontFamily,
                                                  fontSize = if (isTablet) 16.sp else 11.sp,
                                                  fontWeight = FontWeight.Bold,
                                                  color = MaterialTheme.colorScheme.tertiary,
                                                  modifier = Modifier.padding(bottom = 8.dp)
                                              )
                                              Text(
                                                  text = nextTitle,
                                                  fontFamily = NyghtSerifFontFamily,
                                                  fontStyle = FontStyle.Italic,
                                                  fontWeight = FontWeight.Normal,
                                                  fontSize = if (isTablet) 44.sp else 32.sp,
                                                  lineHeight = if (isTablet) 52.sp else 38.sp,
                                                  color = MaterialTheme.colorScheme.onBackground,
                                                  textAlign = TextAlign.Center,
                                                  modifier = Modifier.padding(horizontal = 24.dp)
                                              )
                                              Spacer(modifier = Modifier.height(24.dp))
                                              Box(
                                                  modifier = Modifier
                                                      .clip(RoundedCornerShape(4.dp))
                                                      .background(Color.Transparent)
                                                      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                                      .clickable {
                                                          isPaginating = true
                                                          transitionDirection = 1
                                                          epubChapterIndex++
                                                      }
                                                      .then(placeholderButtonPadding),
                                                  contentAlignment = Alignment.Center
                                              ) {
                                                  Text(
                                                      text = "READ NEXT →",
                                                      fontFamily = JetBrainsMonoFontFamily,
                                                      fontWeight = FontWeight.Bold,
                                                      fontSize = placeholderButtonFontSize,
                                                      color = MaterialTheme.colorScheme.onBackground
                                                  )
                                              }
                                          }
                                      } else {
                                         val actualPageIndex = if (epubChapterIndex > 0) pageIndex - 1 else pageIndex
                                         val textContent = (epubChapterPages.getOrNull(actualPageIndex) ?: "").replace("\r", "")
                                         if (textContent == "[COVER_IMAGE]" && epubBook?.cover != null) {
                                             Box(
                                                 modifier = Modifier
                                                     .fillMaxSize()
                                                     .pointerInput(pagerState.currentPage, epubChapterIndex) {
                                                         detectTapGestures(
                                                             onTap = { offset ->
                                                                 val screenWidth = size.width
                                                                 val tapX = offset.x
                                                                 scope.launch {
                                                                     if (tapX < screenWidth * 0.2f) {
                                                                         if (pagerState.currentPage > 0) {
                                                                             pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                                         } else if (epubChapterIndex > 0) {
                                                                             isPaginating = true
                                                                             transitionDirection = -1
                                                                             epubChapterIndex--
                                                                         }
                                                                     } else if (tapX > screenWidth * 0.8f) {
                                                                         val maxPage = pagerState.pageCount - 1
                                                                         if (pagerState.currentPage < maxPage) {
                                                                             pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                                         } else {
                                                                             val chaptersCount = epubBook?.chapters?.size ?: 0
                                                                             if (epubChapterIndex < chaptersCount - 1) {
                                                                                 isPaginating = true
                                                                                 transitionDirection = 1
                                                                                 epubChapterIndex++
                                                                             }
                                                                         }
                                                                     } else {
                                                                         showControls = !showControls
                                                                     }
                                                                 }
                                                             }
                                                         )
                                                     },
                                                 contentAlignment = Alignment.Center
                                             ) {
                                                 Image(
                                                     bitmap = epubBook!!.cover!!.asImageBitmap(),
                                                     contentDescription = "Cover Page",
                                                     contentScale = ContentScale.Fit,
                                                     modifier = Modifier.fillMaxSize()
                                                 )
                                             }
                                         } else {
                                             val annotatedString = remember(textContent, actualPageIndex, pageOffsets, epubChapterIndex, epubBook, highlights, settings.theme) {
                                                 val pageStart = pageOffsets.getOrNull(actualPageIndex)?.first ?: -1
                                                 val pageEnd = pageOffsets.getOrNull(actualPageIndex)?.second ?: -1
                                                 val ch = epubBook?.chapters?.getOrNull(epubChapterIndex)
                                                 
                                                 buildAnnotatedString {
                                                     append(textContent)
                                                     
                                                     // Render permanent highlights
                                                     val bookUri = activeBook.uri
                                                     highlights.forEach { h ->
                                                         if (h.bookUri == bookUri && h.chapterIndex == epubChapterIndex) {
                                                             val localStart = (h.startOffset - pageStart).coerceAtLeast(0)
                                                             val localEnd = (h.endOffset - pageStart).coerceIn(0, textContent.length)
                                                             if (localStart < localEnd) {
                                                                 addStyle(
                                                                     style = SpanStyle(
                                                                         background = getHighlightColor(settings.theme)
                                                                     ),
                                                                     start = localStart,
                                                                     end = localEnd
                                                                 )
                                                             }
                                                         }
                                                     }
                                                     
                                                     // Render links
                                                     if (ch != null && pageStart != -1 && pageEnd != -1) {
                                                         ch.links.forEach { link ->
                                                             val localStart = (link.start - pageStart).coerceAtLeast(0)
                                                             val localEnd = (link.end - pageStart).coerceIn(0, textContent.length)
                                                             if (localStart < localEnd) {
                                                                 addStyle(
                                                                     style = SpanStyle(
                                                                         color = Color(0xFF0066CC),
                                                                         textDecoration = TextDecoration.Underline
                                                                     ),
                                                                     start = localStart,
                                                                     end = localEnd
                                                                 )
                                                                 addStringAnnotation(
                                                                     tag = "URL",
                                                                     annotation = link.target,
                                                                     start = localStart,
                                                                     end = localEnd
                                                                 )
                                                             }
                                                         }
                                                     }
                                                 }
                                             }

                                             var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                             var selectedRange by remember { mutableStateOf<TextRange?>(null) }
                                             var selectionStartAnchorRange by remember { mutableStateOf<TextRange?>(null) }
                                             
                                             var startDragAccumulatedX by remember { mutableStateOf(0f) }
                                             var startDragAccumulatedY by remember { mutableStateOf(0f) }
                                             var startDragInitialTouchX by remember { mutableStateOf(0f) }
                                             var startDragInitialTouchY by remember { mutableStateOf(0f) }
                                             
                                             var endDragAccumulatedX by remember { mutableStateOf(0f) }
                                             var endDragAccumulatedY by remember { mutableStateOf(0f) }
                                             var endDragInitialTouchX by remember { mutableStateOf(0f) }
                                             var endDragInitialTouchY by remember { mutableStateOf(0f) }
                                              
                                              // Reset selection when page changes
                                              LaunchedEffect(annotatedString) {
                                                  selectedRange = null
                                              }

                                              LaunchedEffect(selectedRange) {
                                                  isSelectionActive = selectedRange != null
                                              }

                                              val annotatedStringWithHighlight = remember(annotatedString, selectedRange, ttsCurrentWordRange, settings.showTtsHighlight, isTtsActive, isTtsPlaying) {
                                                  if ((selectedRange == null || selectedRange!!.collapsed) &&
                                                      (ttsCurrentWordRange == null || !settings.showTtsHighlight || !isTtsActive || !isTtsPlaying)) {
                                                      annotatedString
                                                  } else {
                                                      buildAnnotatedString {
                                                          append(annotatedString)
                                                          if (selectedRange != null && !selectedRange!!.collapsed) {
                                                              addStyle(
                                                                  style = SpanStyle(
                                                                      background = Color(0xFFD9A066).copy(alpha = 0.3f)
                                                                  ),
                                                                  start = selectedRange!!.start,
                                                                  end = selectedRange!!.end
                                                              )
                                                          }
                                                          if (settings.showTtsHighlight && isTtsActive && isTtsPlaying &&
                                                              ttsCurrentWordRange != null && !ttsCurrentWordRange!!.collapsed) {
                                                              val start = ttsCurrentWordRange!!.start.coerceIn(0, textContent.length)
                                                              val end = ttsCurrentWordRange!!.end.coerceIn(0, textContent.length)
                                                              if (start < end) {
                                                                  addStyle(
                                                                      style = SpanStyle(
                                                                          background = Color(0xFFD9A066).copy(alpha = 0.25f),
                                                                          fontWeight = FontWeight.Bold
                                                                      ),
                                                                      start = start,
                                                                      end = end
                                                                  )
                                                              }
                                                          }
                                                      }
                                                  }
                                              }

                                              val pageSelectionMenuRect = remember { mutableStateOf<Rect?>(null) }
                                              var pageShowSelectionMenu by remember { mutableStateOf(false) }

                                              Box(modifier = Modifier.fillMaxSize()) {
                                                  Text(
                                                      text = annotatedStringWithHighlight,
                                                      style = textStyle,
                                                      onTextLayout = { layoutResult = it },
                                                      modifier = Modifier
                                                          .fillMaxSize()
                                                          .pointerInput(annotatedString) {
                                                               detectTapGestures(
                                                                   onTap = { offset ->
                                                                       if (selectedRange != null) {
                                                                           selectedRange = null
                                                                           pageShowSelectionMenu = false
                                                                       } else {
                                                                           var linkClicked = false
                                                                           val layout = layoutResult
                                                                           if (layout != null) {
                                                                               val position = getCorrectedOffsetForPosition(context, layout, offset, settings.readerTextAlignment)
                                                                               val annotations = annotatedString.getStringAnnotations(
                                                                                   tag = "URL",
                                                                                   start = position,
                                                                                   end = position
                                                                               )
                                                                               val link = annotations.firstOrNull()
                                                                               if (link != null) {
                                                                                   handleHyperlinkClick(link.item)
                                                                                   linkClicked = true
                                                                               }
                                                                           }
                                                                           
                                                                           if (!linkClicked) {
                                                                               val screenWidth = size.width
                                                                               val tapX = offset.x
                                                                               scope.launch {
                                                                                   if (tapX < screenWidth * 0.2f) {
                                                                                       if (pagerState.currentPage > 0) {
                                                                                           pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                                                       } else if (epubChapterIndex > 0) {
                                                                                           isPaginating = true
                                                                                           transitionDirection = -1
                                                                                           epubChapterIndex--
                                                                                       }
                                                                                   } else if (tapX > screenWidth * 0.8f) {
                                                                                       val maxPage = pagerState.pageCount - 1
                                                                                       if (pagerState.currentPage < maxPage) {
                                                                                           pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                                                       } else {
                                                                                           val chaptersCount = epubBook?.chapters?.size ?: 0
                                                                                           if (epubChapterIndex < chaptersCount - 1) {
                                                                                               isPaginating = true
                                                                                               transitionDirection = 1
                                                                                               epubChapterIndex++
                                                                                           }
                                                                                       }
                                                                                   } else {
                                                                                       showControls = !showControls
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                )
                                                            }
                                                            .pointerInput(annotatedString) {
                                                                detectDragGesturesAfterLongPress(
                                                                    onDragStart = { offset ->
                                                                        val layout = layoutResult
                                                                        if (layout != null) {
                                                                            val position = getCorrectedOffsetForPosition(context, layout, offset, settings.readerTextAlignment)
                                                                            val wordRange = layout.getWordBoundary(position)
                                                                            if (wordRange.length > 0) {
                                                                                selectedRange = wordRange
                                                                                selectionStartAnchorRange = wordRange
                                                                                pageShowSelectionMenu = false
                                                                            }
                                                                        }
                                                                    },
                                                                    onDrag = { change, dragAmount ->
                                                                        change.consume()
                                                                        val layout = layoutResult
                                                                        val anchorRange = selectionStartAnchorRange
                                                                        if (layout != null && anchorRange != null) {
                                                                            val currentPos = getCorrectedOffsetForPosition(context, layout, change.position, settings.readerTextAlignment)
                                                                            val wordStart = anchorRange.start
                                                                            val wordEnd = anchorRange.end
                                                                            
                                                                            val wordRange = try {
                                                                                layout.getWordBoundary(currentPos)
                                                                            } catch (e: Exception) {
                                                                                TextRange(currentPos, currentPos)
                                                                            }
                                                                            
                                                                            if (currentPos >= wordEnd) {
                                                                                val endPos = if (wordRange.end >= currentPos) wordRange.end else currentPos
                                                                                selectedRange = TextRange(wordStart, endPos)
                                                                            } else if (currentPos <= wordStart) {
                                                                                val startPos = if (wordRange.start <= currentPos) wordRange.start else currentPos
                                                                                selectedRange = TextRange(startPos, wordEnd)
                                                                            } else {
                                                                                selectedRange = TextRange(wordStart, wordEnd)
                                                                            }
                                                                        }
                                                                    },
                                                                    onDragEnd = {
                                                                        pageShowSelectionMenu = true
                                                                    },
                                                                    onDragCancel = {
                                                                        pageShowSelectionMenu = true
                                                                    }
                                                                )
                                                            }
                                                    )
                                                    // Render selection handles
                                                    if (selectedRange != null && layoutResult != null) {
                                                        val startOffset = selectedRange!!.start
                                                        val endOffset = selectedRange!!.end
                                                        
                                                        val startVisibleOffset = getFirstVisibleOffset(textContent, startOffset, endOffset)
                                                        val endVisibleOffset = getLastVisibleOffset(textContent, startOffset, endOffset)
                                                        
                                                        val startRect = try {
                                                            layoutResult!!.getBoundingBox(startVisibleOffset.coerceIn(0, textContent.length - 1))
                                                        } catch (e: Exception) {
                                                            null
                                                        }
                                                        
                                                        val endRect = try {
                                                            layoutResult!!.getBoundingBox(endVisibleOffset.coerceIn(0, textContent.length - 1))
                                                        } catch (e: Exception) {
                                                            null
                                                        }
                                                        
                                                        if (startRect != null && endRect != null) {
                                                            val correctedStartLeft = getCorrectedHorizontalCoordinate(
                                                                context = context,
                                                                layoutResult = layoutResult!!,
                                                                offset = startVisibleOffset,
                                                                isStart = true,
                                                                readerTextAlignment = settings.readerTextAlignment
                                                            )
                                                            val correctedEndRight = getCorrectedHorizontalCoordinate(
                                                                context = context,
                                                                layoutResult = layoutResult!!,
                                                                offset = endVisibleOffset,
                                                                isStart = false,
                                                                readerTextAlignment = settings.readerTextAlignment
                                                            )

                                                            // Position selection popup dynamically above the selection
                                                            LaunchedEffect(selectedRange) {
                                                                val selectionMinY = minOf(startRect.top, endRect.top)
                                                                val selectionCenterX = (correctedStartLeft + correctedEndRight) / 2
                                                                pageSelectionMenuRect.value = Rect(
                                                                    left = selectionCenterX,
                                                                    top = selectionMinY,
                                                                    right = selectionCenterX,
                                                                    bottom = selectionMinY
                                                                )
                                                            }

                                                            // 1. Start Handle
                                                            Box(
                                                                modifier = Modifier
                                                                    .absoluteOffset {
                                                                        IntOffset(
                                                                            x = (correctedStartLeft - 20.dp.toPx()).toInt(),
                                                                            y = (startRect.top - 8.dp.toPx()).toInt()
                                                                        )
                                                                    }
                                                                    .size(40.dp, with(density) { (startRect.height.toDp() + 28.dp) })
                                                                    .pointerInput(Unit) {
                                                                        detectDragGestures(
                                                                            onDragStart = { initialPosition ->
                                                                                val layout = layoutResult
                                                                                val range = selectedRange
                                                                                if (layout != null && range != null) {
                                                                                    val startOffsetLocal = range.start
                                                                                    val startVisibleLocal = getFirstVisibleOffset(textContent, startOffsetLocal, range.end)
                                                                                    val startRectLocal = try {
                                                                                        layout.getBoundingBox(startVisibleLocal.coerceIn(0, textContent.length - 1))
                                                                                    } catch (e: Exception) {
                                                                                        null
                                                                                    }
                                                                                    val correctedStartLeftLocal = getCorrectedHorizontalCoordinate(
                                                                                        context = context,
                                                                                        layoutResult = layout,
                                                                                        offset = startVisibleLocal,
                                                                                        isStart = true,
                                                                                        readerTextAlignment = settings.readerTextAlignment
                                                                                    )
                                                                                    if (startRectLocal != null) {
                                                                                        startDragInitialTouchX = correctedStartLeftLocal - 20.dp.toPx() + initialPosition.x
                                                                                        startDragInitialTouchY = startRectLocal.top - 8.dp.toPx() + initialPosition.y
                                                                                        startDragAccumulatedX = 0f
                                                                                        startDragAccumulatedY = 0f
                                                                                    }
                                                                                }
                                                                            },
                                                                            onDrag = { change, dragAmount ->
                                                                                change.consume()
                                                                                startDragAccumulatedX += dragAmount.x
                                                                                startDragAccumulatedY += dragAmount.y
                                                                                val layout = layoutResult
                                                                                val range = selectedRange
                                                                                if (layout != null && range != null) {
                                                                                    val fingerXInText = startDragInitialTouchX + startDragAccumulatedX
                                                                                    val fingerYInText = startDragInitialTouchY + startDragAccumulatedY
                                                                                    val startOffsetLocal = range.start
                                                                                    val startVisibleLocal = getFirstVisibleOffset(textContent, startOffsetLocal, range.end)
                                                                                    val startRectLocal = try {
                                                                                        layout.getBoundingBox(startVisibleLocal.coerceIn(0, textContent.length - 1))
                                                                                    } catch (e: Exception) {
                                                                                        null
                                                                                    }
                                                                                    val height = startRectLocal?.height ?: startRect.height
                                                                                    val lineCenterOffsetY = height / 2f + 6.dp.toPx()
                                                                                    val adjustedY = fingerYInText - lineCenterOffsetY
                                                                                    val newIndex = getCorrectedOffsetForPosition(context, layout, Offset(fingerXInText, adjustedY), settings.readerTextAlignment)
                                                                                    val safeNewIndex = newIndex.coerceAtMost(range.end - 1)
                                                                                    selectedRange = TextRange(safeNewIndex, range.end)
                                                                                }
                                                                            }
                                                                        )
                                                                    }
                                                            ) {
                                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                                    val barWidth = 2.dp.toPx()
                                                                    val circleRadius = 5.dp.toPx()
                                                                    // Vertical line (shifted by 8dp touch padding at top)
                                                                    drawRect(
                                                                        color = Color(0xFFD9A066),
                                                                        topLeft = Offset((size.width - barWidth) / 2, 8.dp.toPx()),
                                                                        size = Size(barWidth, size.height - 28.dp.toPx())
                                                                    )
                                                                    // Circle at bottom
                                                                    drawCircle(
                                                                        color = Color(0xFFD9A066),
                                                                        radius = circleRadius,
                                                                        center = Offset(size.width / 2, size.height - 14.dp.toPx())
                                                                    )
                                                                }
                                                            }

                                                            // 2. End Handle
                                                            Box(
                                                                modifier = Modifier
                                                                    .absoluteOffset {
                                                                        IntOffset(
                                                                            x = (correctedEndRight - 20.dp.toPx()).toInt(),
                                                                            y = (endRect.top - 8.dp.toPx()).toInt()
                                                                        )
                                                                    }
                                                                    .size(40.dp, with(density) { (endRect.height.toDp() + 28.dp) })
                                                                    .pointerInput(Unit) {
                                                                        detectDragGestures(
                                                                            onDragStart = { initialPosition ->
                                                                                val layout = layoutResult
                                                                                val range = selectedRange
                                                                                if (layout != null && range != null) {
                                                                                    val endOffsetLocal = range.end
                                                                                    val endVisibleLocal = getLastVisibleOffset(textContent, range.start, endOffsetLocal)
                                                                                    val endRectLocal = try {
                                                                                        layout.getBoundingBox(endVisibleLocal.coerceIn(0, textContent.length - 1))
                                                                                    } catch (e: Exception) {
                                                                                        null
                                                                                    }
                                                                                    val correctedEndRightLocal = getCorrectedHorizontalCoordinate(
                                                                                        context = context,
                                                                                        layoutResult = layout,
                                                                                        offset = endVisibleLocal,
                                                                                        isStart = false,
                                                                                        readerTextAlignment = settings.readerTextAlignment
                                                                                    )
                                                                                    if (endRectLocal != null) {
                                                                                        endDragInitialTouchX = correctedEndRightLocal - 20.dp.toPx() + initialPosition.x
                                                                                        endDragInitialTouchY = endRectLocal.top - 8.dp.toPx() + initialPosition.y
                                                                                        endDragAccumulatedX = 0f
                                                                                        endDragAccumulatedY = 0f
                                                                                    }
                                                                                }
                                                                            },
                                                                            onDrag = { change, dragAmount ->
                                                                                change.consume()
                                                                                endDragAccumulatedX += dragAmount.x
                                                                                endDragAccumulatedY += dragAmount.y
                                                                                val layout = layoutResult
                                                                                val range = selectedRange
                                                                                if (layout != null && range != null) {
                                                                                    val fingerXInText = endDragInitialTouchX + endDragAccumulatedX
                                                                                    val fingerYInText = endDragInitialTouchY + endDragAccumulatedY
                                                                                    val endOffsetLocal = range.end
                                                                                    val endVisibleLocal = getLastVisibleOffset(textContent, range.start, endOffsetLocal)
                                                                                    val endRectLocal = try {
                                                                                        layout.getBoundingBox(endVisibleLocal.coerceIn(0, textContent.length - 1))
                                                                                    } catch (e: Exception) {
                                                                                        null
                                                                                    }
                                                                                    val height = endRectLocal?.height ?: endRect.height
                                                                                    val lineCenterOffsetY = height / 2f + 6.dp.toPx()
                                                                                    val adjustedY = fingerYInText - lineCenterOffsetY
                                                                                    val newIndex = getCorrectedOffsetForPosition(context, layout, Offset(fingerXInText, adjustedY), settings.readerTextAlignment)
                                                                                    val safeNewIndex = newIndex.coerceAtLeast(range.start + 1)
                                                                                    selectedRange = TextRange(range.start, safeNewIndex)
                                                                                }
                                                                            }
                                                                        )
                                                                    }
                                                            ) {
                                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                                    val barWidth = 2.dp.toPx()
                                                                    val circleRadius = 5.dp.toPx()
                                                                    // Vertical line (shifted by 8dp touch padding at top)
                                                                    drawRect(
                                                                        color = Color(0xFFD9A066),
                                                                        topLeft = Offset((size.width - barWidth) / 2, 8.dp.toPx()),
                                                                        size = Size(barWidth, size.height - 28.dp.toPx())
                                                                    )
                                                                    // Circle at bottom
                                                                    drawCircle(
                                                                        color = Color(0xFFD9A066),
                                                                        radius = circleRadius,
                                                                        center = Offset(size.width / 2, size.height - 14.dp.toPx())
                                                                    )
                                                                }
                                                            }
                                                       }
                                                   }

                                                  if (pageShowSelectionMenu && pageSelectionMenuRect.value != null && selectedRange != null) {
                                                      val clipboardManager = LocalClipboardManager.current
                                                      Popup(
                                                          popupPositionProvider = remember(pageSelectionMenuRect.value) {
                                                              object : PopupPositionProvider {
                                                                  override fun calculatePosition(
                                                                      anchorBounds: IntRect,
                                                                      windowSize: IntSize,
                                                                      layoutDirection: LayoutDirection,
                                                                      popupContentSize: IntSize
                                                                  ): IntOffset {
                                                                      val rect = pageSelectionMenuRect.value ?: return IntOffset(0, 0)
                                                                      val x = (anchorBounds.left + rect.left + (rect.width - popupContentSize.width) / 2)
                                                                          .toInt()
                                                                          .coerceIn(16, windowSize.width - popupContentSize.width - 16)
                                                                      
                                                                      var y = (anchorBounds.top + rect.top - popupContentSize.height - 20).toInt()
                                                                      if (y < 50) {
                                                                          y = (anchorBounds.top + rect.bottom + 20).toInt()
                                                                      }
                                                                      return IntOffset(x, y.coerceIn(0, windowSize.height - popupContentSize.height))
                                                                  }
                                                              }
                                                          },
                                                          onDismissRequest = {
                                                              pageShowSelectionMenu = false
                                                          }
                                                      ) {
                                                          Row(
                                                              modifier = Modifier
                                                                  .background(Color(0xFF1F1511), RoundedCornerShape(50.dp))
                                                                  .border(1.dp, Color(0xFFD9A066).copy(alpha = 0.4f), RoundedCornerShape(50.dp))
                                                                  .padding(horizontal = 8.dp, vertical = 6.dp),
                                                              verticalAlignment = Alignment.CenterVertically,
                                                              horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                          ) {
                                                              Row(
                                                                  modifier = Modifier
                                                                      .clip(RoundedCornerShape(50.dp))
                                                                      .clickable {
                                                                          val copiedText = textContent.substring(selectedRange!!.start, selectedRange!!.end)
                                                                          clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(copiedText))
                                                                          selectedRange = null
                                                                          pageShowSelectionMenu = false
                                                                          Toast.makeText(context, "Copied text", Toast.LENGTH_SHORT).show()
                                                                      }
                                                                      .padding(horizontal = 12.dp, vertical = 6.dp),
                                                                  verticalAlignment = Alignment.CenterVertically
                                                              ) {
                                                                  Icon(
                                                                      imageVector = Icons.Default.ContentCopy,
                                                                      contentDescription = "Copy",
                                                                      tint = Color(0xFFEFE6DD),
                                                                      modifier = Modifier.size(16.dp)
                                                                  )
                                                                  Spacer(modifier = Modifier.width(4.dp))
                                                                  Text(
                                                                      text = "Copy",
                                                                      fontFamily = InterFontFamily,
                                                                      fontSize = 12.sp,
                                                                      fontWeight = FontWeight.Bold,
                                                                      color = Color(0xFFEFE6DD)
                                                                  )
                                                              }

                                                              Box(
                                                                  modifier = Modifier
                                                                      .size(width = 1.dp, height = 16.dp)
                                                                      .background(Color(0xFFEFE6DD).copy(alpha = 0.2f))
                                                              )

                                                              // --- Highlight/Unhighlight ---
                                                              val pageStart = pageOffsets.getOrNull(actualPageIndex)?.first ?: -1
                                                              val absoluteStart = if (pageStart != -1 && selectedRange != null) pageStart + selectedRange!!.start else -1
                                                              val absoluteEnd = if (pageStart != -1 && selectedRange != null) pageStart + selectedRange!!.end else -1
                                                              val hasOverlappingHighlight = remember(highlights, absoluteStart, absoluteEnd, epubChapterIndex, activeBook) {
                                                                  if (absoluteStart == -1 || absoluteEnd == -1) false
                                                                  else {
                                                                      highlights.any { h ->
                                                                          h.bookUri == activeBook.uri &&
                                                                          h.chapterIndex == epubChapterIndex &&
                                                                          h.startOffset < absoluteEnd &&
                                                                          h.endOffset > absoluteStart
                                                                      }
                                                                  }
                                                              }

                                                              Row(
                                                                  modifier = Modifier
                                                                      .clip(RoundedCornerShape(50.dp))
                                                                      .clickable {
                                                                          val range = selectedRange
                                                                          if (range != null && absoluteStart != -1 && absoluteEnd != -1) {
                                                                              if (hasOverlappingHighlight) {
                                                                                  scope.launch {
                                                                                      repository.removeHighlightsInRange(activeBook.uri, epubChapterIndex, absoluteStart, absoluteEnd)
                                                                                      Toast.makeText(context, "Highlight removed", Toast.LENGTH_SHORT).show()
                                                                                  }
                                                                              } else {
                                                                                  val highlightText = textContent.substring(range.start, range.end)
                                                                                  scope.launch {
                                                                                      repository.addHighlight(activeBook.uri, epubChapterIndex, absoluteStart, absoluteEnd, highlightText)
                                                                                      Toast.makeText(context, "Highlighted!", Toast.LENGTH_SHORT).show()
                                                                                  }
                                                                              }
                                                                          }
                                                                          selectedRange = null
                                                                          pageShowSelectionMenu = false
                                                                      }
                                                                      .padding(horizontal = 12.dp, vertical = 6.dp),
                                                                  verticalAlignment = Alignment.CenterVertically
                                                              ) {
                                                                  Icon(
                                                                      imageVector = Icons.Default.BorderColor,
                                                                      contentDescription = "Highlight",
                                                                      tint = Color(0xFFEFE6DD),
                                                                      modifier = Modifier.size(16.dp)
                                                                  )
                                                                  Spacer(modifier = Modifier.width(4.dp))
                                                                  Text(
                                                                      text = if (hasOverlappingHighlight) "Unhighlight" else "Highlight",
                                                                      fontFamily = InterFontFamily,
                                                                      fontSize = 12.sp,
                                                                      fontWeight = FontWeight.Bold,
                                                                      color = Color(0xFFEFE6DD)
                                                                  )
                                                              }

                                                              Box(
                                                                  modifier = Modifier
                                                                      .size(width = 1.dp, height = 16.dp)
                                                                      .background(Color(0xFFEFE6DD).copy(alpha = 0.2f))
                                                              )

                                                              Row(
                                                                  modifier = Modifier
                                                                      .clip(RoundedCornerShape(50.dp))
                                                                      .clickable {
                                                                          val copiedText = textContent.substring(selectedRange!!.start, selectedRange!!.end)
                                                                          if (copiedText.isNotBlank() && activeBook != null) {
                                                                              scope.launch {
                                                                                  repository.addQuote(copiedText, activeBook)
                                                                                  Toast.makeText(context, "Quote saved to library!", Toast.LENGTH_SHORT).show()
                                                                              }
                                                                          }
                                                                          selectedRange = null
                                                                          pageShowSelectionMenu = false
                                                                      }
                                                                      .padding(horizontal = 12.dp, vertical = 6.dp),
                                                                  verticalAlignment = Alignment.CenterVertically
                                                              ) {
                                                                  Icon(
                                                                      imageVector = Icons.Default.FormatQuote,
                                                                      contentDescription = "Quote",
                                                                      tint = Color(0xFFD9A066),
                                                                      modifier = Modifier.size(16.dp)
                                                                  )
                                                                  Spacer(modifier = Modifier.width(4.dp))
                                                                  Text(
                                                                      text = "Quote",
                                                                      fontFamily = InterFontFamily,
                                                                      fontSize = 12.sp,
                                                                      fontWeight = FontWeight.Bold,
                                                                      color = Color(0xFFD9A066)
                                                                  )
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

                     // 3. Static Footer below Pager (doesn't slide!)
                     Box(
                          modifier = Modifier
                              .fillMaxWidth()
                              .padding(start = 24.dp, end = 24.dp, bottom = 6.dp, top = 2.dp)
                      ) {
                          // Left corner: Battery
                          if (settings.showReaderBattery) {
                              val batteryLevel = rememberBatteryLevel(context)
                              Text(
                                  text = "⚡ $batteryLevel",
                                  fontFamily = JetBrainsMonoFontFamily,
                                  fontSize = 11.sp,
                                  color = MaterialTheme.colorScheme.secondary,
                                  modifier = Modifier.align(Alignment.CenterStart)
                              )
                          }
                          
                          // Center: Chapter / Page text
                          val footerText = when (activeBook.format) {
                              BookFormat.PDF, BookFormat.CBZ -> "${pagerState.currentPage + 1} / $finalPageCount"
                              BookFormat.EPUB -> {
                                  val actualPageIndex = if (epubChapterIndex > 0) pagerState.currentPage - 1 else pagerState.currentPage
                                  val pageDisplay = if (epubChapterPages.isEmpty()) 1 else (actualPageIndex + 1).coerceIn(1, epubChapterPages.size)
                                  val totalPages = epubChapterPages.size.coerceAtLeast(1)
                                  val displayName = if (epubChapterTitle.length > 20) epubChapterTitle.take(17) + "..." else epubChapterTitle
                                  "$displayName • $pageDisplay / $totalPages"
                              }
                          }
                          Text(
                              text = footerText,
                              fontFamily = JetBrainsMonoFontFamily,
                              fontSize = 11.sp,
                              color = MaterialTheme.colorScheme.secondary,
                              modifier = Modifier.align(Alignment.Center)
                          )
                          
                          // Right corner: Percentage Progress
                          if (settings.showReaderProgressPercent) {
                              val progressPercent = when (activeBook.format) {
                                  BookFormat.PDF, BookFormat.CBZ -> {
                                      if (finalPageCount > 1) {
                                          (pagerState.currentPage.toFloat() / (finalPageCount - 1).toFloat() * 100).toInt().coerceIn(0, 100)
                                      } else {
                                          100
                                      }
                                  }
                                  BookFormat.EPUB -> {
                                      val totalChapters = epubBook?.chapters?.size ?: 1
                                      if (totalChapters > 1) {
                                          (epubChapterIndex.toFloat() / (totalChapters - 1).toFloat() * 100).toInt().coerceIn(0, 100)
                                      } else {
                                          100
                                      }
                                  }
                              }
                              Text(
                                  text = "$progressPercent%",
                                  fontFamily = JetBrainsMonoFontFamily,
                                  fontSize = 11.sp,
                                  color = MaterialTheme.colorScheme.secondary,
                                  modifier = Modifier.align(Alignment.CenterEnd)
                              )
                          }
                      }
                 }
             }

            // --- Top App Bar Overlay ---
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 800.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarHeightDp),
                    shadowElevation = 0.dp // Flat design
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    TtsService.stopPlayback()
                                    onBack()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack, 
                                    contentDescription = "Back",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            
                                                        Text(
                                 text = activeBook.title,
                                 fontFamily = systemFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // ToC Option
                            if (activeBook.format == BookFormat.EPUB) {
                                IconButton(
                                    onClick = { showTocDialog = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Table of Contents",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Speech option
                             IconButton(
                                 onClick = {
                                     if (activeBook.format == BookFormat.EPUB) {
                                         startTtsEngine()
                                     }
                                 },
                                 modifier = Modifier.size(48.dp)
                             ) {
                                 Icon(
                                     imageVector = Icons.Default.VolumeUp,
                                     contentDescription = "Listen to book",
                                     tint = MaterialTheme.colorScheme.tertiary,
                                     modifier = Modifier.size(28.dp)
                                 )
                             }

                             // Font settings option
                             if (activeBook.format == BookFormat.EPUB) {
                                 IconButton(
                                     onClick = { showFontSettingsDialog = true },
                                     modifier = Modifier.size(48.dp)
                                 ) {
                                     Text(
                                         text = "Aa",
                                         fontFamily = systemFont,
                                         fontWeight = FontWeight.Bold,
                                         fontSize = 18.sp,
                                         color = MaterialTheme.colorScheme.tertiary
                                     )
                                 }
                             }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // --- Bottom Progress Bar Overlay ---
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 800.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    shadowElevation = 0.dp // Flat design
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val progressText = when (activeBook.format) {
                                BookFormat.PDF, BookFormat.CBZ -> "Page ${pagerState.currentPage + 1} of $finalPageCount"
                                BookFormat.EPUB -> {
                                    val actualPageIndex = if (epubChapterIndex > 0) pagerState.currentPage - 1 else pagerState.currentPage
                                    val pageDisplay = if (epubChapterPages.isEmpty()) 1 else (actualPageIndex + 1).coerceIn(1, epubChapterPages.size)
                                    val totalChapters = epubBook?.chapters?.size ?: 1
                                    val displayName = if (epubChapterTitle.length > 25) epubChapterTitle.take(22) + "..." else epubChapterTitle
                                    "$displayName • Page $pageDisplay of $totalChapters chapters"
                                }
                            }
                            
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = progressText,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            val sliderValue = when (activeBook.format) {
                                BookFormat.PDF, BookFormat.CBZ -> pagerState.currentPage.toFloat()
                                BookFormat.EPUB -> epubChapterIndex.toFloat()
                            }
                            val sliderMax = when (activeBook.format) {
                                BookFormat.PDF, BookFormat.CBZ -> (finalPageCount - 1).toFloat()
                                BookFormat.EPUB -> ((epubBook?.chapters?.size ?: 1) - 1).toFloat()
                            }
                            
                            if (sliderMax > 0f) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (activeBook.format == BookFormat.EPUB && epubChapterIndex > 0) {
                                                isPaginating = true
                                                transitionDirection = -1
                                                epubChapterIndex--
                                            } else if (activeBook.format != BookFormat.EPUB && pagerState.currentPage > 0) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            }
                                        },
                                        enabled = if (activeBook.format == BookFormat.EPUB) epubChapterIndex > 0 else pagerState.currentPage > 0,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Previous Section",
                                            tint = if (if (activeBook.format == BookFormat.EPUB) epubChapterIndex > 0 else pagerState.currentPage > 0) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                            }
                                        )
                                    }

                                    Slider(
                                        value = sliderValue.coerceIn(0f, sliderMax),
                                        onValueChange = {
                                            scope.launch {
                                                when (activeBook.format) {
                                                    BookFormat.PDF, BookFormat.CBZ -> pagerState.scrollToPage(it.toInt())
                                                    BookFormat.EPUB -> {
                                                        isPaginating = true
                                                        epubChapterIndex = it.toInt()
                                                        transitionDirection = 1
                                                    }
                                                }
                                            }
                                        },
                                        valueRange = 0f..sliderMax,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = MaterialTheme.colorScheme.tertiary,
                                            thumbColor = MaterialTheme.colorScheme.tertiary
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            if (activeBook.format == BookFormat.EPUB && epubChapterIndex < (epubBook?.chapters?.size ?: 1) - 1) {
                                                isPaginating = true
                                                transitionDirection = 1
                                                epubChapterIndex++
                                            } else if (activeBook.format != BookFormat.EPUB && pagerState.currentPage < finalPageCount - 1) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            }
                                        },
                                        enabled = if (activeBook.format == BookFormat.EPUB) epubChapterIndex < (epubBook?.chapters?.size ?: 1) - 1 else pagerState.currentPage < finalPageCount - 1,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Next Section",
                                            tint = if (if (activeBook.format == BookFormat.EPUB) epubChapterIndex < (epubBook?.chapters?.size ?: 1) - 1 else pagerState.currentPage < finalPageCount - 1) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- TTS Controls Overlay Banner ---
            AnimatedVisibility(
                visible = showTtsBanner && isTtsActive,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 800.dp)
                    .padding(bottom = if (showControls) 110.dp else 24.dp)
            ) {
                // Compute precise text-based progress and estimated time
                val totalLength = if (epubChapterPages.isNotEmpty()) epubChapterPages.sumOf { it.length } else 0
                val prefixLength = if (epubChapterPages.isNotEmpty()) (0 until pagerState.currentPage).sumOf { epubChapterPages.getOrNull(it)?.length ?: 0 } else 0
                val wordOffset = ttsCurrentWordRange?.start ?: 0
                val currentRead = prefixLength + wordOffset
                val remainingChars = (totalLength - currentRead).coerceAtLeast(0)
                val remainingWords = remainingChars / 5.5f
                val remainingMinutes = remainingWords / (150f * settings.ttsSpeed)

                val timeText = if (remainingMinutes < 1f) {
                    val secs = (remainingMinutes * 60f).toInt()
                    if (secs <= 5) "Done" else "${secs}s left"
                } else {
                    val mins = remainingMinutes.toInt()
                    "${mins}m left"
                }

                val chapterProgress = if (totalLength > 0) {
                    currentRead.toFloat() / totalLength.toFloat()
                } else {
                    0f
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                    shadowElevation = 0.dp // Flat design
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left part: Media Controls
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            TtsService.controlActions.emit(TtsControlAction.SkipPrev)
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (isTtsPlaying) {
                                            TtsService.instance?.pauseSpeaking()
                                        } else {
                                            TtsService.instance?.startSpeaking(resume = true)
                                        }
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            TtsService.controlActions.emit(TtsControlAction.SkipNext)
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Center part: Time remaining & Speed control
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = timeText,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(12.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${settings.ttsSpeed}x",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val newSpeed = when (settings.ttsSpeed) {
                                                    1.0f -> 1.25f
                                                    1.25f -> 1.5f
                                                    1.5f -> 1.75f
                                                    1.75f -> 2.0f
                                                    else -> 1.0f
                                                }
                                                repository.updateSettings { it.copy(ttsSpeed = newSpeed) }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = "Change Speed",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Right part: Close button
                            IconButton(
                                onClick = {
                                    TtsService.stopPlayback()
                                    showTtsBanner = false
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Interactable Slider for chapter progress
                        var sliderValue by remember(chapterProgress) { mutableFloatStateOf(chapterProgress) }
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = {
                                seekToChapterProgress(sliderValue)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.tertiary,
                                activeTrackColor = MaterialTheme.colorScheme.tertiary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // --- Table of Contents Dialog ---
            if (showTocDialog && epubBook != null) {
                val lazyListState = rememberLazyListState()
                val totalChapters = epubBook!!.chapters.size
                val firstVisibleIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
                var isDraggingFastScroll by remember { mutableStateOf(false) }

                LaunchedEffect(showTocDialog) {
                    if (showTocDialog) {
                        lazyListState.scrollToItem((epubChapterIndex - 2).coerceAtLeast(0))
                    }
                }

                Dialog(onDismissRequest = { showTocDialog = false }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "— CONTENTS ·",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                IconButton(
                                    onClick = { showTocDialog = false },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    itemsIndexed(epubBook!!.chapters) { index, chapter ->
                                        val isSelected = index == epubChapterIndex
                                        val chapterNum = String.format("%02d", index + 1)
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    isPaginating = true
                                                    transitionDirection = 1
                                                    epubChapterIndex = index
                                                    showTocDialog = false
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "— $chapterNum  ",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            Text(
                                                text = chapter.title,
                                                fontFamily = systemFont,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 17.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                }

                                if (totalChapters > 10) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(24.dp)
                                            .pointerInput(totalChapters) {
                                                detectTapGestures(
                                                    onPress = { offset ->
                                                        val height = size.height
                                                        val ratio = (offset.y / height).coerceIn(0f, 1f)
                                                        val targetIndex = (ratio * (totalChapters - 1)).toInt()
                                                        scope.launch {
                                                            lazyListState.scrollToItem(targetIndex)
                                                        }
                                                    }
                                                )
                                            }
                                            .pointerInput(totalChapters) {
                                                detectDragGestures(
                                                    onDragStart = { isDraggingFastScroll = true },
                                                    onDragEnd = { isDraggingFastScroll = false },
                                                    onDragCancel = { isDraggingFastScroll = false },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val height = size.height
                                                        val ratio = (change.position.y / height).coerceIn(0f, 1f)
                                                        val targetIndex = (ratio * (totalChapters - 1)).toInt()
                                                        scope.launch {
                                                            lazyListState.scrollToItem(targetIndex)
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        val height = maxHeight
                                        val ratio = firstVisibleIndex.toFloat() / (totalChapters - 1).coerceAtLeast(1).toFloat()
                                        val thumbHeight = 40.dp
                                        val usableHeight = height - thumbHeight
                                        val thumbOffset = usableHeight * ratio

                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(2.dp)
                                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                                .align(Alignment.Center)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .offset(y = thumbOffset)
                                                .width(12.dp)
                                                .height(thumbHeight)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isDraggingFastScroll) MaterialTheme.colorScheme.tertiary
                                                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                                                )
                                                .align(Alignment.TopCenter)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Font Settings Dialog ---
            if (showFontSettingsDialog) {
                Dialog(onDismissRequest = { showFontSettingsDialog = false }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "— READER SETTINGS ·",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                IconButton(
                                    onClick = { showFontSettingsDialog = false },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // Theme selector
                                Text(
                                    text = "Theme Mode",
                                    fontFamily = systemFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isNarrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
                                    val themes = listOf(
                                        Triple(ReaderTheme.LIGHT_COFFEE, if (isNarrow) "Light" else "Light Coffee", CoffeeLightBackground to CoffeeLightPrimary),
                                        Triple(ReaderTheme.DARK_COFFEE, if (isNarrow) "Dark" else "Dark Coffee", CoffeeDarkBackground to CoffeeDarkPrimary),
                                        Triple(ReaderTheme.AMOLED, "AMOLED", AmoledBackground to AmoledPrimary)
                                    )
                                    themes.forEach { (t, name, colors) ->
                                        val isSelected = settings.theme == t
                                        val (bg, fg) = colors
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(bg)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .clickable {
                                                    scope.launch {
                                                        repository.updateSettings { it.copy(theme = t) }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = name,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = fg,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))

                                // Font family selector
                                Text(
                                    text = "Font Family",
                                    fontFamily = systemFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(10.dp))
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
                                            brush = SolidColor(MaterialTheme.colorScheme.outline)
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
                                                    scope.launch {
                                                        repository.updateSettings { it.copy(fontName = fontKey) }
                                                    }
                                                    expandedFontDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                // Font Size selector
                                Text(
                                    text = "Font Size: ${settings.fontSize.toInt()}sp",
                                    fontFamily = systemFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = settings.fontSize,
                                    onValueChange = { size ->
                                        scope.launch {
                                            repository.updateSettings { it.copy(fontSize = size) }
                                        }
                                    },
                                    valueRange = 14f..38f,
                                    steps = 12,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        thumbColor = MaterialTheme.colorScheme.tertiary
                                    )
                                )
                                
                                Spacer(modifier = Modifier.height(20.dp))

                                // Line Height selector
                                Text(
                                    text = "Line Height: ${String.format("%.1f", settings.readerLineHeight)}",
                                    fontFamily = systemFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = settings.readerLineHeight,
                                    onValueChange = { height ->
                                        scope.launch {
                                            repository.updateSettings { it.copy(readerLineHeight = height) }
                                        }
                                    },
                                    valueRange = 1.0f..2.5f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        thumbColor = MaterialTheme.colorScheme.tertiary
                                    )
                                )
                                
                                Spacer(modifier = Modifier.height(20.dp))

                                // Text Alignment selector
                                Text(
                                    text = "Text Alignment",
                                    fontFamily = systemFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp)),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    val alignments = listOf("Justify", "Left", "Center")
                                    alignments.forEachIndexed { index, name ->
                                        val isSelected = settings.readerTextAlignment == index
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    scope.launch {
                                                        repository.updateSettings { it.copy(readerTextAlignment = index) }
                                                    }
                                                }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = name,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                        if (index < 2) {
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .height(40.dp)
                                                    .background(MaterialTheme.colorScheme.outline)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(28.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(20.dp))

                                // HUD Toggles Header
                                Text(
                                    text = "— HEADS UP DISPLAY ·",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // Show Battery Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Show Battery Indicator",
                                            fontFamily = systemFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Display battery status in footer",
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Switch(
                                        checked = settings.showReaderBattery,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                repository.updateSettings { it.copy(showReaderBattery = checked) }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                                            checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                        )
                                    )
                                }

                                // Show Time Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Show System Time",
                                            fontFamily = systemFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Display current time in header",
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Switch(
                                        checked = settings.showReaderTime,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                repository.updateSettings { it.copy(showReaderTime = checked) }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                                            checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                        )
                                    )
                                }

                                // 12-Hour Clock Format Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Use 12-Hour Format",
                                            fontFamily = systemFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Display clock in 12-hour AM/PM format",
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Switch(
                                        checked = settings.use12HourClockFormat,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                repository.updateSettings { it.copy(use12HourClockFormat = checked) }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                                            checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                        ),
                                        enabled = settings.showReaderTime
                                    )
                                }

                                // Show Progress Percent Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Show Progress Percentage",
                                            fontFamily = systemFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Display completion percentage in footer",
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Switch(
                                        checked = settings.showReaderProgressPercent,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                repository.updateSettings { it.copy(showReaderProgressPercent = checked) }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                                            checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(20.dp))

                                // TTS Toggles Header
                                Text(
                                    text = "— SPEECH OPTIONS ·",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                 // Autoplay Next Chapter Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Autoplay Next Chapter",
                                            fontFamily = systemFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Automatically transition and read the next chapter when TTS completes the current one",
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = settings.autoPlayNextChapter,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                repository.updateSettings { it.copy(autoPlayNextChapter = checked) }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                                            checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                        )
                                    )
                                }

                                // Highlight Spoken Word Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Highlight Spoken Word",
                                            fontFamily = systemFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Highlight the current word in real-time as TTS speaks",
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = settings.showTtsHighlight,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                repository.updateSettings { it.copy(showTtsHighlight = checked) }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                                            checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }

            // --- Formatting Overlay ---
            val isEpubPaginating = activeBook.format == BookFormat.EPUB && (isPaginating || lastLoadedChapterIndex != epubChapterIndex)
            if (isEpubPaginating) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Formatting pages...",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
            }


        }
    }
}

// --- PDF Page Renderer Composable ---
@Composable
fun PdfPageRenderer(
    uriString: String,
    pageIndex: Int,
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily,
    onZoomChanged: (Boolean) -> Unit,
    onToggleControls: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit
) {
    val context = LocalContext.current
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uriString, pageIndex) {
        isLoading = true
        pageBitmap = withContext(Dispatchers.IO) {
            PdfRendererHelper.renderPage(context, uriString, pageIndex)
        }
        isLoading = false
    }

    ZoomablePage(
        zoomKey = pageIndex,
        onZoomChanged = onZoomChanged,
        onToggleControls = onToggleControls,
        onNavigateBack = onNavigateBack,
        onNavigateForward = onNavigateForward
    ) { scale, offset ->
        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
        } else if (pageBitmap != null) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "PDF Page $pageIndex",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("Failed to render PDF page.", fontFamily = systemFont, fontSize = 16.sp)
        }
    }
}

// --- CBZ Comic Page Renderer Composable ---
@Composable
fun CbzPageRenderer(
    uriString: String,
    entryName: String,
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily,
    onZoomChanged: (Boolean) -> Unit,
    onToggleControls: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit
) {
    val context = LocalContext.current
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uriString, entryName) {
        isLoading = true
        pageBitmap = withContext(Dispatchers.IO) {
            CbzParser.renderPage(context, uriString, entryName)
        }
        isLoading = false
    }

    ZoomablePage(
        zoomKey = entryName,
        onZoomChanged = onZoomChanged,
        onToggleControls = onToggleControls,
        onNavigateBack = onNavigateBack,
        onNavigateForward = onNavigateForward
    ) { scale, offset ->
        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
        } else if (pageBitmap != null) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "Comic Page $entryName",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("Failed to load comic page.", fontFamily = systemFont, fontSize = 16.sp)
        }
    }
}

@Composable
fun ZoomablePage(
    modifier: Modifier = Modifier,
    zoomKey: Any? = null,
    onZoomChanged: (Boolean) -> Unit,
    onToggleControls: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    content: @Composable BoxScope.(scale: Float, offset: Offset) -> Unit
) {
    var scale by remember(zoomKey) { mutableFloatStateOf(1f) }
    var offset by remember(zoomKey) { mutableStateOf(Offset.Zero) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(zoomKey) {
        scale = 1f
        offset = Offset.Zero
        onZoomChanged(false)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val maxWidthPx = constraints.maxWidth
        val maxHeightPx = constraints.maxHeight

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zoomKey) {
                    var lastTapTime = 0L
                    var lastTapPos = Offset.Zero

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val now = System.currentTimeMillis()
                        val isDoubleTap = (now - lastTapTime < 300) && 
                                          (down.position - lastTapPos).getDistance() < 100f

                        var doubleTapDragActive = false
                        val startDragScale = scale
                        val startDragY = down.position.y
                        var consumedDrag = false

                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop
                        var lastChanges: List<PointerInputChange> = emptyList()

                        if (isDoubleTap) {
                            doubleTapDragActive = true
                            lastTapTime = 0L // Reset immediately to prevent single tap action from executing!
                            lastTapPos = Offset.Zero
                        }

                        do {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            lastChanges = changes
                            val pointerCount = changes.size

                            if (doubleTapDragActive && pointerCount == 1) {
                                // One-finger double tap and drag to zoom!
                                val change = changes[0]
                                val currentY = change.position.y
                                val diffY = currentY - startDragY

                                if (!pastTouchSlop) {
                                    if (kotlin.math.abs(diffY) > touchSlop) {
                                        pastTouchSlop = true
                                    }
                                }

                                if (pastTouchSlop) {
                                    // Slide down (diffY > 0) -> zoom in.
                                    // Slide up (diffY < 0) -> zoom out.
                                    val scaleMultiplier = 1f + (diffY / 300f)
                                    val nextScale = (startDragScale * scaleMultiplier).coerceIn(1f, 5f)

                                    val extraWidth = (nextScale - 1) * maxWidthPx
                                    val extraHeight = (nextScale - 1) * maxHeightPx
                                    val maxX = extraWidth / 2f
                                    val maxY = extraHeight / 2f

                                    val newOffset = Offset(
                                        x = offset.x.coerceIn(-maxX, maxX),
                                        y = offset.y.coerceIn(-maxY, maxY)
                                    )
                                    scale = nextScale
                                    offset = newOffset
                                    onZoomChanged(nextScale > 1.05f)
                                    change.consume()
                                    consumedDrag = true
                                }
                            } else {
                                // Pinch-to-zoom (2+ fingers) or regular pan (1 finger)
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (scale > 1.05f || pointerCount >= 2) {
                                    // Update scale and offset
                                    val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
                                    val extraWidth = (nextScale - 1) * maxWidthPx
                                    val extraHeight = (nextScale - 1) * maxHeightPx
                                    val maxX = extraWidth / 2f
                                    val maxY = extraHeight / 2f

                                    val newOffset = Offset(
                                        x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                        y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                    )
                                    scale = nextScale
                                    offset = newOffset
                                    onZoomChanged(nextScale > 1.05f)

                                    // Consume event changes so parent HorizontalPager doesn't intercept
                                    changes.forEach { it.consume() }
                                }
                            }
                        } while (changes.any { it.pressed })

                        // After gesture finishes (finger up):
                        val fingerUp = lastChanges.firstOrNull()
                        val upTime = System.currentTimeMillis()

                        if (doubleTapDragActive) {
                            if (!consumedDrag) {
                                // If they double tapped but didn't drag, toggle zoom with animation!
                                val targetScale = if (scale > 1.05f) 1f else 2.5f
                                if (targetScale > 1.05f) {
                                    onZoomChanged(true)
                                }
                                coroutineScope.launch {
                                    animate(
                                        initialValue = scale,
                                        targetValue = targetScale,
                                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                                    ) { value, _ ->
                                        scale = value
                                    }
                                    if (targetScale <= 1.05f) {
                                        onZoomChanged(false)
                                    }
                                }
                                coroutineScope.launch {
                                    val startOffset = offset
                                    animate(
                                        initialValue = 0f,
                                        targetValue = 1f,
                                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                                    ) { fraction, _ ->
                                        offset = Offset(
                                            x = startOffset.x + (Offset.Zero.x - startOffset.x) * fraction,
                                            y = startOffset.y + (Offset.Zero.y - startOffset.y) * fraction
                                        )
                                    }
                                }
                            }
                            // Reset double tap tracker
                            lastTapTime = 0L
                            lastTapPos = Offset.Zero
                        } else {
                            // First tap tracking
                            // Only count as tap if released quickly and didn't move past slop
                            val dragDistance = if (fingerUp != null) {
                                (fingerUp.position - down.position).getDistance()
                            } else {
                                0f
                            }
                            val duration = upTime - now
                            if (duration < 250 && dragDistance < touchSlop) {
                                lastTapTime = now
                                lastTapPos = down.position
                                
                                val tapX = down.position.x
                                val currentTapTime = now
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(250)
                                    if (lastTapTime == currentTapTime) {
                                        // Single tap action!
                                        if (scale > 1.05f) {
                                            onToggleControls()
                                        } else {
                                            val screenWidth = size.width
                                            if (tapX < screenWidth * 0.2f) {
                                                onNavigateBack()
                                            } else if (tapX > screenWidth * 0.8f) {
                                                onNavigateForward()
                                            } else {
                                                onToggleControls()
                                            }
                                        }
                                        // Reset tap info
                                        lastTapTime = 0L
                                        lastTapPos = Offset.Zero
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentAlignment = Alignment.Center
            ) {
                content(scale, offset)
            }
        }
    }
}

private fun getBookFromUri(context: Context, uriString: String): Book {
    val uri = Uri.parse(uriString)
    var name = "Unknown Book"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex) ?: ""
                }
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    if (name.isEmpty() || name == "Unknown Book") {
        name = uri.lastPathSegment ?: "book"
    }
    
    val format = when {
        name.endsWith(".pdf", ignoreCase = true) -> BookFormat.PDF
        name.endsWith(".epub", ignoreCase = true) -> BookFormat.EPUB
        name.endsWith(".cbz", ignoreCase = true) -> BookFormat.CBZ
        else -> {
            val mime = context.contentResolver.getType(uri)
            when (mime) {
                "application/pdf" -> BookFormat.PDF
                "application/epub+zip" -> BookFormat.EPUB
                "application/x-cbz" -> BookFormat.CBZ
                else -> BookFormat.EPUB
            }
        }
    }
    
    var title = name.substringBeforeLast(".")
    var author = "Unknown Author"
    if (title.contains("-")) {
        val parts = title.split("-", limit = 2)
        author = parts[0].trim()
        title = parts[1].trim()
    }
    
    return Book(
        uri = uriString,
        name = name,
        title = title,
        author = author,
        description = "Format: ${format.name}, Size: ${size} bytes",
        format = format,
        byteSize = size
    )
}

private fun logToFile(context: Context, message: String) {
    try {
        val dir = context.getExternalFilesDir(null)
        if (dir != null) {
            val file = java.io.File(dir, "selection_debug.txt")
            file.appendText("${System.currentTimeMillis()}: $message\n")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getCorrectedHorizontalCoordinate(
    context: Context,
    layoutResult: TextLayoutResult,
    offset: Int,
    isStart: Boolean,
    readerTextAlignment: Int
): Float {
    val text = layoutResult.layoutInput.text
    if (text.isEmpty()) return 0f
    
    val safeOffset = offset.coerceIn(0, text.length - 1)
    val line = layoutResult.getLineForOffset(safeOffset)
    val lineStart = layoutResult.getLineStart(line)
    val lineEnd = layoutResult.getLineEnd(line)
    
    val baseRect = try {
        layoutResult.getBoundingBox(safeOffset)
    } catch (e: Exception) {
        null
    }
    if (baseRect == null) return 0f
    val originalX = if (isStart) baseRect.left else baseRect.right
    
    // Justification correction is only applicable when alignment is Justify (not 1 and not 2)
    val isJustified = readerTextAlignment != 1 && readerTextAlignment != 2
    if (!isJustified) {
        return originalX
    }
    
    // Check if it's the last line of a paragraph or the last line of the layout
    val isLastLineOfParagraph = line == layoutResult.lineCount - 1 || 
            (lineEnd > 0 && text.getOrNull(lineEnd - 1) == '\n')
            
    if (isLastLineOfParagraph) {
        return originalX
    }
    
    val lineText = text.substring(lineStart, lineEnd)
    val leadingSpaces = lineText.takeWhile { it.isWhitespace() }.length
    val trailingSpaces = lineText.takeLastWhile { it.isWhitespace() }.length
    
    val totalMiddleSpaces = lineText.substring(
        leadingSpaces,
        (lineText.length - trailingSpaces).coerceAtLeast(leadingSpaces)
    ).count { it == ' ' }
    
    if (totalMiddleSpaces <= 0) {
        return originalX
    }
    
    val lastCharBox = try {
        var lastVisibleCharIndex = lineEnd - 1
        while (lastVisibleCharIndex >= lineStart) {
            val c = text.getOrNull(lastVisibleCharIndex)
            if (c != null && !c.isWhitespace()) {
                break
            }
            lastVisibleCharIndex--
        }
        layoutResult.getBoundingBox(lastVisibleCharIndex.coerceAtLeast(lineStart))
    } catch (e: Exception) {
        null
    }
    val unconstrainedLineRight = lastCharBox?.right ?: layoutResult.size.width.toFloat()
    
    val extraSpace = layoutResult.size.width.toFloat() - unconstrainedLineRight
    if (extraSpace <= 0f) {
        return originalX
    }
    
    val addedPerSpace = extraSpace / totalMiddleSpaces
    
    // Count spaces before the offset on the current line
    val textBefore = text.substring(lineStart, safeOffset.coerceIn(lineStart, lineEnd))
    val spacesBefore = textBefore.count { it == ' ' }
    val middleSpacesBefore = (spacesBefore - leadingSpaces).coerceIn(0, totalMiddleSpaces)
    
    val isOffsetMiddleSpace = safeOffset >= lineStart + leadingSpaces && safeOffset < lineEnd - trailingSpaces && text[safeOffset] == ' '
    
    val spacesShift = if (!isStart && isOffsetMiddleSpace) {
        middleSpacesBefore + 1
    } else {
        middleSpacesBefore
    }
    
    val corrected = originalX + spacesShift * addedPerSpace
    return corrected
}

private fun getCorrectedOffsetForPosition(
    context: Context,
    layoutResult: TextLayoutResult,
    position: Offset,
    readerTextAlignment: Int
): Int {
    val text = layoutResult.layoutInput.text
    if (text.isEmpty()) return 0
    
    val y = position.y
    val line = try {
        layoutResult.getLineForVerticalPosition(y).coerceIn(0, layoutResult.lineCount - 1)
    } catch (e: Exception) {
        0
    }
    
    val lineStart = layoutResult.getLineStart(line)
    val lineEnd = layoutResult.getLineEnd(line)
    if (lineStart >= lineEnd) return lineStart
    
    val x = position.x
    
    // Find the character on this line whose corrected bounds contain x
    var bestOffset = lineStart
    var minDistance = Float.MAX_VALUE
    
    for (i in lineStart until lineEnd) {
        val left = getCorrectedHorizontalCoordinate(context, layoutResult, i, isStart = true, readerTextAlignment)
        val right = getCorrectedHorizontalCoordinate(context, layoutResult, i, isStart = false, readerTextAlignment)
        
        if (x in left..right) {
            return i
        }
        
        val center = (left + right) / 2
        val dist = kotlin.math.abs(x - center)
        if (dist < minDistance) {
            minDistance = dist
            bestOffset = i
        }
    }
    
    return bestOffset
}

private fun getHighlightColor(theme: ReaderTheme): Color {
    return when (theme) {
        ReaderTheme.LIGHT_COFFEE -> Color(0xFFE6AF6D).copy(alpha = 0.4f)
        ReaderTheme.DARK_COFFEE -> Color(0xFFB88B58).copy(alpha = 0.35f)
        ReaderTheme.AMOLED -> Color(0xFF8B5E3C).copy(alpha = 0.4f)
    }
}

private fun getFirstVisibleOffset(text: String, start: Int, end: Int): Int {
    for (i in start until end) {
        val c = text.getOrNull(i)
        if (c != null && c != ' ' && c != '\n' && c != '\r' && c != '\t') {
            return i
        }
    }
    return start
}

private fun getLastVisibleOffset(text: String, start: Int, end: Int): Int {
    for (i in (end - 1) downTo start) {
        val c = text.getOrNull(i)
        if (c != null && c != ' ' && c != '\n' && c != '\r' && c != '\t') {
            return i
        }
    }
    return (end - 1).coerceAtLeast(start)
}

