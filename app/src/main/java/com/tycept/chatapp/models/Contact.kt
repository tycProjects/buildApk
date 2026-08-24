package com.tycept.chatapp.models

data class Contact(
    val id: Int,
    val name: String,
    val lastMessage: String,
    val time: String,
    val avatarColor: String,
    val isOnline: Boolean,
    val unreadCount: Int = 0
)
