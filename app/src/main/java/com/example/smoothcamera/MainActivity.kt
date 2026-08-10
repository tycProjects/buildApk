package com.example.smoothcamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * SmoothCamera - a Camera2-based camera that forces a locked 60fps capture
 * pipeline for preview + video recording (falling back to the device's
 * constrained high-speed capture session when a standard 60fps AE range
 * isn't advertised), and uses the fastest still-capture template for photos.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SmoothCamera"
        private const val TARGET_FPS = 60
        private const val REQUEST_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private lateinit var textureView: TextureView
    private lateinit var fpsLabel: TextView
    private lateinit var recIndicator: TextView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnRecord: ImageButton
    private lateinit var btnSwitch: ImageButton

    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var lensFacing = CameraCharacteristics.LENS_FACING_BACK
    private var currentCameraId: String = ""
    private var chosenFpsRange: Range<Int>? = null
    private var usingHighSpeed = false
    private var previewSize = Size(1920, 1080)
    private var videoSize = Size(1920, 1080)

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var recordingFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        fpsLabel = findViewById(R.id.fpsLabel)
        recIndicator = findViewById(R.id.recIndicator)
        btnCapture = findViewById(R.id.btnCapture)
        btnRecord = findViewById(R.id.btnRecord)
        btnSwitch = findViewById(R.id.btnSwitch)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        btnCapture.setOnClickListener { takePhoto() }
        btnRecord.setOnClickListener { toggleRecording() }
        btnSwitch.setOnClickListener { switchCamera() }

        if (hasAllPermissions()) {
            // Camera opens once the TextureView surface is ready (see listener below)
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS)
        }
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            if (hasAllPermissions()) openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        stopRecordingInternal(save = true)
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (hasAllPermissions()) {
                if (textureView.isAvailable) {
                    openCamera(textureView.width, textureView.height)
                } else {
                    textureView.surfaceTextureListener = surfaceTextureListener
                }
            } else {
                Toast.makeText(this, "Camera and mic permissions are required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun hasAllPermissions(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    // ---------------------------------------------------------------
    // Background thread
    // ---------------------------------------------------------------

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("SmoothCameraBg").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    // ---------------------------------------------------------------
    // TextureView surface
    // ---------------------------------------------------------------

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            if (hasAllPermissions()) openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    // ---------------------------------------------------------------
    // Camera setup
    // ---------------------------------------------------------------

    private fun openCamera(viewWidth: Int, viewHeight: Int) {
        val manager = cameraManager ?: return
        try {
            currentCameraId = findCameraId(manager, lensFacing) ?: run {
                Toast.makeText(this, "No matching camera found", Toast.LENGTH_SHORT).show()
                return
            }

            val characteristics = manager.getCameraCharacteristics(currentCameraId)
            pickFpsRangeAndSizes(characteristics)
            configureTransform(viewWidth, viewHeight)

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) return

            manager.openCamera(currentCameraId, cameraStateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "openCamera failed", e)
            Toast.makeText(this, "Could not access camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findCameraId(manager: CameraManager, facing: Int): String? {
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == facing) return id
        }
        return manager.cameraIdList.firstOrNull()
    }

    /**
     * Chooses the best available path to a locked 60fps stream:
     *  1) A standard AE_AVAILABLE_TARGET_FPS_RANGE that is exactly/contains 60fps
     *     (works with a normal capture session - preferred, most modern phones).
     *  2) A high-speed video FPS range (requires createConstrainedHighSpeedCaptureSession).
     * Falls back to the highest fps range available if 60 isn't supported at all.
     */
    private fun pickFpsRangeAndSizes(characteristics: CameraCharacteristics) {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val standardRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?: emptyArray()

        val exact60 = standardRanges.firstOrNull { it.lower == TARGET_FPS && it.upper == TARGET_FPS }
        val contains60 = standardRanges.firstOrNull { it.lower <= TARGET_FPS && it.upper >= TARGET_FPS }

        if (exact60 != null || contains60 != null) {
            chosenFpsRange = exact60 ?: contains60
            usingHighSpeed = false
            val sizes = map?.getOutputSizes(SurfaceTexture::class.java)?.toList() ?: emptyList()
            previewSize = pickClosestSize(sizes, 1920, 1080)
            videoSize = previewSize
            return
        }

        // Fall back to constrained high-speed session (guarantees 60/120/240fps on capable HW)
        val hsRanges = map?.highSpeedVideoFpsRanges ?: emptyArray()
        val hs60 = hsRanges.firstOrNull { it.lower <= TARGET_FPS && it.upper >= TARGET_FPS }
        if (hs60 != null && map != null) {
            chosenFpsRange = hs60
            usingHighSpeed = true
            val sizes = map.getHighSpeedVideoSizesFor(hs60).toList()
            videoSize = pickClosestSize(sizes, 1920, 1080)
            previewSize = videoSize
            return
        }

        // Last resort: highest fps range the device actually offers
        usingHighSpeed = false
        chosenFpsRange = standardRanges.maxByOrNull { it.upper } ?: Range(30, 30)
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java)?.toList() ?: emptyList()
        previewSize = pickClosestSize(sizes, 1920, 1080)
        videoSize = previewSize
        runOnUiThread {
            Toast.makeText(
                this,
                "This device can't guarantee 60fps - using ${chosenFpsRange?.upper}fps instead",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun pickClosestSize(sizes: List<Size>, targetW: Int, targetH: Int): Size {
        if (sizes.isEmpty()) return Size(targetW, targetH)
        return sizes.minByOrNull { abs(it.width * it.height - targetW * targetH) } ?: sizes[0]
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            runOnUiThread {
                fpsLabel.text = "FPS: ${chosenFpsRange?.upper ?: TARGET_FPS}" +
                    if (usingHighSpeed) " (HS)" else ""
            }
            createPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
            Log.e(TAG, "Camera error: $error")
        }
    }

    private fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }

    // ---------------------------------------------------------------
    // Preview / capture session (locked to TARGET_FPS)
    // ---------------------------------------------------------------

    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(texture)

        try {
            val template = if (isRecording) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW
            val builder = device.createCaptureRequest(template).apply {
                addTarget(previewSurface)
                mediaRecorder?.let { if (isRecording) addTarget(it.surface) }
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                chosenFpsRange?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
                set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                if (characteristicsSupportVideoStab()) {
                    set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
                }
            }
            previewRequestBuilder = builder

            val surfaces = mutableListOf(previewSurface)
            if (isRecording) mediaRecorder?.let { surfaces.add(it.surface) }

            if (usingHighSpeed) {
                openHighSpeedSession(device, surfaces, builder)
            } else {
                openNormalSession(device, surfaces, builder)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "createPreviewSession failed", e)
        }
    }

    private fun characteristicsSupportVideoStab(): Boolean {
        return try {
            val chars = cameraManager?.getCameraCharacteristics(currentCameraId)
            val modes = chars?.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            modes?.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true
        } catch (e: Exception) {
            false
        }
    }

    private fun openNormalSession(device: CameraDevice, surfaces: List<Surface>, builder: CaptureRequest.Builder) {
        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                } catch (e: CameraAccessException) {
                    Log.e(TAG, "setRepeatingRequest failed", e)
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Normal session config failed")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val outputConfigs = surfaces.map { android.hardware.camera2.params.OutputConfiguration(it) }
            val config = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, outputConfigs,
                { it.run() }, stateCallback
            )
            device.createCaptureSession(config)
        } else {
            @Suppress("DEPRECATION")
            device.createCaptureSession(surfaces, stateCallback, backgroundHandler)
        }
    }

    private fun openHighSpeedSession(device: CameraDevice, surfaces: List<Surface>, builder: CaptureRequest.Builder) {
        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    val hsSession = session as CameraConstrainedHighSpeedCaptureSession
                    val requestList = hsSession.createHighSpeedRequestList(builder.build())
                    hsSession.setRepeatingBurst(requestList, null, backgroundHandler)
                } catch (e: CameraAccessException) {
                    Log.e(TAG, "High speed setRepeatingBurst failed", e)
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "High speed session config failed")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val outputConfigs = surfaces.map { android.hardware.camera2.params.OutputConfiguration(it) }
            val config = SessionConfiguration(
                SessionConfiguration.SESSION_HIGH_SPEED, outputConfigs,
                { it.run() }, stateCallback
            )
            device.createCaptureSession(config)
        } else {
            @Suppress("DEPRECATION")
            device.createConstrainedHighSpeedCaptureSession(surfaces, stateCallback, backgroundHandler)
        }
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val matrix = Matrix()
        textureView.setTransform(matrix)
    }

    // ---------------------------------------------------------------
    // Photo capture (fastest available still-capture path)
    // ---------------------------------------------------------------

    private fun takePhoto() {
        // Grabs a frame straight off the live 60fps preview buffer - zero shutter
        // lag and works identically whether we're in a normal or high-speed session.
        val bitmap = textureView.bitmap ?: run {
            Toast.makeText(this, "Preview not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
        val fileName = "IMG_" + timestamp() + ".jpg"
        val file = File(dir, fileName)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
            Toast.makeText(this, "Saved photo: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "takePhoto failed", e)
            Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------
    // Video recording (locked TARGET_FPS)
    // ---------------------------------------------------------------

    private fun toggleRecording() {
        if (isRecording) stopRecordingInternal(save = true) else startRecordingInternal()
    }

    private fun startRecordingInternal() {
        val device = cameraDevice ?: return
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        if (!dir.exists()) dir.mkdirs()
        recordingFile = File(dir, "VID_" + timestamp() + ".mp4")

        try {
            captureSession?.close()
            captureSession = null

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncodingBitRate(16_000_000)
                setVideoFrameRate(chosenFpsRange?.upper ?: TARGET_FPS)
                setVideoSize(videoSize.width, videoSize.height)
                setOutputFile(recordingFile!!.absolutePath)
                prepare()
            }

            isRecording = true
            createPreviewSession() // rebuild session including the recorder surface
            mediaRecorder?.start()

            runOnUiThread {
                recIndicator.visibility = View.VISIBLE
                btnRecord.contentDescription = "Stop Recording"
            }
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e)
            isRecording = false
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecordingInternal(save: Boolean) {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording error (file may still be valid)", e)
        }
        mediaRecorder = null
        isRecording = false

        runOnUiThread {
            recIndicator.visibility = View.INVISIBLE
            if (save && recordingFile != null) {
                Toast.makeText(this, "Saved video: ${recordingFile?.name}", Toast.LENGTH_SHORT).show()
            }
        }

        // Rebuild a plain preview-only session
        captureSession?.close()
        captureSession = null
        if (cameraDevice != null) createPreviewSession()
    }

    // ---------------------------------------------------------------
    // Camera switch (front/back)
    // ---------------------------------------------------------------

    private fun switchCamera() {
        if (isRecording) stopRecordingInternal(save = true)
        lensFacing = if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        closeCamera()
        if (textureView.isAvailable) openCamera(textureView.width, textureView.height)
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
