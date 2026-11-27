package com.clestial.keyboard.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.clestial.keyboard.Prefs

object FloatingMenuManager {

    const val ACTION_SHOW_MENU = "com.clestial.keyboard.floating.ACTION_SHOW_MENU"
    const val ACTION_HIDE_MENU = "com.clestial.keyboard.floating.ACTION_HIDE_MENU"
    const val ACTION_TOGGLE_MENU = "com.clestial.keyboard.floating.ACTION_TOGGLE_MENU"

    const val ACTION_SHOW_DOCK = "com.clestial.keyboard.floating.ACTION_SHOW_DOCK"
    const val ACTION_HIDE_DOCK = "com.clestial.keyboard.floating.ACTION_HIDE_DOCK"

    const val ACTION_SHOW_BOX = "com.clestial.keyboard.floating.ACTION_SHOW_BOX"
    const val ACTION_HIDE_BOX = "com.clestial.keyboard.floating.ACTION_HIDE_BOX"

    const val ACTION_SEARCH_GOOGLE = "com.clestial.keyboard.floating.ACTION_SEARCH_GOOGLE"
    const val ACTION_SHOW_AUTO_ANSWER = "com.clestial.keyboard.floating.ACTION_SHOW_AUTO_ANSWER"
    const val ACTION_SHOW_AI_RESULT = "com.clestial.keyboard.floating.ACTION_SHOW_AI_RESULT"
    const val EXTRA_QUERY = "extra_query"
    const val EXTRA_SOLUTION = "extra_solution"

    fun canDrawOverlays(context: Context): Boolean {
        if (OverlayAccessibilityService.isRunning) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun start(context: Context) {
        if (!canDrawOverlays(context)) return
        val prefs = Prefs(context)
        prefs.floatingEnabled = true
        val accessibilityService = OverlayAccessibilityService.instance
        if (accessibilityService != null) {
            accessibilityService.showFloatingBubble()
            context.stopService(Intent(context, FloatingMenuService::class.java))
            return
        }
        val intent = Intent(context, FloatingMenuService::class.java)
        context.startService(intent)
    }

    fun stop(context: Context) {
        val prefs = Prefs(context)
        prefs.floatingEnabled = false
        OverlayAccessibilityService.instance?.hideFloatingOverlay()
        val intent = Intent(context, FloatingMenuService::class.java)
        context.stopService(intent)
    }

    fun show(context: Context) {
        dispatchAction(context, ACTION_SHOW_MENU)
    }

    fun showDock(context: Context) {
        dispatchAction(context, ACTION_SHOW_DOCK)
    }

    fun showSelectionBox(context: Context) {
        dispatchAction(context, ACTION_SHOW_BOX)
    }

    fun searchGoogle(context: Context, query: String) {
        dispatchAction(context, ACTION_SEARCH_GOOGLE, query = query)
    }

    fun showAutoAnswer(context: Context, solutionText: String? = null) {
        dispatchAction(context, ACTION_SHOW_AUTO_ANSWER, solution = solutionText)
    }

    fun showAiResult(context: Context, questionText: String, answerText: String) {
        dispatchAction(context, ACTION_SHOW_AI_RESULT, query = questionText, solution = answerText)
    }

    fun toggle(context: Context) {
        dispatchAction(context, ACTION_TOGGLE_MENU)
    }

    private fun dispatchAction(
        context: Context,
        action: String,
        query: String? = null,
        solution: String? = null
    ) {
        if (!canDrawOverlays(context)) return
        val accessibilityService = OverlayAccessibilityService.instance
        if (accessibilityService != null) {
            accessibilityService.dispatchAction(action, query, solution)
            return
        }
        val intent = Intent(context, FloatingMenuService::class.java).apply {
            this.action = action
            if (query != null) {
                putExtra(EXTRA_QUERY, query)
            }
            if (solution != null) {
                putExtra(EXTRA_SOLUTION, solution)
            }
        }
        context.startService(intent)
    }
}
