package com.bookandpeace.app.ui.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bookandpeace.app.data.local.AmbientSoundMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 100% offline procedural acoustic engine for Book & Peace:
 * 1. Ultra-low-latency realistic physical paper page-turning SFX (3 organic variations + corner lift + chapter turn)
 *    synthesized with multi-band paper-friction & air-swoosh DSP and played via Android SoundPool.
 * 2. Seamless stereo Ambient Reading Soundscapes (Rain, Fireplace, Quiet Night) synthesized continuously
 *    via stereo AudioTrack with smooth fade-in/fade-out ramps and zero clicks.
 */
internal class BookSoundEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile var pageSoundEnabled: Boolean = true
    @Volatile private var currentAmbientMode: AmbientSoundMode = AmbientSoundMode.OFF
    @Volatile private var targetAmbientVolume: Float = 0.48f
    @Volatile private var isPausedByLifecycle: Boolean = false

    private var soundPool: SoundPool? = null
    private val flipSoundIds = IntArray(3)
    private var liftSoundId: Int = 0
    private var chapterSoundId: Int = 0
    private var lastFlipIndex = -1
    private var lastLiftPlayMs = 0L
    private var lastFlipPlayMs = 0L

    private var ambientJob: Job? = null

    init {
        initPageTurnSfxAsync()
    }

    private fun initPageTurnSfxAsync() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
        soundPool = pool

        scope.launch(Dispatchers.IO) {
            runCatching {
                val sfxDir = File(context.cacheDir, "book_sfx_v1").apply { mkdirs() }
                val sr = 22050

                val flipSpecs = listOf(
                    Triple("flip_1.wav", 101, 0.28f),
                    Triple("flip_2.wav", 202, 0.31f),
                    Triple("flip_3.wav", 303, 0.34f)
                )
                flipSpecs.forEachIndexed { idx, (name, seed, durSec) ->
                    val file = File(sfxDir, name)
                    if (!file.exists() || file.length() < 1000L) {
                        val pcm = synthesizePageFlipPcm(sr, durSec, seed, isChapter = false)
                        writeMonoWav(file, pcm, sr)
                    }
                    flipSoundIds[idx] = pool.load(file.absolutePath, 1)
                }

                val liftFile = File(sfxDir, "lift.wav")
                if (!liftFile.exists() || liftFile.length() < 500L) {
                    writeMonoWav(liftFile, synthesizeCornerLiftPcm(sr, 0.095f, 404), sr)
                }
                liftSoundId = pool.load(liftFile.absolutePath, 1)

                val chapterFile = File(sfxDir, "chapter.wav")
                if (!chapterFile.exists() || chapterFile.length() < 1000L) {
                    writeMonoWav(chapterFile, synthesizePageFlipPcm(sr, 0.39f, 505, isChapter = true), sr)
                }
                chapterSoundId = pool.load(chapterFile.absolutePath, 1)
            }
        }
    }

    /** Whisper-soft paper corner catch when dragging a page corner */
    fun playPaperLift() {
        if (!pageSoundEnabled) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLiftPlayMs < 180L) return
        lastLiftPlayMs = now
        val id = liftSoundId
        if (id != 0) {
            soundPool?.play(id, 0.36f, 0.36f, 1, 0, 0.96f + Random.nextFloat() * 0.08f)
        }
    }

    /** Realistic multi-phase paper rustle + air swoosh + gentle landing pat */
    fun playPageFlip() {
        if (!pageSoundEnabled) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFlipPlayMs < 90L) return
        lastFlipPlayMs = now

        var idx = Random.nextInt(flipSoundIds.size)
        if (idx == lastFlipIndex) idx = (idx + 1) % flipSoundIds.size
        lastFlipIndex = idx

        val id = flipSoundIds[idx]
        if (id != 0) {
            val rate = 0.95f + Random.nextFloat() * 0.10f
            soundPool?.play(id, 0.58f, 0.58f, 1, 0, rate)
        }
    }

    /** Slightly fuller paper fold for chapter transitions */
    fun playChapterTurn() {
        if (!pageSoundEnabled) return
        val id = chapterSoundId
        if (id != 0) {
            soundPool?.play(id, 0.65f, 0.65f, 1, 0, 0.96f)
        } else {
            playPageFlip()
        }
    }

    fun updateAmbient(mode: AmbientSoundMode, volume: Float) {
        targetAmbientVolume = volume.coerceIn(0.05f, 1.0f)
        if (mode != currentAmbientMode) {
            currentAmbientMode = mode
            restartAmbientIfNeeded()
        }
    }

    fun setLifecyclePaused(paused: Boolean) {
        if (isPausedByLifecycle == paused) return
        isPausedByLifecycle = paused
        restartAmbientIfNeeded()
    }

    private fun restartAmbientIfNeeded() {
        ambientJob?.cancel()
        ambientJob = null
        val mode = currentAmbientMode
        if (mode == AmbientSoundMode.OFF || isPausedByLifecycle) return

        ambientJob = scope.launch(Dispatchers.IO) {
            streamAmbientLoop(mode)
        }
    }

    private fun streamAmbientLoop(mode: AmbientSoundMode) {
        val sampleRate = 24000
        val minBufBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBufBytes * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return

        val generator = createAmbientGenerator(mode, sampleRate)
        val chunkFrames = 1024
        val pcmBuffer = ShortArray(chunkFrames * 2)
        var smoothGain = 0f

        try {
            track.play()
            while (scope.isActive && currentAmbientMode == mode && !isPausedByLifecycle) {
                val desiredGain = targetAmbientVolume
                for (i in 0 until chunkFrames) {
                    // Ultra-smooth gain ramp (~150ms) so volume sliders & start/stop never click
                    smoothGain += (desiredGain - smoothGain) * 0.0012f
                    val (l, r) = generator.nextStereoSample()
                    val sl = (l * smoothGain * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                    val sr = (r * smoothGain * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                    pcmBuffer[i * 2] = sl
                    pcmBuffer[i * 2 + 1] = sr
                }
                val written = track.write(pcmBuffer, 0, pcmBuffer.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
            }
        } catch (_: Throwable) {
            // Stream cancelled cleanly
        } finally {
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
    }

    fun release() {
        ambientJob?.cancel()
        ambientJob = null
        runCatching { soundPool?.release() }
        soundPool = null
        scope.cancel()
    }
}

@Composable
internal fun rememberBookSoundEngine(
    pageSoundEnabled: Boolean,
    ambientMode: AmbientSoundMode,
    ambientVolume: Float
): BookSoundEngine {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = remember(context) { BookSoundEngine(context.applicationContext) }

    LaunchedEffect(pageSoundEnabled) {
        engine.pageSoundEnabled = pageSoundEnabled
    }

    LaunchedEffect(ambientMode, ambientVolume) {
        engine.updateAmbient(ambientMode, ambientVolume)
    }

    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> engine.setLifecyclePaused(true)
                Lifecycle.Event.ON_RESUME -> engine.setLifecyclePaused(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine.release()
        }
    }

    return engine
}

// =========================================================================
// PROCEDURAL PAPER-FLIP SFX SYNTHESIS (Multi-Band Filtered Friction & Swoosh)
// =========================================================================

private fun synthesizeCornerLiftPcm(sampleRate: Int, durationSec: Float, seed: Int): ShortArray {
    val n = (sampleRate * durationSec).toInt()
    val out = ShortArray(n)
    val rng = Random(seed)
    var lp1 = 0f
    var hpPrev = 0f
    var hpOut = 0f

    for (i in 0 until n) {
        val t = i.toFloat() / n.toFloat()
        // Quick delicate rise and silky fade
        val env = sin(t * PI).toFloat() * exp(-t * 2.2f)
        val white = rng.nextFloat() * 2f - 1f
        // Band-pass around crisp dry paper fiber (~1.5kHz - 4.2kHz)
        lp1 += 0.42f * (white - lp1)
        hpOut = 0.78f * (hpOut + lp1 - hpPrev)
        hpPrev = lp1
        val sample = (hpOut * env * 0.55f).coerceIn(-0.95f, 0.95f)
        out[i] = (sample * 32767f).toInt().toShort()
    }
    return out
}

private fun synthesizePageFlipPcm(
    sampleRate: Int,
    durationSec: Float,
    seed: Int,
    isChapter: Boolean
): ShortArray {
    val n = (sampleRate * durationSec).toInt()
    val out = ShortArray(n)
    val rng = Random(seed)

    // Filter states for 3 acoustic layers:
    // 1) Air swoosh (warm mid-low band)
    // 2) Paper fiber rustle (crisp high-mid band)
    // 3) Soft page-landing pat (damped low-mid thump near the end)
    var swooshLp = 0f
    var swooshHpPrev = 0f
    var swooshHpOut = 0f

    var fiberLp = 0f
    var fiberHpPrev = 0f
    var fiberHpOut = 0f

    var patLp = 0f
    val flutterFreq = if (isChapter) 15f else (18f + rng.nextFloat() * 5f)
    val pitchShift = if (isChapter) 0.85f else 1.0f

    for (i in 0 until n) {
        val t = i.toFloat() / n.toFloat()
        val timeSec = i.toFloat() / sampleRate.toFloat()
        val white = rng.nextFloat() * 2f - 1f

        // Layer 1: Warm curved air swoosh peaking around t = 0.42
        val swooshEnv = exp(-((t - 0.42f) * (t - 0.42f)) / 0.055f) *
            (0.84f + 0.16f * sin(2.0 * PI * flutterFreq * timeSec).toFloat())
        swooshLp += (0.18f * pitchShift) * (white - swooshLp)
        swooshHpOut = 0.91f * (swooshHpOut + swooshLp - swooshHpPrev)
        swooshHpPrev = swooshLp

        // Layer 2: Dry paper-fiber friction peaking slightly earlier (lift) + mid-arc
        val liftPeak = exp(-((t - 0.14f) * (t - 0.14f)) / 0.012f) * 0.65f
        val mainRustle = exp(-((t - 0.48f) * (t - 0.48f)) / 0.048f) * 0.95f
        val settleBrush = exp(-((t - 0.80f) * (t - 0.80f)) / 0.014f) * 0.42f
        val fiberEnv = (liftPeak + mainRustle + settleBrush) *
            (0.88f + 0.12f * sin(2.0 * PI * (flutterFreq * 1.4f) * timeSec).toFloat())
        fiberLp += (0.48f * pitchShift) * (white - fiberLp)
        fiberHpOut = 0.72f * (fiberHpOut + fiberLp - fiberHpPrev)
        fiberHpPrev = fiberLp

        // Layer 3: Gentle paper-landing muted thump around t = 0.78
        val patEnv = exp(-((t - 0.78f) * (t - 0.78f)) / 0.0055f)
        patLp += 0.065f * (white - patLp)

        // Smooth edge fade-in / fade-out so zero clicks
        val edgeFade = when {
            t < 0.03f -> t / 0.03f
            t > 0.92f -> (1f - t) / 0.08f
            else -> 1f
        }.coerceIn(0f, 1f)

        val mix = (swooshHpOut * swooshEnv * (if (isChapter) 0.72f else 0.58f) +
            fiberHpOut * fiberEnv * 0.34f +
            patLp * patEnv * (if (isChapter) 0.85f else 0.55f)) * edgeFade

        val softClipped = (mix / (1f + kotlin.math.abs(mix) * 0.55f)).coerceIn(-0.95f, 0.95f)
        out[i] = (softClipped * 32767f).toInt().toShort()
    }
    return out
}

private fun writeMonoWav(file: File, pcm: ShortArray, sampleRate: Int) {
    val dataSize = pcm.size * 2
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray())
        putInt(36 + dataSize)
        put("WAVE".toByteArray())
        put("fmt ".toByteArray())
        putInt(16) // PCM chunk size
        putShort(1) // PCM format
        putShort(1) // Mono
        putInt(sampleRate)
        putInt(sampleRate * 2) // Byte rate
        putShort(2) // Block align
        putShort(16) // Bits per sample
        put("data".toByteArray())
        putInt(dataSize)
    }
    val dataBuf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
    for (s in pcm) dataBuf.putShort(s)

    FileOutputStream(file).use { fos ->
        fos.write(header.array())
        fos.write(dataBuf.array())
    }
}

