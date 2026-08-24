package com.tycept.chatapp.models

data class Message(
    val text: String,
    val isSent: Boolean,
    val timestamp: Long
)
