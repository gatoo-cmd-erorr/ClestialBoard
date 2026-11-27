package com.clestial.keyboard.floating

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.clestial.keyboard.Prefs

class OverlayAccessibilityService : AccessibilityService(), OverlayWindowHost {

    companion object {
        @Volatile
        var instance: OverlayAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }

    private lateinit var windowManager: WindowManager
    private var controller: FloatingOverlayController? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override val hostContext: Context
        get() = this

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        stopService(Intent(this, FloatingMenuService::class.java))

        controller?.destroy()
        controller = FloatingOverlayController(this).apply {
            initAllViews()
            if (Prefs(this@OverlayAccessibilityService).floatingEnabled) {
                showBubble()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun createLayoutParams(
        width: Int,
        height: Int,
        gravity: Int,
        x: Int,
        y: Int
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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

    fun showFloatingBubble() {
        runOnMain {
            controller?.showBubble()
        }
    }

    fun hideFloatingOverlay() {
        runOnMain {
            controller?.hideAll()
        }
    }

    fun dispatchAction(action: String?, query: String? = null, solution: String? = null) {
        runOnMain {
            controller?.handleAction(action, query, solution)
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanupOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanupOverlay()
        super.onDestroy()
    }

    private fun cleanupOverlay() {
        mainHandler.removeCallbacksAndMessages(null)
        controller?.destroy()
        controller = null
        if (instance === this) {
            instance = null
        }
    }
}
