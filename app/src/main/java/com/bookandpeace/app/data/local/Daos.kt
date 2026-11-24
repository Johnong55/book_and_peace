package com.bookandpeace.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY CASE WHEN lastReadAt > 0 THEN lastReadAt ELSE addedAt END DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun observeBook(bookId: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: Long): BookEntity?

    @Query("SELECT * FROM books WHERE sourceType = :sourceType AND sourceRef = :sourceRef LIMIT 1")
    suspend fun findBySource(sourceType: String, sourceRef: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET currentChapterIndex = :chapterIndex, currentScrollOffset = :scrollOffset, lastReadAt = :timestamp WHERE id = :bookId")
    suspend fun updateReadingProgress(bookId: Long, chapterIndex: Int, scrollOffset: Int, timestamp: Long)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: Long)
}

@Dao
interface ChapterDao {
    @Query("SELECT id, bookId, chapterIndex, title FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    fun observeChapterHeaders(bookId: Long): Flow<List<ChapterHeader>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    fun observeChapters(bookId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    suspend fun getChaptersForBook(bookId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND chapterIndex = :index LIMIT 1")
    suspend fun getChapter(bookId: Long, index: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}

@Dao
interface StoryScriptDao {
    @Query("SELECT * FROM story_scripts WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observeScriptsForBook(bookId: Long): Flow<List<StoryScriptEntity>>

    @Query("SELECT * FROM story_scripts ORDER BY updatedAt DESC")
    fun observeAllScripts(): Flow<List<StoryScriptEntity>>

    @Query("SELECT * FROM story_scripts WHERE id = :scriptId")
    fun observeScript(scriptId: Long): Flow<StoryScriptEntity?>

    @Query("SELECT * FROM story_scripts WHERE id = :scriptId")
    suspend fun getScriptById(scriptId: Long): StoryScriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: StoryScriptEntity): Long

    @Update
    suspend fun updateScript(script: StoryScriptEntity)

    @Query("UPDATE story_scripts SET status = :status, statusMessage = :message, updatedAt = :updatedAt WHERE id = :scriptId")
    suspend fun updateStatus(scriptId: Long, status: String, message: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM story_scripts WHERE id = :scriptId")
    suspend fun deleteScriptById(scriptId: Long)

    @Query("SELECT * FROM audio_segments WHERE scriptId = :scriptId ORDER BY segmentIndex ASC")
    fun observeSegments(scriptId: Long): Flow<List<AudioSegmentEntity>>

    @Query("SELECT * FROM audio_segments WHERE scriptId = :scriptId ORDER BY segmentIndex ASC")
    suspend fun getSegments(scriptId: Long): List<AudioSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<AudioSegmentEntity>)

    @Query("DELETE FROM audio_segments WHERE scriptId = :scriptId")
    suspend fun deleteSegmentsForScript(scriptId: Long)

    @Query("UPDATE audio_segments SET audioFilePath = :path, durationMs = :durationMs, status = :status WHERE id = :segmentId")
    suspend fun updateSegmentAudio(segmentId: Long, path: String?, durationMs: Long, status: String)
}

@Dao
interface OpdsSourceDao {
    @Query("SELECT * FROM opds_sources ORDER BY addedAt ASC")
    fun observeSources(): Flow<List<OpdsSourceEntity>>

    @Query("SELECT COUNT(*) FROM opds_sources")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: OpdsSourceEntity): Long

    @Delete
    suspend fun delete(source: OpdsSourceEntity)
}
