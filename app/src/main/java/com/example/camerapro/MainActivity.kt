package com.example.camerapro

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var shutter: Button
    private lateinit var executor: ExecutorService
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var mode = "PHOTO"
    private var timerSec = 0
    private var hdr = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        preview = findViewById(R.id.preview); status = findViewById(R.id.status); shutter = findViewById(R.id.shutter)
        executor = Executors.newSingleThreadExecutor()
        setupButtons()
        if (hasPermissions()) startCamera() else ActivityCompat.requestPermissions(this, REQUIRED, REQ)
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.switchCamera).setOnClickListener { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK; startCamera() }
        findViewById<Button>(R.id.flash).setOnClickListener { toggleFlash() }
        findViewById<Button>(R.id.hdr).setOnClickListener { hdr = !hdr; findViewById<Button>(R.id.hdr).text = if (hdr) "HDR ON" else "HDR" }
        findViewById<Button>(R.id.timer).setOnClickListener { timerSec = when(timerSec){0->3;3->10;else->0}; findViewById<Button>(R.id.timer).text = "${timerSec}s" }
        findViewById<Button>(R.id.settings).setOnClickListener { showSettings() }
        findViewById<ImageButton>(R.id.gallery).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)) }
        findViewById<TextView>(R.id.photoMode).setOnClickListener { mode="PHOTO"; updateMode() }
        findViewById<TextView>(R.id.videoMode).setOnClickListener { mode="VIDEO"; updateMode() }
        findViewById<TextView>(R.id.portraitMode).setOnClickListener { mode="PORTRAIT"; updateMode() }
        (findViewById<ViewGroup>(R.id.zoomRow)).let { row -> for(i in 0 until row.childCount) row.getChildAt(i).setOnClickListener { val text=(it as Button).text.toString(); val z=text.replace("×","").toFloat(); camera?.cameraControl?.setZoomRatio(z.coerceAtLeast(camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f)) } }
        shutter.setOnClickListener { if (mode=="VIDEO") toggleVideo() else takePhoto() }
        preview.setOnTouchListener { _, e -> if(e.action==MotionEvent.ACTION_UP) { val point=preview.meteringPointFactory.createPoint(e.x,e.y); val action=FocusMeteringAction.Builder(point).setAutoCancel(2500).build(); camera?.cameraControl?.startFocusAndMetering(action); true } else true }
        updateMode()
    }

    private fun updateMode(){
        findViewById<TextView>(R.id.photoMode).alpha=if(mode=="PHOTO")1f else .55f
        findViewById<TextView>(R.id.videoMode).alpha=if(mode=="VIDEO")1f else .55f
        findViewById<TextView>(R.id.portraitMode).alpha=if(mode=="PORTRAIT")1f else .55f
        shutter.backgroundTintList=android.content.res.ColorStateList.valueOf(if(mode=="VIDEO") Color.rgb(210,30,30) else Color.WHITE)
    }

    private fun startCamera(){
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider=future.get(); provider.unbindAll()
                val selector=CameraSelector.Builder().requireLensFacing(lensFacing).build()
                if(!provider.hasCamera(selector)){ showError("هذه الكاميرا غير متوفرة"); return@addListener }
                val p=Preview.Builder().build().also{it.setSurfaceProvider(preview.surfaceProvider)}
                imageCapture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                val recorder=Recorder.Builder().setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.FHD,Quality.HD,Quality.SD))).build()
                videoCapture=VideoCapture.withOutput(recorder)
                camera=provider.bindToLifecycle(this,selector,p,imageCapture,videoCapture)
            }catch(e:Exception){ showError("تعذر تشغيل الكاميرا: ${e.localizedMessage}") }
        },ContextCompat.getMainExecutor(this))
    }

    private fun toggleFlash(){ val c=camera ?: return; if(lensFacing==CameraSelector.LENS_FACING_FRONT)return; val on=c.cameraInfo.torchState.value==TorchState.ON; c.cameraControl.enableTorch(!on) }

    private fun takePhoto(){
        val capture=imageCapture ?: return
        if(timerSec>0){ Toast.makeText(this,"التقاط بعد $timerSec ثوانٍ",Toast.LENGTH_SHORT).show(); Handler(Looper.getMainLooper()).postDelayed({savePhoto(capture)},timerSec*1000L) } else savePhoto(capture)
    }
    private fun savePhoto(capture: ImageCapture){
        val name="CAM_${SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())}.jpg"
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");if(Build.VERSION.SDK_INT>=29)put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/CameraPro")}
        val output=ImageCapture.OutputFileOptions.Builder(contentResolver,MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values).build()
        capture.takePicture(output,executor,object:ImageCapture.OnImageSavedCallback{
            override fun onImageSaved(result:ImageCapture.OutputFileResults){runOnUiThread{Toast.makeText(this@MainActivity,"تم حفظ الصورة ✓",Toast.LENGTH_SHORT).show()}}
            override fun onError(e:ImageCaptureException){runOnUiThread{showError("فشل التصوير: ${e.message}")}}
        })
    }

    private fun toggleVideo(){
        val vc=videoCapture ?: return
        val active=recording
        if(active!=null){active.stop();recording=null;shutter.text="";Toast.makeText(this,"تم حفظ الفيديو ✓",Toast.LENGTH_SHORT).show();return}
        val name="VID_${SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())+".mp4"
        val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,name);put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");if(Build.VERSION.SDK_INT>=29)put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/CameraPro")}
        val output=MediaStoreOutputOptions.Builder(contentResolver,MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        recording=vc.output.prepareRecording(this,output).apply{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)withAudioEnabled()}.start(ContextCompat.getMainExecutor(this)){event-> when(event){is VideoRecordEvent.Start->runOnUiThread{shutter.text="■"};is VideoRecordEvent.Finalize->runOnUiThread{shutter.text=""; if(event.hasError())showError("فشل الفيديو: ${event.error}")}}}}
    }

    private fun showSettings(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(40,20,40,20)}
        val quality=TextView(this).apply{text="جودة التصوير\nFHD • 16:9\n\nالحفظ: Pictures/CameraPro و Movies/CameraPro\n\nاضغط خارج النافذة للإغلاق";setTextColor(Color.WHITE);textSize=16f}
        box.addView(quality)
        val dialog=android.app.Dialog(this);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.setContentView(box);dialog.window?.setDimAmount(.65f);dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);dialog.show();dialog.window?.setLayout(-1,-2)
    }

    private fun hasPermissions()=REQUIRED.all{ContextCompat.checkSelfPermission(this,it)==PackageManager.PERMISSION_GRANTED}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==REQ){if(hasPermissions())startCamera() else showError("اسمح للكاميرا والميكروفون من إعدادات الهاتف ثم أعد فتح التطبيق")}}
    private fun showError(msg:String){status.text=msg;status.visibility=View.VISIBLE;status.postDelayed({status.visibility=View.GONE},6000)}
    override fun onDestroy(){recording?.stop();executor.shutdown();super.onDestroy()}
    companion object{private const val REQ=10;private val REQUIRED=arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)}
}
