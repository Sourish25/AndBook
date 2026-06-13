# Graph Report - .  (2026-06-14)

## Corpus Check
- Corpus is ~31,310 words - fits in a single context window. You may not need a graph.

## Summary
- 325 nodes · 594 edges · 23 communities (21 shown, 2 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 36 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Text-to-Speech Service|Text-to-Speech Service]]
- [[_COMMUNITY_Data Repository & Library Scanner|Data Repository & Library Scanner]]
- [[_COMMUNITY_Main Screen UI & Dashboard Navigation|Main Screen UI & Dashboard Navigation]]
- [[_COMMUNITY_EPUB Book Parser & Loader|EPUB Book Parser & Loader]]
- [[_COMMUNITY_App Entry & Navigation Routes|App Entry & Navigation Routes]]
- [[_COMMUNITY_Reader UI Screen & Page Rendering|Reader UI Screen & Page Rendering]]
- [[_COMMUNITY_Reading Statistics & Goals Dashboard|Reading Statistics & Goals Dashboard]]
- [[_COMMUNITY_Quotes Viewer & Sharing Image Generator|Quotes Viewer & Sharing Image Generator]]
- [[_COMMUNITY_TTS Engine Lifecycle Helper|TTS Engine Lifecycle Helper]]
- [[_COMMUNITY_Main Screen ViewModel & Tests|Main Screen ViewModel & Tests]]
- [[_COMMUNITY_CBZ Comic Book Parser|CBZ Comic Book Parser]]
- [[_COMMUNITY_Book Detail Dialog UI|Book Detail Dialog UI]]
- [[_COMMUNITY_Core Data Models|Core Data Models]]
- [[_COMMUNITY_Book Cover Cache Manager|Book Cover Cache Manager]]
- [[_COMMUNITY_Book Content Pager & Layout|Book Content Pager & Layout]]
- [[_COMMUNITY_PDF Document Renderer Helper|PDF Document Renderer Helper]]
- [[_COMMUNITY_Main Screen UI Integration Tests|Main Screen UI Integration Tests]]
- [[_COMMUNITY_Onboarding Video Animation Script|Onboarding Video Animation Script]]

## God Nodes (most connected - your core abstractions)
1. `DataRepository` - 33 edges
2. `TtsService` - 21 edges
3. `ReaderScreen()` - 20 edges
4. `MainScreen()` - 16 edges
5. `EpubParser` - 12 edges
6. `Color` - 11 edges
7. `String` - 10 edges
8. `TtsHelper` - 10 edges
9. `String` - 9 edges
10. `BookDetailDialog()` - 9 edges

## Surprising Connections (you probably didn't know these)
- `ReaderScreen()` --calls--> `ReadingProgress`  [INFERRED]
  app/src/main/java/com/example/andbook/ui/reader/ReaderScreen.kt → app/src/main/java/com/example/andbook/data/Models.kt
- `ReaderScreen()` --calls--> `TextStyle`  [INFERRED]
  app/src/main/java/com/example/andbook/ui/reader/ReaderScreen.kt → app/src/main/java/com/example/andbook/reader/BookPaginator.kt
- `MainNavigation()` --calls--> `MainScreen()`  [INFERRED]
  app/src/main/java/com/example/andbook/Navigation.kt → app/src/main/java/com/example/andbook/ui/main/MainScreen.kt
- `MainNavigation()` --calls--> `ReaderScreen()`  [INFERRED]
  app/src/main/java/com/example/andbook/Navigation.kt → app/src/main/java/com/example/andbook/ui/reader/ReaderScreen.kt
- `MainNavigation()` --calls--> `StatsScreen()`  [INFERRED]
  app/src/main/java/com/example/andbook/Navigation.kt → app/src/main/java/com/example/andbook/ui/settings/StatsScreen.kt

## Import Cycles
- None detected.

## Communities (23 total, 2 thin omitted)

### Community 0 - "Text-to-Speech Service"
Cohesion: 0.09
Nodes (23): Bitmap, Boolean, Context, Float, Int, String, TextToSpeech, IBinder (+15 more)

### Community 1 - "Data Repository & Library Scanner"
Cohesion: 0.12
Nodes (14): AppSettings, Book, Boolean, HistoryItem, Int, List, Long, Quote (+6 more)

### Community 2 - "Main Screen UI & Dashboard Navigation"
Cohesion: 0.13
Nodes (36): Boolean, Modifier, androidx, AppSettings, Book, Boolean, HistoryItem, List (+28 more)