// =========================================================================
// PROCEDURAL STEREO AMBIENT READING SOUNDSCAPES (Warm, Organic, Seamless)
// =========================================================================

private interface StereoAmbientGenerator {
    fun nextStereoSample(): Pair<Float, Float>
}

private fun createAmbientGenerator(mode: AmbientSoundMode, sampleRate: Int): StereoAmbientGenerator =
    when (mode) {
        AmbientSoundMode.RAIN -> RainSoundGenerator(sampleRate)
        AmbientSoundMode.FIREPLACE -> FireplaceSoundGenerator(sampleRate)
        AmbientSoundMode.QUIET_NIGHT -> QuietNightSoundGenerator(sampleRate)
        AmbientSoundMode.OFF -> object : StereoAmbientGenerator {
            override fun nextStereoSample(): Pair<Float, Float> = 0f to 0f
        }
    }

/** Paul Kellet's warm stereo pink noise filter (-3 dB/octave — silky and non-fatiguing) */
private class PinkNoiseFilter {
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var b3 = 0f
    private var b4 = 0f
    private var b5 = 0f
    private var b6 = 0f

    fun process(white: Float): Float {
        b0 = 0.99886f * b0 + white * 0.0555179f
        b1 = 0.99332f * b1 + white * 0.0750759f
        b2 = 0.96900f * b2 + white * 0.1538520f
        b3 = 0.86650f * b3 + white * 0.3104856f
        b4 = 0.55000f * b4 + white * 0.5329522f
        b5 = -0.7616f * b5 - white * 0.0168980f
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
        b6 = white * 0.115926f
        return pink * 0.11f
    }
}

