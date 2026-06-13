package com.example.andbook.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class EpubLink(
    val start: Int,
    val end: Int,
    val target: String
)

data class ChapterContentResult(
    val content: String,
    val links: List<EpubLink>,
    val anchors: Map<String, Int>
)

data class EpubChapter(
    val title: String,
    var content: String, // Plain text content of the chapter
    val zipPath: String = "",
    var links: List<EpubLink> = emptyList(),
    var anchors: Map<String, Int> = emptyMap()
)

data class EpubBook(
    val title: String,
    val author: String,
    val description: String,
    val cover: Bitmap?,
    val chapters: List<EpubChapter>
)

object EpubParser {

    fun getCover(context: Context, uri: Uri): Bitmap? {
        try {
            val metadataFiles = readMetadataFiles(context, uri)
            val containerXml = metadataFiles["META-INF/container.xml"] ?: return null
            val opfPath = parseContainerXml(containerXml) ?: return null
            val cleanedOpfPath = cleanPath(opfPath)
            val opfBytes = metadataFiles[cleanedOpfPath] ?: return null
            val opfData = parseOpfXml(opfBytes)
            
            val opfDir = if (cleanedOpfPath.contains("/")) cleanedOpfPath.substringBeforeLast("/") + "/" else ""
            
            val targetPaths = mutableSetOf<String>()
            var coverPath: String? = null
            if (opfData.coverId != null) {
                val coverHref = opfData.manifest[opfData.coverId]
                if (coverHref != null) {
                    coverPath = cleanPath(opfDir + coverHref)
                    targetPaths.add(coverPath)
                }
            }
            
            var fallbackCoverPath: String? = null
            val coverHref = opfData.manifest.values.firstOrNull { it.contains("cover", ignoreCase = true) }
            if (coverHref != null) {
                fallbackCoverPath = cleanPath(opfDir + coverHref)
                targetPaths.add(fallbackCoverPath)
            }
            
            if (targetPaths.isEmpty()) return null
            
            val zipEntries = readSelectiveFiles(context, uri, targetPaths)
            
            var coverBitmap: Bitmap? = null
            if (coverPath != null) {
                val coverBytes = zipEntries[coverPath]
                if (coverBytes != null) {
                    coverBitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                }
            }
            if (coverBitmap == null && fallbackCoverPath != null) {
                val coverBytes = zipEntries[fallbackCoverPath]
                if (coverBytes != null) {
                    coverBitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                }
            }
            return coverBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
 
    fun parse(context: Context, uri: Uri): EpubBook {
        // Pass 1: Extract META-INF/container.xml and any .opf file
        val metadataFiles = readMetadataFiles(context, uri)
        
        val containerXml = metadataFiles["META-INF/container.xml"]
            ?: throw Exception("Invalid EPUB: container.xml missing")
        val opfPath = parseContainerXml(containerXml)
            ?: throw Exception("Invalid EPUB: OPF file path not found")
        val cleanedOpfPath = cleanPath(opfPath)
        
        val opfBytes = metadataFiles[cleanedOpfPath]
            ?: throw Exception("OPF file missing at $opfPath")
        
        val opfData = parseOpfXml(opfBytes)
        
        // OPF dir path to resolve relative hrefs
        val opfDir = if (cleanedOpfPath.contains("/")) cleanedOpfPath.substringBeforeLast("/") + "/" else ""
        
        // Collect target paths for Pass 2 (spine chapters & cover image)
        val targetPaths = mutableSetOf<String>()
        
        // Cover path
        var coverPath: String? = null
        if (opfData.coverId != null) {
            val coverHref = opfData.manifest[opfData.coverId]
            if (coverHref != null) {
                coverPath = cleanPath(opfDir + coverHref)
                targetPaths.add(coverPath)
            }
        }
        
        // Fallback cover search: look for any image with "cover" in name in manifest
        var fallbackCoverPath: String? = null
        val coverHref = opfData.manifest.values.firstOrNull { it.contains("cover", ignoreCase = true) }
        if (coverHref != null) {
            fallbackCoverPath = cleanPath(opfDir + coverHref)
            targetPaths.add(fallbackCoverPath)
        }
        
        // Spine paths
        val spinePaths = mutableListOf<String>() // maintain order
        for (itemId in opfData.spine) {
            val href = opfData.manifest[itemId] ?: continue
            val chapterZipPath = cleanPath(opfDir + href)
            spinePaths.add(chapterZipPath)
            targetPaths.add(chapterZipPath)
        }
        
        // Pass 2: Extract only the needed files
        val zipEntries = readSelectiveFiles(context, uri, targetPaths)
        
        // 3. Extract cover image if available
        var coverBitmap: Bitmap? = null
        if (coverPath != null) {
            val coverBytes = zipEntries[coverPath]
            if (coverBytes != null) {
                coverBitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
            }
        }
        if (coverBitmap == null && fallbackCoverPath != null) {
            val coverBytes = zipEntries[fallbackCoverPath]
            if (coverBytes != null) {
                coverBitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
            }
        }
        
        // 4. Parse spine chapters (lazy content loading, only parse title)
        val chapters = mutableListOf<EpubChapter>()
        var chapterIndex = 1
        for (chapterZipPath in spinePaths) {
            val htmlBytes = zipEntries[chapterZipPath] ?: continue
            val htmlText = String(htmlBytes, Charsets.UTF_8)
            val title = extractTitle(htmlText) ?: "Chapter $chapterIndex"
            chapters.add(EpubChapter(title, "", chapterZipPath))
            chapterIndex++
        }
        
        return EpubBook(
            title = opfData.title.ifEmpty { "Unknown Title" },
            author = opfData.author.ifEmpty { "Unknown Author" },
            description = opfData.description.ifEmpty { "No description available." },
            cover = coverBitmap,
            chapters = chapters
        )
    }

    fun loadChapterContent(context: Context, uri: Uri, chapterZipPath: String): ChapterContentResult {
        var inputStream: InputStream? = null
        var zis: ZipInputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return ChapterContentResult("", emptyList(), emptyMap())
            zis = ZipInputStream(inputStream)
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val cleanName = cleanPath(entry.name)
                    if (cleanName == chapterZipPath) {
                        val bos = ByteArrayOutputStream()
                        zis.copyTo(bos)
                        val htmlBytes = bos.toByteArray()
                        val htmlText = String(htmlBytes, Charsets.UTF_8)
                        return parseHtmlToContentResult(htmlText)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { zis?.close() } catch (e: Exception) {}
            try { inputStream?.close() } catch (e: Exception) {}
        }
        return ChapterContentResult("", emptyList(), emptyMap())
    }
 
    private fun readMetadataFiles(context: Context, uri: Uri): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        var inputStream: InputStream? = null
        var zis: ZipInputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return map
            zis = ZipInputStream(inputStream)
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val cleanName = cleanPath(entry.name)
                    if (cleanName == "META-INF/container.xml" || cleanName.endsWith(".opf", ignoreCase = true)) {
                        val bos = ByteArrayOutputStream()
                        zis.copyTo(bos)
                        map[cleanName] = bos.toByteArray()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { zis?.close() } catch (e: Exception) {}
            try { inputStream?.close() } catch (e: Exception) {}
        }
        return map
    }
 
    private fun readSelectiveFiles(context: Context, uri: Uri, targetPaths: Set<String>): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        var inputStream: InputStream? = null
        var zis: ZipInputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return map
            zis = ZipInputStream(inputStream)
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val cleanName = cleanPath(entry.name)
                    if (targetPaths.contains(cleanName)) {
                        val bos = ByteArrayOutputStream()
                        zis.copyTo(bos)
                        map[cleanName] = bos.toByteArray()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { zis?.close() } catch (e: Exception) {}
            try { inputStream?.close() } catch (e: Exception) {}
        }
        return map
    }

