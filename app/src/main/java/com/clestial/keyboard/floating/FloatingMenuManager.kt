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
    const val EXTRA_QUERY = "extra_query"
    const val EXTRA_SOLUTION = "extra_solution"

    fun canDrawOverlays(context: Context): Boolean {
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
        val intent = Intent(context, FloatingMenuService::class.java)
        context.startService(intent)
    }

    fun stop(context: Context) {
        val prefs = Prefs(context)
        prefs.floatingEnabled = false
        val intent = Intent(context, FloatingMenuService::class.java)
        context.stopService(intent)
    }

    fun show(context: Context) {
        if (!canDrawOverlays(context)) return
        val intent = Intent(context, FloatingMenuService::class.java).apply {
            action = ACTION_SHOW_MENU
        }
        context.startService(intent)
    }

    fun showDock(context: Context) {
        if (!canDrawOverlays(context)) return
        val intent = Intent(context, FloatingMenuService::class.java).apply {
            action = ACTION_SHOW_DOCK
        }
        context.startService(intent)
    }

    fun showSelectionBox(context: Context) {
        if (!canDrawOverlays(context)) return
        val intent = Intent(context, FloatingMenuService::class.java).apply {
            action = ACTION_SHOW_BOX
        }
        context.startService(intent)
    }

    fun searchGoogle(context: Context, query: String) {
        if (!canDrawOverlays(context)) return
        val intent = Intent(context, FloatingMenuService::class.java).apply {
            action = ACTION_SEARCH_GOOGLE
            putExtra(EXTRA_QUERY, query)
        }
        context.startService(intent)
    }

    fun showAutoAnswer(context: Context, solutionText: String? = null) {
        if (!canDrawOverlays(context)) return
        val intent = Intent(context, FloatingMenuService::class.java).apply {
            action = ACTION_SHOW_AUTO_ANSWER
            if (solutionText != null) {
                putExtra(EXTRA_SOLUTION, solutionText)
            }
        }
        context.startService(intent)
    }

    fun toggle(context: Context) {
        if (!canDrawOverlays(context)) return
        val intent = Intent(context, FloatingMenuService::class.java).apply {
            action = ACTION_TOGGLE_MENU
        }
        context.startService(intent)
    }
}