/**
 * Rain on Porch / Window:
 * - Warm stereo pink-noise rain sheet shaped by soft low-pass (~2.2 kHz) and gentle weather swells
 * - Cozy indoor low-frequency rain murmur
 * - Stochastic delicate raindrops panned in stereo
 */
private class RainSoundGenerator(private val sampleRate: Int) : StereoAmbientGenerator {
    private val rng = Random(777)
    private val pinkL = PinkNoiseFilter()
    private val pinkR = PinkNoiseFilter()
    private var lpL = 0f
    private var lpR = 0f
    private var murmur = 0f

    private var dropAmp = 0f
    private var dropFreq = 1200f
    private var dropPhase = 0f
    private var dropPan = 0.5f
    private var sampleIdx = 0L

    override fun nextStereoSample(): Pair<Float, Float> {
        val t = sampleIdx++.toFloat() / sampleRate.toFloat()
        val wL = rng.nextFloat() * 2f - 1f
        val wR = rng.nextFloat() * 2f - 1f

        val pL = pinkL.process(wL)
        val pR = pinkR.process(wR)

        // Slow natural rain intensity breathing
        val swellL = 0.82f + 0.18f * sin(2.0 * PI * 0.07f * t).toFloat()
        val swellR = 0.82f + 0.18f * sin(2.0 * PI * 0.055f * t + 1.4).toFloat()

        // Gentle warm low-pass (~2.1kHz) so rain feels cozy, never harsh static
        lpL += 0.30f * (pL - lpL)
        lpR += 0.30f * (pR - lpR)

        // Deep distant outdoor rain warmth
        murmur += 0.015f * (((wL + wR) * 0.5f) - murmur)

        // Occasional delicate raindrop splash on roof/leaves
        if (dropAmp < 0.002f && rng.nextFloat() < 0.00055f) {
            dropAmp = 0.06f + rng.nextFloat() * 0.09f
            dropFreq = 650f + rng.nextFloat() * 1400f
            dropPan = 0.15f + rng.nextFloat() * 0.70f
            dropPhase = 0f
        }
        var dropSample = 0f
        if (dropAmp >= 0.001f) {
            dropPhase += (2.0 * PI * dropFreq / sampleRate).toFloat()
            dropSample = sin(dropPhase) * dropAmp
            dropAmp *= 0.992f
        }

        val left = (lpL * swellL * 0.52f + murmur * 0.38f + dropSample * (1f - dropPan)).coerceIn(-0.9f, 0.9f)
        val right = (lpR * swellR * 0.52f + murmur * 0.38f + dropSample * dropPan).coerceIn(-0.9f, 0.9f)
        return left to right
    }
}

