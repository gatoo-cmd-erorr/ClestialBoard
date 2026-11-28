package com.clestial.keyboard.autoanswer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.clestial.keyboard.Prefs
import com.clestial.keyboard.floating.ScreenOcrProcessor
import com.clestial.keyboard.math.AiRouterClient
import java.util.Locale
import kotlin.random.Random

class AutoScannerService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoScannerService? = null
            private set

        val isRunning: Boolean
            get() = instance != null

        var onStatusChangedListener: ((Boolean) -> Unit)? = null
        var onAnswerResultListener: ((AbcdSolutionResult) -> Unit)? = null
        var onStatusMessageListener: ((String) -> Unit)? = null
        var onProgressUpdateListener: ((Int, Int) -> Unit)? = null

        private const val MAX_SCROLL_STEPS = 4
        private const val SCROLL_SETTLE_DELAY_MS = 450L
        private const val RESTORE_SCROLL_DELAY_MS = 320L
        private const val AUTO_DEBOUNCE_MS = 1800L
        private const val PIN_BYPASS_COOLDOWN_MS = 2500L
    }

    private lateinit var prefs: Prefs
    private val mainHandler = Handler(Looper.getMainLooper())
    private var immersiveOverlayView: View? = null

    private var lastQuestionStem: String = ""
    private var lastAnswerTimeMs: Long = 0L
    private var lastPinBypassMs: Long = 0L
    private var isScanningOrAnswering = false
    private var pendingClickRunnable: Runnable? = null

    var isContinuousRunning: Boolean = false
        private set
    var isKkmMode: Boolean = false
        private set
    var answeredCount: Int = 0
        private set
    var totalQuestionsEstimate: Int = 30

    private data class ScannedNode(
        val node: AccessibilityNodeInfo,
        val text: String,
        val bounds: Rect,
        val visible: Boolean
    )

    private data class ScrollStepRecord(
        val usedNodeAction: Boolean
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        val info = serviceInfo
        if (info != null) {
            info.flags = 0
            serviceInfo = info
        }
        if (prefs.hideNavigationEnabled) {
            setImmersiveMode(true)
        }
        onStatusChangedListener?.invoke(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::prefs.isInitialized) prefs = Prefs(this)
        if (event.packageName?.toString() == packageName) return

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val now = System.currentTimeMillis()
            if (now - lastPinBypassMs >= PIN_BYPASS_COOLDOWN_MS) {
                if (detectAndBypassPinning()) {
                    lastPinBypassMs = now
                    return
                }
            }
        }

        if (!prefs.autoAnswerEnabled) return
        if (!isContinuousRunning && prefs.autoAnswerMode != Prefs.MODE_AUTO_CLICK) return

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            checkAndAutoAnswerDebounced()
        }
    }

    fun detectAndBypassPinning(preferNodeOnly: Boolean = false): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val lockState = am.lockTaskModeState
        if (lockState != ActivityManager.LOCK_TASK_MODE_PINNED &&
            lockState != ActivityManager.LOCK_TASK_MODE_LOCKED
        ) {
            return false
        }

        if (preferNodeOnly) {
            return tryClickUnpinNode()
        }
        return forceUnpinSystem()
    }

    fun forceUnpinSystem(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val lockState = am.lockTaskModeState
        if (lockState != ActivityManager.LOCK_TASK_MODE_PINNED &&
            lockState != ActivityManager.LOCK_TASK_MODE_LOCKED
        ) {
            return false
        }

        if (tryClickUnpinNode()) {
            emitStatus("Mode Semat terdeteksi: tombol keluar/unpin diklik otomatis.")
            return true
        }

        emitStatus("Mode Semat (Screen Pinning) terdeteksi, melepaskan sematan...")
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dispatchVerticalSwipe(startYRatio = 0.99f, endYRatio = 0.50f) {
                    mainHandler.postDelayed({ tryClickUnpinNode() }, 180L)
                }
            } else {
                mainHandler.postDelayed({ tryClickUnpinNode() }, 180L)
            }
        }, 300L)
        return true
    }

    fun isScreenPinned(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val lockState = am.lockTaskModeState
        return lockState == ActivityManager.LOCK_TASK_MODE_PINNED ||
            lockState == ActivityManager.LOCK_TASK_MODE_LOCKED
    }

    fun simulateUnpinGesture(onFinished: (() -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onFinished?.invoke()
            return false
        }
        val dm = resources.displayMetrics
        val y = (dm.heightPixels * 0.95f).coerceIn(1f, dm.heightPixels - 1f)
        val backX = (dm.widthPixels * 0.20f).coerceIn(1f, dm.widthPixels - 1f)
        val recentsX = (dm.widthPixels * 0.80f).coerceIn(1f, dm.widthPixels - 1f)

        val pathBack = Path().apply { moveTo(backX, y) }
        val pathRecents = Path().apply { moveTo(recentsX, y) }

        val strokeBack = GestureDescription.StrokeDescription(pathBack, 0L, 2000L)
        val strokeRecents = GestureDescription.StrokeDescription(pathRecents, 0L, 2000L)

        val gesture = GestureDescription.Builder()
            .addStroke(strokeBack)
            .addStroke(strokeRecents)
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onFinished?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onFinished?.invoke()
            }
        }, null)
        if (!dispatched) {
            onFinished?.invoke()
        }
        return dispatched
    }

    private fun tryClickUnpinNode(): Boolean {
        val roots = LinkedHashSet<AccessibilityNodeInfo>()
        try {
            windows?.forEach { w -> w.root?.let { roots.add(it) } }
        } catch (_: Exception) {
        }
        rootInActiveWindow?.let { roots.add(it) }

        val targetPhrases = listOf(
            "keluar dari mode semat",
            "keluar mode semat",
            "lepas sematan",
            "unpin",
            "oke",
            "mengerti"
        )
        for (root in roots) {
            val match = findNodeMatchingPhrases(root, targetPhrases, allowExactDialogOnly = true)
            if (match != null && performClickOnNodeOrParent(match)) {
                return true
            }
        }
        return false
    }

    private fun findNodeMatchingPhrases(
        node: AccessibilityNodeInfo?,
        phrases: List<String>,
        allowExactDialogOnly: Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val desc = node.contentDescription?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val combined = "$text $desc".trim()

        if (combined.isNotEmpty()) {
            val isUnpinPhrase = combined.contains("keluar dari mode semat") ||
                combined.contains("keluar mode semat") ||
                combined.contains("lepas sematan") ||
                combined == "unpin" ||
                combined.contains("unpin")
            if (isUnpinPhrase) {
                return node
            }
            if (allowExactDialogOnly && (node.packageName?.toString() == "com.android.systemui")) {
                if (phrases.any { combined == it || combined.contains(it) }) {
                    return node
                }
            }
        }

        for (i in 0 until node.childCount) {
            val found = findNodeMatchingPhrases(node.getChild(i), phrases, allowExactDialogOnly)
            if (found != null) return found
        }
        return null
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    override fun onInterrupt() {
        stopGeneration()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        setImmersiveMode(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopGeneration()
        setImmersiveMode(false)
        mainHandler.removeCallbacksAndMessages(null)
        if (instance === this) {
            instance = null
        }
        onStatusChangedListener?.invoke(false)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    fun setImmersiveMode(active: Boolean) {
        mainHandler.post {
            val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return@post
            if (!active) {
                immersiveOverlayView?.let { v ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        v.windowInsetsController?.show(
                            WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                        )
                    }
                    v.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                    if (v.isAttachedToWindow) {
                        try {
                            wm.removeView(v)
                        } catch (_: Exception) {
                        }
                    }
                }
                immersiveOverlayView = null
                return@post
            }

            val view = immersiveOverlayView ?: View(this).also { immersiveOverlayView = it }
            view.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            if (!view.isAttachedToWindow) {
                val params = WindowManager.LayoutParams(
                    1,
                    1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = 0
                }
                try {
                    wm.addView(view, params)
                } catch (_: Exception) {
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.post {
                    view.windowInsetsController?.let { controller ->
                        controller.systemBarsBehavior =
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(
                            WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                        )
                    }
                }
            }
        }
    }

    fun startAuto100() {
        isContinuousRunning = true
        isKkmMode = false
        triggerAutoAnswer { _, _ -> }
    }

    fun startAutoKkm() {
        isContinuousRunning = true
        isKkmMode = true
        triggerAutoAnswer { _, _ -> }
    }

    fun stopGeneration() {
        isContinuousRunning = false
        isScanningOrAnswering = false
        pendingClickRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingClickRunnable = null
    }

    private fun checkAndAutoAnswerDebounced() {
        if (isScanningOrAnswering) return
        val now = System.currentTimeMillis()
        val minCooldown = if (prefs.autoAnswerDelayMs == Prefs.DELAY_RANDOM) {
            AUTO_DEBOUNCE_MS
        } else {
            prefs.autoAnswerDelayMs.toLong().coerceAtLeast(AUTO_DEBOUNCE_MS)
        }
        if (now - lastAnswerTimeMs < minCooldown) return
        triggerAutoAnswer(fromAutoEvent = true) { _, _ -> }
    }

    fun triggerAutoAnswer(fromAutoEvent: Boolean = false, onDone: (Boolean, String) -> Unit) {
        if (!::prefs.isInitialized) prefs = Prefs(this)
        if (isScanningOrAnswering) {
            onDone(false, "Pemindaian sebelumnya masih berjalan.")
            return
        }
        val root = rootInActiveWindow ?: run {
            onDone(false, "Tidak dapat mengakses konten layar aktif.")
            return
        }

        isScanningOrAnswering = true
        emitStatus("Memindai node layar secara mendalam...")

        val initialNodes = collectNodesDeep(root)
        val stitchedTexts = LinkedHashSet<String>()
        initialNodes.forEach { stitchedTexts.add(it.text) }

        val initialText = stitchedTexts.joinToString("\n")
        val initialParsed = extractQuestionAndOptions(initialNodes, initialText)

        if (!needsFallbackScroll(initialNodes, initialParsed.first)) {
            processExtractedContent(
                fullStitchedText = initialText,
                parsedQuestion = initialParsed.first,
                fromAutoEvent = fromAutoEvent,
                onDone = onDone
            )
            return
        }

        emitStatus("Teks terpotong/sedikit, melakukan fallback auto-scroll...")
        performFallbackScrollAndStitch(
            stitchedTexts = stitchedTexts,
            scrollHistory = ArrayList(),
            step = 0
        ) { finalText, scrollHistory ->
            restoreScrollPosition(scrollHistory) {
                val freshRoot = rootInActiveWindow
                val freshNodes = if (freshRoot != null) collectNodesDeep(freshRoot) else initialNodes
                val parsed = extractQuestionAndOptions(freshNodes, finalText)
                processExtractedContent(
                    fullStitchedText = finalText,
                    parsedQuestion = parsed.first,
                    fromAutoEvent = fromAutoEvent,
                    onDone = onDone
                )
            }
        }
    }

    private fun collectNodesDeep(root: AccessibilityNodeInfo): List<ScannedNode> {
        val out = ArrayList<ScannedNode>()
        val seenKeys = HashSet<String>()
        traverseNodesDeep(root, out, seenKeys)
        return out
    }

    private fun traverseNodesDeep(
        node: AccessibilityNodeInfo?,
        out: MutableList<ScannedNode>,
        seenKeys: MutableSet<String>
    ) {
        if (node == null) return
        if (node.packageName?.toString() == packageName) return

        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val effectiveText = if (text.isNotEmpty()) text else desc

        if (effectiveText.isNotEmpty()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val key = "$effectiveText|${rect.left},${rect.top},${rect.right},${rect.bottom}"
            if (seenKeys.add(key)) {
                out.add(ScannedNode(node, effectiveText, rect, node.isVisibleToUser))
            }
        }

        for (i in 0 until node.childCount) {
            traverseNodesDeep(node.getChild(i), out, seenKeys)
        }
    }

    private fun needsFallbackScroll(nodes: List<ScannedNode>, question: AbcdQuestion?): Boolean {
        if (nodes.isEmpty()) return true
        val hasEllipsis = nodes.any {
            it.text.endsWith("...") || it.text.endsWith("…")
        }
        if (hasEllipsis) return true
        val totalChars = nodes.sumOf { it.text.length }
        if (totalChars < 80 || nodes.size < 4) return true
        if (question == null || question.options.size < 2) return true
        return false
    }

    private fun performFallbackScrollAndStitch(
        stitchedTexts: LinkedHashSet<String>,
        scrollHistory: MutableList<ScrollStepRecord>,
        step: Int,
        onComplete: (String, List<ScrollStepRecord>) -> Unit
    ) {
        if (step >= MAX_SCROLL_STEPS) {
            onComplete(stitchedTexts.joinToString("\n"), scrollHistory)
            return
        }
        val root = rootInActiveWindow
        if (root == null) {
            onComplete(stitchedTexts.joinToString("\n"), scrollHistory)
            return
        }

        scrollForwardOnce(root) { scrolled, usedNodeAction ->
            if (!scrolled) {
                onComplete(stitchedTexts.joinToString("\n"), scrollHistory)
                return@scrollForwardOnce
            }
            scrollHistory.add(ScrollStepRecord(usedNodeAction))
            mainHandler.postDelayed({
                val nextRoot = rootInActiveWindow
                if (nextRoot == null) {
                    onComplete(stitchedTexts.joinToString("\n"), scrollHistory)
                    return@postDelayed
                }
                val nextNodes = collectNodesDeep(nextRoot)
                var addedNew = 0
                for (item in nextNodes) {
                    if (stitchedTexts.add(item.text)) {
                        addedNew++
                    }
                }
                val stillTruncated = nextNodes.any { it.text.endsWith("...") || it.text.endsWith("…") }
                if (addedNew == 0 && !stillTruncated) {
                    onComplete(stitchedTexts.joinToString("\n"), scrollHistory)
                } else {
                    performFallbackScrollAndStitch(stitchedTexts, scrollHistory, step + 1, onComplete)
                }
            }, SCROLL_SETTLE_DELAY_MS)
        }
    }

    private fun scrollForwardOnce(
        root: AccessibilityNodeInfo,
        onDone: (Boolean, Boolean) -> Unit
    ) {
        val scrollable = findScrollableNode(root, forward = true)
        if (scrollable != null && scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            onDone(true, true)
            return
        }
        dispatchVerticalSwipe(startYRatio = 0.80f, endYRatio = 0.20f) { ok ->
            onDone(ok, false)
        }
    }

    private fun restoreScrollPosition(
        scrollHistory: List<ScrollStepRecord>,
        index: Int = scrollHistory.lastIndex,
        onFinished: () -> Unit
    ) {
        if (index < 0) {
            onFinished()
            return
        }
        val record = scrollHistory[index]
        val root = rootInActiveWindow
        if (record.usedNodeAction && root != null) {
            val scrollable = findScrollableNode(root, forward = false)
            if (scrollable != null && scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                mainHandler.postDelayed({
                    restoreScrollPosition(scrollHistory, index - 1, onFinished)
                }, RESTORE_SCROLL_DELAY_MS)
                return
            }
        }
        dispatchVerticalSwipe(startYRatio = 0.20f, endYRatio = 0.80f) {
            mainHandler.postDelayed({
                restoreScrollPosition(scrollHistory, index - 1, onFinished)
            }, RESTORE_SCROLL_DELAY_MS)
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?, forward: Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.packageName?.toString() != packageName) {
            val targetAction = if (forward) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
            }
            if (node.isScrollable || node.actionList.contains(targetAction)) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i), forward)
            if (found != null) return found
        }
        return null
    }

    private fun dispatchVerticalSwipe(
        startYRatio: Float,
        endYRatio: Float,
        onDone: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onDone(false)
            return
        }
        val dm = resources.displayMetrics
        val x = (dm.widthPixels * 0.50f).coerceIn(1f, dm.widthPixels - 1f)
        val startY = (dm.heightPixels * startYRatio).coerceIn(1f, dm.heightPixels - 1f)
        val endY = (dm.heightPixels * endYRatio).coerceIn(1f, dm.heightPixels - 1f)

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 380L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onDone(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onDone(false)
            }
        }, null)
        if (!ok) onDone(false)
    }

    private fun extractQuestionAndOptions(
        nodes: List<ScannedNode>,
        stitchedText: String
    ): Pair<AbcdQuestion?, Map<Char, ScannedNode>> {
        if (nodes.isEmpty()) return Pair(null, emptyMap())

        val optionRegex = Regex("""^[\(\[]?([A-Ea-e])[\)\]\.\:\-]\s*(.*)$""")
        val optionNodes = LinkedHashMap<Char, ScannedNode>()
        val optionsMap = LinkedHashMap<Char, String>()
        val nonOptionTexts = ArrayList<String>()

        for (item in nodes) {
            val match = optionRegex.find(item.text)
            if (match != null) {
                val optChar = match.groupValues[1].uppercase(Locale.ROOT)[0]
                val optVal = match.groupValues[2].trim().ifEmpty { item.text }
                optionNodes.putIfAbsent(optChar, item)
                optionsMap.putIfAbsent(optChar, optVal)
            } else {
                nonOptionTexts.add(item.text)
            }
        }

        if (optionsMap.size < 2) {
            for (i in 0 until nodes.size - 1) {
                val cur = nodes[i].text.trim()
                if (cur.length in 1..2 && cur.first().isLetter()) {
                    val c = cur.first().uppercaseChar()
                    if (c in 'A'..'E' && !optionNodes.containsKey(c)) {
                        val next = nodes[i + 1]
                        optionNodes[c] = next
                        optionsMap[c] = next.text.trim()
                    }
                }
            }
        }

        val parsed = if (optionsMap.size >= 2) {
            val stem = nonOptionTexts.filter { it.length > 4 }.joinToString("\n").trim()
                .ifEmpty { stitchedText.trim() }
            AbcdQuestion(questionText = stem, options = optionsMap, rawText = stitchedText)
        } else {
            AbcdSolver.parseFromText(stitchedText)
        }

        return Pair(parsed, optionNodes)
    }

    private fun processExtractedContent(
        fullStitchedText: String,
        parsedQuestion: AbcdQuestion?,
        fromAutoEvent: Boolean,
        onDone: (Boolean, String) -> Unit
    ) {
        val cleanText = fullStitchedText.trim()
        if (cleanText.length < 5) {
            isScanningOrAnswering = false
            onDone(false, "Tidak ada teks soal yang terbaca di layar.")
            return
        }

        val stemKey = parsedQuestion?.questionText ?: cleanText
        if (fromAutoEvent && stemKey == lastQuestionStem && System.currentTimeMillis() - lastAnswerTimeMs < AUTO_DEBOUNCE_MS) {
            isScanningOrAnswering = false
            onDone(false, "Soal ini baru saja dipindai.")
            return
        }

        val preApiDelayMs = if (prefs.antiDetectEnabled) {
            Random.nextLong(350L, 1250L)
        } else {
            0L
        }

        if (preApiDelayMs > 0L) {
            emitStatus("Anti Detek aktif: jeda acak ${preApiDelayMs}ms sebelum kirim ke 9Router...")
        } else {
            emitStatus("Mengirim teks layar ke AI (9Router)...")
        }

        mainHandler.postDelayed({
            solveWithAiOrFallback(cleanText, parsedQuestion) { solution ->
                isScanningOrAnswering = false
                onAnswerResultListener?.invoke(solution)

                if (!solution.ok) {
                    onDone(false, solution.error ?: "Gagal menentukan jawaban.")
                    return@solveWithAiOrFallback
                }

                lastQuestionStem = stemKey
                lastAnswerTimeMs = System.currentTimeMillis()
                answeredCount++
                onProgressUpdateListener?.invoke(answeredCount, totalQuestionsEstimate)

                copyToClipboard(solution)

                if (prefs.autoAnswerMode == Prefs.MODE_KEY_ONLY) {
                    val label = solution.selectedOption?.toString() ?: solution.optionText.take(24)
                    emitStatus("Mode Kunci & Salin: [$label] tersalin ke papan klip.")
                    onDone(true, "Kunci [$label] disalin ke papan klip.")
                    return@solveWithAiOrFallback
                }

                val clickDelayMs = resolveClickDelayMs()
                val targetLabel = solution.selectedOption?.toString() ?: solution.optionText.take(24)
                emitStatus("Menunggu jeda klik ${formatDelay(clickDelayMs)} untuk memilih [$targetLabel]...")

                pendingClickRunnable?.let { mainHandler.removeCallbacks(it) }
                val runnable = Runnable {
                    pendingClickRunnable = null
                    val freshRoot = rootInActiveWindow
                    if (freshRoot == null) {
                        onDone(false, "Jendela aktif hilang sebelum klik [$targetLabel].")
                        return@Runnable
                    }
                    val freshNodes = collectNodesDeep(freshRoot)
                    val targetNode = findMatchingTargetNode(
                        nodes = freshNodes,
                        targetChar = solution.selectedOption,
                        targetOptionText = solution.optionText
                    )
                    if (targetNode == null) {
                        onDone(false, "Kunci [$targetLabel] disalin, node opsi tidak ditemukan di layar.")
                        return@Runnable
                    }
                    clickNodeWithHumanization(targetNode.node) { clicked ->
                        val statusMsg = if (clicked) {
                            "Berhasil klik [$targetLabel] (${targetNode.text.take(36)})"
                        } else {
                            "Gagal klik [$targetLabel]; kunci tetap disalin ke papan klip."
                        }
                        emitStatus(statusMsg)
                        onDone(clicked, statusMsg)
                    }
                }
                pendingClickRunnable = runnable
                mainHandler.postDelayed(runnable, clickDelayMs)
            }
        }, preApiDelayMs)
    }

    private fun solveWithAiOrFallback(
        fullStitchedText: String,
        parsedQuestion: AbcdQuestion?,
        onDone: (AbcdSolutionResult) -> Unit
    ) {
        if (parsedQuestion != null && parsedQuestion.options.size >= 2) {
            AbcdSolver.solve(parsedQuestion, prefs, onDone)
            return
        }

        if (!AiRouterClient.hasConfiguredEndpoint(prefs)) {
            onDone(AbcdSolutionResult(ok = false, error = "Tidak ada opsi A/B/C/D terdeteksi dan API 9Router belum dikonfigurasi."))
            return
        }

        val prompt = """
Berikut adalah teks lengkap hasil pemindaian layar soal ujian/kuis:
---
${fullStitchedText.take(1900)}
---

Tugasmu:
1. Tentukan soal utama dan pilih jawaban paling benar (huruf A/B/C/D/E jika ada pilihan, atau teks opsi jawaban yang tepat).
2. Jawab WAJIB dalam 3 baris berikut:
KUNCI: [Huruf pilihan atau jawaban singkat]
JAWABAN_TEKS: [Teks persis pilihan/jawaban di layar]
PENJELASAN: [Penjelasan singkat 1-2 kalimat]
""".trimIndent()

        AiRouterClient.askQuestion(prefs, prompt) { res ->
            if (!res.ok || res.text.isBlank()) {
                onDone(AbcdSolutionResult(ok = false, error = res.error.ifBlank { "Respons AI kosong" }))
                return@askQuestion
            }
            val parsedResult = parseFreeformAiResponse(res.text)
            onDone(parsedResult)
        }
    }

    private fun parseFreeformAiResponse(aiText: String): AbcdSolutionResult {
        val clean = aiText.trim()
        val keyMatch = Regex("""KUNCI\s*:\s*[\(\[]?([A-Ea-e])[\)\]\.]?""", RegexOption.IGNORE_CASE).find(clean)
        val letter = keyMatch?.groupValues?.get(1)?.uppercase(Locale.ROOT)?.firstOrNull()

        val textMatch = Regex("""JAWABAN_TEKS\s*:\s*(.+)""", RegexOption.IGNORE_CASE).find(clean)
        val rawAnswerText = textMatch?.groupValues?.get(1)?.trim()
            ?: Regex("""KUNCI\s*:\s*(.+)""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.trim()
            ?: clean.lineSequence().firstOrNull().orEmpty()

        val explMatch = Regex("""PENJELASAN\s*:\s*([\s\S]+)""", RegexOption.IGNORE_CASE).find(clean)
        val expl = explMatch?.groupValues?.get(1)?.trim() ?: clean

        return AbcdSolutionResult(
            ok = true,
            selectedOption = letter,
            optionText = rawAnswerText,
            explanation = expl,
            source = "AI (9Router)"
        )
    }

    private fun findMatchingTargetNode(
        nodes: List<ScannedNode>,
        targetChar: Char?,
        targetOptionText: String
    ): ScannedNode? {
        val cleanOptText = targetOptionText.trim().lowercase(Locale.ROOT)
        if (targetChar != null) {
            val upper = targetChar.uppercaseChar()
            val optionPattern = Regex("""^[\(\[]?${upper}[\)\]\.\:\-](\s|$)""", RegexOption.IGNORE_CASE)
            nodes.firstOrNull { optionPattern.containsMatchIn(it.text) }?.let { return it }

            for (i in 0 until nodes.size - 1) {
                val cur = nodes[i].text.trim()
                if (cur.equals(upper.toString(), ignoreCase = true) || cur.equals("$upper.", ignoreCase = true)) {
                    return nodes[i + 1]
                }
            }
        }

        if (cleanOptText.length >= 2) {
            nodes.firstOrNull { it.text.trim().equals(cleanOptText, ignoreCase = true) }?.let { return it }
            nodes.firstOrNull { it.text.trim().lowercase(Locale.ROOT).contains(cleanOptText) }?.let { return it }
            nodes.firstOrNull {
                val nt = it.text.trim().lowercase(Locale.ROOT)
                nt.length >= 4 && cleanOptText.contains(nt)
            }?.let { return it }
        }

        return null
    }

    private fun resolveClickDelayMs(): Long {
        return if (prefs.autoAnswerDelayMs == Prefs.DELAY_RANDOM) {
            Random.nextLong(20_000L, 180_000L)
        } else {
            prefs.autoAnswerDelayMs.toLong().coerceAtLeast(0L)
        }
    }

    private fun formatDelay(ms: Long): String {
        return if (ms >= 1000L) String.format(Locale.US, "%.1fs", ms / 1000.0) else "${ms}ms"
    }

    private fun clickNodeWithHumanization(node: AccessibilityNodeInfo, onComplete: (Boolean) -> Unit) {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            onComplete(true)
            return
        }

        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                onComplete(true)
                return
            }
            parent = parent.parent
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete(false)
            return
        }

        val dm = resources.displayMetrics
        val jitterX = Random.nextInt(-10, 10)
        val jitterY = Random.nextInt(-10, 10)
        val tapX = (rect.centerX() + jitterX).coerceIn(1, dm.widthPixels - 1).toFloat()
        val tapY = (rect.centerY() + jitterY).coerceIn(1, dm.heightPixels - 1).toFloat()

        val path = Path().apply { moveTo(tapX, tapY) }
        val duration = Random.nextLong(65L, 115L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onComplete(false)
            }
        }, null)

        if (!dispatched) {
            onComplete(false)
        }
    }

    private fun copyToClipboard(solution: AbcdSolutionResult) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val header = if (solution.selectedOption != null) {
            "🎯 Kunci: [${solution.selectedOption}] ${solution.optionText}"
        } else {
            "🎯 Jawaban: ${solution.optionText}"
        }
        val fullText = buildString {
            append(header)
            if (solution.explanation.isNotBlank()) {
                append("\nPenjelasan: ").append(solution.explanation)
            }
        }
        cm.setPrimaryClip(ClipData.newPlainText("Hasil Pindai AI", fullText))
    }

    private fun emitStatus(msg: String) {
        mainHandler.post { onStatusMessageListener?.invoke(msg) }
    }

    /**
     * Tangkap layar senyap (Android 11+) tanpa dialog "Bagikan layar".
     */
    fun captureRegionSilent(cropX: Int, cropY: Int, cropW: Int, cropH: Int, onFallback: () -> Unit) {
        captureRegionSilentBitmap(cropX, cropY, cropW, cropH) { cropped ->
            if (cropped != null) {
                ScreenOcrProcessor.processCroppedBitmap(applicationContext, cropped)
            } else {
                onFallback()
            }
        }
    }

    fun captureRegionSilentBitmap(
        cropX: Int,
        cropY: Int,
        cropW: Int,
        cropH: Int,
        onResult: (Bitmap?) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(null)
            return
        }
        mainHandler.postDelayed({
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                val hwBitmap = Bitmap.wrapHardwareBuffer(
                                    screenshot.hardwareBuffer,
                                    screenshot.colorSpace
                                )
                                screenshot.hardwareBuffer.close()
                                if (hwBitmap != null) {
                                    val softBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    hwBitmap.recycle()
                                    val safeX = cropX.coerceIn(0, softBitmap.width - 1)
                                    val safeY = cropY.coerceIn(0, softBitmap.height - 1)
                                    val safeW = cropW.coerceAtMost(softBitmap.width - safeX).coerceAtLeast(1)
                                    val safeH = cropH.coerceAtMost(softBitmap.height - safeY).coerceAtLeast(1)
                                    val cropped = Bitmap.createBitmap(softBitmap, safeX, safeY, safeW, safeH)
                                    softBitmap.recycle()
                                    onResult(cropped)
                                } else {
                                    onResult(null)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                onResult(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            onResult(null)
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }, 120)
    }
}
