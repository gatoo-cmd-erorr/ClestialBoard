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
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.clestial.keyboard.Prefs
import com.clestial.keyboard.R
import com.clestial.keyboard.math.AiRouterClient
import com.clestial.keyboard.math.FieldType
import com.clestial.keyboard.math.MathModule
import com.clestial.keyboard.math.MathRegistry
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

    // State Math di Pop-up
    private var activeMathModule: MathModule = MathRegistry.modules.first()
    private val mathInputFields = LinkedHashMap<String, EditText>()

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
            else -> {
                if (!isPopupShowing && !isDockShowing && !isBoxShowing && bubbleView == null) {
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

        // 5. ✕ Tutup Dock (Kembali ke mode tersamar)
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

            val captureIntent = Intent(this, ScreenCaptureActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(ScreenCaptureActivity.EXTRA_CROP_X, cropX)
                putExtra(ScreenCaptureActivity.EXTRA_CROP_Y, cropY)
                putExtra(ScreenCaptureActivity.EXTRA_CROP_W, cropW)
                putExtra(ScreenCaptureActivity.EXTRA_CROP_H, cropH)
            }
            startActivity(captureIntent)
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
            y = dp(70f)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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
        val panelAi = popup.findViewById<View>(R.id.panelAi)
        val panelGoogle = popup.findViewById<View>(R.id.panelGoogle)
        val panelMath = popup.findViewById<View>(R.id.panelMath)

        fun selectTab(idx: Int) {
            tabAi.isSelected = (idx == 0)
            tabGoogle.isSelected = (idx == 1)
            tabMath.isSelected = (idx == 2)
            panelAi.visibility = if (idx == 0) View.VISIBLE else View.GONE
            panelGoogle.visibility = if (idx == 1) View.VISIBLE else View.GONE
            panelMath.visibility = if (idx == 2) View.VISIBLE else View.GONE
        }
        selectTabRef = ::selectTab

        tabAi.setOnClickListener { selectTab(0) }
        tabGoogle.setOnClickListener { selectTab(1) }
        tabMath.setOnClickListener { selectTab(2) }
        selectTab(0)

        // --- Tab 1: AI (ChatGPT)
        val etAi = popup.findViewById<EditText>(R.id.etAiQuestion)
        val tvAiResponse = popup.findViewById<TextView>(R.id.tvAiResponse)
        val btnAiPaste = popup.findViewById<Button>(R.id.btnAiPaste)
        val btnAiClear = popup.findViewById<Button>(R.id.btnAiClear)
        val btnAiSubmit = popup.findViewById<Button>(R.id.btnAiSubmit)
        val btnAiCopy = popup.findViewById<Button>(R.id.btnAiCopy)
        etAiQuestionRef = etAi

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
            tvAiResponse.text = "Hasil jawaban AI akan muncul di sini..."
        }

        btnAiSubmit.setOnClickListener {
            val q = etAi.text.toString().trim()
            if (q.isEmpty()) {
                Toast.makeText(this, "Tulis atau tempel pertanyaan terlebih dahulu", Toast.LENGTH_SHORT).show()
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
                        tvAiResponse.text = "Error: ${res.error ?: "Gagal memproses"}"
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

        // --- Tab 3: SMP 9 Math Modules
        val mathChipContainer = popup.findViewById<LinearLayout>(R.id.mathChipsRow)
        val tvMathDesc = popup.findViewById<TextView>(R.id.tvFloatingMathDesc)
        val containerMathInputs = popup.findViewById<LinearLayout>(R.id.mathFieldsContainer)
        val tvMathResult = popup.findViewById<TextView>(R.id.tvFloatingMathResult)
        val btnMathSolve = popup.findViewById<Button>(R.id.btnFloatingMathSolve)
        val btnMathCopy = popup.findViewById<Button>(R.id.btnFloatingMathCopy)

        fun buildMathModuleUI(mod: MathModule) {
            activeMathModule = mod
            tvMathDesc.text = mod.description
            containerMathInputs.removeAllViews()
            mathInputFields.clear()

            for (field in mod.fields) {
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
                    setTextColor(Color.parseColor("#A0A5B5"))
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
                }

                val input = EditText(this).apply {
                    setText(field.defaultValue)
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_input)
                    setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, dp(40f), 1.8f)

                    inputType = when (field.type) {
                        FieldType.INTEGER -> android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                        FieldType.NUMBER -> android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                        FieldType.CHOICE -> android.text.InputType.TYPE_CLASS_TEXT
                        FieldType.TEXT -> android.text.InputType.TYPE_CLASS_TEXT
                    }
                }

                row.addView(label)
                row.addView(input)
                containerMathInputs.addView(row)
                mathInputFields[field.key] = input
            }
        }

        // Buat chip untuk ke-11 modul
        MathRegistry.modules.forEachIndexed { index, mod ->
            val chip = Button(this).apply {
                text = "${index + 1}. ${mod.name.take(16).uppercase()}"
                textSize = 11f
                setTextColor(if (index == 0) Color.WHITE else Color.parseColor("#8E92A0"))
                setBackgroundResource(if (index == 0) R.drawable.bg_pill_on else R.drawable.bg_pill)
                setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(32f)
                ).apply { marginEnd = dp(6f) }

                setOnClickListener {
                    for (i in 0 until mathChipContainer.childCount) {
                        val c = mathChipContainer.getChildAt(i) as? Button ?: continue
                        c.setBackgroundResource(if (i == index) R.drawable.bg_pill_on else R.drawable.bg_pill)
                        c.setTextColor(if (i == index) Color.WHITE else Color.parseColor("#8E92A0"))
                    }
                    buildMathModuleUI(mod)
                }
            }
            mathChipContainer.addView(chip)
        }

        buildMathModuleUI(activeMathModule)

        btnMathSolve.setOnClickListener {
            val map = mutableMapOf<String, String>()
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
