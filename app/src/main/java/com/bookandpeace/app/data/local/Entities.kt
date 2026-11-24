package com.bookandpeace.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object BookSourceType {
    const val LOCAL_EPUB = "LOCAL_EPUB"
    const val LOCAL_PDF = "LOCAL_PDF"
    const val LOCAL_TXT = "LOCAL_TXT"
    const val WIKISOURCE = "WIKISOURCE"
    const val GUTENBERG = "GUTENBERG"
    const val OPDS = "OPDS"
    const val BUILTIN = "BUILTIN"
}

object StoryStyleMode {
    const val VAN_VO_QUICK = "VAN_VO_QUICK"
    const val VAN_VO_STANDARD = "VAN_VO_STANDARD"
    const val VAN_VO_DEEP = "VAN_VO_DEEP"
    const val BEDTIME_CALM = "BEDTIME_CALM"

    fun displayName(mode: String): String = when (mode) {
        VAN_VO_QUICK -> "Kể Nhanh (~10 phút)"
        VAN_VO_STANDARD -> "Kể Chuyện Sâu (~20 phút)"
        VAN_VO_DEEP -> "Phân Tích Chi Tiết (~30+ phút)"
        BEDTIME_CALM -> "Kể Chuyện Đêm Khuya (~15 phút)"
        else -> "Kể Chuyện Sâu"
    }

    fun targetWords(mode: String): Int = when (mode) {
        VAN_VO_QUICK -> 1000
        VAN_VO_STANDARD -> 2800
        VAN_VO_DEEP -> 5000
        BEDTIME_CALM -> 2000
        else -> 2800
    }
}

object AiProviderType {
    const val AUTO = "AUTO"
    const val GEMINI_CLOUD = "GEMINI_CLOUD"
    const val GEMINI_NANO = "GEMINI_NANO"
    const val GEMINI_APP_SHARE = "GEMINI_APP_SHARE"
}

object TtsEngineType {
    const val AUTO = "AUTO"
    const val GEMINI_TTS = "GEMINI_TTS"
    const val ANDROID_TTS = "ANDROID_TTS"
    const val CUSTOM_TTS = "CUSTOM_TTS"
}

object ScriptStatus {
    const val DRAFT = "DRAFT"
    const val GENERATING_SCRIPT = "GENERATING_SCRIPT"
    const val GENERATING_AUDIO = "GENERATING_AUDIO"
    const val READY = "READY"
    const val ERROR = "ERROR"
}

object SegmentStatus {
    const val PENDING = "PENDING"
    const val SYNTHESIZING = "SYNTHESIZING"
    const val READY = "READY"
    const val ERROR = "ERROR"
}

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val description: String = "",
    val language: String = "vi",
    val category: String = "Văn học",
    val sourceType: String = BookSourceType.LOCAL_EPUB,
    val sourceRef: String = "",
    val coverPath: String? = null,
    val localFilePath: String? = null,
    val totalChapters: Int = 0,
    val currentChapterIndex: Int = 0,
    val currentScrollOffset: Int = 0,
    val wordCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L
)

data class ChapterHeader(
    val id: Long,
    val bookId: Long,
    val chapterIndex: Int,
    val title: String
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId", "chapterIndex"], unique = true)]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val title: String,
    val content: String
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val chapterTitle: String,
    val excerpt: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "story_scripts",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class StoryScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val title: String,
    val styleMode: String = StoryStyleMode.VAN_VO_STANDARD,
    val aiProvider: String = AiProviderType.GEMINI_CLOUD,
    val ttsEngine: String = TtsEngineType.AUTO,
    val rawScript: String = "",
    val status: String = ScriptStatus.DRAFT,
    val statusMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audio_segments",
    foreignKeys = [
        ForeignKey(
            entity = StoryScriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["scriptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["scriptId", "segmentIndex"], unique = true)]
)
data class AudioSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scriptId: Long,
    val segmentIndex: Int,
    val sectionTitle: String,
    val toneHint: String = "trầm ấm, tự nhiên",
    val spokenText: String,
    val audioFilePath: String? = null,
    val durationMs: Long = 0L,
    val status: String = SegmentStatus.PENDING
)

@Entity(tableName = "opds_sources")
data class OpdsSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val description: String = "",
    val addedAt: Long = System.currentTimeMillis()
)
