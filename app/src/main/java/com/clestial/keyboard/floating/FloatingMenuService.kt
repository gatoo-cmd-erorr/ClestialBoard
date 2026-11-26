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
import android.widget.ImageButton
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

    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var popupView: View? = null
    private var popupParams: WindowManager.LayoutParams? = null
    private var isPopupShowing = false

    // State Math di Pop-up
    private var activeMathModule: MathModule = MathRegistry.modules.first()
    private val mathInputFields = LinkedHashMap<String, EditText>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)

        createBubbleView()
        createPopupView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            FloatingMenuManager.ACTION_SHOW_MENU -> showPopup()
            FloatingMenuManager.ACTION_HIDE_MENU -> hidePopup()
            FloatingMenuManager.ACTION_TOGGLE_MENU -> {
                if (isPopupShowing) hidePopup() else showPopup()
            }
            else -> {
                if (!isPopupShowing && bubbleView == null) {
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
            x = 24
            y = resources.displayMetrics.heightPixels / 3
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var lastTapTime = 0L

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
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 380) {
                            // Ketuk 2x: Buka Pop-up Menu!
                            showPopup()
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                        }
                    }
                    true
                }
                else -> false
            }
        }

        bubbleView = bubble
        bubbleParams = params
        showBubble()
    }

    private fun showBubble() {
        val b = bubbleView ?: return
        val p = bubbleParams ?: return
        if (!b.isAttachedToWindow) {
            try {
                wm.addView(b, p)
            } catch (e: Exception) {
                // Ignore
            }
        }
        b.visibility = View.VISIBLE
    }

    private fun hideBubble() {
        bubbleView?.visibility = View.GONE
    }

    // ------------------------------------------------------------- 2. Pop-up Window
    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun createPopupView() {
        if (!FloatingMenuManager.canDrawOverlays(this)) return

        val inflater = LayoutInflater.from(this)
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
                            // Ketuk 2x pada header: Tutup Menu!
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

        tabAi.setOnClickListener { selectTab(0) }
        tabGoogle.setOnClickListener { selectTab(1) }
        tabMath.setOnClickListener { selectTab(2) }

        // --- Tab 1: AI (ChatGPT)
        val etAi = popup.findViewById<EditText>(R.id.etAiQuestion)
        val tvAiResponse = popup.findViewById<TextView>(R.id.tvAiResponse)
        val btnAiPaste = popup.findViewById<Button>(R.id.btnAiPaste)
        val btnAiClear = popup.findViewById<Button>(R.id.btnAiClear)
        val btnAiSubmit = popup.findViewById<Button>(R.id.btnAiSubmit)
        val btnAiCopy = popup.findViewById<Button>(R.id.btnAiCopy)

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
                Toast.makeText(this, "Ketik pertanyaan terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvAiResponse.text = "Sedang berpikir & merumuskan jawaban..."
            btnAiSubmit.isEnabled = false

            AiRouterClient.askQuestion(prefs, q) { res ->
                mainHandler.post {
                    btnAiSubmit.isEnabled = true
                    if (res.ok) {
                        tvAiResponse.text = res.text
                    } else {
                        tvAiResponse.text = "Error: ${res.error}"
                    }
                }
            }
        }

        btnAiCopy.setOnClickListener {
            val ans = tvAiResponse.text.toString().trim()
            if (ans.isNotEmpty() && !ans.startsWith("Hasil jawaban") && !ans.startsWith("Sedang berpikir")) {
                cm.setPrimaryClip(ClipData.newPlainText("AI Answer", ans))
                Toast.makeText(this, "Jawaban disalin ke papan klip!", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Tab 2: Mini Google Search Webview
        val etGoogle = popup.findViewById<EditText>(R.id.etGoogleQuery)
        val btnSearch = popup.findViewById<Button>(R.id.btnGoogleSearch)
        val wv = popup.findViewById<WebView>(R.id.wvGoogle)
        val btnWebBack = popup.findViewById<Button>(R.id.btnWebBack)
        val btnWebReload = popup.findViewById<Button>(R.id.btnWebReload)
        val tvWebUrl = popup.findViewById<TextView>(R.id.tvWebUrl)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tvWebUrl.text = url.orEmpty()
            }
        }
        wv.webChromeClient = WebChromeClient()

        fun doGoogleSearch(query: String) {
            val enc = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
            val target = "https://www.google.com/search?q=$enc"
            wv.loadUrl(target)
        }

        btnSearch.setOnClickListener {
            val query = etGoogle.text.toString().trim()
            if (query.isNotEmpty()) {
                doGoogleSearch(query)
            }
        }

        btnWebBack.setOnClickListener {
            if (wv.canGoBack()) wv.goBack()
        }
        btnWebReload.setOnClickListener {
            wv.reload()
        }

        // --- Tab 3: SMP 9 Math Quick Solver
        setupMathTab(popup)

        popupView = popup
        popupParams = params
    }

    private fun setupMathTab(popup: View) {
        val chipsRow = popup.findViewById<LinearLayout>(R.id.mathChipsRow)
        val descView = popup.findViewById<TextView>(R.id.tvFloatingMathDesc)
        val fieldsContainer = popup.findViewById<LinearLayout>(R.id.mathFieldsContainer)
        val btnSolve = popup.findViewById<Button>(R.id.btnFloatingMathSolve)
        val tvResult = popup.findViewById<TextView>(R.id.tvFloatingMathResult)
        val btnCopy = popup.findViewById<Button>(R.id.btnFloatingMathCopy)

        chipsRow.removeAllViews()

        fun renderFieldsForModule(m: MathModule) {
            activeMathModule = m
            descView.text = "${m.name}: ${m.description}"
            fieldsContainer.removeAllViews()
            mathInputFields.clear()

            for (f in m.fields) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(34f)
                    ).apply { topMargin = dp(3f) }
                    gravity = Gravity.CENTER_VERTICAL
                }

                val label = TextView(this).apply {
                    text = "${f.label}:"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(dp(110f), LinearLayout.LayoutParams.WRAP_CONTENT)
                }

                val input = EditText(this).apply {
                    hint = f.defaultValue.ifEmpty { "0" }
                    if (f.defaultValue.isNotEmpty()) setText(f.defaultValue)
                    setBackgroundResource(R.drawable.bg_input)
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    inputType = android.text.InputType.TYPE_CLASS_TEXT
                }

                row.addView(label)
                row.addView(input)
                fieldsContainer.addView(row)
                mathInputFields[f.key] = input
            }
        }

        MathRegistry.modules.forEachIndexed { idx, mod ->
            val chip = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = "${idx + 1}. ${mod.name.take(15)}"
                setTextColor(Color.WHITE)
                textSize = 10f
                setBackgroundResource(if (idx == 0) R.drawable.bg_btn else R.drawable.bg_pill)
                setPadding(dp(8f), 0, dp(8f), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { marginEnd = dp(4f) }
                setOnClickListener {
                    renderFieldsForModule(mod)
                    // Update visual chips
                    for (i in 0 until chipsRow.childCount) {
                        chipsRow.getChildAt(i).setBackgroundResource(if (i == idx) R.drawable.bg_btn else R.drawable.bg_pill)
                    }
                }
            }
            chipsRow.addView(chip)
        }

        renderFieldsForModule(MathRegistry.modules.first())

        btnSolve.setOnClickListener {
            val inputs = HashMap<String, String>()
            mathInputFields.forEach { (k, et) ->
                inputs[k] = et.text.toString().trim()
            }
            val res = activeMathModule.solve(inputs)
            if (res.ok) {
                val full = buildString {
                    appendLine("Materi: ${activeMathModule.name}")
                    appendLine("Jawaban: ${res.answer}")
                    appendLine("Langkah-langkah:")
                    res.steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
                    if (res.check.isNotEmpty()) appendLine("Cek: ${res.check}")
                }
                tvResult.text = full
            } else {
                tvResult.text = "Error: ${res.error}"
            }
        }

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        btnCopy.setOnClickListener {
            val text = tvResult.text.toString().trim()
            if (text.isNotEmpty() && !text.startsWith("Jawaban dan langkah")) {
                cm.setPrimaryClip(ClipData.newPlainText("Math Solution", text))
                Toast.makeText(this, "Jawaban & langkah disalin!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPopup() {
        val p = popupView ?: return
        val params = popupParams ?: return
        hideBubble()
        if (!p.isAttachedToWindow) {
            try {
                wm.addView(p, params)
            } catch (e: Exception) {
                // Ignore
            }
        }
        p.visibility = View.VISIBLE
        isPopupShowing = true
    }

    private fun hidePopup() {
        popupView?.visibility = View.GONE
        isPopupShowing = false
        showBubble()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bubbleView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
            popupView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