/**
 * Cozy Crackling Fireplace:
 * - Warm flickering Brownian hearth rumble (< 160 Hz)
 * - Stochastic dry wood-fiber sparks & hollow warm ember pops panned softly in stereo
 */
private class FireplaceSoundGenerator(private val sampleRate: Int) : StereoAmbientGenerator {
    private val rng = Random(888)
    private var brownL = 0f
    private var brownR = 0f
    private var rumbleL = 0f
    private var rumbleR = 0f

    private var popAmp = 0f
    private var popFreq = 520f
    private var popPhase = 0f
    private var popPan = 0.5f

    private var sparkEnv = 0f
    private var sparkPan = 0.5f
    private var sparkHpPrev = 0f
    private var sparkHpOut = 0f
    private var sampleIdx = 0L

    override fun nextStereoSample(): Pair<Float, Float> {
        val t = sampleIdx++.toFloat() / sampleRate.toFloat()
        val wL = rng.nextFloat() * 2f - 1f
        val wR = rng.nextFloat() * 2f - 1f

        // Silky Brownian hearth air draft
        brownL = (brownL + wL * 0.045f).coerceIn(-1f, 1f) * 0.996f
        brownR = (brownR + wR * 0.045f).coerceIn(-1f, 1f) * 0.996f

        val flicker = 0.76f + 0.16f * sin(2.0 * PI * 0.38f * t).toFloat() +
            0.08f * sin(2.0 * PI * 1.15f * t + 0.9).toFloat()

        rumbleL += 0.042f * (brownL - rumbleL)
        rumbleR += 0.042f * (brownR - rumbleR)

        // Occasional warm hollow wood pop
        if (popAmp < 0.002f && rng.nextFloat() < 0.00018f) {
            popAmp = 0.16f + rng.nextFloat() * 0.18f
            popFreq = 320f + rng.nextFloat() * 520f
            popPan = 0.22f + rng.nextFloat() * 0.56f
            popPhase = 0f
        }
        var popSample = 0f
        if (popAmp >= 0.001f) {
            popPhase += (2.0 * PI * popFreq / sampleRate).toFloat()
            popSample = sin(popPhase) * popAmp
            popAmp *= 0.978f
        }

        // Crisp tiny dry spark crackles
        if (sparkEnv < 0.005f && rng.nextFloat() < 0.00045f) {
            sparkEnv = 0.18f + rng.nextFloat() * 0.24f
            sparkPan = 0.15f + rng.nextFloat() * 0.70f
        }
        var sparkSample = 0f
        if (sparkEnv >= 0.002f) {
            val rawSpark = rng.nextFloat() * 2f - 1f
            sparkHpOut = 0.65f * (sparkHpOut + rawSpark - sparkHpPrev)
            sparkHpPrev = rawSpark
            sparkSample = sparkHpOut * sparkEnv
            sparkEnv *= 0.92f
        }

        val left = (rumbleL * flicker * 1.45f + popSample * (1f - popPan) + sparkSample * (1f - sparkPan)).coerceIn(-0.9f, 0.9f)
        val right = (rumbleR * flicker * 1.45f + popSample * popPan + sparkSample * sparkPan).coerceIn(-0.9f, 0.9f)
        return left to right
    }
}

