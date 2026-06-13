package com.example.andbook.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.ui.text.TextRange
import com.example.andbook.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

sealed class TtsControlAction {
    object Play : TtsControlAction()
    object Pause : TtsControlAction()
    object SkipNext : TtsControlAction()
    object SkipPrev : TtsControlAction()
    data class SeekTo(val charOffset: Int) : TtsControlAction()
    data class SetSpeed(val speed: Float) : TtsControlAction()
}

class TtsService : Service() {

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var mediaSession: MediaSession? = null
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var currentStartIndex = 0

    companion object {
        var instance: TtsService? = null
            private set

        val isServiceRunning = MutableStateFlow(false)
        val isPlaying = MutableStateFlow(false)
        val currentBookUri = MutableStateFlow<String?>(null)
        val currentBookTitle = MutableStateFlow<String?>(null)
        val currentChapterTitle = MutableStateFlow<String?>(null)
        val currentPageText = MutableStateFlow("")
        val currentWordRange = MutableStateFlow<TextRange?>(null)
        val ttsSpeed = MutableStateFlow(1.0f)
        val ttsPitch = MutableStateFlow(1.0f)
        val currentCover = MutableStateFlow<Bitmap?>(null)

        val controlActions = MutableSharedFlow<TtsControlAction>(extraBufferCapacity = 64)

        fun start(context: Context, bookUri: String, bookTitle: String, chapterTitle: String, pageText: String, speed: Float, pitch: Float, cover: Bitmap?) {
            currentBookUri.value = bookUri
            currentBookTitle.value = bookTitle
            currentChapterTitle.value = chapterTitle
            currentPageText.value = pageText
            ttsSpeed.value = speed
            ttsPitch.value = pitch
            currentCover.value = cover

            val intent = Intent(context, TtsService::class.java).apply {
                action = "ACTION_START"
            }
            context.startForegroundService(intent)
        }

        fun updateTrackInfo(bookUri: String, bookTitle: String, chapterTitle: String, pageText: String) {
            currentBookUri.value = bookUri
            currentBookTitle.value = bookTitle
            currentChapterTitle.value = chapterTitle
            
            // Only reset word range if the text actually changed
            if (currentPageText.value != pageText) {
                currentPageText.value = pageText
                currentWordRange.value = null
            }
            
            instance?.let { service ->
                service.updateMetadata()
                if (isPlaying.value) {
                    service.startSpeaking(resume = false)
                } else {
                    service.updatePlaybackState()
                }
            }
        }

        fun stopPlayback() {
            instance?.stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isServiceRunning.value = true

        createNotificationChannel()
        initMediaSession()
        initTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_START" -> {
                startForeground(1001, buildNotification())
                updateMetadata()
                updatePlaybackState()
            }
            "ACTION_PLAY" -> mediaSessionCallback.onPlay()
            "ACTION_PAUSE" -> mediaSessionCallback.onPause()
            "ACTION_NEXT" -> mediaSessionCallback.onSkipToNext()
            "ACTION_PREV" -> mediaSessionCallback.onSkipToPrevious()
            "ACTION_STOP" -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        
        tts?.shutdown()
        tts = null
        isTtsInitialized = false

        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null

        isPlaying.value = false
        isServiceRunning.value = false
        currentWordRange.value = null
        instance = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "tts_channel",
            "Audiobook Reader",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls the Text-to-Speech audiobook playback."
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "AndBookMediaSession").apply {
            setCallback(mediaSessionCallback)
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            // Set session activity intent to open the app on click
            val launchIntent = Intent(this@TtsService, MainActivity::class.java).apply {
                putExtra("bookUri", currentBookUri.value)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                this@TtsService,
                99,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setSessionActivity(pi)
            isActive = true
        }
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                tts?.language = Locale.getDefault()
                setupTtsListener()
                
                // If started and we have text, start speaking
                if (isPlaying.value) {
                    startSpeaking(resume = false)
                } else {
                    // Start playing by default when initialized
                    mediaSessionCallback.onPlay()
                }
            }
        }
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == "page_tts") {
                    serviceScope.launch(Dispatchers.Main) {
                        currentWordRange.value = null
                        controlActions.emit(TtsControlAction.SkipNext)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}

            override fun onError(utteranceId: String?, errorCode: Int) {
                super.onError(utteranceId, errorCode)
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId == "page_tts") {
                    serviceScope.launch(Dispatchers.Main) {
                        val absoluteStart = currentStartIndex + start
                        val absoluteEnd = currentStartIndex + end
                        currentWordRange.value = TextRange(absoluteStart, absoluteEnd)
                        updatePlaybackState()
                    }
                }
            }
        })
    }

    fun startSpeaking(resume: Boolean) {
        if (!isTtsInitialized) {
            isPlaying.value = true
            return
        }

        isPlaying.value = true
        val text = currentPageText.value
        if (text.isBlank() || text == "[COVER_IMAGE]") {
            // Trigger skip next if page is blank/cover
            serviceScope.launch(Dispatchers.Main) {
                controlActions.emit(TtsControlAction.SkipNext)
            }
            return
        }

        if (!resume) {
            currentStartIndex = 0
        } else {
            currentStartIndex = currentWordRange.value?.start ?: 0
        }

        val speechText = if (currentStartIndex in text.indices) {
            text.substring(currentStartIndex)
        } else {
            text
        }

        tts?.apply {
            setSpeechRate(ttsSpeed.value)
            setPitch(ttsPitch.value)
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "page_tts")
            }
            speak(speechText, TextToSpeech.QUEUE_FLUSH, params, "page_tts")
        }

        updateNotification()
        updatePlaybackState()
    }

    fun pauseSpeaking() {
        isPlaying.value = false
        tts?.stop()
        updateNotification()
        updatePlaybackState()
    }

    fun speakFromOffset(charOffset: Int) {
        currentStartIndex = charOffset.coerceIn(0, currentPageText.value.length)
        currentWordRange.value = TextRange(currentStartIndex, currentStartIndex)
        if (isPlaying.value) {
            startSpeaking(resume = true)
        } else {
            updatePlaybackState()
        }
    }

    fun updateSpeed(speed: Float) {
        ttsSpeed.value = speed
        if (isPlaying.value) {
            startSpeaking(resume = true)
        } else {
            updatePlaybackState()
        }
    }

    private fun updateMetadata() {
        val totalLength = currentPageText.value.length
        val durationMs = totalLength * 100L // Map 1 char to 100ms for seekbar scaling

        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentBookTitle.value ?: "AndBook")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentChapterTitle.value ?: "Audiobook")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)

        currentCover.value?.let {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, it)
        }

        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun updatePlaybackState() {
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_SET_PLAYBACK_SPEED
            )

        val state = if (isPlaying.value) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val currentPosition = (currentWordRange.value?.start ?: 0) * 100L

        stateBuilder.setState(
            state,
            currentPosition,
            ttsSpeed.value
        )

        // Select speed badge icon resource ID dynamically
        val speedIconRes = when (ttsSpeed.value) {
            1.25f -> com.example.andbook.R.drawable.ic_speed_1_25
            1.5f -> com.example.andbook.R.drawable.ic_speed_1_5
            1.75f -> com.example.andbook.R.drawable.ic_speed_1_75
            2.0f -> com.example.andbook.R.drawable.ic_speed_2_0
            else -> com.example.andbook.R.drawable.ic_speed_1_0
        }

        val customActionSpeed = PlaybackState.CustomAction.Builder(
            "action_change_speed",
            "Speed: ${ttsSpeed.value}x",
            speedIconRes
        ).build()
        stateBuilder.addCustomAction(customActionSpeed)

        mediaSession?.setPlaybackState(stateBuilder.build())
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, buildNotification())
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("bookUri", currentBookUri.value)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            99,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, "tts_channel")
            .setContentTitle(currentBookTitle.value ?: "AndBook")
            .setContentText(currentChapterTitle.value ?: "Reading aloud...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying.value)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        currentCover.value?.let {
            builder.setLargeIcon(it)
        }

        // Button 0: Previous
        builder.addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_media_previous,
                "Previous Page",
                createPendingIntent(TtsControlAction.SkipPrev)
            ).build()
        )

        // Button 1: Play/Pause
        val playPauseIcon = if (isPlaying.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying.value) "Pause" else "Play"
        builder.addAction(
            Notification.Action.Builder(
                playPauseIcon,
                playPauseTitle,
                createPendingIntent(if (isPlaying.value) TtsControlAction.Pause else TtsControlAction.Play)
            ).build()
        )

        // Button 2: Next
        builder.addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_media_next,
                "Next Page",
                createPendingIntent(TtsControlAction.SkipNext)
            ).build()
        )

        return builder.build()
    }

    private fun createPendingIntent(action: TtsControlAction): PendingIntent {
        val intent = Intent(this, TtsService::class.java).apply {
            when (action) {
                is TtsControlAction.Play -> this.action = "ACTION_PLAY"
                is TtsControlAction.Pause -> this.action = "ACTION_PAUSE"
                is TtsControlAction.SkipNext -> this.action = "ACTION_NEXT"
                is TtsControlAction.SkipPrev -> this.action = "ACTION_PREV"
                else -> {}
            }
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val mediaSessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            startSpeaking(resume = true)
            serviceScope.launch { controlActions.emit(TtsControlAction.Play) }
        }

        override fun onPause() {
            pauseSpeaking()
            serviceScope.launch { controlActions.emit(TtsControlAction.Pause) }
        }

        override fun onSkipToNext() {
            serviceScope.launch { controlActions.emit(TtsControlAction.SkipNext) }
        }

        override fun onSkipToPrevious() {
            serviceScope.launch { controlActions.emit(TtsControlAction.SkipPrev) }
        }

        override fun onSeekTo(pos: Long) {
            val charOffset = (pos / 100).toInt().coerceIn(0, currentPageText.value.length)
            speakFromOffset(charOffset)
            serviceScope.launch { controlActions.emit(TtsControlAction.SeekTo(charOffset)) }
        }

        override fun onSetPlaybackSpeed(speed: Float) {
            val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
            updateSpeed(clampedSpeed)
            serviceScope.launch { controlActions.emit(TtsControlAction.SetSpeed(clampedSpeed)) }
        }

        override fun onCustomAction(action: String, extras: Bundle?) {
            if (action == "action_change_speed") {
                val nextSpeed = when (ttsSpeed.value) {
                    1.0f -> 1.25f
                    1.25f -> 1.5f
                    1.5f -> 1.75f
                    1.75f -> 2.0f
                    else -> 1.0f
                }
                updateSpeed(nextSpeed)
                serviceScope.launch { controlActions.emit(TtsControlAction.SetSpeed(nextSpeed)) }
            }
        }
    }
}
