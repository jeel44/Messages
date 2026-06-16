package com.sms.textmessages.messenger.data.db

import androidx.room.*

@Dao
interface ThreadDao {

    @Query("SELECT * FROM sms_threads ORDER BY date DESC LIMIT 200")
    fun getThreadsFlow(): kotlinx.coroutines.flow.Flow<List<ThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThreads(threads: List<ThreadEntity>)

    @Query("DELETE FROM sms_threads")
    suspend fun clearThreads()
}