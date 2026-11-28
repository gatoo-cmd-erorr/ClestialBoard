package com.clestial.keyboard.floating

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import com.clestial.keyboard.Prefs
import com.clestial.keyboard.R
import com.clestial.keyboard.autoanswer.AbcdSolutionResult
import com.clestial.keyboard.autoanswer.AbcdSolver
import com.clestial.keyboard.autoanswer.AutoScannerService
import com.clestial.keyboard.math.AiRouterClient
import com.clestial.keyboard.math.FieldType
import com.clestial.keyboard.math.MathModule
import com.clestial.keyboard.math.MathRegistry
import com.clestial.keyboard.math.OfflineMathAssistant
import com.clestial.keyboard.math.ScientificCalculator
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import kotlin.math.abs

interface OverlayWindowHost {
    val hostContext: Context
    fun createLayoutParams(
        width: Int,
        height: Int,
        gravity: Int,
        x: Int,
        y: Int
    ): WindowManager.LayoutParams

    fun addOverlayView(view: View, params: WindowManager.LayoutParams)
    fun updateOverlayView(view: View, params: WindowManager.LayoutParams)
    fun removeOverlayView(view: View)
}

class FloatingOverlayController(private val host: OverlayWindowHost) {

    private val context: Context = host.hostContext
    private val prefs = Prefs(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    // 1. Stealth Bubble (Tombol Mengambang Tersamar di tepi layar)
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // 2. Vertical Floating Dock (Bilah Vertikal Samping)
    private var dockView: View? = null
    private var dockParams: WindowManager.LayoutParams? = null
    private var isDockShowing = false

    // 3. Selection Box (Kotak Seleksi Bergaris Biru Neon)
    private var selectionBoxView: View? = null
    private var selectionBoxParams: WindowManager.LayoutParams? = null
    private var isBoxShowing = false

    // 4. Pop-up Window (Tanya AI, Google Web, Kalkulator & SMP 9, Auto ABCD)
    private var popupView: View? = null
    private var popupParams: WindowManager.LayoutParams? = null
    private var isPopupShowing = false

    // State Pop-up Views
    private var etAiQuestionRef: EditText? = null
    private var etGoogleSearchRef: EditText? = null
    private var webViewRef: WebView? = null
    private var selectTabRef: ((Int) -> Unit)? = null

    // State Math & Kalkulator di Pop-up
    private var isCalcModeSelected = true
    private var activeMathModule: MathModule = MathRegistry.modules.first()
    private val mathInputFields = LinkedHashMap<String, EditText>()
    private val mathChoiceValues = LinkedHashMap<String, String>()
    private var activeMathEditText: EditText? = null
    private var calcExprEditText: EditText? = null

    fun initAllViews() {
        createBubbleView()
        createDockView()
        createSelectionBoxView()
        createPopupView()
    }

    fun handleAction(action: String?, query: String? = null, solution: String? = null) {
        when (action) {
            FloatingMenuManager.ACTION_SHOW_MENU -> {
                hideDock()
                hideSelectionBox()
                showPopup(0)
            }
            FloatingMenuManager.ACTION_HIDE_MENU -> hidePopup()
            FloatingMenuManager.ACTION_TOGGLE_MENU -> {
                if (isPopupShowing) hidePopup() else showPopup(0)
            }
            FloatingMenuManager.ACTION_SHOW_DOCK -> {
                hidePopup()
                hideSelectionBox()
                showDock()
            }
            FloatingMenuManager.ACTION_HIDE_DOCK -> hideDock()
            FloatingMenuManager.ACTION_SHOW_BOX -> {
                hideDock()
                hidePopup()
                showSelectionBox()
            }
            FloatingMenuManager.ACTION_HIDE_BOX -> hideSelectionBox()
            FloatingMenuManager.ACTION_SEARCH_GOOGLE -> {
                hideDock()
                hideSelectionBox()
                showPopup(tabIndex = 1, initialQuery = query.orEmpty())
            }
            FloatingMenuManager.ACTION_SHOW_AUTO_ANSWER -> {
                hideDock()
                hideSelectionBox()
                showPopup(tabIndex = 3)
                if (!solution.isNullOrEmpty()) {
                    popupView?.findViewById<TextView>(R.id.tvAutoAnswerStatus)?.text = solution
                }
            }
            FloatingMenuManager.ACTION_SHOW_AI_RESULT -> {
                val qText = query.orEmpty()
                val ansText = solution.orEmpty()
                hideDock()
                hideSelectionBox()
                showPopup(tabIndex = 0)
                if (qText.isNotEmpty()) etAiQuestionRef?.setText(qText)
                if (ansText.isNotEmpty()) {
                    popupView?.findViewById<TextView>(R.id.tvAiResponse)?.text = ansText
                }
            }
            else -> {
                if (!isPopupShowing && !isDockShowing && !isBoxShowing) {
                    showBubble()
                }
            }
        }
    }

    private fun dp(v: Float): Int = (v * context.resources.displayMetrics.density).toInt()

    private fun disguisedWindowTitle(): String {
        return if (prefs.antiDetectEnabled) "SystemAssistOverlay" else "ClestialOverlay"
    }

    private fun applyWindowDisguise(params: WindowManager.LayoutParams) {
        params.title = disguisedWindowTitle()
    }

    private fun applyWindowDisguiseAll() {
        val title = disguisedWindowTitle()
        listOf(
            bubbleView to bubbleParams,
            dockView to dockParams,
            selectionBoxView to selectionBoxParams,
            popupView to popupParams
        ).forEach { (view, params) ->
            if (params != null) {
                params.title = title
                safeUpdateView(view, params)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyNavigationBarMode(view: View?, hideNav: Boolean) {
        if (view == null) return
        view.systemUiVisibility = if (hideNav) {
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            View.SYSTEM_UI_FLAG_VISIBLE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = view.windowInsetsController
                ?: (context as? Activity)?.window?.insetsController
            if (controller != null) {
                if (hideNav) {
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())
                } else {
                    controller.show(WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())
                }
            }
        }
    }

    private fun applyNavigationBarModeAll(hideNav: Boolean) {
        listOf(bubbleView, dockView, selectionBoxView, popupView).forEach { v ->
            applyNavigationBarMode(v, hideNav)
        }
        AutoScannerService.instance?.setImmersiveMode(hideNav)
    }

    @Suppress("DEPRECATION")
    private fun ensureOverlayWindowParams(params: WindowManager.LayoutParams) {
        if (context is AccessibilityService) {
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        params.format = PixelFormat.TRANSLUCENT
    }

    private fun safeAddView(view: View, params: WindowManager.LayoutParams) {
        applyWindowDisguise(params)
        ensureOverlayWindowParams(params)
        applyNavigationBarMode(view, prefs.hideNavigationEnabled)
        if (!view.isAttachedToWindow) {
            try {
                host.addOverlayView(view, params)
                view.post { applyNavigationBarMode(view, prefs.hideNavigationEnabled) }
                AutoScannerService.instance?.setImmersiveMode(prefs.hideNavigationEnabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun safeUpdateView(view: View?, params: WindowManager.LayoutParams) {
        if (view != null && view.isAttachedToWindow) {
            try {
                host.updateOverlayView(view, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ------------------------------------------------------------- 1. Stealth Bubble
    @SuppressLint("ClickableViewAccessibility")
    private fun createBubbleView() {
        val size = dp(prefs.floatingSizeDp.toFloat().coerceIn(24f, 64f))
        val bubble = View(context).apply {
            setBackgroundResource(R.drawable.bg_floating_bubble)
            alpha = (prefs.floatingOpacity / 100f).coerceIn(0.12f, 1.0f)
        }

        val params = host.createLayoutParams(
            width = size,
            height = size,
            gravity = Gravity.TOP or Gravity.START,
            x = 16,
            y = context.resources.displayMetrics.heightPixels / 3
        ).apply {
            ensureOverlayWindowParams(this)
        }

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (initialX + (event.rawX - initialTouchX)).toInt()
                    params.y = (initialY + (event.rawY - initialTouchY)).toInt()
                    safeUpdateView(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < touchSlop && diffY < touchSlop) {
                        if (isDockShowing) {
                            hideDock()
                        } else {
                            showDock()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        bubbleView = bubble
        bubbleParams = params
    }

    fun showBubble() {
        if (bubbleView == null) createBubbleView()
        val v = bubbleView ?: return
        val p = bubbleParams ?: return
        safeAddView(v, p)
        v.visibility = View.VISIBLE
        applyNavigationBarMode(v, prefs.hideNavigationEnabled)
    }

    fun hideBubble() {
        bubbleView?.visibility = View.GONE
    }

    // ------------------------------------------------------------- 2. Vertical Floating Dock
    @SuppressLint("ClickableViewAccessibility")
    private fun createDockView() {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_ClestialKeyboard)
        val dockRoot = LayoutInflater.from(themedContext).inflate(R.layout.layout_floating_dock, null)

        val params = host.createLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START,
            x = 0,
            y = context.resources.displayMetrics.heightPixels / 3 - dp(40f)
        ).apply {
            ensureOverlayWindowParams(this)
        }

        val dragHandle = dockRoot.findViewById<View>(R.id.dockDragHandle)
        var initialY = 0
        var touchStartY = 0f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.y = (initialY + (event.rawY - touchStartY)).toInt()
                    safeUpdateView(dockView, params)
                    true
                }
                else -> false
            }
        }

        dockRoot.findViewById<View>(R.id.btnDockCrop).setOnClickListener {
            hideDock()
            showSelectionBox()
        }

        dockRoot.findViewById<View>(R.id.btnDockAi).setOnClickListener {
            hideDock()
            showPopup(0)
        }

        dockRoot.findViewById<View>(R.id.btnDockWeb).setOnClickListener {
            hideDock()
            showPopup(1)
        }

        dockRoot.findViewById<View>(R.id.btnDockMath).setOnClickListener {
            hideDock()
            showPopup(2)
        }

        dockRoot.findViewById<View>(R.id.btnDockAutoAnswer).setOnClickListener {
            hideDock()
            val service = AutoScannerService.instance
            if (service != null && prefs.autoAnswerMode == Prefs.MODE_AUTO_CLICK) {
                Toast.makeText(context, "⚡ Memindai & menjawab otomatis...", Toast.LENGTH_SHORT).show()
                service.triggerAutoAnswer { _, _ -> }
            } else {
                showPopup(3)
            }
        }
        dockRoot.findViewById<View>(R.id.btnDockAutoAnswer).setOnLongClickListener {
            hideDock()
            showPopup(3)
            true
        }

        dockRoot.findViewById<View>(R.id.btnDockHide).setOnClickListener {
            hideDock()
            showBubble()
        }

        dockView = dockRoot
        dockParams = params
    }

    fun showDock() {
        if (dockView == null) createDockView()
        val v = dockView ?: return
        val p = dockParams ?: return

        bubbleParams?.let { bp ->
            p.y = bp.y - dp(30f)
        }

        safeAddView(v, p)
        v.visibility = View.VISIBLE
        applyNavigationBarMode(v, prefs.hideNavigationEnabled)
        AutoScannerService.instance?.detectAndBypassPinning()
        isDockShowing = true
        hideBubble()
    }

    fun hideDock() {
        dockView?.visibility = View.GONE
        isDockShowing = false
    }

    // ------------------------------------------------------------- 3. Selection Box
    @SuppressLint("ClickableViewAccessibility")
    private fun createSelectionBoxView() {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_ClestialKeyboard)
        val boxRoot = LayoutInflater.from(themedContext).inflate(R.layout.layout_selection_box, null)

        val displayW = context.resources.displayMetrics.widthPixels
        val defaultW = (displayW * 0.78f).toInt().coerceAtLeast(dp(220f))
        val defaultH = dp(170f)

        val params = host.createLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START,
            x = (displayW - defaultW) / 2,
            y = dp(180f)
        ).apply {
            ensureOverlayWindowParams(this)
        }

        val boxFrame = boxRoot.findViewById<FrameLayout>(R.id.boxFrame)
        val ivResizeHandle = boxRoot.findViewById<ImageView>(R.id.ivResizeHandle)
        val btnCropCancel = boxRoot.findViewById<ImageButton>(R.id.btnCropCancel)
        val btnCropConfirm = boxRoot.findViewById<ImageButton>(R.id.btnCropConfirm)

        val frameLp = boxFrame.layoutParams
        frameLp.width = defaultW
        frameLp.height = defaultH
        boxFrame.layoutParams = frameLp

        var dragStartX = 0
        var dragStartY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        boxFrame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = params.x
                    dragStartY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (dragStartX + (event.rawX - touchStartX)).toInt()
                    params.y = (dragStartY + (event.rawY - touchStartY)).toInt()
                    safeUpdateView(selectionBoxView, params)
                    true
                }
                else -> false
            }
        }

        var initialBoxW = 0
        var initialBoxH = 0
        var resizeTouchX = 0f
        var resizeTouchY = 0f

        ivResizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialBoxW = boxFrame.width
                    initialBoxH = boxFrame.height
                    resizeTouchX = event.rawX
                    resizeTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaW = (event.rawX - resizeTouchX).toInt()
                    val deltaH = (event.rawY - resizeTouchY).toInt()
                    val newW = (initialBoxW + deltaW).coerceAtLeast(dp(150f))
                    val newH = (initialBoxH + deltaH).coerceAtLeast(dp(80f))
                    val lp = boxFrame.layoutParams
                    lp.width = newW
                    lp.height = newH
                    boxFrame.layoutParams = lp
                    true
                }
                else -> false
            }
        }

        btnCropCancel.setOnClickListener {
            hideSelectionBox()
            showBubble()
        }

        btnCropConfirm.setOnClickListener {
            val location = IntArray(2)
            boxFrame.getLocationOnScreen(location)
            val cropX = location[0]
            val cropY = location[1]
            val cropW = boxFrame.width
            val cropH = boxFrame.height

            removeSelectionBoxOverlay()

            fun launchMediaProjection() {
                val captureIntent = Intent(context, ScreenCaptureActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    putExtra(ScreenCaptureActivity.EXTRA_CROP_X, cropX)
                    putExtra(ScreenCaptureActivity.EXTRA_CROP_Y, cropY)
                    putExtra(ScreenCaptureActivity.EXTRA_CROP_W, cropW)
                    putExtra(ScreenCaptureActivity.EXTRA_CROP_H, cropH)
                }
                context.startActivity(captureIntent)
            }

            val accService = AutoScannerService.instance
            if (accService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                accService.captureRegionSilentBitmap(cropX, cropY, cropW, cropH) { cropped ->
                    mainHandler.post {
                        if (cropped != null) {
                            analyzeCroppedBitmapWith9Router(cropped)
                        } else if (accService.isScreenPinned()) {
                            accService.simulateUnpinGesture {
                                mainHandler.postDelayed({ launchMediaProjection() }, 1000L)
                            }
                        } else {
                            launchMediaProjection()
                        }
                    }
                }
            } else if (accService != null && accService.isScreenPinned()) {
                accService.simulateUnpinGesture {
                    mainHandler.postDelayed({ launchMediaProjection() }, 1000L)
                }
            } else {
                launchMediaProjection()
            }
        }

        selectionBoxView = boxRoot
        selectionBoxParams = params
    }

    private fun analyzeCroppedBitmapWith9Router(bitmap: Bitmap) {
        showPopup(tabIndex = 0)
        etAiQuestionRef?.setText("[Analisis Gambar Seleksi]")
        val tvAiResponse = popupView?.findViewById<TextView>(R.id.tvAiResponse)

        if (!AiRouterClient.hasConfiguredEndpoint(prefs)) {
            tvAiResponse?.text = "Endpoint 9Router belum dikonfigurasi, memproses dengan OCR lokal..."
            ScreenOcrProcessor.runLocalOcrFallback(context, bitmap, visionError = null)
            return
        }

        tvAiResponse?.text = "Mengirim gambar ke AI 9Router Vision..."
        val base64Jpeg = ScreenOcrProcessor.bitmapToBase64Jpeg(bitmap)
        AiRouterClient.askVision(prefs, base64Jpeg) { res ->
            mainHandler.post {
                if (res.ok && res.text.isNotBlank()) {
                    showPopup(tabIndex = 0)
                    etAiQuestionRef?.setText("[Analisis Gambar Seleksi]")
                    popupView?.findViewById<TextView>(R.id.tvAiResponse)?.text = res.text
                } else {
                    ScreenOcrProcessor.runLocalOcrFallback(context, bitmap, visionError = res.error)
                }
            }
        }
    }

    private fun removeSelectionBoxOverlay() {
        selectionBoxView?.let { v ->
            if (v.isAttachedToWindow) {
                host.removeOverlayView(v)
            }
            v.visibility = View.GONE
        }
        selectionBoxView = null
        selectionBoxParams = null
        isBoxShowing = false
    }

    @Suppress("unused")
    private fun fallbackGoogleLensWithTwoFingerUnpin(bitmap: Bitmap) {
        val imageUri = saveBitmapForLens(bitmap) ?: return
        val accService = AutoScannerService.instance
        if (accService != null && accService.isScreenPinned()) {
            accService.simulateUnpinGesture {
                mainHandler.postDelayed({
                    launchGoogleLensIntent(imageUri)
                }, 1000L)
            }
        } else {
            launchGoogleLensIntent(imageUri)
        }
    }

    private fun saveBitmapForLens(bitmap: Bitmap): Uri? {
        return try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val outFile = File(dir, "crop_lens.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        } catch (_: Exception) {
            null
        }
    }

    private fun launchGoogleLensIntent(imageUri: Uri): Boolean {
        val candidatePackages = listOf("com.google.ar.lens", "com.google.android.googlequicksearchbox")
        for (pkg in candidatePackages) {
            val lensIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (context.packageManager.resolveActivity(lensIntent, 0) != null) {
                return try {
                    context.startActivity(lensIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        return false
    }

    fun showSelectionBox() {
        if (selectionBoxView == null) createSelectionBoxView()
        val v = selectionBoxView ?: return
        val p = selectionBoxParams ?: return
        safeAddView(v, p)
        v.visibility = View.VISIBLE
        applyNavigationBarMode(v, prefs.hideNavigationEnabled)
        isBoxShowing = true
        hideBubble()
        hideDock()
    }

    fun hideSelectionBox() {
        selectionBoxView?.visibility = View.GONE
        isBoxShowing = false
    }

    // ------------------------------------------------------------- 4. Pop-up Window
    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun createPopupView() {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_ClestialKeyboard)
        val popup = LayoutInflater.from(themedContext).inflate(R.layout.layout_floating_popup, null)

        val displayW = context.resources.displayMetrics.widthPixels
        val popupW = (displayW * 0.94f).toInt().coerceAtLeast(dp(280f))

        val params = host.createLayoutParams(
            width = popupW,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            x = 0,
            y = dp(8f)
        ).apply {
            ensureOverlayWindowParams(this)
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        popup.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                setPopupFocusable(false)
            }
            false
        }

        fun enableOverlayTyping(et: EditText, trackAsMathField: Boolean = false) {
            et.isFocusable = true
            et.isFocusableInTouchMode = true
            et.isEnabled = true
            et.setTextColor(Color.parseColor("#F8FAFC"))
            et.setHintTextColor(Color.parseColor("#94A3B8"))
            fun activateFieldInput(v: View) {
                if (trackAsMathField) activeMathEditText = et
                setPopupFocusable(true)
                v.requestFocus()
                mainHandler.postDelayed({
                    v.requestFocus()
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }, 90)
            }
            et.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) activateFieldInput(v)
            }
            et.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                    activateFieldInput(v)
                }
                false
            }
            et.setOnClickListener { v ->
                activateFieldInput(v)
            }
        }