/**
 * Peaceful Forest Night:
 * - Soft spacious stereo night breeze drifting through leaves
 * - Ultra-gentle distant rhythmic cricket chirps (softly panned, never loud or harsh)
 */
private class QuietNightSoundGenerator(private val sampleRate: Int) : StereoAmbientGenerator {
    private val rng = Random(999)
    private val pinkL = PinkNoiseFilter()
    private val pinkR = PinkNoiseFilter()
    private var breezeLpL = 0f
    private var breezeLpR = 0f
    private var breezeHpPrevL = 0f
    private var breezeHpOutL = 0f
    private var breezeHpPrevR = 0f
    private var breezeHpOutR = 0f

    private var sampleIdx = 0L

    override fun nextStereoSample(): Pair<Float, Float> {
        val t = sampleIdx++.toFloat() / sampleRate.toFloat()
        val pL = pinkL.process(rng.nextFloat() * 2f - 1f)
        val pR = pinkR.process(rng.nextFloat() * 2f - 1f)

        // Band-pass soft foliage wind (220Hz - 850Hz) with slow asynchronous stereo swells
        breezeLpL += 0.12f * (pL - breezeLpL)
        breezeHpOutL = 0.95f * (breezeHpOutL + breezeLpL - breezeHpPrevL)
        breezeHpPrevL = breezeLpL

        breezeLpR += 0.12f * (pR - breezeLpR)
        breezeHpOutR = 0.95f * (breezeHpOutR + breezeLpR - breezeHpPrevR)
        breezeHpPrevR = breezeLpR

        val windSwellL = 0.45f + 0.55f * (0.5f + 0.5f * sin(2.0 * PI * 0.065f * t).toFloat())
        val windSwellR = 0.45f + 0.55f * (0.5f + 0.5f * sin(2.0 * PI * 0.052f * t + 1.8).toFloat())

        // Gentle far-away cricket 3-pulse chirp every ~3.4s (soft left) and ~4.7s (soft right)
        val c1 = softCricketChirp(t, period = 3.4f, carrierHz = 4080f) * 0.032f
        val c2 = softCricketChirp(t + 1.7f, period = 4.7f, carrierHz = 4320f) * 0.024f

        val left = (breezeHpOutL * windSwellL * 0.72f + c1 * 0.78f + c2 * 0.25f).coerceIn(-0.9f, 0.9f)
        val right = (breezeHpOutR * windSwellR * 0.72f + c1 * 0.25f + c2 * 0.78f).coerceIn(-0.9f, 0.9f)
        return left to right
    }

    private fun softCricketChirp(t: Float, period: Float, carrierHz: Float): Float {
        val phaseInCycle = t % period
        if (phaseInCycle > 0.42f) return 0f
        // 3 delicate pulses inside 0.42s
        val pulseWindow = phaseInCycle % 0.14f
        if (pulseWindow > 0.09f) return 0f
        val env = sin((pulseWindow / 0.09f) * PI).toFloat()
        val tremolo = 0.55f + 0.45f * sin(2.0 * PI * 42.0 * phaseInCycle).toFloat()
        return sin(2.0 * PI * carrierHz * t).toFloat() * env * tremolo
    }
}
