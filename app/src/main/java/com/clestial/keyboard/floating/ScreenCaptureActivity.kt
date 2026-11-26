package com.clestial.keyboard.floating

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Activity transparan untuk meminta izin MediaProjection, lalu langsung mendelegasikan
 * tangkapan layar ke [MediaProjectionCaptureService] (wajib ForegroundService di Android 14+).
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
            FloatingMenuManager.start(applicationContext)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val svcIntent = Intent(this, MediaProjectionCaptureService::class.java).apply {
                    putExtra(MediaProjectionCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(MediaProjectionCaptureService.EXTRA_RESULT_DATA, data)
                    putExtra(MediaProjectionCaptureService.EXTRA_CROP_X, cropX)
                    putExtra(MediaProjectionCaptureService.EXTRA_CROP_Y, cropY)
                    putExtra(MediaProjectionCaptureService.EXTRA_CROP_W, cropW)
                    putExtra(MediaProjectionCaptureService.EXTRA_CROP_H, cropH)
                }
                ContextCompat.startForegroundService(this, svcIntent)
            } else {
                Toast.makeText(this, "Tangkapan layar dibatalkan", Toast.LENGTH_SHORT).show()
                FloatingMenuManager.start(applicationContext)
            }
            finish()
        }
    }
}