        val header = popup.findViewById<View>(R.id.floatingHeader)
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var lastHeaderTap = 0L

        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (initialX + (event.rawX - initialTouchX)).toInt()
                    params.y = (initialY + (event.rawY - initialTouchY)).toInt()
                    safeUpdateView(popupView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < touchSlop && diffY < touchSlop) {
                        val now = System.currentTimeMillis()
                        if (now - lastHeaderTap < 380) {
                            hidePopup()
                            lastHeaderTap = 0L
                        } else {
                            lastHeaderTap = now
                        }
                    }
                    true
                }
                else -> false
            }
        }

        val btnAntiDetectToggle = popup.findViewById<Button>(R.id.btnAntiDetectToggle)
        fun updateAntiDetectUI() {
            val enabled = prefs.antiDetectEnabled
            btnAntiDetectToggle.setBackgroundResource(if (enabled) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
            btnAntiDetectToggle.setTextColor(if (enabled) Color.WHITE else Color.parseColor("#94A3B8"))
            btnAntiDetectToggle.text = if (enabled) "Anti Detek" else "Anti Detek (Off)"
        }
        updateAntiDetectUI()
        btnAntiDetectToggle.setOnClickListener {
            prefs.antiDetectEnabled = !prefs.antiDetectEnabled
            updateAntiDetectUI()
            applyWindowDisguiseAll()
            Toast.makeText(
                context,
                if (prefs.antiDetectEnabled) "Anti Detek aktif (jeda acak + samaran window)" else "Anti Detek nonaktif",
                Toast.LENGTH_SHORT
            ).show()
        }

        popup.findViewById<ImageButton>(R.id.btnFloatingClose).setOnClickListener {
            hidePopup()
        }

        val tabAi = popup.findViewById<TextView>(R.id.tabAi)
        val tabGoogle = popup.findViewById<TextView>(R.id.tabGoogle)
        val tabMath = popup.findViewById<TextView>(R.id.tabMath)
        val tabAutoAnswer = popup.findViewById<TextView>(R.id.tabAutoAnswer)
        val panelAi = popup.findViewById<View>(R.id.panelAi)
        val panelGoogle = popup.findViewById<View>(R.id.panelGoogle)
        val panelMath = popup.findViewById<View>(R.id.panelMath)
        val panelAutoAnswer = popup.findViewById<View>(R.id.panelAutoAnswer)

        fun selectTab(idx: Int) {
            tabAi.isSelected = (idx == 0)
            tabGoogle.isSelected = (idx == 1)
            tabMath.isSelected = (idx == 2)
            tabAutoAnswer.isSelected = (idx == 3)
            panelAi.visibility = if (idx == 0) View.VISIBLE else View.GONE
            panelGoogle.visibility = if (idx == 1) View.VISIBLE else View.GONE
            panelMath.visibility = if (idx == 2) View.VISIBLE else View.GONE
            panelAutoAnswer.visibility = if (idx == 3) View.VISIBLE else View.GONE
        }
        selectTabRef = ::selectTab

        tabAi.setOnClickListener { selectTab(0) }
        tabGoogle.setOnClickListener { selectTab(1) }
        tabMath.setOnClickListener { selectTab(2) }
        tabAutoAnswer.setOnClickListener { selectTab(3) }
        selectTab(0)

        // --- Tab 1: AI
        val etAi = popup.findViewById<EditText>(R.id.etAiQuestion)
        val tvAiResponse = popup.findViewById<TextView>(R.id.tvAiResponse)
        val btnAiPaste = popup.findViewById<Button>(R.id.btnAiPaste)
        val btnAiClear = popup.findViewById<Button>(R.id.btnAiClear)
        val btnAiKeyToggle = popup.findViewById<Button>(R.id.btnAiKeyToggle)
        val panelAiKeyConfig = popup.findViewById<View>(R.id.panelAiKeyConfig)
        val etAiBaseUrl = popup.findViewById<EditText>(R.id.etAiBaseUrl)
        val etAiApiKey = popup.findViewById<EditText>(R.id.etAiApiKey)
        val etAiModel = popup.findViewById<EditText>(R.id.etAiModel)
        val btnAiTestKey = popup.findViewById<Button>(R.id.btnAiTestKey)
        val btnAiSaveKey = popup.findViewById<Button>(R.id.btnAiSaveKey)
        val btnAiSubmit = popup.findViewById<Button>(R.id.btnAiSubmit)
        val btnAiCopy = popup.findViewById<Button>(R.id.btnAiCopy)
        etAiQuestionRef = etAi
        enableOverlayTyping(etAi)
        enableOverlayTyping(etAiBaseUrl)
        enableOverlayTyping(etAiApiKey)
        enableOverlayTyping(etAiModel)

        fun syncAiConfigFields() {
            etAiBaseUrl.setText(prefs.chatGptEndpoint)
            etAiApiKey.setText(prefs.chatGptApiKey)
            etAiModel.setText(prefs.chatGptModel)
        }
        syncAiConfigFields()

        btnAiKeyToggle.setOnClickListener {
            val show = panelAiKeyConfig.visibility != View.VISIBLE
            if (show) syncAiConfigFields()
            panelAiKeyConfig.visibility = if (show) View.VISIBLE else View.GONE
        }

        btnAiSaveKey.setOnClickListener {
            val rawUrl = etAiBaseUrl.text.toString().trim()
            val rawKey = etAiApiKey.text.toString().trim()
            val rawModel = etAiModel.text.toString().trim()
            if (rawUrl.isEmpty() && rawKey.isEmpty()) {
                prefs.chatGptApiKey = ""
                prefs.chatGptEndpoint = Prefs.DEFAULT_CHATGPT_ENDPOINT
                prefs.chatGptModel = Prefs.DEFAULT_CHATGPT_MODEL
                syncAiConfigFields()
                Toast.makeText(context, "Konfigurasi AI direset (mode offline aktif)", Toast.LENGTH_SHORT).show()
                panelAiKeyConfig.visibility = View.GONE
                return@setOnClickListener
            }
            if (rawUrl.isNotEmpty() && AiRouterClient.normalizeBaseUrl(rawUrl) == null) {
                Toast.makeText(context, "Base URL harus diawali http:// atau https://", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val saved = AiRouterClient.saveDynamicConfig(prefs, rawUrl, rawKey, rawModel)
            syncAiConfigFields()
            Toast.makeText(context, saved.message, Toast.LENGTH_SHORT).show()
            panelAiKeyConfig.visibility = View.GONE
        }

        btnAiTestKey.setOnClickListener {
            val rawUrl = etAiBaseUrl.text.toString().trim()
            val rawKey = etAiApiKey.text.toString().trim()
            val rawModel = etAiModel.text.toString().trim()
            if (rawUrl.isNotEmpty() && AiRouterClient.normalizeBaseUrl(rawUrl) == null) {
                Toast.makeText(context, "Base URL harus diawali http:// atau https://", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AiRouterClient.saveDynamicConfig(prefs, rawUrl, rawKey, rawModel)
            syncAiConfigFields()
            tvAiResponse.text = "Menguji koneksi ke ${AiRouterClient.resolveModelsEndpoint(prefs.chatGptEndpoint)}..."
            btnAiTestKey.isEnabled = false
            AiRouterClient.testConnection(prefs) { res ->
                mainHandler.post {
                    btnAiTestKey.isEnabled = true
                    tvAiResponse.text = res.message
                }
            }
        }

        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        btnAiPaste.setOnClickListener {
            val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!clip.isNullOrEmpty()) {
                etAi.setText(clip)
                etAi.setSelection(clip.length)
            } else {
                Toast.makeText(context, "Papan klip kosong", Toast.LENGTH_SHORT).show()
            }
        }

        btnAiClear.setOnClickListener {
            etAi.setText("")
            tvAiResponse.text = "Hasil jawaban AI akan muncul di sini…"
        }

        btnAiSubmit.setOnClickListener {
            val q = etAi.text.toString().trim()
            if (q.isEmpty()) {
                Toast.makeText(context, "Tulis atau tempel pertanyaan terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!AiRouterClient.hasConfiguredEndpoint(prefs)) {
                val abcd = AbcdSolver.parseFromText(q)
                if (abcd != null) {
                    val sol = AbcdSolver.solveLocally(abcd)
                    tvAiResponse.text = "🎯 Kunci: [${sol.selectedOption}] ${sol.optionText}\nSumber: ${sol.source}\nPenjelasan: ${sol.explanation}"
                    return@setOnClickListener
                }
                val offlineMath = OfflineMathAssistant.trySolveMathQuestion(q)
                if (offlineMath != null) {
                    tvAiResponse.text = offlineMath
                    return@setOnClickListener
                }
                tvAiResponse.text = "Belum ada Base URL / API Key AI yang diisi.\n\n" +
                    "• Untuk soal Matematika (SPLDV, PLSV, Kuadrat, 5C2, 5!), ketik persamaannya langsung di sini atau buka tab ∑ Kalkulator.\n" +
                    "• Untuk AI (9router / OpenAI / Groq / OpenRouter), ketuk '🔑 Key' di atas atau buka menu Pengaturan / Tools untuk mengisi Base URL & API Key."
                return@setOnClickListener
            }

            tvAiResponse.text = "Sedang memproses dengan AI..."
            btnAiSubmit.isEnabled = false

            AiRouterClient.askQuestion(prefs, q) { res ->
                mainHandler.post {
                    btnAiSubmit.isEnabled = true
                    if (res.ok) {
                        tvAiResponse.text = res.text
                    } else {
                        val offlineFallback = OfflineMathAssistant.trySolveMathQuestion(q)
                        if (offlineFallback != null) {
                            tvAiResponse.text = "$offlineFallback\n\n(AI offline/error: ${res.error})"
                        } else {
                            tvAiResponse.text = "Error: ${res.error ?: "Gagal memproses"}"
                        }
                    }
                }
            }
        }

        btnAiCopy.setOnClickListener {
            val ans = tvAiResponse.text.toString().trim()
            if (ans.isNotEmpty() && !ans.startsWith("Hasil jawaban AI") && !ans.startsWith("Sedang memproses")) {
                cm.setPrimaryClip(ClipData.newPlainText("Jawaban AI", ans))
                Toast.makeText(context, "Jawaban AI berhasil disalin!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Belum ada jawaban yang dapat disalin", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Tab 2: Google Web Search
        val etGoogleSearch = popup.findViewById<EditText>(R.id.etGoogleQuery)
        val btnGoogleSubmit = popup.findViewById<Button>(R.id.btnGoogleSearch)
        val btnGoogleBack = popup.findViewById<Button>(R.id.btnWebBack)
        val btnGoogleReload = popup.findViewById<Button>(R.id.btnWebReload)
        val tvGoogleUrl = popup.findViewById<TextView>(R.id.tvWebUrl)
        val webView = popup.findViewById<WebView>(R.id.wvGoogle)
        etGoogleSearchRef = etGoogleSearch
        webViewRef = webView
        enableOverlayTyping(etGoogleSearch)

        setupWebView(webView, tvGoogleUrl)

        fun performSearch(query: String) {
            val trimmed = query.trim()
            val targetUrl = if (trimmed.isEmpty()) {
                "https://www.google.com"
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
            }
            webView.loadUrl(targetUrl)
        }

        btnGoogleSubmit.setOnClickListener {
            performSearch(etGoogleSearch.text.toString())
        }

        btnGoogleBack.setOnClickListener {
            if (webView.canGoBack()) webView.goBack() else webView.loadUrl("https://www.google.com")
        }

        btnGoogleReload.setOnClickListener {
            webView.reload()
        }

        webView.loadUrl("https://www.google.com")

        // --- Tab 3: Kalkulator Umum, Kombinasi & SMP 9 Math Modules
        val mathChipContainer = popup.findViewById<LinearLayout>(R.id.mathChipsRow)
        val tvMathDesc = popup.findViewById<TextView>(R.id.tvFloatingMathDesc)
        val svMathFields = popup.findViewById<ScrollView>(R.id.svMathFields)
        val containerMathInputs = popup.findViewById<LinearLayout>(R.id.mathFieldsContainer)
        val btnKeypadPageNum = popup.findViewById<AppCompatButton>(R.id.btnKeypadPageNum)
        val btnKeypadPageSci = popup.findViewById<AppCompatButton>(R.id.btnKeypadPageSci)
        val btnKeypadPageAbc = popup.findViewById<AppCompatButton>(R.id.btnKeypadPageAbc)
        val btnKeypadBack = popup.findViewById<AppCompatButton>(R.id.btnKeypadBack)
        val btnKeypadClear = popup.findViewById<AppCompatButton>(R.id.btnKeypadClear)
        val quickKeypadGrid = popup.findViewById<LinearLayout>(R.id.mathQuickKeypadGrid)
        val svMathResult = popup.findViewById<ScrollView>(R.id.svMathResult)
        val tvMathResult = popup.findViewById<TextView>(R.id.tvFloatingMathResult)
        val btnMathSolve = popup.findViewById<AppCompatButton>(R.id.btnFloatingMathSolve)
        val btnMathExpand = popup.findViewById<AppCompatButton>(R.id.btnFloatingMathExpand)
        val btnMathCopy = popup.findViewById<AppCompatButton>(R.id.btnFloatingMathCopy)
        val svAiResponse = popup.findViewById<ScrollView>(R.id.svAiResponse)

        val scrollTouchAllow = View.OnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
            }
            false
        }
        svMathResult.setOnTouchListener(scrollTouchAllow)
        svAiResponse.setOnTouchListener(scrollTouchAllow)
        svMathFields.setOnTouchListener(scrollTouchAllow)

        var isMathResultExpanded = false
        btnMathExpand.setOnClickListener {
            isMathResultExpanded = !isMathResultExpanded
            val lp = svMathResult.layoutParams
            lp.height = dp(if (isMathResultExpanded) 235f else 120f)
            svMathResult.layoutParams = lp
            btnMathExpand.text = if (isMathResultExpanded) "🔽 Perkecil" else "🔍 Perbesar"
        }

        fun makeDarkBtn(labelText: String, textSizeSp: Float = 11f, textHex: String = "#F8FAFC"): AppCompatButton {
            return AppCompatButton(themedContext).apply {
                text = labelText
                isAllCaps = false
                textSize = textSizeSp
                setTextColor(Color.parseColor(textHex))
                backgroundTintList = null
                stateListAnimator = null
                minHeight = 0
                minimumHeight = 0
                minWidth = 0
                minimumWidth = 0
                gravity = Gravity.CENTER
            }
        }

        fun mathShortTitle(mod: MathModule, index: Int): String = when (mod.id) {
            "spldv" -> "1. SPLDV"
            "pangkat_akar" -> "2. Pangkat & Akar"
            "persamaan_kuadrat" -> "3. Pers. Kuadrat"
            "fungsi_kuadrat" -> "4. Fungsi Kuadrat"
            "garis_linear" -> "5. Garis Linear"
            "plsv_ptlsv" -> "6. PLSV & PtLSV"
            "transformasi" -> "7. Transformasi"
            "kesebangunan" -> "8. Kesebangunan"
            "bangun_ruang" -> "9. Bangun Ruang"
            "statistika" -> "10. Statistika"
            "peluang" -> "11. Peluang & Kombinasi"
            else -> "${index + 1}. ${mod.name.take(16)}"
        }

        fun buildCalculatorUI() {
            isCalcModeSelected = true
            tvMathDesc.text = "Kalkulator Umum, Ilmiah & Kombinasi/Permutasi (contoh: 5C2, C(5,2), 5P2, 5!, 2^3 + √144)"
            containerMathInputs.removeAllViews()
            mathInputFields.clear()
            mathChoiceValues.clear()
            calcExprEditText = null

            val svLp = svMathFields.layoutParams
            svLp.height = LinearLayout.LayoutParams.WRAP_CONTENT
            svMathFields.layoutParams = svLp

            val label = TextView(themedContext).apply {
                text = "Ekspresi Matematika:"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4f) }
            }

            val input = EditText(themedContext).apply {
                hint = "Misal: 5C2 + 3! atau C(6,2) atau (12 + 8) × 3"
                setBackgroundResource(R.drawable.bg_floating_input)
                setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                textSize = 14f
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4f) }
            }
            enableOverlayTyping(input, trackAsMathField = true)
            calcExprEditText = input
            activeMathEditText = input

            containerMathInputs.addView(label)
            containerMathInputs.addView(input)
            btnMathSolve.text = "Hitung Sekarang"
        }

        fun buildMathModuleUI(mod: MathModule) {
            isCalcModeSelected = false
            activeMathModule = mod
            tvMathDesc.text = mod.description
            containerMathInputs.removeAllViews()
            mathInputFields.clear()
            mathChoiceValues.clear()
            calcExprEditText = null
            activeMathEditText = null

            val svLp = svMathFields.layoutParams
            svLp.height = if (mod.fields.size >= 3) dp(112f) else LinearLayout.LayoutParams.WRAP_CONTENT
            svMathFields.layoutParams = svLp

            val dynamicLabels = HashMap<String, TextView>()
            fun updateProbContextLabels(modeVal: String) {
                if (mod.id != "peluang") return
                when (modeVal) {
                    "Kombinasi C(n, r)" -> {
                        dynamicLabels["a"]?.text = "Nilai n (total):"
                        dynamicLabels["b"]?.text = "Nilai r (dipilih):"
                    }
                    "Permutasi P(n, r)" -> {
                        dynamicLabels["a"]?.text = "Nilai n (total):"
                        dynamicLabels["b"]?.text = "Nilai r (urutan):"
                    }
                    "Faktorial n!" -> {
                        dynamicLabels["a"]?.text = "Nilai n:"
                        dynamicLabels["b"]?.text = "Kosongkan:"
                    }
                    else -> {
                        dynamicLabels["a"]?.text = "n(A) / P(A) / n:"
                        dynamicLabels["b"]?.text = "n(S) / N / k / r:"
                    }
                }
            }

            for (field in mod.fields) {
                if (field.type == FieldType.CHOICE && field.choices.isNotEmpty()) {
                    val col = LinearLayout(themedContext).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(5f) }
                    }

                    val label = TextView(themedContext).apply {
                        text = "${field.label}:"
                        setTextColor(Color.parseColor("#CBD5E1"))
                        textSize = 11.5f
                    }

                    val hsv = HorizontalScrollView(themedContext).apply {
                        isHorizontalScrollBarEnabled = false
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(4f) }
                    }

                    val choiceRow = LinearLayout(themedContext).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }

                    val defaultVal = field.defaultValue.ifEmpty { field.choices.first() }
                    mathChoiceValues[field.key] = defaultVal
                    val choiceButtons = ArrayList<AppCompatButton>()

                    fun renderChoicePills(selectedVal: String) {
                        for (btn in choiceButtons) {
                            val isSel = btn.tag == selectedVal
                            btn.setBackgroundResource(if (isSel) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
                            btn.setTextColor(if (isSel) Color.WHITE else Color.parseColor("#CBD5E1"))
                        }
                    }

                    for (ch in field.choices) {
                        val btn = makeDarkBtn(ch, 11f, "#CBD5E1").apply {
                            tag = ch
                            setPadding(dp(10f), dp(2f), dp(10f), dp(2f))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                dp(28f)
                            ).apply { marginEnd = dp(6f) }
                            setOnClickListener {
                                mathChoiceValues[field.key] = ch
                                renderChoicePills(ch)
                                if (field.key == "mode") updateProbContextLabels(ch)
                            }
                        }
                        choiceButtons.add(btn)
                        choiceRow.addView(btn)
                    }
                    renderChoicePills(defaultVal)
                    hsv.addView(choiceRow)
                    col.addView(label)
                    col.addView(hsv)
                    containerMathInputs.addView(col)
                    continue
                }

                val row = LinearLayout(themedContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(5f) }
                }

                val label = TextView(themedContext).apply {
                    text = "${field.label}:"
                    setTextColor(Color.parseColor("#CBD5E1"))
                    textSize = 11.5f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                        marginEnd = dp(6f)
                    }
                }
                dynamicLabels[field.key] = label

                val input = EditText(themedContext).apply {
                    setText(field.defaultValue)
                    hint = field.hint
                    setBackgroundResource(R.drawable.bg_floating_input)
                    setPadding(dp(10f), dp(7f), dp(10f), dp(7f))
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f)
                    inputType = when (field.type) {
                        FieldType.INTEGER -> android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                        else -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    }
                }
                enableOverlayTyping(input, trackAsMathField = true)
                if (activeMathEditText == null) activeMathEditText = input

                row.addView(label)
                row.addView(input)
                containerMathInputs.addView(row)
                mathInputFields[field.key] = input
            }
            mathChoiceValues["mode"]?.let { updateProbContextLabels(it) }
            btnMathSolve.text = "Hitung Solusi Eksak"
        }

        fun applyKeypadToken(ins: String) {
            val targetEt = activeMathEditText
                ?: calcExprEditText
                ?: mathInputFields.values.firstOrNull()
                ?: return
            targetEt.requestFocus()
            val editable = targetEt.text ?: return
            val start = targetEt.selectionStart.coerceIn(0, editable.length)
            val end = targetEt.selectionEnd.coerceIn(0, editable.length)
            val selMin = minOf(start, end)
            val selMax = maxOf(start, end)
            when (ins) {
                "CLEAR" -> editable.clear()
                "BACK" -> {
                    if (selMin != selMax) {
                        editable.delete(selMin, selMax)
                    } else if (selMin > 0) {
                        editable.delete(selMin - 1, selMin)
                    }
                }
                else -> editable.replace(selMin, selMax, ins)
            }
        }

        btnKeypadBack.setOnClickListener { applyKeypadToken("BACK") }
        btnKeypadClear.setOnClickListener { applyKeypadToken("CLEAR") }

        fun renderKeypadPage(page: Int) {
            btnKeypadPageNum.setBackgroundResource(if (page == 0) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
            btnKeypadPageNum.setTextColor(if (page == 0) Color.WHITE else Color.parseColor("#CBD5E1"))
            btnKeypadPageSci.setBackgroundResource(if (page == 1) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
            btnKeypadPageSci.setTextColor(if (page == 1) Color.WHITE else Color.parseColor("#CBD5E1"))
            btnKeypadPageAbc.setBackgroundResource(if (page == 2) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
            btnKeypadPageAbc.setTextColor(if (page == 2) Color.WHITE else Color.parseColor("#CBD5E1"))

            val keyRows: List<List<Pair<String, String>>> = when (page) {
                0 -> listOf(
                    listOf("7" to "7", "8" to "8", "9" to "9", "÷" to "÷", "×" to "×", "nCr" to "C", "nPr" to "P"),
                    listOf("4" to "4", "5" to "5", "6" to "6", "-" to "-", "+" to "+", "!" to "!", "√" to "√("),
                    listOf("1" to "1", "2" to "2", "3" to "3", "0" to "0", "(" to "(", ")" to ")", "," to ",")
                )
                1 -> listOf(
                    listOf("x" to "x", "y" to "y", "=" to "=", "<" to "<", ">" to ">", "≤" to "<=", "≥" to ">="),
                    listOf("^" to "^", "x²" to "²", "x³" to "³", "π" to "π", ";" to ";", "/" to "/", "." to "."),
                    listOf("sin" to "sin(", "cos" to "cos(", "tan" to "tan(", "log" to "log(", "ln" to "ln(", "%" to "%", "abs" to "abs(")
                )
                else -> listOf(
                    listOf("q" to "q", "w" to "w", "e" to "e", "r" to "r", "t" to "t", "y" to "y", "u" to "u", "i" to "i", "o" to "o", "p" to "p"),
                    listOf("a" to "a", "s" to "s", "d" to "d", "f" to "f", "g" to "g", "h" to "h", "j" to "j", "k" to "k", "l" to "l", "␣" to " "),
                    listOf("z" to "z", "x" to "x", "c" to "c", "v" to "v", "b" to "b", "n" to "n", "m" to "m", "," to ",", "." to ".", "-" to "-")
                )
            }

            quickKeypadGrid.removeAllViews()
            keyRows.forEachIndexed { rIdx, rList ->
                val rowLl = LinearLayout(themedContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(28f)
                    ).apply { if (rIdx > 0) topMargin = dp(3f) }
                }
                rList.forEachIndexed { cIdx, (lbl, ins) ->
                    val btn = makeDarkBtn(lbl, if (page == 2) 11f else 11.5f, "#F8FAFC").apply {
                        setBackgroundResource(R.drawable.bg_floating_keypad_btn)
                        setPadding(dp(2f), 0, dp(2f), 0)
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1f
                        ).apply { if (cIdx > 0) marginStart = dp(3f) }
                        setOnClickListener { applyKeypadToken(ins) }
                    }
                    rowLl.addView(btn)
                }
                quickKeypadGrid.addView(rowLl)
            }
        }

        btnKeypadPageNum.setOnClickListener { renderKeypadPage(0) }
        btnKeypadPageSci.setOnClickListener { renderKeypadPage(1) }
        btnKeypadPageAbc.setOnClickListener { renderKeypadPage(2) }
        renderKeypadPage(0)

        val allChipButtons = ArrayList<AppCompatButton>()
        fun highlightChip(selectedIdx: Int) {
            allChipButtons.forEachIndexed { i, btn ->
                btn.setBackgroundResource(if (i == selectedIdx) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
                btn.setTextColor(if (i == selectedIdx) Color.WHITE else Color.parseColor("#CBD5E1"))
            }
        }

        val calcChip = makeDarkBtn("0. KALKULATOR", 11f, "#FFFFFF").apply {
            setPadding(dp(12f), dp(2f), dp(12f), dp(2f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(30f)
            ).apply { marginEnd = dp(6f) }
            setOnClickListener {
                highlightChip(0)
                buildCalculatorUI()
            }
        }
        allChipButtons.add(calcChip)
        mathChipContainer.addView(calcChip)

        MathRegistry.modules.forEachIndexed { index, mod ->
            val chipIdx = index + 1
            val chip = makeDarkBtn(mathShortTitle(mod, index), 11f, "#CBD5E1").apply {
                setPadding(dp(12f), dp(2f), dp(12f), dp(2f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(30f)
                ).apply { marginEnd = dp(6f) }

                setOnClickListener {
                    highlightChip(chipIdx)
                    buildMathModuleUI(mod)
                }
            }
            allChipButtons.add(chip)
            mathChipContainer.addView(chip)
        }

        highlightChip(0)
        buildCalculatorUI()

        btnMathSolve.setOnClickListener {
            if (isCalcModeSelected) {
                val expr = calcExprEditText?.text?.toString()?.trim().orEmpty()
                if (expr.isEmpty()) {
                    tvMathResult.text = "Masukkan ekspresi matematika terlebih dahulu (misal: 5C2, 5!, 2^3 + √144)."
                    return@setOnClickListener
                }
                val out = ScientificCalculator.evaluate(expr, degreeMode = true)
                tvMathResult.text = if (out.ok) {
                    "Ekspresi: $expr\nHasil: ${out.display}"
                } else {
                    "Error: ${out.error}"
                }
                return@setOnClickListener
            }

            val map = mutableMapOf<String, String>()
            for ((k, v) in mathChoiceValues) {
                map[k] = v
            }
            for ((k, et) in mathInputFields) {
                map[k] = et.text.toString().trim()
            }
            val res = activeMathModule.solve(map)
            val fullText = buildString {
                if (res.ok) {
                    append("Hasil: ").append(res.answer).append("\n\n")
                    if (res.steps.isNotEmpty()) {
                        append("Langkah-langkah:\n")
                        res.steps.forEachIndexed { i: Int, s: String -> append("${i + 1}. $s\n") }
                    }
                    if (res.check.isNotEmpty()) {
                        append("\nCek: ").append(res.check)
                    }
                } else {
                    append("Error: ").append(res.error)
                }
            }
            tvMathResult.text = fullText
        }

        btnMathCopy.setOnClickListener {
            val textToCopy = tvMathResult.text.toString().trim()
            if (textToCopy.isNotEmpty() && !textToCopy.startsWith("Jawaban dan langkah")) {
                cm.setPrimaryClip(ClipData.newPlainText("Solusi Matematika SMP 9", textToCopy))
                Toast.makeText(context, "Solusi dan langkah berhasil disalin!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Belum ada solusi untuk disalin", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Tab 4: Auto Jawab ABCD
        val tvAccStatus = popup.findViewById<TextView>(R.id.tvAutoAnswerAccStatus)
        val btnAccSettings = popup.findViewById<Button>(R.id.btnOpenAccSettings)
        val switchAutoAnswer = popup.findViewById<SwitchCompat>(R.id.switchAutoAnswerEnabled)
        val switchHideNavigation = popup.findViewById<SwitchCompat>(R.id.switchHideNavigation)
        val btnModeAuto = popup.findViewById<Button>(R.id.btnModeAutoClick)
        val btnModeKey = popup.findViewById<Button>(R.id.btnModeKeyOnly)
        val btnD0 = popup.findViewById<Button>(R.id.btnDelay0)
        val btnD1 = popup.findViewById<Button>(R.id.btnDelay1)
        val btnD2 = popup.findViewById<Button>(R.id.btnDelay2)
        val btnD3 = popup.findViewById<Button>(R.id.btnDelay3)
        val btnDRandom = popup.findViewById<Button>(R.id.btnDelayRandom)
        val btnTriggerScreenAnswer = popup.findViewById<Button>(R.id.btnTriggerScreenAnswer)
        val tvAutoStatus = popup.findViewById<TextView>(R.id.tvAutoAnswerStatus)
        val btnCopyAutoAnswer = popup.findViewById<Button>(R.id.btnCopyAutoAnswer)

        fun updateAccStatus() {
            val running = AutoScannerService.isRunning
            if (running) {
                tvAccStatus.text = "Aksesibilitas: Aktif (AutoScanner Siap)"
                tvAccStatus.setTextColor(Color.parseColor("#10B981"))
                btnAccSettings.text = "Pengaturan"
            } else {
                tvAccStatus.text = "Aksesibilitas: Nonaktif (Klik untuk Izin)"
                tvAccStatus.setTextColor(Color.parseColor("#F59E0B"))
                btnAccSettings.text = "Aktifkan Izin"
            }
        }
        updateAccStatus()
        AutoScannerService.onStatusChangedListener = {
            mainHandler.post { updateAccStatus() }
        }

        btnAccSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "Nyalakan layanan Aksesibilitas Clestial di daftar layanan", Toast.LENGTH_LONG).show()
        }

        switchAutoAnswer.isChecked = prefs.autoAnswerEnabled
        switchAutoAnswer.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoAnswerEnabled = isChecked
            Toast.makeText(context, if (isChecked) "Otomatis Pindai Layar Aktif" else "Otomatis Pindai Layar Nonaktif", Toast.LENGTH_SHORT).show()
        }

        switchHideNavigation.isChecked = prefs.hideNavigationEnabled
        switchHideNavigation.setOnCheckedChangeListener { _, isChecked ->
            prefs.hideNavigationEnabled = isChecked
            applyNavigationBarModeAll(isChecked)
            Toast.makeText(
                context,
                if (isChecked) "Sembunyikan Navigasi Aktif" else "Sembunyikan Navigasi Nonaktif",
                Toast.LENGTH_SHORT
            ).show()
        }

        fun updateModeUI() {
            val isAuto = prefs.autoAnswerMode == Prefs.MODE_AUTO_CLICK
            btnModeAuto.setBackgroundResource(if (isAuto) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
            btnModeAuto.setTextColor(if (isAuto) Color.WHITE else Color.parseColor("#CBD5E1"))
            btnModeKey.setBackgroundResource(if (!isAuto) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
            btnModeKey.setTextColor(if (!isAuto) Color.WHITE else Color.parseColor("#CBD5E1"))
        }
        updateModeUI()

        btnModeAuto.setOnClickListener {
            prefs.autoAnswerMode = Prefs.MODE_AUTO_CLICK
            updateModeUI()
            Toast.makeText(context, "Mode: Auto-Klik Layar (Accessibility)", Toast.LENGTH_SHORT).show()
        }
        btnModeKey.setOnClickListener {
            prefs.autoAnswerMode = Prefs.MODE_KEY_ONLY
            updateModeUI()
            Toast.makeText(context, "Mode: Kunci & Salin Saja", Toast.LENGTH_SHORT).show()
        }

        val delayButtons = listOf(
            btnD0 to 0,
            btnD1 to 1500,
            btnD2 to 2000,
            btnD3 to 3000,
            btnDRandom to Prefs.DELAY_RANDOM
        )
        fun updateDelayUI() {
            val cur = prefs.autoAnswerDelayMs
            delayButtons.forEach { (btn, ms) ->
                val active = cur == ms
                btn.setBackgroundResource(if (active) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
                btn.setTextColor(if (active) Color.WHITE else Color.parseColor("#CBD5E1"))
            }
        }
        updateDelayUI()
        delayButtons.forEach { (btn, ms) ->
            btn.setOnClickListener {
                prefs.autoAnswerDelayMs = ms
                updateDelayUI()
            }
        }

        var lastAnswerResultText = ""

        fun displaySolution(res: AbcdSolutionResult) {
            val text = if (res.ok) {
                val keyLabel = res.selectedOption?.let { "[$it] " }.orEmpty()
                "🎯 Kunci: $keyLabel${res.optionText}\n" +
                    "Sumber: ${res.source}\n" +
                    "Penjelasan: ${res.explanation}"
            } else {
                "Error: ${res.error ?: "Gagal memproses soal."}"
            }
            lastAnswerResultText = text
            tvAutoStatus.text = text
        }

        AutoScannerService.onAnswerResultListener = { sol ->
            mainHandler.post { displaySolution(sol) }
        }
        AutoScannerService.onStatusMessageListener = { statusMsg ->
            mainHandler.post {
                if (!tvAutoStatus.text.startsWith("🎯 Kunci:") || statusMsg.startsWith("Berhasil klik") || statusMsg.startsWith("Gagal klik") || statusMsg.startsWith("Mode Kunci")) {
                    tvAutoStatus.text = statusMsg
                }
            }
        }

        btnTriggerScreenAnswer.setOnClickListener {
            val service = AutoScannerService.instance
            if (service != null) {
                tvAutoStatus.text = "Memindai layar & menyusun teks soal..."
                service.triggerAutoAnswer(fromAutoEvent = false) { success, msg ->
                    mainHandler.post {
                        if (!success && !tvAutoStatus.text.startsWith("🎯 Kunci:")) {
                            tvAutoStatus.text = msg
                        }
                    }
                }
            } else {
                tvAutoStatus.text = "Layanan Aksesibilitas belum aktif. Ketuk Aktifkan Izin terlebih dahulu."
                Toast.makeText(context, "Izin Aksesibilitas belum aktif", Toast.LENGTH_SHORT).show()
            }
        }

        btnCopyAutoAnswer.setOnClickListener {
            if (lastAnswerResultText.isNotEmpty()) {
                cm.setPrimaryClip(ClipData.newPlainText("Hasil & Penjelasan AI", lastAnswerResultText))
                Toast.makeText(context, "Hasil & penjelasan berhasil disalin!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Belum ada hasil untuk disalin", Toast.LENGTH_SHORT).show()
            }
        }

        popupView = popup
        popupParams = params
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView(webView: WebView, tvUrl: TextView) {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tvUrl.text = url ?: "https://www.google.com"
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                setPopupFocusable(true)
            }
            false
        }
    }

    private fun setPopupFocusable(focusable: Boolean) {
        val popup = popupView ?: return
        val p = popupParams ?: return
        val hasNotFocusable = (p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0
        val wantNotFocusable = !focusable
        if (hasNotFocusable == wantNotFocusable) return
        p.flags = if (focusable) {
            p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        safeUpdateView(popup, p)
    }

    fun showPopup(tabIndex: Int = 0, initialQuery: String? = null) {
        if (popupView == null) createPopupView()
        val v = popupView ?: return
        val p = popupParams ?: return

        selectTabRef?.invoke(tabIndex)

        if (!initialQuery.isNullOrEmpty()) {
            etAiQuestionRef?.setText(initialQuery)
            etGoogleSearchRef?.setText(initialQuery)
            val searchUrl = "https://www.google.com/search?q=" + URLEncoder.encode(initialQuery, "UTF-8")
            webViewRef?.loadUrl(searchUrl)
        }

        safeAddView(v, p)
        v.visibility = View.VISIBLE
        applyNavigationBarMode(v, prefs.hideNavigationEnabled)
        AutoScannerService.instance?.detectAndBypassPinning()
        isPopupShowing = true
        hideBubble()
        hideDock()
        hideSelectionBox()
    }

    fun hidePopup() {
        popupView?.let { v ->
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
        setPopupFocusable(false)
        popupView?.visibility = View.GONE
        isPopupShowing = false
        showBubble()
    }

    fun hideAll() {
        hidePopup()
        hideDock()
        hideSelectionBox()
        hideBubble()
    }

    fun destroy() {
        AutoScannerService.instance?.setImmersiveMode(false)
        AutoScannerService.onStatusChangedListener = null
        AutoScannerService.onAnswerResultListener = null
        AutoScannerService.onStatusMessageListener = null
        bubbleView?.let { if (it.isAttachedToWindow) host.removeOverlayView(it) }
        dockView?.let { if (it.isAttachedToWindow) host.removeOverlayView(it) }
        selectionBoxView?.let { if (it.isAttachedToWindow) host.removeOverlayView(it) }
        popupView?.let { if (it.isAttachedToWindow) host.removeOverlayView(it) }
    }
}
