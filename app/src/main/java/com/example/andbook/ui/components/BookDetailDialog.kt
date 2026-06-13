package com.example.andbook.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.andbook.data.Book
import com.example.andbook.data.BookFormat
import com.example.andbook.data.HistoryItem
import com.example.andbook.reader.BookCoverHelper
import com.example.andbook.reader.CbzParser
import com.example.andbook.reader.EpubParser
import com.example.andbook.reader.PdfRendererHelper
import com.example.andbook.theme.InterFontFamily
import com.example.andbook.theme.JetBrainsMonoFontFamily
import com.example.andbook.theme.NyghtSerifFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BookDetailDialog(
    item: HistoryItem,
    isSavedInLibrary: Boolean,
    onToggleLibrary: () -> Unit,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onDeleteFromHistory: () -> Unit,
    modifier: Modifier = Modifier,
    systemFont: androidx.compose.ui.text.font.FontFamily = NyghtSerifFontFamily
) {
    val context = LocalContext.current
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingCover by remember { mutableStateOf(true) }

    LaunchedEffect(item.book.uri) {
        isLoadingCover = true
        coverBitmap = withContext(Dispatchers.IO) {
            try {
                BookCoverHelper.getCover(context, item.book)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        isLoadingCover = false
    }

    val estTimeRemaining = remember(item.progress) {
        calculateEstimatedTime(item)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)) // Flat outline border
                .clip(RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.background // Matches organic paper white
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Vinyl Cover Disc Layout (From picture)
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Dark cocoa vinyl backing disc
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1F1511)) // Dark cocoa disc background
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                    )
                    
                    // Book Cover
                    Box(
                        modifier = Modifier
                            .size(125.dp, 180.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingCover) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else if (coverBitmap != null) {
                            Image(
                                bitmap = coverBitmap!!.asImageBitmap(),
                                contentDescription = item.book.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxHeight()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = "Book icon",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Centered metadata row (typewriter style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.book.format.name,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "  |  ",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    val formattedSize = formatSizeString(item.book.byteSize)
                    Text(
                        text = formattedSize,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title (Playfair Display)
                Text(
                    text = item.book.title,
                    fontFamily = systemFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Author
                Text(
                    text = item.book.author,
                    fontFamily = InterFontFamily,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Time remaining label (flat monospaced border card)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = estTimeRemaining,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Library Toggle Button
                OutlinedButton(
                    onClick = onToggleLibrary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isSavedInLibrary) Icons.Default.LibraryAddCheck else Icons.Default.LibraryAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSavedInLibrary) "Saved in Library" else "Save to Library",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            onDeleteFromHistory()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Remove",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            onResume()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1F1511), // Dark cocoa button
                            contentColor = Color(0xFFEFE6DD)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(1.dp, Color(0xFF1F1511), RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Resume",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatSizeString(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun calculateEstimatedTime(item: HistoryItem): String {
    val progress = item.progress
    val pagesLeft = progress.totalPages - progress.lastPage - 1
    
    if (pagesLeft <= 0) {
        return "Book Completed"
    }

    val minutesPerPage = when (item.book.format) {
        BookFormat.PDF -> 1.5
        BookFormat.CBZ -> 0.5
        BookFormat.EPUB -> 1.0
    }

    val totalMinutesLeft = (pagesLeft * minutesPerPage).toInt()
    
    return when {
        totalMinutesLeft < 1 -> "Less than a minute left"
        totalMinutesLeft < 60 -> "$totalMinutesLeft mins left to read"
        else -> {
            val hours = totalMinutesLeft / 60
            val minutes = totalMinutesLeft % 60
            if (minutes == 0) {
                if (hours == 1) "1 hour left" else "$hours hours left"
            } else {
                if (hours == 1) "1 hour $minutes mins left" else "$hours hrs $minutes mins left"
            }
        }
    }
}
