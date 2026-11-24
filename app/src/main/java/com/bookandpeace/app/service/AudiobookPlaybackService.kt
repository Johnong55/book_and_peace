package com.bookandpeace.app.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.bookandpeace.app.data.local.AudioSegmentEntity
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class AudiobookPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

data class PlaybackUiState(
    val scriptId: Long? = null,
    val bookId: Long? = null,
    val bookTitle: String = "",
    val scriptTitle: String = "",
    val segments: List<AudioSegmentEntity> = emptyList(),
    val currentSegmentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerEndEpochMs: Long? = null
) {
    val hasActiveTrack: Boolean get() = scriptId != null && segments.isNotEmpty()
    val currentSegment: AudioSegmentEntity? get() = segments.getOrNull(currentSegmentIndex)
}

class AudioPlayerController(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var pendingPlaybackAction: ((MediaController) -> Unit)? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = controller ?: return
            _state.update {
                it.copy(
                    currentSegmentIndex = ctrl.currentMediaItemIndex.coerceAtLeast(0),
                    positionMs = ctrl.currentPosition.coerceAtLeast(0L),
                    durationMs = ctrl.duration.coerceAtLeast(0L)
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val ctrl = controller ?: return
            _state.update {
                it.copy(
                    durationMs = ctrl.duration.coerceAtLeast(0L),
                    isPlaying = ctrl.isPlaying
                )
            }
        }
    }

    private fun ensureController(onReady: (MediaController) -> Unit) {
        val existing = controller
        if (existing != null && existing.isConnected) {
            onReady(existing)
            return
        }
        pendingPlaybackAction = onReady
        val sessionToken = SessionToken(appContext, ComponentName(appContext, AudiobookPlaybackService::class.java))
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        future.addListener({
            runCatching {
                val mediaController = future.get()
                controller?.removeListener(playerListener)
                controller = mediaController
                mediaController.addListener(playerListener)
                startTicker()
                pendingPlaybackAction?.invoke(mediaController)
                pendingPlaybackAction = null
            }
        }, MoreExecutors.directExecutor())
    }

    fun playScript(
        scriptId: Long,
        bookId: Long,
        bookTitle: String,
        scriptTitle: String,
        segments: List<AudioSegmentEntity>,
        startIndex: Int = 0,
        speed: Float = _state.value.playbackSpeed
    ) {
        val playable = segments.filter { !it.audioFilePath.isNullOrBlank() && File(it.audioFilePath).exists() }
        if (playable.isEmpty()) return

        val safeStart = startIndex.coerceIn(0, playable.lastIndex)
        _state.update {
            it.copy(
                scriptId = scriptId,
                bookId = bookId,
                bookTitle = bookTitle,
                scriptTitle = scriptTitle,
                segments = playable,
                currentSegmentIndex = safeStart,
                playbackSpeed = speed
            )
        }

        ensureController { ctrl ->
            val mediaItems = playable.map { seg ->
                val uri = Uri.fromFile(File(seg.audioFilePath!!))
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId("seg_${seg.id}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("${seg.sectionTitle} — $bookTitle")
                            .setArtist("Book & Peace • Sách nói Kể Chuyện Sâu")
                            .setAlbumTitle(scriptTitle)
                            .build()
                    )
                    .build()
            }
            ctrl.setMediaItems(mediaItems, safeStart, 0L)
            ctrl.playbackParameters = PlaybackParameters(speed)
            ctrl.prepare()
            ctrl.play()
        }
    }

    fun togglePlayPause() {
        ensureController { ctrl ->
            if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
        }
    }

    fun seekToSegment(index: Int) {
        val list = _state.value.segments
        if (index !in list.indices) return
        ensureController { ctrl ->
            ctrl.seekTo(index, 0L)
            ctrl.play()
        }
    }

    fun seekRelative(deltaMs: Long) {
        ensureController { ctrl ->
            val target = (ctrl.currentPosition + deltaMs).coerceIn(0L, ctrl.duration.coerceAtLeast(0L))
            ctrl.seekTo(target)
        }
    }

    fun seekToPosition(positionMs: Long) {
        ensureController { ctrl ->
            ctrl.seekTo(positionMs.coerceAtLeast(0L))
        }
    }

    fun skipNext() {
        ensureController { ctrl ->
            if (ctrl.hasNextMediaItem()) ctrl.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        ensureController { ctrl ->
            if (ctrl.currentPosition > 4000L) {
                ctrl.seekTo(0L)
            } else if (ctrl.hasPreviousMediaItem()) {
                ctrl.seekToPreviousMediaItem()
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.7f, 2.0f)
        _state.update { it.copy(playbackSpeed = clamped) }
        controller?.let { it.playbackParameters = PlaybackParameters(clamped) }
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        val endEpoch = minutes?.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 60_000L }
        _state.update { it.copy(sleepTimerEndEpochMs = endEpoch) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                val ctrl = controller
                if (ctrl != null && ctrl.isConnected) {
                    val endEpoch = _state.value.sleepTimerEndEpochMs
                    if (endEpoch != null && System.currentTimeMillis() >= endEpoch) {
                        ctrl.pause()
                        _state.update { it.copy(sleepTimerEndEpochMs = null, isPlaying = false) }
                    } else {
                        _state.update {
                            it.copy(
                                positionMs = ctrl.currentPosition.coerceAtLeast(0L),
                                durationMs = ctrl.duration.coerceAtLeast(0L),
                                currentSegmentIndex = ctrl.currentMediaItemIndex.coerceAtLeast(0),
                                isPlaying = ctrl.isPlaying
                            )
                        }
                    }
                }
                delay(400L)
            }
        }
    }
}
