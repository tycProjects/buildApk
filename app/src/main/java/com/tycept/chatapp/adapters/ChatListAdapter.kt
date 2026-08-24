package com.tycept.chatapp.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tycept.chatapp.R
import com.tycept.chatapp.models.Contact

class ChatListAdapter(
    private val contacts: List<Contact>,
    private val onClick: (Contact) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ContactHolder>() {

    private var lastAnimatedPosition = -1

    class ContactHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: TextView = itemView.findViewById(R.id.avatarText)
        val onlineDot: View = itemView.findViewById(R.id.onlineDot)
        val name: TextView = itemView.findViewById(R.id.contactName)
        val lastMessage: TextView = itemView.findViewById(R.id.lastMessage)
        val time: TextView = itemView.findViewById(R.id.messageTime)
        val badge: TextView = itemView.findViewById(R.id.unreadBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ContactHolder(view)
    }

    override fun onBindViewHolder(holder: ContactHolder, position: Int) {
        val contact = contacts[position]

        holder.avatar.text = contact.name.trim().split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")

        val bg = holder.avatar.background as? GradientDrawable
        bg?.setColor(Color.parseColor(contact.avatarColor))

        holder.name.text = contact.name
        holder.lastMessage.text = contact.lastMessage
        holder.time.text = contact.time
        holder.onlineDot.visibility = if (contact.isOnline) View.VISIBLE else View.GONE

        if (contact.unreadCount > 0) {
            holder.badge.visibility = View.VISIBLE
            holder.badge.text = contact.unreadCount.toString()
            holder.lastMessage.setTypeface(null, Typeface.BOLD)
        } else {
            holder.badge.visibility = View.GONE
            holder.lastMessage.setTypeface(null, Typeface.NORMAL)
        }

        holder.itemView.setOnClickListener { onClick(contact) }

        animateRowIn(holder.itemView, position)
    }

    private fun animateRowIn(view: View, position: Int) {
        if (position <= lastAnimatedPosition) return
        lastAnimatedPosition = position
        view.alpha = 0f
        view.translationX = -60f
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay((position * 40).toLong())
            .setDuration(320)
            .start()
    }

    override fun getItemCount(): Int = contacts.size
}
