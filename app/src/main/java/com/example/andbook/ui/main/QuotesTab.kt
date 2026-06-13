package com.example.andbook.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.andbook.R
import com.example.andbook.data.Book
import com.example.andbook.data.DataRepository
import com.example.andbook.data.Quote
import com.example.andbook.data.ReaderTheme
import com.example.andbook.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class QuotesSort {
    NEWEST, OLDEST, TEXT_ASC, TEXT_DESC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesTab(
    repository: DataRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val quotes by repository.quotes.collectAsState()
    val settings by repository.settings.collectAsState()

    var sortBy by remember { mutableStateOf(QuotesSort.NEWEST) }
    var selectedBookUriFilter by remember { mutableStateOf<String?>(null) } // null means "All Books"

    var showSortMenu by remember { mutableStateOf(false) }
    var showBookFilterMenu by remember { mutableStateOf(false) }

    var enlargedQuote by remember { mutableStateOf<Quote?>(null) }

    // Unique books list for filtering
    val booksWithQuotes = remember(quotes) {
        quotes.map { it.bookTitle to it.bookUri }.distinctBy { it.second }
    }

    // Filter and Sort the quotes list
    val processedQuotes = remember(quotes, sortBy, selectedBookUriFilter) {
        var list = if (selectedBookUriFilter == null) {
            quotes
        } else {
            quotes.filter { it.bookUri == selectedBookUriFilter }
        }

        list = when (sortBy) {
            QuotesSort.NEWEST -> list.sortedByDescending { it.timestamp }
            QuotesSort.OLDEST -> list.sortedBy { it.timestamp }
            QuotesSort.TEXT_ASC -> list.sortedBy { it.text.lowercase() }
            QuotesSort.TEXT_DESC -> list.sortedByDescending { it.text.lowercase() }
        }
        list
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        // Controls Row: Filter by book & Sorting options
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Book Filter Button
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showBookFilterMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                ) {
                    val activeBookName = if (selectedBookUriFilter == null) {
                        "All Books"
                    } else {
                        booksWithQuotes.firstOrNull { it.second == selectedBookUriFilter }?.first ?: "Selected Book"
                    }
                    Text(
                        text = activeBookName,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter",
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showBookFilterMenu,
                    onDismissRequest = { showBookFilterMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Books", fontFamily = InterFontFamily) },
                        onClick = {
                            selectedBookUriFilter = null
                            showBookFilterMenu = false
                        }
                    )
                    booksWithQuotes.forEach { (title, uri) ->
                        DropdownMenuItem(
                            text = { Text(title, fontFamily = InterFontFamily) },
                            onClick = {
                                selectedBookUriFilter = uri
                                showBookFilterMenu = false
                            }
                        )
                    }
                }
            }

            // Sort Button
            Box {
                OutlinedButton(
                    onClick = { showSortMenu = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                ) {
                    val sortLabel = when (sortBy) {
                        QuotesSort.NEWEST -> "Newest"
                        QuotesSort.OLDEST -> "Oldest"
                        QuotesSort.TEXT_ASC -> "A-Z"
                        QuotesSort.TEXT_DESC -> "Z-A"
                    }
                    Text(
                        text = sortLabel,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort",
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Newest first", fontFamily = InterFontFamily) },
                        onClick = {
                            sortBy = QuotesSort.NEWEST
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Oldest first", fontFamily = InterFontFamily) },
                        onClick = {
                            sortBy = QuotesSort.OLDEST
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Alphabetical (A-Z)", fontFamily = InterFontFamily) },
                        onClick = {
                            sortBy = QuotesSort.TEXT_ASC
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Alphabetical (Z-A)", fontFamily = InterFontFamily) },
                        onClick = {
                            sortBy = QuotesSort.TEXT_DESC
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        // Quotes List
        if (processedQuotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No quotes saved yet",
                        fontFamily = NyghtSerifFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Highlight text while reading to create quotes.",
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(processedQuotes, key = { it.id }) { quote ->
                    QuoteCard(
                        quote = quote,
                        onClick = { enlargedQuote = quote }
                    )
                }
            }
        }
    }

    // Enlarged Dialog Overlay
    enlargedQuote?.let { quote ->
        EnlargedQuoteDialog(
            quote = quote,
            theme = settings.theme,
            onDismiss = { enlargedQuote = null },
            onDelete = {
                scope.launch {
                    repository.deleteQuote(quote.id)
                    enlargedQuote = null
                }
            }
        )
    }
}

@Composable
fun QuoteCard(
    quote: Quote,
    onClick: () -> Unit
) {
    val isLongText = quote.text.length > 150
    // Design elements: warm paper, light border, italics
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (MaterialTheme.colorScheme.background == Color(0xFFFBF7F0) || MaterialTheme.colorScheme.background == Color(0xFFFAF6EE)) {
                Color(0xFFF6F1EA) // Slight darker warm sand paper
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Elegant large quotation icon in start
            Icon(
                imageVector = Icons.Default.FormatQuote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Italicized Quote Text
            Text(
                text = "“${quote.text}”",
                fontFamily = NyghtSerifFontFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = if (isLongText) 3 else Int.MAX_VALUE,
                overflow = if (isLongText) TextOverflow.Ellipsis else TextOverflow.Clip
            )

            if (isLongText) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to view full quote",
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            Spacer(modifier = Modifier.height(10.dp))

            // Book details footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quote.bookTitle,
                        fontFamily = NyghtSerifFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = quote.bookAuthor,
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun EnlargedQuoteDialog(
    quote: Quote,
    theme: ReaderTheme,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header with quote icon and delete button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Quote",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable full text in italics
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        text = "“${quote.text}”",
                        fontFamily = NyghtSerifFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 20.sp,
                        lineHeight = 30.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Citation details
                Text(
                    text = quote.bookTitle,
                    fontFamily = NyghtSerifFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                )
                Text(
                    text = "by ${quote.bookAuthor}",
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons: Copy raw text & Share Stylized Image
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Copy button
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(quote.text))
                            Toast.makeText(context, "Quote copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Text", fontFamily = InterFontFamily, fontSize = 13.sp)
                    }

                    // Share Stylized Image button
                    Button(
                        onClick = {
                            shareQuoteImage(context, quote.text, quote.bookTitle, quote.bookAuthor, theme)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1F1511), // Theme's dark brown button
                            contentColor = Color(0xFFEFE6DD)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Card", fontFamily = InterFontFamily, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "Close",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

// --- Image Quote Card Generation (Editorial Design, No Quality Loss) ---
fun generateQuoteImage(context: Context, quoteText: String, bookTitle: String, bookAuthor: String, theme: ReaderTheme): Bitmap {
    val size = 1080
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Color definitions based on active theme
    val bgColor: Int
    val textColor: Int
    val citationColor: Int
    val borderColor: Int
    val quoteMarkColor: Int

    when (theme) {
        ReaderTheme.LIGHT_COFFEE -> {
            bgColor = 0xFFFAF6F0.toInt()
            textColor = 0xFF1E1A17.toInt()
            citationColor = 0xFF6E655C.toInt()
            borderColor = 0xFFE5DFD5.toInt()
            quoteMarkColor = 0xFFC69A70.toInt()
        }
        ReaderTheme.DARK_COFFEE -> {
            bgColor = 0xFF1B1613.toInt()
            textColor = 0xFFE6DFD5.toInt()
            citationColor = 0xFF948A80.toInt()
            borderColor = 0xFF332A24.toInt()
            quoteMarkColor = 0xFFD9A066.toInt()
        }
        ReaderTheme.AMOLED -> {
            bgColor = 0xFF000000.toInt()
            textColor = 0xFFEEEEEE.toInt()
            citationColor = 0xFF888888.toInt()
            borderColor = 0xFF2E2E2E.toInt()
            quoteMarkColor = 0xFFD9A066.toInt()
        }
    }

    // Paint background
    val backgroundPaint = android.graphics.Paint().apply {
        color = bgColor
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), backgroundPaint)

    // Single elegant framing border matching theme outline (minimalist editorial)
    val borderPaint = android.graphics.Paint().apply {
        color = borderColor
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRect(40f, 40f, size.toFloat() - 40f, size.toFloat() - 40f, borderPaint)

    // Faint decorative quotation mark in background
    val quoteMarkPaint = android.graphics.Paint().apply {
        color = quoteMarkColor
        alpha = 20 // ~8% opacity
        textSize = 380f
        typeface = try {
            androidx.core.content.res.ResourcesCompat.getFont(context, R.font.nyght_serif_bold_italic)
        } catch (e: Exception) {
            android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD_ITALIC)
        }
        textAlign = android.graphics.Paint.Align.LEFT
    }
    canvas.drawText("“", 80f, 350f, quoteMarkPaint)

    // Setup typeface and text paint for the quote (italics mandatory)
    val quoteTypeface = try {
        androidx.core.content.res.ResourcesCompat.getFont(context, R.font.nyght_serif_medium_italic)
    } catch (e: Exception) {
        android.graphics.Typeface.create("serif", android.graphics.Typeface.ITALIC)
    }

    val textPaint = android.text.TextPaint().apply {
        color = textColor
        textSize = 44f
        isAntiAlias = true
        typeface = quoteTypeface
    }

    val padding = 120
    val maxTextWidth = size - (padding * 2)

    // StaticLayout wrapping for multiline text support
    val builder = android.text.StaticLayout.Builder.obtain(quoteText, 0, quoteText.length, textPaint, maxTextWidth)
        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(0f, 1.45f)
        .setIncludePad(false)
    val staticLayout = builder.build()

    // Setup citation details
    val citationText = "— $bookTitle\nby $bookAuthor"
    val citationPaint = android.text.TextPaint().apply {
        color = citationColor
        textSize = 28f
        isAntiAlias = true
        typeface = try {
            androidx.core.content.res.ResourcesCompat.getFont(context, R.font.outfit)
        } catch (e: Exception) {
            android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
    }
    val citationBuilder = android.text.StaticLayout.Builder.obtain(citationText, 0, citationText.length, citationPaint, maxTextWidth)
        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(0f, 1.25f)
        .setIncludePad(false)
    val citationLayout = citationBuilder.build()

    // Compute centering layout
    val totalHeight = staticLayout.height + 80 + citationLayout.height
    val startY = ((size - totalHeight) / 2f).coerceAtLeast(120f)

    // Render elements onto Canvas
    canvas.save()
    canvas.translate(padding.toFloat(), startY)
    staticLayout.draw(canvas)
    canvas.restore()

    canvas.save()
    canvas.translate(padding.toFloat(), startY + staticLayout.height + 80f)
    citationLayout.draw(canvas)
    canvas.restore()

    return bitmap
}

// --- Share Cache Utility ---
fun shareQuoteImage(context: Context, quoteText: String, bookTitle: String, bookAuthor: String, theme: ReaderTheme) {
    try {
        val bitmap = generateQuoteImage(context, quoteText, bookTitle, bookAuthor, theme)
        val cacheDir = File(context.cacheDir, "shared_quotes")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        // Save using clean timestamps to avoid conflicts
        val file = File(cacheDir, "quote_${System.currentTimeMillis()}.png")
        val outStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
        outStream.flush()
        outStream.close()

        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Stylized Quote Card"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to build shareable quote card: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
