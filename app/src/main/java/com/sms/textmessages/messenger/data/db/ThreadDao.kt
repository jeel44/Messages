package com.sms.textmessages.messenger.data.db

import androidx.room.*

@Dao
interface ThreadDao {

    // Single reactive source of truth for the main inbox - re-emits on any
    // underlying table change (insert/update/delete), no one-shot fetch.
    @Query("SELECT * FROM sms_threads WHERE archived = 0 AND blocked = 0 ORDER BY pinned DESC, date DESC")
    fun getThreadsFlow(): kotlinx.coroutines.flow.Flow<List<ThreadEntity>>

    @Query("SELECT * FROM sms_threads WHERE archived = 1 ORDER BY pinned DESC, date DESC")
    fun getArchivedThreadsFlow(): kotlinx.coroutines.flow.Flow<List<ThreadEntity>>

    @Query("SELECT * FROM sms_threads WHERE blocked = 1 ORDER BY pinned DESC, date DESC")
    fun getBlockedThreadsFlow(): kotlinx.coroutines.flow.Flow<List<ThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThreads(threads: List<ThreadEntity>)

    // One-shot snapshot (not a Flow) of Room's current thread state. Used by
    // SmsRepository.refreshThreads() to preserve isRead across a resync
    // instead of blindly re-deriving it from a live content:// query that can
    // race a concurrent targeted write like markThreadAsRead().
    @Query("SELECT * FROM sms_threads")
    suspend fun getAllThreadsOnce(): List<ThreadEntity>

    @Query("UPDATE sms_threads SET isRead = 1 WHERE threadId = :threadId")
    suspend fun markThreadAsRead(threadId: Long)

    // Matched by trailing digits, same convention PreferenceManager's archived/
    // blocked/pinned number sets already use, since stored phone formatting
    // (spaces, dashes, country code) isn't guaranteed to match exactly.
    @Query("UPDATE sms_threads SET archived = :archived WHERE phone LIKE '%' || :last10")
    suspend fun setArchivedForNumber(last10: String, archived: Boolean)

    @Query("UPDATE sms_threads SET blocked = :blocked WHERE phone LIKE '%' || :last10")
    suspend fun setBlockedForNumber(last10: String, blocked: Boolean)

    @Query("UPDATE sms_threads SET pinned = :pinned WHERE phone LIKE '%' || :last10")
    suspend fun setPinnedForNumber(last10: String, pinned: Boolean)

    // Same archived=0 AND blocked=0 filter as getThreadsFlow, so this count
    // matches exactly what's shown bolded/dotted in the inbox list - used by
    // CallEndOverlayService's post-call "back to Messages" banner.
    @Query("SELECT COUNT(*) FROM sms_threads WHERE archived = 0 AND blocked = 0 AND isRead = 0")
    suspend fun getUnreadThreadCount(): Int

    @Query("DELETE FROM sms_threads")
    suspend fun clearThreads()

    @Transaction
    suspend fun replaceAllThreads(threads: List<ThreadEntity>) {
        clearThreads()
        threads.chunked(500).forEach { chunk -> insertThreads(chunk) }
    }
}
