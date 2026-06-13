package com.example.andbook.reader

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

object BookPaginator {

    fun paginate(
        text: String,
        measurer: TextMeasurer,
        style: TextStyle,
        widthPx: Int,
        heightPx: Int
    ): List<String> {
        if (text.isBlank() || widthPx <= 0 || heightPx <= 0) {
            return listOf(text)
        }

        val pages = mutableListOf<String>()
        var currentOffset = 0
        val textLength = text.length

        // Safety limit to prevent infinite loops in edge cases
        var iterations = 0
        val maxIterations = 5000

        while (currentOffset < textLength && iterations < maxIterations) {
            iterations++
            
            // Skip leading whitespace/newlines for cleaner pages and to prevent empty measurements
            while (currentOffset < textLength && text[currentOffset].isWhitespace()) {
                currentOffset++
            }
            if (currentOffset >= textLength) break

            val remainingLength = textLength - currentOffset
            var windowSize = 3000.coerceAtMost(remainingLength)
            var layoutResult: TextLayoutResult? = null
            var lastFittingLine = -1
            var lineCount = 0

            // Dynamically grow window size if the entire window fits on the page
            while (windowSize <= remainingLength) {
                val chunk = text.substring(currentOffset, currentOffset + windowSize)
                val chunkLayout = measurer.measure(
                    text = chunk,
                    style = style,
                    constraints = Constraints(
                        maxWidth = widthPx,
                        maxHeight = Constraints.Infinity
                    ),
                    overflow = TextOverflow.Clip
                )
                
                lineCount = chunkLayout.lineCount
                if (lineCount == 0) {
                    layoutResult = chunkLayout
                    break
                }

                // Check if the last line of the chunk fits within heightPx
                val lastLineBottom = chunkLayout.getLineBottom(lineCount - 1)
                if (lastLineBottom <= heightPx && windowSize < remainingLength) {
                    // The whole chunk fits and we have more text, so grow the window size
                    layoutResult = chunkLayout
                    windowSize = (windowSize * 2).coerceAtMost(remainingLength)
                } else {
                    // The chunk overflows or we reached the end of the text
                    layoutResult = chunkLayout
                    
                    // Find the last line that fits
                    for (i in 0 until lineCount) {
                        val lineBottom = chunkLayout.getLineBottom(i)
                        if (lineBottom <= heightPx) {
                            lastFittingLine = i
                        } else {
                            break
                        }
                    }
                    break
                }
            }

            val result = layoutResult ?: break
            if (lineCount == 0) {
                val pageText = text.substring(currentOffset, currentOffset + windowSize)
                if (pageText.isNotBlank()) {
                    pages.add(pageText)
                }
                currentOffset += windowSize
                continue
            }

            if (lastFittingLine == -1) {
                // If even the first line doesn't fit, take at least one character to progress
                var lineEnd = result.getLineEnd(0)
                if (lineEnd <= 0) lineEnd = 1
                val pageText = text.substring(currentOffset, currentOffset + lineEnd)
                if (pageText.isNotBlank()) {
                    pages.add(pageText)
                }
                currentOffset += lineEnd
            } else if (lastFittingLine == lineCount - 1 && windowSize == remainingLength) {
                // All remaining text fits on this page
                val pageText = text.substring(currentOffset, currentOffset + windowSize)
                if (pageText.isNotBlank()) {
                    pages.add(pageText)
                }
                break
            } else {
                // Split text at the end of the last fitting line
                val lineEnd = result.getLineEnd(lastFittingLine)
                
                // Fine-tuning: avoid splitting words by adjusting to previous space if close
                var splitIndex = lineEnd
                val chunkLength = result.layoutInput.text.length
                if (splitIndex < chunkLength && !result.layoutInput.text[splitIndex].isWhitespace()) {
                    val lastSpace = result.layoutInput.text.substring(0, splitIndex).lastIndexOf(' ')
                    if (lastSpace > 0 && (splitIndex - lastSpace) < 20) {
                        splitIndex = lastSpace + 1
                    }
                }
                
                if (splitIndex <= 0) {
                    splitIndex = if (lineEnd > 0) lineEnd else 1
                }
                
                val pageText = text.substring(currentOffset, currentOffset + splitIndex)
                if (pageText.isNotBlank()) {
                    pages.add(pageText)
                }
                currentOffset += splitIndex
            }
        }

        return if (pages.isEmpty()) listOf("") else pages
    }
}
