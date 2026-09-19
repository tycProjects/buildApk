package com.example.autodig

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        fun field(value: String) = EditText(this).apply {
            setText(value)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        val targetX = field("-20")
        val targetY = field("20")
        val status = TextView(this).apply {
            text = "Mục tiêu: (-20,20)\nThiết lập vùng OCR/joystick trong mã nguồn trước khi chạy."
            setPadding(0, 20, 0, 20)
        }

        root.addView(TextView(this).apply { text = "Treasure Hunter Auto Dig" })
        root.addView(TextView(this).apply { text = "Target X" })
        root.addView(targetX)
        root.addView(TextView(this).apply { text = "Target Y" })
        root.addView(targetY)

        val save = Button(this).apply {
            text = "Lưu mục tiêu"
            setOnClickListener {
                getSharedPreferences("cfg", MODE_PRIVATE).edit()
                    .putInt("tx", targetX.text.toString().toIntOrNull() ?: -20)
                    .putInt("ty", targetY.text.toString().toIntOrNull() ?: 20)
                    .apply()
                status.text = "Đã lưu mục tiêu (${targetX.text},${targetY.text})"
            }
        }
        root.addView(save)

        root.addView(Button(this).apply {
            text = "Mở Trợ năng"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "Hướng dẫn"
            setOnClickListener {
                status.text = "1) Bật dịch vụ Auto Dig trong Trợ năng.\n" +
                        "2) Mở game.\n3) Dịch vụ đọc tọa độ OCR.\n" +
                        "4) Khi tọa độ đạt mục tiêu, dịch vụ thực hiện thao tác đào."
            }
        })
        root.addView(status)

        setContentView(root)
    }
}
