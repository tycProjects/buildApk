package com.tycept.chatapp

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tycept.chatapp.adapters.MessageAdapter
import com.tycept.chatapp.models.Message
import com.tycept.chatapp.utils.DummyData

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: MessageAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var typingIndicator: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val contactName = intent.getStringExtra("contact_name") ?: "Chat"
        val contactColor = intent.getStringExtra("contact_color") ?: "#1877F2"
        val isOnline = intent.getBooleanExtra("contact_online", false)

        findViewById<TextView>(R.id.chatContactName).text = contactName
        findViewById<TextView>(R.id.chatStatus).text = if (isOnline) "Active now" else "Offline"

        val avatar = findViewById<TextView>(R.id.chatAvatar)
        avatar.text = contactName.trim().split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
        (avatar.background as? GradientDrawable)?.setColor(Color.parseColor(contactColor))

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in_short, R.anim.slide_out_right)
        }

        recyclerView = findViewById(R.id.messagesRecyclerView)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        val messages = DummyData.conversation(contactName)
        adapter = MessageAdapter(messages)
        recyclerView.adapter = adapter
        recyclerView.scrollToPosition(messages.size - 1)

        typingIndicator = findViewById(R.id.typingIndicator)

        val input = findViewById<EditText>(R.id.messageInput)
        findViewById<ImageButton>(R.id.sendButton).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            animateSendButton(it)
            adapter.addMessage(Message(text, true, System.currentTimeMillis()))
            recyclerView.scrollToPosition(adapter.itemCount - 1)
            input.text.clear()

            simulateReply()
        }
    }

    private fun animateSendButton(view: View) {
        view.animate().scaleX(0.75f).scaleY(0.75f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }.start()
    }

    private fun simulateReply() {
        typingIndicator.visibility = View.VISIBLE
        typingIndicator.alpha = 0f
        typingIndicator.animate().alpha(1f).setDuration(200).start()

        val replies = listOf(
            "Nice 👍", "Haha for real", "Say less", "On my way",
            "That's actually huge", "Let's gooo 🔥", "Bet"
        )

        Handler(Looper.getMainLooper()).postDelayed({
            typingIndicator.animate().alpha(0f).setDuration(150).withEndAction {
                typingIndicator.visibility = View.GONE
            }.start()
            adapter.addMessage(Message(replies.random(), false, System.currentTimeMillis()))
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }, 1400)
    }
}
