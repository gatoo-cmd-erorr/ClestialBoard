package com.clestial.keyboard.floating

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import android.widget.TextView
import android.widget.Toast
import android.provider.Settings
import androidx.appcompat.widget.SwitchCompat
import com.clestial.keyboard.Prefs
import com.clestial.keyboard.R
import com.clestial.keyboard.autoanswer.AbcdSolutionResult
import com.clestial.keyboard.autoanswer.AbcdSolver
import com.clestial.keyboard.autoanswer.AutoAnswerAccessibilityService
import com.clestial.keyboard.math.AiRouterClient
import com.clestial.keyboard.math.FieldType
import com.clestial.keyboard.math.MathModule
import com.clestial.keyboard.math.MathRegistry
import com.clestial.keyboard.math.OfflineMathAssistant
import com.clestial.keyboard.math.ScientificCalculator
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.max

class FloatingMenuService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val mainHandler = Handler(Looper.getMainLooper())

    // 1. Stealth Bubble (Tombol Mengambang Tersamar di tepi layar)
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // 2. Vertical Floating Dock (Bilah Vertikal Samping - Foto 1)
    private var dockView: View? = null
    private var dockParams: WindowManager.LayoutParams? = null
    private var isDockShowing = false

    // 3. Selection Box (Kotak Seleksi Bergaris Biru Neon - Foto 2)
    private var selectionBoxView: View? = null
    private var selectionBoxParams: WindowManager.LayoutParams? = null
    private var isBoxShowing = false

    // 4. Pop-up Window (Tanya AI, Google Web, SMP 9)
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)

        createBubbleView()
        createDockView()
        createSelectionBoxView()
        createPopupView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
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
                val query = intent.getStringExtra(FloatingMenuManager.EXTRA_QUERY).orEmpty()
                hideDock()
                hideSelectionBox()
                showPopup(tabIndex = 1, initialQuery = query)
            }
            FloatingMenuManager.ACTION_SHOW_AUTO_ANSWER -> {
                val solText = intent.getStringExtra(FloatingMenuManager.EXTRA_SOLUTION)
                hideDock()
                hideSelectionBox()
                showPopup(tabIndex = 3)
                if (!solText.isNullOrEmpty()) {
                    popupView?.findViewById<TextView>(R.id.tvAutoAnswerStatus)?.text = solText
                }
            }
            FloatingMenuManager.ACTION_SHOW_AI_RESULT -> {
                val qText = intent.getStringExtra(FloatingMenuManager.EXTRA_QUERY).orEmpty()
                val ansText = intent.getStringExtra(FloatingMenuManager.EXTRA_SOLUTION).orEmpty()
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
        return START_STICKY
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------- 1. Stealth Bubble
    @SuppressLint("ClickableViewAccessibility")
    private fun createBubbleView() {
        if (!FloatingMenuManager.canDrawOverlays(this)) return

        val size = dp(prefs.floatingSizeDp.toFloat().coerceIn(24f, 64f))
        val bubble = View(this).apply {
            setBackgroundResource(R.drawable.bg_floating_bubble)
            alpha = (prefs.floatingOpacity / 100f).coerceIn(0.12f, 1.0f)
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            size, size,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = resources.displayMetrics.heightPixels / 3
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
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
                    if (bubbleView?.isAttachedToWindow == true) {
                        wm.updateViewLayout(bubbleView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < touchSlop && diffY < touchSlop) {
                        // Sentuh gelembung untuk membuka / menutup Dock menu melayang
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

    private fun showBubble() {
        if (bubbleView == null) createBubbleView()
        val v = bubbleView ?: return
        val p = bubbleParams ?: return
        if (!v.isAttachedToWindow) {
            try {
                wm.addView(v, p)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        v.visibility = View.VISIBLE
    }

    private fun hideBubble() {
        bubbleView?.visibility = View.GONE
    }

    // ------------------------------------------------------------- 2. Vertical Floating Dock (Foto 1)
    @SuppressLint("ClickableViewAccessibility")
    private fun createDockView() {
        if (!FloatingMenuManager.canDrawOverlays(this)) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_ClestialKeyboard)
        val inflater = LayoutInflater.from(themedContext)
        val dockRoot = inflater.inflate(R.layout.layout_floating_dock, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = resources.displayMetrics.heightPixels / 3 - dp(40f)
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
                    if (dockView?.isAttachedToWindow == true) {
                        wm.updateViewLayout(dockView, params)
                    }
                    true
                }
                else -> false
            }
        }

        // 1. 🔍 Kotak Seleksi Soal (Circle to Search / Crop Real-time)
        dockRoot.findViewById<View>(R.id.btnDockCrop).setOnClickListener {
            hideDock()
            showSelectionBox()
        }

        // 2. ✨ Tanya AI
        dockRoot.findViewById<View>(R.id.btnDockAi).setOnClickListener {
            hideDock()
            showPopup(0)
        }

        // 3. 🌐 Google Web Melayang
        dockRoot.findViewById<View>(R.id.btnDockWeb).setOnClickListener {
            hideDock()
            showPopup(1)
        }

        // 4. ∑ Matematika SMP 9
        dockRoot.findViewById<View>(R.id.btnDockMath).setOnClickListener {
            hideDock()
            showPopup(2)
        }

        // 5. 🎯 Auto Jawab Soal ABCD
        dockRoot.findViewById<View>(R.id.btnDockAutoAnswer).setOnClickListener {
            hideDock()
            val service = AutoAnswerAccessibilityService.instance
            if (service != null && prefs.autoAnswerMode == Prefs.MODE_AUTO_CLICK) {
                Toast.makeText(this, "⚡ Memindai & menjawab otomatis...", Toast.LENGTH_SHORT).show()
                service.triggerAutoAnswer { success, msg ->
                    // Jawaban dieksekusi oleh service
                }
            } else {
                showPopup(3)
            }
        }
        dockRoot.findViewById<View>(R.id.btnDockAutoAnswer).setOnLongClickListener {
            hideDock()
            showPopup(3)
            true
        }

        // 6. ✕ Tutup Dock (Kembali ke mode tersamar)
        dockRoot.findViewById<View>(R.id.btnDockHide).setOnClickListener {
            hideDock()
            showBubble()
        }

        dockView = dockRoot
        dockParams = params
    }

    private fun showDock() {
        if (dockView == null) createDockView()
        val v = dockView ?: return
        val p = dockParams ?: return

        // Posisikan dock vertikal sejajar dengan posisi y bubble
        bubbleParams?.let { bp ->
            p.y = bp.y - dp(30f)
        }

        if (!v.isAttachedToWindow) {
            try {
                wm.addView(v, p)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        v.visibility = View.VISIBLE
        isDockShowing = true
        hideBubble()
    }

    private fun hideDock() {
        dockView?.visibility = View.GONE
        isDockShowing = false
    }

    // ------------------------------------------------------------- 3. Selection Box (Foto 2)
    @SuppressLint("ClickableViewAccessibility")
    private fun createSelectionBoxView() {
        if (!FloatingMenuManager.canDrawOverlays(this)) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_ClestialKeyboard)
        val inflater = LayoutInflater.from(themedContext)
        val boxRoot = inflater.inflate(R.layout.layout_selection_box, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayW = resources.displayMetrics.widthPixels
        val defaultW = (displayW * 0.78f).toInt().coerceAtLeast(dp(220f))
        val defaultH = dp(170f)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (displayW - defaultW) / 2
            y = dp(180f)
        }

        val boxFrame = boxRoot.findViewById<FrameLayout>(R.id.boxFrame)
        val ivResizeHandle = boxRoot.findViewById<ImageView>(R.id.ivResizeHandle)
        val btnCropCancel = boxRoot.findViewById<ImageButton>(R.id.btnCropCancel)
        val btnCropConfirm = boxRoot.findViewById<ImageButton>(R.id.btnCropConfirm)

        val frameLp = boxFrame.layoutParams
        frameLp.width = defaultW
        frameLp.height = defaultH
        boxFrame.layoutParams = frameLp

        // Geser posisi kotak seleksi di layar
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
                    if (selectionBoxView?.isAttachedToWindow == true) {
                        wm.updateViewLayout(selectionBoxView, params)
                    }
                    true
                }
                else -> false
            }
        }

        // Ubah ukuran (resize) kotak dari sudut kanan bawah
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

        // Batal: tutup kotak seleksi, kembalikan gelembung tersamar
        btnCropCancel.setOnClickListener {
            hideSelectionBox()
            showBubble()
        }

        // Selesai/Pindai: Ambil koordinat dan luncurkan OCR & pencarian real-time
        btnCropConfirm.setOnClickListener {
            val location = IntArray(2)
            boxFrame.getLocationOnScreen(location)
            val cropX = location[0]
            val cropY = location[1]
            val cropW = boxFrame.width
            val cropH = boxFrame.height

            hideSelectionBox()

            fun launchMediaProjection() {
                val captureIntent = Intent(this, ScreenCaptureActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(ScreenCaptureActivity.EXTRA_CROP_X, cropX)
                    putExtra(ScreenCaptureActivity.EXTRA_CROP_Y, cropY)
                    putExtra(ScreenCaptureActivity.EXTRA_CROP_W, cropW)
                    putExtra(ScreenCaptureActivity.EXTRA_CROP_H, cropH)
                }
                startActivity(captureIntent)
            }

            // Jika layanan aksesibilitas aktif di Android 11+, tangkap layar senyap tanpa dialog "Bagikan layar"!
            val accService = AutoAnswerAccessibilityService.instance
            if (accService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                accService.captureRegionSilent(cropX, cropY, cropW, cropH) {
                    mainHandler.post { launchMediaProjection() }
                }
            } else {
                launchMediaProjection()
            }
        }

        selectionBoxView = boxRoot
        selectionBoxParams = params
    }

    private fun showSelectionBox() {
        if (selectionBoxView == null) createSelectionBoxView()
        val v = selectionBoxView ?: return
        val p = selectionBoxParams ?: return
        if (!v.isAttachedToWindow) {
            try {
                wm.addView(v, p)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        v.visibility = View.VISIBLE
        isBoxShowing = true
        hideBubble()
        hideDock()
    }

    private fun hideSelectionBox() {
        selectionBoxView?.visibility = View.GONE
        isBoxShowing = false
    }

    // ------------------------------------------------------------- 4. Pop-up Window
    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun createPopupView() {
        if (!FloatingMenuManager.canDrawOverlays(this)) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_ClestialKeyboard)
        val inflater = LayoutInflater.from(themedContext)
        val popup = inflater.inflate(R.layout.layout_floating_popup, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayW = resources.displayMetrics.widthPixels
        val popupW = (displayW * 0.94f).toInt().coerceAtLeast(dp(280f))

        val params = WindowManager.LayoutParams(
            popupW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(32f)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        }

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        fun enableOverlayTyping(et: EditText, trackAsMathField: Boolean = false) {
            et.isFocusable = true
            et.isFocusableInTouchMode = true
            et.isEnabled = true
            et.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    if (trackAsMathField) activeMathEditText = et
                    mainHandler.post {
                        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
            et.setOnClickListener { v ->
                if (trackAsMathField) activeMathEditText = et
                v.requestFocus()
                mainHandler.postDelayed({
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }, 60)
            }
        }

        // --- Drag header & Double tap to close
        val header = popup.findViewById<View>(R.id.floatingHeader)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
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
                    if (popupView?.isAttachedToWindow == true) {
                        wm.updateViewLayout(popupView, params)
                    }
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

        popup.findViewById<ImageButton>(R.id.btnFloatingClose).setOnClickListener {
            hidePopup()
        }

        // --- Tabs
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

        // --- Tab 1: AI (ChatGPT + Offline Math Fallback)
        val etAi = popup.findViewById<EditText>(R.id.etAiQuestion)
        val tvAiResponse = popup.findViewById<TextView>(R.id.tvAiResponse)
        val btnAiPaste = popup.findViewById<Button>(R.id.btnAiPaste)
        val btnAiClear = popup.findViewById<Button>(R.id.btnAiClear)
        val btnAiKeyToggle = popup.findViewById<Button>(R.id.btnAiKeyToggle)
        val panelAiKeyConfig = popup.findViewById<View>(R.id.panelAiKeyConfig)
        val etAiApiKey = popup.findViewById<EditText>(R.id.etAiApiKey)
        val btnAiSaveKey = popup.findViewById<Button>(R.id.btnAiSaveKey)
        val btnAiSubmit = popup.findViewById<Button>(R.id.btnAiSubmit)
        val btnAiCopy = popup.findViewById<Button>(R.id.btnAiCopy)
        etAiQuestionRef = etAi
        enableOverlayTyping(etAi)
        enableOverlayTyping(etAiApiKey)

        if (prefs.chatGptApiKey.isNotEmpty()) {
            etAiApiKey.setText(prefs.chatGptApiKey)
        }

        btnAiKeyToggle.setOnClickListener {
            panelAiKeyConfig.visibility = if (panelAiKeyConfig.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnAiSaveKey.setOnClickListener {
            val rawKey = etAiApiKey.text.toString().trim()
            if (rawKey.isEmpty()) {
                prefs.chatGptApiKey = ""
                Toast.makeText(this, "API Key dihapus (mode offline aktif)", Toast.LENGTH_SHORT).show()
                panelAiKeyConfig.visibility = View.GONE
                return@setOnClickListener
            }
            when {
                rawKey.startsWith("http://") || rawKey.startsWith("https://") -> {
                    prefs.aiProxyUrl = rawKey
                    Toast.makeText(this, "URL Proxy AI disimpan!", Toast.LENGTH_SHORT).show()
                }
                rawKey.startsWith("gsk_") -> {
                    prefs.chatGptApiKey = rawKey
                    prefs.chatGptEndpoint = "https://api.groq.com/openai/v1/chat/completions"
                    prefs.chatGptModel = "llama-3.3-70b-versatile"
                    Toast.makeText(this, "Groq API Key tersimpan!", Toast.LENGTH_SHORT).show()
                }
                rawKey.startsWith("sk-or-") -> {
                    prefs.chatGptApiKey = rawKey
                    prefs.chatGptEndpoint = "https://openrouter.ai/api/v1/chat/completions"
                    prefs.chatGptModel = "openai/gpt-4o-mini"
                    Toast.makeText(this, "OpenRouter API Key tersimpan!", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    prefs.chatGptApiKey = rawKey
                    prefs.chatGptEndpoint = Prefs.DEFAULT_CHATGPT_ENDPOINT
                    prefs.chatGptModel = Prefs.DEFAULT_CHATGPT_MODEL
                    Toast.makeText(this, "OpenAI API Key tersimpan!", Toast.LENGTH_SHORT).show()
                }
            }
            panelAiKeyConfig.visibility = View.GONE
        }

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        btnAiPaste.setOnClickListener {
            val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!clip.isNullOrEmpty()) {
                etAi.setText(clip)
                etAi.setSelection(clip.length)
            } else {
                Toast.makeText(this, "Papan klip kosong", Toast.LENGTH_SHORT).show()
            }
        }

        btnAiClear.setOnClickListener {
            etAi.setText("")
            tvAiResponse.text = "Hasil jawaban AI akan muncul di sini…"
        }

        btnAiSubmit.setOnClickListener {
            val q = etAi.text.toString().trim()
            if (q.isEmpty()) {
                Toast.makeText(this, "Tulis atau tempel pertanyaan terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val hasApiKey = prefs.chatGptApiKey.isNotBlank()
            val hasCustomProxy = prefs.aiProxyUrl.isNotBlank() && !prefs.aiProxyUrl.contains("10.0.2.2")

            if (!hasApiKey && !hasCustomProxy) {
                // Mode Offline Pintar: coba pecahkan soal ABCD atau matematika (SPLDV, PLSV, Kuadrat, Kalkulator/Kombinasi)
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
                tvAiResponse.text = "Belum ada API Key AI yang diisi.\n\n" +
                    "• Untuk soal Matematika (SPLDV, PLSV, Kuadrat, 5C2, 5!), ketik persamaannya langsung di sini atau buka tab ∑ Kalkulator.\n" +
                    "• Untuk pertanyaan umum / esai, ketuk tombol '🔑 Key' di atas dan tempel API Key OpenAI (sk-...), Groq gratis (gsk_...), atau OpenRouter (sk-or-...)."
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
                Toast.makeText(this, "Jawaban AI berhasil disalin!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Belum ada jawaban yang dapat disalin", Toast.LENGTH_SHORT).show()
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

        // Muat Google halaman awal
        webView.loadUrl("https://www.google.com")

        // --- Tab 3: Kalkulator Umum, Kombinasi & SMP 9 Math Modules
        val mathChipContainer = popup.findViewById<LinearLayout>(R.id.mathChipsRow)
        val tvMathDesc = popup.findViewById<TextView>(R.id.tvFloatingMathDesc)
        val containerMathInputs = popup.findViewById<LinearLayout>(R.id.mathFieldsContainer)
        val quickKeypadRow = popup.findViewById<LinearLayout>(R.id.mathQuickKeypadRow)
        val tvMathResult = popup.findViewById<TextView>(R.id.tvFloatingMathResult)
        val btnMathSolve = popup.findViewById<Button>(R.id.btnFloatingMathSolve)
        val btnMathCopy = popup.findViewById<Button>(R.id.btnFloatingMathCopy)

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
            tvMathDesc.text = "Kalkulator Umum, Ilmiah & Kombinasi/Permutasi (contoh: 5C2, 5P2, 5!, 2^3 + √144, sin(30))"
            containerMathInputs.removeAllViews()
            mathInputFields.clear()
            mathChoiceValues.clear()
            calcExprEditText = null

            val label = TextView(this).apply {
                text = "Ekspresi Matematika:"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4f) }
            }

            val input = EditText(this).apply {
                hint = "Misal: 5C2 + 3! atau (12 + 8) × 3"
                setHintTextColor(Color.parseColor("#64748B"))
                setTextColor(Color.parseColor("#F8FAFC"))
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

            for (field in mod.fields) {
                if (field.type == FieldType.CHOICE && field.choices.isNotEmpty()) {
                    val col = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(6f) }
                    }

                    val label = TextView(this).apply {
                        text = "${field.label}:"
                        setTextColor(Color.parseColor("#CBD5E1"))
                        textSize = 11.5f
                    }

                    val hsv = HorizontalScrollView(this).apply {
                        isHorizontalScrollBarEnabled = false
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(4f) }
                    }

                    val choiceRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }

                    val defaultVal = field.defaultValue.ifEmpty { field.choices.first() }
                    mathChoiceValues[field.key] = defaultVal
                    val choiceButtons = ArrayList<Button>()

                    fun renderChoicePills(selectedVal: String) {
                        for (btn in choiceButtons) {
                            val isSel = btn.tag == selectedVal
                            btn.setBackgroundResource(if (isSel) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
                            btn.setTextColor(if (isSel) Color.WHITE else Color.parseColor("#CBD5E1"))
                        }
                    }

                    for (ch in field.choices) {
                        val btn = Button(this).apply {
                            text = ch
                            tag = ch
                            isAllCaps = false
                            textSize = 11f
                            setPadding(dp(10f), dp(2f), dp(10f), dp(2f))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                dp(30f)
                            ).apply { marginEnd = dp(6f) }
                            setOnClickListener {
                                mathChoiceValues[field.key] = ch
                                renderChoicePills(ch)
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

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(6f) }
                }

                val label = TextView(this).apply {
                    text = "${field.label}:"
                    setTextColor(Color.parseColor("#CBD5E1"))
                    textSize = 11.5f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                        marginEnd = dp(6f)
                    }
                }

                val input = EditText(this).apply {
                    setText(field.defaultValue)
                    hint = field.hint
                    setHintTextColor(Color.parseColor("#64748B"))
                    setTextColor(Color.parseColor("#F8FAFC"))
                    setBackgroundResource(R.drawable.bg_floating_input)
                    setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f)

                    // Pakai TYPE_CLASS_TEXT + NO_SUGGESTIONS agar bisa ketik pecahan (1/2), koma (2,3), tanda kurung, & huruf.
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
            btnMathSolve.text = "Hitung Solusi Eksak"
        }

        // Bilah Keypad Cepat (berfungsi meski keyboard sistem terblokir atau tidak muncul)
        val quickKeys = listOf(
            "⌫" to "BACK", "AC" to "CLEAR",
            "7" to "7", "8" to "8", "9" to "9", "4" to "4", "5" to "5", "6" to "6",
            "1" to "1", "2" to "2", "3" to "3", "0" to "0",
            "+" to "+", "-" to "-", "×" to "×", "÷" to "÷",
            "(" to "(", ")" to ")", "^" to "^", "√" to "√(", "!" to "!",
            "nCr" to "C", "nPr" to "P",
            "x" to "x", "y" to "y", "=" to "=", "<" to "<", ">" to ">",
            "/" to "/", "," to ",", ";" to ";", "." to "."
        )
        for ((label, ins) in quickKeys) {
            val kBtn = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 11.5f
                setTextColor(if (ins == "CLEAR" || ins == "BACK") Color.parseColor("#FCA5A5") else Color.parseColor("#F8FAFC"))
                setBackgroundResource(R.drawable.bg_floating_keypad_btn)
                setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
                minWidth = dp(34f)
                minimumWidth = dp(34f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(30f)
                ).apply { marginEnd = dp(4f) }

                setOnClickListener {
                    val targetEt = activeMathEditText
                        ?: calcExprEditText
                        ?: mathInputFields.values.firstOrNull()
                        ?: return@setOnClickListener
                    targetEt.requestFocus()
                    val editable = targetEt.text ?: return@setOnClickListener
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
            }
            quickKeypadRow.addView(kBtn)
        }

        // Buat chip: 0. Kalkulator + 11 modul SMP 9
        val allChipButtons = ArrayList<Button>()
        fun highlightChip(selectedIdx: Int) {
            allChipButtons.forEachIndexed { i, btn ->
                btn.setBackgroundResource(if (i == selectedIdx) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
                btn.setTextColor(if (i == selectedIdx) Color.WHITE else Color.parseColor("#CBD5E1"))
            }
        }

        val calcChip = Button(this).apply {
            text = "0. KALKULATOR"
            isAllCaps = false
            textSize = 11f
            setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32f)
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
            val chip = Button(this).apply {
                text = mathShortTitle(mod, index)
                isAllCaps = false
                textSize = 11f
                setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(32f)
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
                Toast.makeText(this, "Solusi dan langkah berhasil disalin!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Belum ada solusi untuk disalin", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Tab 4: Auto Jawab ABCD
        val tvAccStatus = popup.findViewById<TextView>(R.id.tvAutoAnswerAccStatus)
        val btnAccSettings = popup.findViewById<Button>(R.id.btnOpenAccSettings)
        val switchAutoAnswer = popup.findViewById<SwitchCompat>(R.id.switchAutoAnswerEnabled)
        val btnModeAuto = popup.findViewById<Button>(R.id.btnModeAutoClick)
        val btnModeKey = popup.findViewById<Button>(R.id.btnModeKeyOnly)
        val btnD0 = popup.findViewById<Button>(R.id.btnDelay0)
        val btnD1 = popup.findViewById<Button>(R.id.btnDelay1)
        val btnD2 = popup.findViewById<Button>(R.id.btnDelay2)
        val btnD3 = popup.findViewById<Button>(R.id.btnDelay3)
        val btnTriggerScreenAnswer = popup.findViewById<Button>(R.id.btnTriggerScreenAnswer)
        val tvAutoStatus = popup.findViewById<TextView>(R.id.tvAutoAnswerStatus)
        val btnCopyAutoAnswer = popup.findViewById<Button>(R.id.btnCopyAutoAnswer)

        fun updateAccStatus() {
            val running = AutoAnswerAccessibilityService.isRunning
            if (running) {
                tvAccStatus.text = "Aksesibilitas: Aktif (Auto-Klik Siap)"
                tvAccStatus.setTextColor(Color.parseColor("#10B981"))
                btnAccSettings.text = "Pengaturan"
            } else {
                tvAccStatus.text = "Aksesibilitas: Nonaktif (Klik untuk Izin)"
                tvAccStatus.setTextColor(Color.parseColor("#F59E0B"))
                btnAccSettings.text = "Aktifkan Izin"
            }
        }
        updateAccStatus()
        AutoAnswerAccessibilityService.onStatusChangedListener = {
            mainHandler.post { updateAccStatus() }
        }

        btnAccSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Toast.makeText(this, "Nyalakan 'Clestial Auto Jawab ABCD' di daftar layanan", Toast.LENGTH_LONG).show()
        }

        switchAutoAnswer.isChecked = prefs.autoAnswerEnabled
        switchAutoAnswer.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoAnswerEnabled = isChecked
            Toast.makeText(this, if (isChecked) "Auto-Jawab ABCD Diaktifkan" else "Auto-Jawab ABCD Dinonaktifkan", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Mode: Auto-Klik Layar (Accessibility)", Toast.LENGTH_SHORT).show()
        }
        btnModeKey.setOnClickListener {
            prefs.autoAnswerMode = Prefs.MODE_KEY_ONLY
            updateModeUI()
            Toast.makeText(this, "Mode: Kunci Jawaban & Salin Saja", Toast.LENGTH_SHORT).show()
        }

        fun updateDelayUI() {
            val cur = prefs.autoAnswerDelayMs
            btnD0.setBackgroundResource(if (cur == 0) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
            btnD0.setTextColor(if (cur == 0) Color.WHITE else Color.parseColor("#CBD5E1"))
            btnD1.setBackgroundResource(if (cur == 1500) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
            btnD1.setTextColor(if (cur == 1500) Color.WHITE else Color.parseColor("#CBD5E1"))
            btnD2.setBackgroundResource(if (cur == 2000) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
            btnD2.setTextColor(if (cur == 2000) Color.WHITE else Color.parseColor("#CBD5E1"))
            btnD3.setBackgroundResource(if (cur == 3000) R.drawable.bg_floating_pill_on else R.drawable.bg_floating_pill)
            btnD3.setTextColor(if (cur == 3000) Color.WHITE else Color.parseColor("#CBD5E1"))
        }
        updateDelayUI()

        btnD0.setOnClickListener { prefs.autoAnswerDelayMs = 0; updateDelayUI() }
        btnD1.setOnClickListener { prefs.autoAnswerDelayMs = 1500; updateDelayUI() }
        btnD2.setOnClickListener { prefs.autoAnswerDelayMs = 2000; updateDelayUI() }
        btnD3.setOnClickListener { prefs.autoAnswerDelayMs = 3000; updateDelayUI() }

        var lastAnswerResultText = ""

        fun displaySolution(res: AbcdSolutionResult) {
            val text = if (res.ok) {
                "🎯 Kunci: [${res.selectedOption}] ${res.optionText}\n" +
                "Sumber: ${res.source}\n" +
                "Penjelasan: ${res.explanation}"
            } else {
                "Error: ${res.error ?: "Gagal memproses soal."}"
            }
            lastAnswerResultText = text
            tvAutoStatus.text = text
        }

        AutoAnswerAccessibilityService.onAnswerResultListener = { sol ->
            mainHandler.post { displaySolution(sol) }
        }

        btnTriggerScreenAnswer.setOnClickListener {
            val service = AutoAnswerAccessibilityService.instance
            if (service != null) {
                tvAutoStatus.text = "Sedang memindai soal di layar & menentukan jawaban..."
                service.triggerAutoAnswer { success, msg ->
                    mainHandler.post {
                        if (!success && !tvAutoStatus.text.startsWith("🎯 Kunci:")) {
                            tvAutoStatus.text = msg
                        }
                    }
                }
            } else {
                tvAutoStatus.text = "Layanan Aksesibilitas belum aktif. Aktifkan izin untuk auto-klik langsung di layar."
                Toast.makeText(this, "Izin Aksesibilitas belum aktif", Toast.LENGTH_SHORT).show()
            }
        }

        btnCopyAutoAnswer.setOnClickListener {
            if (lastAnswerResultText.isNotEmpty()) {
                cm.setPrimaryClip(ClipData.newPlainText("Kunci Jawaban Soal", lastAnswerResultText))
                Toast.makeText(this, "Kunci jawaban berhasil disalin!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Belum ada kunci jawaban untuk disalin", Toast.LENGTH_SHORT).show()
            }
        }

        popupView = popup
        popupParams = params
    }

    @SuppressLint("SetJavaScriptEnabled")
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

        if (!v.isAttachedToWindow) {
            try {
                wm.addView(v, p)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        v.visibility = View.VISIBLE
        isPopupShowing = true
        hideBubble()
        hideDock()
        hideSelectionBox()
    }

    fun hidePopup() {
        popupView?.visibility = View.GONE
        isPopupShowing = false
        showBubble()
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        dockView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        selectionBoxView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        popupView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
    }
}
