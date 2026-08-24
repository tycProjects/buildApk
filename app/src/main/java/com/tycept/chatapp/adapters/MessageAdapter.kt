package com.tycept.chatapp.adapters

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tycept.chatapp.R
import com.tycept.chatapp.models.Message
import com.tycept.chatapp.utils.TimeUtils

class MessageAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
    }

    private var lastAnimatedPosition = -1

    class SentHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.messageText)
        val time: TextView = itemView.findViewById(R.id.messageTime)
    }

    class ReceivedHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.messageText)
        val time: TextView = itemView.findViewById(R.id.messageTime)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isSent) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            SentHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
        } else {
            ReceivedHolder(inflater.inflate(R.layout.item_message_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val text: TextView
        val time: TextView
        when (holder) {
            is SentHolder -> { text = holder.text; time = holder.time }
            is ReceivedHolder -> { text = holder.text; time = holder.time }
            else -> return
        }
        text.text = message.text
        time.text = TimeUtils.formatTimestamp(message.timestamp)
        animateBubbleIn(holder.itemView, position)
    }

    private fun animateBubbleIn(view: View, position: Int) {
        if (position <= lastAnimatedPosition) return
        lastAnimatedPosition = position
        view.scaleX = 0.7f
        view.scaleY = 0.7f
        view.alpha = 0f
        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(view, "scaleX", 0.7f, 1f),
            ObjectAnimator.ofFloat(view, "scaleY", 0.7f, 1f),
            ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        )
        set.duration = 260
        set.interpolator = OvershootInterpolator(1.2f)
        set.start()
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}
