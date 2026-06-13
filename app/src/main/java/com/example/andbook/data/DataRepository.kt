package com.example.andbook.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

open class DataRepository(private val context: Context? = null) {

    companion object {
        @Volatile
        private var INSTANCE: DataRepository? = null

        fun getInstance(context: Context): DataRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    open val data: Flow<List<String>> = flow { emit(emptyList()) }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val filesDir: File
        get() = context?.filesDir ?: File("build/tmp/test_files").apply { mkdirs() }

    private val settingsFile get() = File(filesDir, "settings.json")
    private val historyFile get() = File(filesDir, "history.json")
    private val libraryFile get() = File(filesDir, "library.json")
    private val quotesFile get() = File(filesDir, "quotes.json")
    private val highlightsFile get() = File(filesDir, "highlights.json")

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private val _library = MutableStateFlow(loadLibrary())
    val library: StateFlow<List<Book>> = _library.asStateFlow()

    private val _quotes = MutableStateFlow(loadQuotes())
    val quotes: StateFlow<List<Quote>> = _quotes.asStateFlow()

    private val _highlights = MutableStateFlow(loadHighlights())
    val highlights: StateFlow<List<Highlight>> = _highlights.asStateFlow()

    private val _scannedBooks = MutableStateFlow<List<Book>>(emptyList())
    val scannedBooks: StateFlow<List<Book>> = _scannedBooks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // --- Settings ---
    private fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                json.decodeFromString<AppSettings>(settingsFile.readText())
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AppSettings()
        }
    }

    suspend fun updateSettings(update: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
        val newSettings = update(_settings.value)
        _settings.value = newSettings
        try {
            settingsFile.writeText(json.encodeToString(newSettings))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- History ---
    private fun loadHistory(): List<HistoryItem> {
        return try {
            if (historyFile.exists()) {
                json.decodeFromString<List<HistoryItem>>(historyFile.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun saveHistory(list: List<HistoryItem>) = withContext(Dispatchers.IO) {
        _history.value = list
        try {
            historyFile.writeText(json.encodeToString(list))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateBookProgress(book: Book, progressUpdate: (ReadingProgress?) -> ReadingProgress) = withContext(Dispatchers.IO) {
        val currentHistory = _history.value.toMutableList()
        val index = currentHistory.indexOfFirst { it.book.uri == book.uri }
        
        val currentProgress = if (index >= 0) currentHistory[index].progress else null
        val newProgress = progressUpdate(currentProgress)
        val newItem = HistoryItem(book, newProgress)

        if (index >= 0) {
            currentHistory.removeAt(index)
        }
        // Add to the front of history (most recent)
        currentHistory.add(0, newItem)
        saveHistory(currentHistory)
    }

    suspend fun deleteFromHistory(bookUri: String) = withContext(Dispatchers.IO) {
        val currentHistory = _history.value.filter { it.book.uri != bookUri }
        saveHistory(currentHistory)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        saveHistory(emptyList())
    }

    // --- Library ---
    private fun loadLibrary(): List<Book> {
        return try {
            if (libraryFile.exists()) {
                json.decodeFromString<List<Book>>(libraryFile.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun saveLibrary(list: List<Book>) = withContext(Dispatchers.IO) {
        _library.value = list
        try {
            libraryFile.writeText(json.encodeToString(list))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addToLibrary(book: Book) = withContext(Dispatchers.IO) {
        val current = _library.value.toMutableList()
        if (current.none { it.uri == book.uri }) {
            current.add(book)
            saveLibrary(current)
        }
    }

    fun getBookFromUri(uri: Uri): Book {
        var name = "Unknown Book"
        var size = 0L
        try {
            context?.contentResolver?.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
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
        
        val mime = context?.contentResolver?.getType(uri)
        val format = when {
            name.endsWith(".pdf", ignoreCase = true) || mime == "application/pdf" -> BookFormat.PDF
            name.endsWith(".epub", ignoreCase = true) || mime == "application/epub+zip" -> BookFormat.EPUB
            name.endsWith(".cbz", ignoreCase = true) || mime == "application/x-cbz" -> BookFormat.CBZ
            else -> BookFormat.EPUB
        }
        
        var title = name.substringBeforeLast(".")
        var author = "Unknown Author"
        if (title.contains("-")) {
            val parts = title.split("-", limit = 2)
            author = parts[0].trim()
            title = parts[1].trim()
        }
        
        return Book(
            uri = uri.toString(),
            name = name,
            title = title,
            author = author,
            description = "Format: ${format.name}, Size: ${formatSize(size)}",
            format = format,
            byteSize = size
        )
    }

    suspend fun removeFromLibrary(bookUri: String) = withContext(Dispatchers.IO) {
        val current = _library.value.filter { it.uri != bookUri }
        saveLibrary(current)
    }

    // --- Folder Scanning (Storage Access Framework) ---
    suspend fun scanBooksFolder() = withContext(Dispatchers.IO) {
        val folderUriStr = _settings.value.booksFolderUri ?: return@withContext
        _isScanning.value = true
        val booksList = mutableListOf<Book>()
        try {
            val rootUri = Uri.parse(folderUriStr)
            val rootDocId = DocumentsContract.getTreeDocumentId(rootUri)
            scanUriRecursively(rootUri, rootDocId, booksList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _scannedBooks.value = booksList
        _isScanning.value = false
    }

    private fun scanUriRecursively(treeUri: Uri, parentDocId: String, results: MutableList<Book>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        context?.contentResolver?.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val mime = cursor.getString(mimeCol) ?: ""
                val size = cursor.getLong(sizeCol)

                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    // Recursive scan for subdirectories
                    scanUriRecursively(treeUri, docId, results)
                } else {
                    val format = when {
                        name.endsWith(".pdf", ignoreCase = true) || mime == "application/pdf" -> BookFormat.PDF
                        name.endsWith(".epub", ignoreCase = true) || mime == "application/epub+zip" -> BookFormat.EPUB
                        name.endsWith(".cbz", ignoreCase = true) || mime == "application/x-cbz" -> BookFormat.CBZ
                        else -> null
                    }
                    if (format != null) {
                        // Guess title and author from filename: e.g. "Author - Title.epub" or "Title.epub"
                        var title = name.substringBeforeLast(".")
                        var author = "Unknown Author"
                        if (title.contains("-")) {
                            val parts = title.split("-", limit = 2)
                            author = parts[0].trim()
                            title = parts[1].trim()
                        }
                        results.add(
                            Book(
                                uri = fileUri.toString(),
                                name = name,
                                title = title,
                                author = author,
                                description = "Format: $format, Size: ${formatSize(size)}",
                                format = format,
                                byteSize = size
                            )
                        )
                    }
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    // --- Quotes ---
    private fun loadQuotes(): List<Quote> {
        return try {
            if (quotesFile.exists()) {
                json.decodeFromString<List<Quote>>(quotesFile.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun saveQuotes(list: List<Quote>) = withContext(Dispatchers.IO) {
        _quotes.value = list
        try {
            quotesFile.writeText(json.encodeToString(list))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addQuote(text: String, book: Book) = withContext(Dispatchers.IO) {
        val current = _quotes.value.toMutableList()
        val quote = Quote(
            id = java.util.UUID.randomUUID().toString(),
            text = text,
            bookUri = book.uri,
            bookTitle = book.title,
            bookAuthor = book.author,
            timestamp = System.currentTimeMillis()
        )
        current.add(0, quote) // Add to beginning (newest first)
        saveQuotes(current)
    }

    suspend fun deleteQuote(id: String) = withContext(Dispatchers.IO) {
        val current = _quotes.value.filter { it.id != id }
        saveQuotes(current)
    }

    // --- Highlights ---
    private fun loadHighlights(): List<Highlight> {
        return try {
            if (highlightsFile.exists()) {
                json.decodeFromString<List<Highlight>>(highlightsFile.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun saveHighlights(list: List<Highlight>) = withContext(Dispatchers.IO) {
        _highlights.value = list
        try {
            highlightsFile.writeText(json.encodeToString(list))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addHighlight(bookUri: String, chapterIndex: Int, startOffset: Int, endOffset: Int, text: String) = withContext(Dispatchers.IO) {
        val current = _highlights.value.toMutableList()
        val highlight = Highlight(
            id = java.util.UUID.randomUUID().toString(),
            bookUri = bookUri,
            chapterIndex = chapterIndex,
            startOffset = startOffset,
            endOffset = endOffset,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        current.add(highlight)
        saveHighlights(current)
    }

    suspend fun deleteHighlight(id: String) = withContext(Dispatchers.IO) {
        val current = _highlights.value.filter { it.id != id }
        saveHighlights(current)
    }

    suspend fun removeHighlightsInRange(bookUri: String, chapterIndex: Int, startOffset: Int, endOffset: Int) = withContext(Dispatchers.IO) {
        val current = _highlights.value.filter { h ->
            !(h.bookUri == bookUri && h.chapterIndex == chapterIndex &&
              h.startOffset < endOffset && h.endOffset > startOffset)
        }
        saveHighlights(current)
    }
}
