package com.bookandpeace.app.data.tts

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavUtils {

    fun parseSampleRateFromMime(mimeType: String?, defaultRate: Int = 24000): Int {
        if (mimeType.isNullOrBlank()) return defaultRate
        val match = Regex("rate=(\\d+)", RegexOption.IGNORE_CASE).find(mimeType)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: defaultRate
    }

    fun writePcmAsWav(
        pcmBytes: ByteArray,
        outputFile: File,
        sampleRate: Int = 24000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): Long {
        outputFile.parentFile?.mkdirs()
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val dataSize = pcmBytes.size
        val chunkSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(chunkSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM subchunk size
            putShort(1) // PCM format
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        FileOutputStream(outputFile).use { fos ->
            fos.write(header)
            fos.write(pcmBytes)
        }
        return if (byteRate > 0) (dataSize.toLong() * 1000L) / byteRate else 0L
    }

    fun estimateWavDurationMs(wavFile: File): Long {
        if (!wavFile.exists() || wavFile.length() <= 44) return 0L
        return runCatching {
            wavFile.inputStream().use { input ->
                val header = ByteArray(44)
                if (input.read(header) < 44) return 0L
                val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val riff = String(header, 0, 4, Charsets.US_ASCII)
                if (riff != "RIFF") return 0L
                val byteRate = buf.getInt(28)
                val dataBytes = wavFile.length() - 44L
                if (byteRate > 0) (dataBytes * 1000L) / byteRate else 0L
            }
        }.getOrDefault(0L)
    }
}