    private fun cleanPath(path: String): String {
        // Normalize paths by removing duplicate slashes, leading slashes, and handling "../"
        var normalized = path.replace("\\", "/").replace("//", "/")
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1)
        }
        
        val segments = normalized.split("/")
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
        return cleanSegments.joinToString("/")
    }

    private fun parseContainerXml(xmlBytes: ByteArray): String? {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), null)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "full-path") {
                        return parser.getAttributeValue(i)
                    }
                }
            }
            eventType = parser.next()
        }
        return null
    }

    private data class OpfData(
        var title: String = "",
        var author: String = "",
        var description: String = "",
        var coverId: String? = null,
        val manifest: MutableMap<String, String> = mutableMapOf(), // id -> href
        val spine: MutableList<String> = mutableListOf() // idrefs
    )

    private fun parseOpfXml(xmlBytes: ByteArray): OpfData {
        val data = OpfData()
        val parser = Xml.newPullParser()
        // Handle namespace or entities issues gracefully by bypassing validation
        parser.setInput(ByteArrayInputStream(xmlBytes), null)
        
        var eventType = parser.eventType
        var currentTag = ""
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (id != null && href != null) {
                                // Decode href URL encoding (like %20 to space)
                                val decodedHref = Uri.decode(href)
                                data.manifest[id] = decodedHref
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (idref != null) {
                                data.spine.add(idref)
                            }
                        }
                        "meta" -> {
                            val name = parser.getAttributeValue(null, "name")
                            val content = parser.getAttributeValue(null, "content")
                            if (name == "cover" && content != null) {
                                data.coverId = content
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text.trim()
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "dc:title", "title" -> data.title = text
                            "dc:creator", "creator" -> data.author = text
                            "dc:description", "description" -> data.description = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return data
    }

    private fun extractTitle(htmlText: String): String? {
        val titleRegex = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
        val match = titleRegex.find(htmlText)
        return match?.groups?.get(1)?.value?.trim()
    }

    fun parseHtmlToContentResult(html: String): ChapterContentResult {
        var cleanedHtml = html
        
        // 1. Remove style and script blocks completely
        cleanedHtml = cleanedHtml.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        cleanedHtml = cleanedHtml.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        
        // 2. Remove everything before body if present
        val bodyMatch = Regex("<body[^>]*>", RegexOption.IGNORE_CASE).find(cleanedHtml)
        if (bodyMatch != null) {
            cleanedHtml = cleanedHtml.substring(bodyMatch.range.last + 1)
        } else {
            val headEndIndex = cleanedHtml.indexOf("</head>", ignoreCase = true)
            if (headEndIndex >= 0) {
                cleanedHtml = cleanedHtml.substring(headEndIndex + 7)
            }
        }
        
        val builder = PlainTextBuilder()
        val links = mutableListOf<EpubLink>()
        val anchors = mutableMapOf<String, Int>()
        val activeLinkStack = java.util.Stack<Pair<String, Int>>() // href, startOffset
        
        val hrefRegex = Regex("""href=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val idRegex = Regex("""id=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        
        var i = 0
        val len = cleanedHtml.length
        while (i < len) {
            val nextOpen = cleanedHtml.indexOf('<', i)
            if (nextOpen == -1) {
                builder.appendText(decodeHtmlEntities(cleanedHtml.substring(i)))
                break
            }
            
            if (nextOpen > i) {
                builder.appendText(decodeHtmlEntities(cleanedHtml.substring(i, nextOpen)))
            }
            
            val nextClose = cleanedHtml.indexOf('>', nextOpen)
            if (nextClose == -1) {
                builder.appendText(decodeHtmlEntities(cleanedHtml.substring(nextOpen)))
                break
            }
            
            val tagContent = cleanedHtml.substring(nextOpen + 1, nextClose).trim()
            i = nextClose + 1
            
            if (tagContent.isEmpty()) continue
            
            // Extract ID attribute if present
            val idMatch = idRegex.find(tagContent)
            val tagId = idMatch?.groups?.get(1)?.value
            if (tagId != null) {
                anchors[tagId] = builder.length
            }
            
            val tagParts = tagContent.split(Regex("\\s+"), limit = 2)
            val tagName = tagParts[0].lowercase()
            
            when {
                tagName == "a" -> {
                    val hrefMatch = hrefRegex.find(tagContent)
                    val href = hrefMatch?.groups?.get(1)?.value ?: ""
                    activeLinkStack.push(Pair(href, builder.length))
                }
                tagName == "/a" -> {
                    if (activeLinkStack.isNotEmpty()) {
                        val (href, startOffset) = activeLinkStack.pop()
                        val endOffset = builder.length
                        if (endOffset > startOffset && href.isNotEmpty()) {
                            links.add(EpubLink(startOffset, endOffset, href))
                        }
                    }
                }
                tagName == "p" || tagName == "/p" || tagName == "div" || tagName == "/div" ||
                tagName == "h1" || tagName == "h2" || tagName == "h3" || tagName == "h4" ||
                tagName == "h5" || tagName == "h6" || tagName == "blockquote" || tagName == "/blockquote" -> {
                    builder.appendBlockBreak()
                }
                tagName == "br" -> {
                    builder.appendLineBreak()
                }
                tagName == "li" -> {
                    builder.appendLineBreak()
                    builder.appendText("• ")
                }
                tagName == "/li" -> {
                    builder.appendLineBreak()
                }
            }
        }
        
        return ChapterContentResult(builder.toString(), links, anchors)
    }

    private fun decodeHtmlEntities(text: String): String {
        return text.replace("&nbsp;", " ")
            .replace("\u00A0", " ")
            .replace("\u2007", " ")
            .replace("\u202F", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&ldquo;", "\"")
            .replace("&rdquo;", "\"")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&middot;", "·")
            .replace("&bull;", "•")
    }

    private class PlainTextBuilder {
        private val sb = java.lang.StringBuilder()
        
        val length: Int
            get() = sb.length
            
        fun appendText(text: String) {
            if (text.isEmpty()) return
            
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\n' || c == '\r' || c.isWhitespace() || c == '\u00A0' || c == '\u2007' || c == '\u202F') {
                    appendSpace()
                } else {
                    sb.append(c)
                }
                i++
            }
        }
        
        fun appendSpace() {
            if (sb.isNotEmpty() && sb[sb.length - 1] != ' ' && sb[sb.length - 1] != '\n') {
                sb.append(' ')
            }
        }
        
        fun appendLineBreak() {
            while (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
                sb.deleteCharAt(sb.length - 1)
            }
            if (sb.isNotEmpty() && sb[sb.length - 1] != '\n') {
                sb.append('\n')
            }
        }
        
        fun appendBlockBreak() {
            while (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
                sb.deleteCharAt(sb.length - 1)
            }
            
            var newlineCount = 0
            var idx = sb.length - 1
            while (idx >= 0 && sb[idx] == '\n') {
                newlineCount++
                idx--
            }
            
            if (sb.isNotEmpty()) {
                if (newlineCount == 0) {
                    sb.append("\n\n")
                } else if (newlineCount == 1) {
                    sb.append("\n")
                }
            }
        }
        
        override fun toString(): String {
            return sb.toString().trim()
        }
    }
}
