package com.example.andbook.data

import kotlinx.serialization.Serializable

@Serializable
enum class BookFormat {
    PDF, EPUB, CBZ
}

@Serializable
data class Book(
    val uri: String, // Android SAF Uri string (primary key)
    val name: String, // Filename
    val title: String,
    val author: String,
    val description: String,
    val format: BookFormat,
    val coverPath: String? = null, // Temporary file path for cache, if any
    val byteSize: Long = 0
)

@Serializable
data class ReadingProgress(
    val bookUri: String,
    val lastPage: Int, // 0-based page index
    val lastCharOffset: Int = 0, // Used for EPUB precise scroll mapping
    val totalPages: Int,
    val lastReadTime: Long,
    val completionPercentage: Float = 0f
)

@Serializable
enum class ReaderTheme {
    LIGHT_COFFEE,
    DARK_COFFEE,
    AMOLED
}

@Serializable
data class AppSettings(
    val theme: ReaderTheme = ReaderTheme.LIGHT_COFFEE,
    val fontName: String = "lora",
    val systemFontName: String = "nyght_serif",
    val fontSize: Float = 24f,
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val booksFolderUri: String? = null,
    val booksFolderName: String? = null,
    val dailyReadingGoalMinutes: Int = 30,
    val readingTimeTodaySeconds: Long = 0,
    val lastReadDateStr: String = "",
    val totalReadingTimeSeconds: Long = 0,
    val readerLineHeight: Float = 1.4f,
    val readerTextAlignment: Int = 0, // 0 = Justify, 1 = Left, 2 = Center
    val showReaderBattery: Boolean = true,
    val showReaderTime: Boolean = true,
    val showReaderProgressPercent: Boolean = true,
    val autoPlayNextChapter: Boolean = false,
    val showTtsHighlight: Boolean = true,
    val use12HourClockFormat: Boolean = false,
    val animationSpeedMultiplier: Float = 1.0f,
    val themeAnimationSpeedMultiplier: Float = 0.3f,
    val zoomAnimationSpeedMultiplier: Float = 1.0f,
    val dailyHistory: Map<String, Long> = emptyMap()
)


@Serializable
data class HistoryItem(
    val book: Book,
    val progress: ReadingProgress
)

@Serializable
data class Quote(
    val id: String,
    val text: String,
    val bookUri: String,
    val bookTitle: String,
    val bookAuthor: String,
    val timestamp: Long
)

@Serializable
data class Highlight(
    val id: String,
    val bookUri: String,
    val chapterIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val timestamp: Long
)

