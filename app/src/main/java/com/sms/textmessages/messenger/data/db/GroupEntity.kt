package com.sms.textmessages.messenger.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(

    @PrimaryKey
    val groupId: String,

    // Comma-separated phone numbers. AppDatabase has no TypeConverter set up
    // for List<String> yet, so this mirrors ThreadEntity's plain-column style
    // rather than introducing new Room plumbing for a single field.
    val participantNumbers: String,

    val groupName: String?,

    val createdAt: Long
)
