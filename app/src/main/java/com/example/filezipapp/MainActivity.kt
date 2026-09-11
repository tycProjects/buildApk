package com.example.filezipapp

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Simple File Zip App.
 *
 * Lets the user pick one or more files from device storage (via the
 * Storage Access Framework, so no special storage permissions are needed
 * on modern Android versions / emulators), then compresses them into a
 * single .zip file which is saved to a location the user chooses.
 */
class MainActivity : AppCompatActivity() {

    private var selectedUris: List<Uri> = emptyList()

    private lateinit var selectedCountText: TextView
    private lateinit var fileListView: ListView
    private lateinit var zipNameEdit: EditText
    private lateinit var btnCreateZip: Button
    private lateinit var statusText: TextView

    // Launcher for picking multiple files
    private val pickFilesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                selectedUris = uris
                val names = uris.map { queryFileName(it) }
                fileListView.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    names
                )
                selectedCountText.text = "${uris.size} file(s) selected"
                btnCreateZip.isEnabled = true
                statusText.text = ""
            } else {
                selectedCountText.text = "No files selected"
                btnCreateZip.isEnabled = false
            }
        }

    // Launcher for choosing where to save the resulting zip file
    private val createZipLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) {
                createZipFile(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectedCountText = findViewById(R.id.selectedCountText)
        fileListView = findViewById(R.id.fileListView)
        zipNameEdit = findViewById(R.id.zipNameEdit)
        btnCreateZip = findViewById(R.id.btnCreateZip)
        statusText = findViewById(R.id.statusText)

        val btnPickFiles: Button = findViewById(R.id.btnPickFiles)

        btnPickFiles.setOnClickListener {
            pickFilesLauncher.launch(arrayOf("*/*"))
        }

        btnCreateZip.setOnClickListener {
            if (selectedUris.isEmpty()) {
                Toast.makeText(this, "Please select files first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            var zipName = zipNameEdit.text.toString().trim()
            if (zipName.isEmpty()) zipName = "archive.zip"
            if (!zipName.endsWith(".zip")) zipName += ".zip"
            createZipLauncher.launch(zipName)
        }
    }

    /** Resolves the display name of a content Uri (falls back to last path segment). */
    private fun queryFileName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "file"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = it.getString(idx)
            }
        }
        return name
    }

    /** Streams each selected file into a new zip archive written to [outputUri]. */
    private fun createZipFile(outputUri: Uri) {
        statusText.text = "Creating zip..."
        try {
            contentResolver.openOutputStream(outputUri)?.use { rawOut ->
                ZipOutputStream(BufferedOutputStream(rawOut)).use { zipOut ->
                    for (uri in selectedUris) {
                        val entryName = queryFileName(uri)
                        contentResolver.openInputStream(uri)?.use { rawIn ->
                            BufferedInputStream(rawIn).use { input ->
                                zipOut.putNextEntry(ZipEntry(entryName))
                                input.copyTo(zipOut)
                                zipOut.closeEntry()
                            }
                        }
                    }
                }
            }
            statusText.text = "Zip file created successfully!"
            Toast.makeText(this, "Zip created successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            statusText.text = "Error: ${e.message}"
            Toast.makeText(this, "Failed to create zip: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
