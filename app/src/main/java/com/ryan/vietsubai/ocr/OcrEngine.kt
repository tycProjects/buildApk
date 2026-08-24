package com.ryan.vietsubai.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    suspend fun recognize(bitmap: Bitmap, crop: FloatArray): String = suspendCancellableCoroutine { cont ->
        val left=(bitmap.width*crop[0]).toInt().coerceIn(0,bitmap.width-1)
        val top=(bitmap.height*crop[1]).toInt().coerceIn(0,bitmap.height-1)
        val right=(bitmap.width*crop[2]).toInt().coerceIn(left+1,bitmap.width)
        val bottom=(bitmap.height*crop[3]).toInt().coerceIn(top+1,bitmap.height)
        val cropped=Bitmap.createBitmap(bitmap,left,top,right-left,bottom-top)
        recognizer.process(InputImage.fromBitmap(cropped,0)).addOnSuccessListener { if(cont.isActive) cont.resume(it.text) }.addOnFailureListener { if(cont.isActive) cont.resume("") }
    }
}