### Community 3 - "EPUB Book Parser & Loader"
Cohesion: 0.16
Nodes (15): Bitmap, Context, Int, String, Uri, ByteArray, Map, ChapterContentResult (+7 more)

### Community 4 - "App Entry & Navigation Routes"
Cohesion: 0.10
Nodes (20): MainActivity, MainNavigation(), Main, Reader, Settings, Stats, android, String (+12 more)

### Community 5 - "Reader UI Screen & Page Rendering"
Cohesion: 0.18
Nodes (25): Any, androidx, Book, Boolean, Context, Float, Int, Modifier (+17 more)

### Community 6 - "Reading Statistics & Goals Dashboard"
Cohesion: 0.19
Nodes (14): androidx, DataRepository, Float, Int, List, Long, Modifier, String (+6 more)

### Community 7 - "Quotes Viewer & Sharing Image Generator"
Cohesion: 0.24
Nodes (13): Bitmap, Context, DataRepository, Modifier, Quote, ReaderTheme, String, EnlargedQuoteDialog() (+5 more)

### Community 8 - "TTS Engine Lifecycle Helper"
Cohesion: 0.17
Nodes (8): Float, Int, String, TextToSpeech, end, TtsHelper, start, Unit

### Community 9 - "Main Screen ViewModel & Tests"
Cohesion: 0.21
Nodes (8): List, String, DataRepository, Flow, MainScreenViewModel, FakeMyModelRepository, MainScreenViewModelTest, ViewModel

### Community 10 - "CBZ Comic Book Parser"
Cohesion: 0.38
Nodes (6): Bitmap, Context, List, String, File, CbzParser

### Community 11 - "Book Detail Dialog UI"
Cohesion: 0.29
Nodes (9): androidx, Boolean, HistoryItem, Long, Modifier, String, BookDetailDialog(), calculateEstimatedTime() (+1 more)

### Community 12 - "Core Data Models"
Cohesion: 0.22
Nodes (8): AppSettings, Book, BookFormat, Highlight, HistoryItem, Quote, ReaderTheme, ReadingProgress

### Community 13 - "Book Cover Cache Manager"
Cohesion: 0.29
Nodes (5): Bitmap, Book, Context, String, BookCoverHelper

### Community 14 - "Book Content Pager & Layout"
Cohesion: 0.25
Nodes (6): Int, List, String, BookPaginator, TextMeasurer, TextStyle

### Community 15 - "PDF Document Renderer Helper"
Cohesion: 0.36
Nodes (5): Bitmap, Context, Int, String, PdfRendererHelper

## Knowledge Gaps
- **69 isolated node(s):** `String`, `StateFlow`, `Boolean`, `MutableList`, `Long` (+64 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **2 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ReaderScreen()` connect `Reader UI Screen & Page Rendering` to `Main Screen UI & Dashboard Navigation`, `App Entry & Navigation Routes`, `Reading Statistics & Goals Dashboard`, `Core Data Models`, `Book Content Pager & Layout`?**
  _High betweenness centrality (0.236) - this node is a cross-community bridge._
- **Why does `Pair` connect `Reading Statistics & Goals Dashboard` to `EPUB Book Parser & Loader`, `Reader UI Screen & Page Rendering`?**
  _High betweenness centrality (0.136) - this node is a cross-community bridge._
- **Why does `Color` connect `Main Screen UI & Dashboard Navigation` to `Book Detail Dialog UI`, `Reader UI Screen & Page Rendering`, `Quotes Viewer & Sharing Image Generator`?**
  _High betweenness centrality (0.129) - this node is a cross-community bridge._
- **Are the 5 inferred relationships involving `ReaderScreen()` (e.g. with `MainNavigation()` and `ReadingProgress`) actually correct?**
  _`ReaderScreen()` has 5 INFERRED edges - model-reasoned connections that need verification._
- **Are the 7 inferred relationships involving `MainScreen()` (e.g. with `MainNavigation()` and `Reader`) actually correct?**
  _`MainScreen()` has 7 INFERRED edges - model-reasoned connections that need verification._
- **What connects `String`, `StateFlow`, `Boolean` to the rest of the system?**
  _69 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Text-to-Speech Service` be split into smaller, more focused modules?**
  _Cohesion score 0.09175377468060394 - nodes in this community are weakly interconnected._