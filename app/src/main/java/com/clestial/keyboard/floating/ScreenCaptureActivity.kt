package com.clestial.keyboard.floating

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Activity transparan untuk menangkap gambar layar area kotak seleksi (Circle to Search / Crop Soal)
 * menggunakan MediaProjection resmi Android, lalu mengekstrak teks soal dengan ML Kit Text Recognition
 * dan langsung membuka Google Search melayang secara real-time.
 */
class ScreenCaptureActivity : Activity() {

    companion object {
        const val EXTRA_CROP_X = "crop_x"
        const val EXTRA_CROP_Y = "crop_y"
        const val EXTRA_CROP_W = "crop_w"
        const val EXTRA_CROP_H = "crop_h"
        private const val REQUEST_CODE_CAPTURE = 9921
    }

    private var cropX = 0
    private var cropY = 0
    private var cropW = 0
    private var cropH = 0

    private var projectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cropX = intent.getIntExtra(EXTRA_CROP_X, 0)
        cropY = intent.getIntExtra(EXTRA_CROP_Y, 0)
        cropW = intent.getIntExtra(EXTRA_CROP_W, 0)
        cropH = intent.getIntExtra(EXTRA_CROP_H, 0)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager != null) {
            val captureIntent = projectionManager!!.createScreenCaptureIntent()
            startActivityForResult(captureIntent, REQUEST_CODE_CAPTURE)
        } else {
            Toast.makeText(this, "MediaProjection tidak didukung", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                captureAndProcess(data)
            } else {
                Toast.makeText(this, "Tangkapan layar dibatalkan", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun captureAndProcess(data: Intent) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        mediaProjection = projectionManager?.getMediaProjection(RESULT_OK, data)
        if (mediaProjection == null) {
            Toast.makeText(this, "Gagal mendapatkan MediaProjection", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ClestialScreenCrop",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        Handler(Looper.getMainLooper()).postDelayed({
            var croppedBitmap: Bitmap? = null
            try {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val safeX = cropX.coerceIn(0, width - 1)
                    val safeY = cropY.coerceIn(0, height - 1)
                    val safeW = cropW.coerceAtMost(width - safeX).coerceAtLeast(1)
                    val safeH = cropH.coerceAtMost(height - safeY).coerceAtLeast(1)

                    croppedBitmap = Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                virtualDisplay?.release()
                mediaProjection?.stop()
            }

            if (croppedBitmap != null) {
                runOcrAndOpenGoogle(croppedBitmap)
            } else {
                Toast.makeText(applicationContext, "Gagal mengambil area layar", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, 250)
    }

    private fun runOcrAndOpenGoogle(bitmap: Bitmap) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.trim().replace("\n", " ")
                if (text.isNotEmpty()) {
                    Toast.makeText(applicationContext, "Soal terbaca: $text", Toast.LENGTH_SHORT).show()
                    FloatingMenuManager.searchGoogle(applicationContext, text)
                } else {
                    Toast.makeText(applicationContext, "Tidak ada teks/soal terbaca di dalam kotak", Toast.LENGTH_SHORT).show()
                    FloatingMenuManager.searchGoogle(applicationContext, "")
                }
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(applicationContext, "OCR Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                FloatingMenuManager.searchGoogle(applicationContext, "")
                finish()
            }
    }
}
