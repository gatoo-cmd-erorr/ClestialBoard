package com.clestial.keyboard.floating

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import com.clestial.keyboard.Prefs

class FloatingMenuService : Service(), OverlayWindowHost {

    private lateinit var windowManager: WindowManager
    private var controller: FloatingOverlayController? = null

    override val hostContext: Context
        get() = this

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (OverlayAccessibilityService.isRunning) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        controller = FloatingOverlayController(this).apply {
            initAllViews()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (OverlayAccessibilityService.isRunning) {
            stopSelf()
            return START_NOT_STICKY
        }
        val activeController = controller ?: FloatingOverlayController(this).also {
            controller = it
            it.initAllViews()
        }
        val action = intent?.action
        if (action == null) {
            if (Prefs(this).floatingEnabled) {
                activeController.showBubble()
            }
        } else {
            activeController.handleAction(
                action = action,
                query = intent.getStringExtra(FloatingMenuManager.EXTRA_QUERY),
                solution = intent.getStringExtra(FloatingMenuManager.EXTRA_SOLUTION)
            )
        }
        return START_STICKY
    }

    override fun createLayoutParams(
        width: Int,
        height: Int,
        gravity: Int,
        x: Int,
        y: Int
    ): WindowManager.LayoutParams {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }
    }

    override fun addOverlayView(view: View, params: WindowManager.LayoutParams) {
        if (!::windowManager.isInitialized) return
        try {
            windowManager.addView(view, params)
        } catch (_: Exception) {
        }
    }

    override fun updateOverlayView(view: View, params: WindowManager.LayoutParams) {
        if (!::windowManager.isInitialized) return
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    override fun removeOverlayView(view: View) {
        if (!::windowManager.isInitialized) return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        controller?.destroy()
        controller = null
        super.onDestroy()
    }
}
