package com.sms.textmessages.messenger.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "sms_threads",
    indices = [
        Index(value = ["date"]),
        Index(value = ["phone"])
    ]
)
data class ThreadEntity(

    @PrimaryKey
    val threadId: Long,

    val phone: String,
    val lastMessage: String,
    val date: Long,
    val isRead: Boolean
)