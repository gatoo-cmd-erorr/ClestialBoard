package com.clestial.keyboard.floating

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.clestial.keyboard.R

/**
 * Foreground Service khusus MediaProjection (wajib di Android 14+ / API 34)
 * agar tangkapan layar kotak seleksi tidak crash dengan SecurityException.
 */
class MediaProjectionCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_CROP_X = "crop_x"
        const val EXTRA_CROP_Y = "crop_y"
        const val EXTRA_CROP_W = "crop_w"
        const val EXTRA_CROP_H = "crop_h"
        private const val CHANNEL_ID = "clestial_capture_channel"
        private const val NOTIF_ID = 4901
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        val cropX = intent?.getIntExtra(EXTRA_CROP_X, 0) ?: 0
        val cropY = intent?.getIntExtra(EXTRA_CROP_Y, 0) ?: 0
        val cropW = intent?.getIntExtra(EXTRA_CROP_W, 0) ?: 0
        val cropH = intent?.getIntExtra(EXTRA_CROP_H, 0) ?: 0

        if (resultCode != Activity.RESULT_OK || data == null) {
            Toast.makeText(applicationContext, "Izin tangkap layar tidak diberikan", Toast.LENGTH_SHORT).show()
            FloatingMenuManager.show(applicationContext)
            finishService()
            return START_NOT_STICKY
        }

        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val projection = try {
            pm?.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        if (projection == null) {
            Toast.makeText(applicationContext, "Gagal memulai tangkapan layar", Toast.LENGTH_SHORT).show()
            FloatingMenuManager.show(applicationContext)
            finishService()
            return START_NOT_STICKY
        }

        mediaProjection = projection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    cleanupProjection()
                }
            }, mainHandler)
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection.createVirtualDisplay(
            "ClestialScreenCrop",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, mainHandler
        )

        mainHandler.postDelayed({
            var croppedBitmap: Bitmap? = null
            try {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val fullBitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    fullBitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val safeX = cropX.coerceIn(0, width - 1)
                    val safeY = cropY.coerceIn(0, height - 1)
                    val safeW = cropW.coerceAtMost(width - safeX).coerceAtLeast(1)
                    val safeH = cropH.coerceAtMost(height - safeY).coerceAtLeast(1)

                    croppedBitmap = Bitmap.createBitmap(fullBitmap, safeX, safeY, safeW, safeH)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cleanupProjection()
            }

            if (croppedBitmap != null) {
                ScreenOcrProcessor.processCroppedBitmap(applicationContext, croppedBitmap)
            } else {
                Toast.makeText(applicationContext, "Gagal mengambil area layar", Toast.LENGTH_SHORT).show()
                FloatingMenuManager.show(applicationContext)
            }
            finishService()
        }, 300)

        return START_NOT_STICKY
    }

    private fun ensureForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Pemindaian Layar Cepat",
                NotificationManager.IMPORTANCE_MIN
            )
            nm.createNotificationChannel(ch)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notif = builder
            .setContentTitle("ClestialKeyboard")
            .setContentText("Memindai area soal...")
            .setSmallIcon(R.drawable.ic_kb_search)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun cleanupProjection() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
    }

    private fun finishService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        cleanupProjection()
        super.onDestroy()
    }
}
