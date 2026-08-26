package com.example.videometaeditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import com.arthenica.ffmpegkit.ReturnCode
import com.example.videometaeditor.databinding.ActivityMainBinding
import java.util.concurrent.Executors

/**
 * A video metadata editor with "full control":
 *  - Reads every format-level and per-stream metadata tag ffprobe exposes.
 *  - Lets the user overwrite standard fields (title, artist, album, comment,
 *    genre, date/creation_time, GPS location).
 *  - Lets the user add/remove arbitrary custom key/value metadata pairs.
 *  - Optionally strips ALL existing metadata before applying the new values.
 *  - Remuxes with "-c copy" (no re-encoding, no quality loss, fast) via FFmpegKit,
 *    which also transparently handles all the low-level container bookkeeping
 *    (moov atom rebuilding, chunk offsets, etc.) that would otherwise be needed
 *    to hand-edit an MP4/MOV/MKV file safely.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var inputUri: Uri? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers don't support persistable permissions; safe to ignore
                // since we only need access for the current session.
            }
            inputUri = uri
            binding.tvSelectedFile.text = queryFileName(uri) ?: uri.toString()
            clearCustomFields()
            loadMetadata(uri)
        }

    private val createOutputLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            uri ?: return@registerForActivityResult
            applyMetadata(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSelectVideo.setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }

        binding.btnAddCustomField.setOnClickListener {
            addCustomFieldRow("", "")
        }

        binding.btnSave.setOnClickListener {
            val uri = inputUri
            if (uri == null) {
                Toast.makeText(this, "Select a video first", Toast.LENGTH_SHORT).show()
            } else {
                val baseName = (queryFileName(uri) ?: "video").substringBeforeLast('.')
                createOutputLauncher.launch("${baseName}_edited.mp4")
            }
        }
    }

    // ---------------------------------------------------------------------
    // Reading metadata
    // ---------------------------------------------------------------------

    private fun loadMetadata(uri: Uri) {
        binding.tvCurrentMetadata.text = "Reading metadata..."
        executor.execute {
            val safPath = FFmpegKitConfig.getSafParameterForRead(this, uri)
            val session = FFprobeKit.getMediaInformation(safPath)
            val info: MediaInformation? = session.mediaInformation
            val sb = StringBuilder()

            if (info == null) {
                sb.append("Could not read metadata.\n\nffprobe log:\n")
                sb.append(session.allLogsAsString ?: "(no log output)")
            } else {
                sb.append("Format: ${info.format}\n")
                sb.append("Duration: ${info.duration} s\n")
                sb.append("Bitrate: ${info.bitrate}\n")
                sb.append("Size: ${info.size} bytes\n\n")

                sb.append("Format-level tags:\n")
                // NOTE: this ffmpeg-kit fork's getTags() carries an unresolved generic
                // signature that Kotlin can't use directly (isNullOrEmpty/forEach fail
                // to resolve against it). Casting to Map<*, *> up front sidesteps that.
                val tags = info.tags as? Map<*, *>
                if (tags.isNullOrEmpty()) {
                    sb.append("  (none)\n")
                } else {
                    for ((k, v) in tags) sb.append("  $k = $v\n")
                }

                info.streams?.forEachIndexed { i, s ->
                    sb.append("\nStream #$i  type=${s.type}  codec=${s.codec}\n")
                    val sTags = s.tags as? Map<*, *>
                    if (sTags.isNullOrEmpty()) {
                        sb.append("  (no tags)\n")
                    } else {
                        for ((k, v) in sTags) sb.append("  $k = $v\n")
                    }
                }
            }

            runOnUiThread {
                binding.tvCurrentMetadata.text = sb.toString()
                prefillFields(info)
            }
        }
    }

    private fun prefillFields(info: MediaInformation?) {
        // Same fork-specific generic-signature issue as in loadMetadata(): cast to
        // Map<*, *> before touching .entries so Kotlin has a concrete type to work with.
        val tags = info?.tags as? Map<*, *> ?: return

        fun findKey(vararg names: String): String {
            for (n in names) {
                val match = tags.entries.firstOrNull { (it.key as? String)?.equals(n, ignoreCase = true) == true }
                if (match != null) return match.value?.toString() ?: ""
            }
            return ""
        }

        binding.etTitle.setText(findKey("title"))
        binding.etArtist.setText(findKey("artist"))
        binding.etAlbum.setText(findKey("album"))
        binding.etComment.setText(findKey("comment"))
        binding.etGenre.setText(findKey("genre"))
        binding.etDate.setText(findKey("date", "creation_time"))
        binding.etLocation.setText(findKey("location", "com.apple.quicktime.location.iso6709"))
    }

    // ---------------------------------------------------------------------
    // Writing metadata
    // ---------------------------------------------------------------------

    private fun applyMetadata(outputUri: Uri) {
        val input = inputUri ?: return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false
        binding.tvStatus.text = "Processing..."

        executor.execute {
            val inputSaf = FFmpegKitConfig.getSafParameterForRead(this, input)
            val outputSaf = FFmpegKitConfig.getSafParameterForWrite(this, outputUri)

            val args = mutableListOf("-y", "-i", inputSaf)

            if (binding.cbClearMetadata.isChecked) {
                // Strip every existing format + stream metadata tag first.
                args += listOf("-map_metadata", "-1")
            }

            args += listOf("-map", "0")

            for ((key, value) in collectFieldsMap()) {
                args += listOf("-metadata", "$key=$value")
            }

            // Remux only — no re-encoding, so this is fast and lossless.
            args += listOf("-c", "copy", outputSaf)

            val session = FFmpegKit.executeWithArguments(args.toTypedArray())
            val success = ReturnCode.isSuccess(session.returnCode)

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                binding.tvStatus.text = if (success) {
                    "Saved successfully."
                } else {
                    "Failed (return code ${session.returnCode}). Log:\n${session.allLogsAsString}"
                }
                if (success) {
                    Toast.makeText(this, "Video saved", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun collectFieldsMap(): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()

        fun put(key: String, editText: EditText) {
            val v = editText.text.toString().trim()
            if (v.isNotEmpty()) map[key] = v
        }

        put("title", binding.etTitle)
        put("artist", binding.etArtist)
        put("album", binding.etAlbum)
        put("comment", binding.etComment)
        put("genre", binding.etGenre)
        put("date", binding.etDate)

        val loc = binding.etLocation.text.toString().trim()
        if (loc.isNotEmpty()) map["location"] = loc

        for (i in 0 until binding.customFieldsLayout.childCount) {
            val row = binding.customFieldsLayout.getChildAt(i) as LinearLayout
            val keyEt = row.getChildAt(0) as EditText
            val valEt = row.getChildAt(1) as EditText
            val k = keyEt.text.toString().trim()
            val v = valEt.text.toString().trim()
            if (k.isNotEmpty()) map[k] = v
        }

        return map
    }

    // ---------------------------------------------------------------------
    // Custom field rows (dynamic key/value UI)
    // ---------------------------------------------------------------------

    private fun clearCustomFields() {
        binding.customFieldsLayout.removeAllViews()
    }

    private fun addCustomFieldRow(key: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }

        val keyEt = EditText(this).apply {
            hint = "key"
            setText(key)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valEt = EditText(this).apply {
            hint = "value"
            setText(value)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val removeBtn = Button(this).apply {
            text = "✕"
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            setOnClickListener { binding.customFieldsLayout.removeView(row) }
        }

        row.addView(keyEt)
        row.addView(valEt)
        row.addView(removeBtn)
        binding.customFieldsLayout.addView(row)
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx)
            }
        }
        return name
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
