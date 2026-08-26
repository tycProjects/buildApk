package com.app.vietsubai

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class HistoryActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout
    private lateinit var store: HistoryStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        findViewById<ViewGroup>(android.R.id.content).getChildAt(0).animateEntrance()
        findViewById<Button>(R.id.clearHistoryButton).enablePressMotion()
        container = findViewById(R.id.historyContainer)
        store = HistoryStore(this)
        findViewById<Button>(R.id.clearHistoryButton).setOnClickListener {
            store.all().forEach { store.remove(it.id) }
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) render()
    }

    private fun render() {
        container.removeAllViews()
        val items = store.all()
        if (items.isEmpty()) {
            container.addView(TextView(this).apply { text = "Chưa có lịch sử export." })
            return
        }
        items.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)
            }
            card.addView(TextView(this).apply {
                text = "${item.inputName}\n${item.status}"
                textSize = 16f
            })
            item.error?.let { errorText ->
                card.addView(TextView(this).apply { text = "Lỗi: $errorText" })
            }
            item.outputPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    card.addView(Button(this).apply {
                        text = "Mở video output"
                        setOnClickListener {
                            val uri = FileProvider.getUriForFile(this@HistoryActivity, "${packageName}.fileprovider", file)
                            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }
                    })
                }
            }
            container.addView(card, ViewGroup.LayoutParams(-1, -2))
        }
    }
}
