package com.tycept.chatapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tycept.chatapp.adapters.ChatListAdapter
import com.tycept.chatapp.utils.DummyData

class ChatListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        val recyclerView = findViewById<RecyclerView>(R.id.contactsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val contacts = DummyData.contacts()
        recyclerView.adapter = ChatListAdapter(contacts) { contact ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("contact_name", contact.name)
            intent.putExtra("contact_color", contact.avatarColor)
            intent.putExtra("contact_online", contact.isOnline)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_short)
        }
    }
}
