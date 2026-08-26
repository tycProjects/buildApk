package com.app.vietsubai

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var store: ApiKeyStore
    private lateinit var geminiInput: EditText
    private lateinit var groqInput: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0).animateEntrance()
        store = ApiKeyStore(this)
        geminiInput = findViewById(R.id.geminiKeyInput); groqInput = findViewById(R.id.groqKeyInput); status = findViewById(R.id.settingsStatus)
        showConfiguredState()
        findViewById<Button>(R.id.saveKeysButton).setOnClickListener { saveKeys() }
        findViewById<Button>(R.id.saveKeysButton).enablePressMotion(); findViewById<Button>(R.id.clearKeysButton).enablePressMotion()
        findViewById<Button>(R.id.clearKeysButton).setOnClickListener { store.clear(); geminiInput.text.clear(); groqInput.text.clear(); showConfiguredState() }
    }

    private fun saveKeys() {
        val gemini = geminiInput.text.toString().trim(); val groq = groqInput.text.toString().trim()
        if (gemini.isBlank() || groq.isBlank()) { status.text = "Cần nhập đủ Gemini API key và Groq API key."; return }
        store.save(gemini, groq); geminiInput.text.clear(); groqInput.text.clear(); status.text = "Đã lưu key mã hóa. Key không được hiển thị lại trên màn hình."
    }
    private fun showConfiguredState() { status.text = if (store.isConfigured()) "Trạng thái: đã cấu hình Gemini và Groq." else "Trạng thái: chưa cấu hình đủ API key." }
}
