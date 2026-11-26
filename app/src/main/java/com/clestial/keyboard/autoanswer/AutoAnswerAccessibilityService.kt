package com.clestial.keyboard.autoanswer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.clestial.keyboard.Prefs
import com.clestial.keyboard.floating.ScreenOcrProcessor
import java.util.Locale

class AutoAnswerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoAnswerAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null

        var onStatusChangedListener: ((Boolean) -> Unit)? = null
        var onAnswerResultListener: ((AbcdSolutionResult) -> Unit)? = null
        var onProgressUpdateListener: ((Int, Int) -> Unit)? = null
    }

    private lateinit var prefs: Prefs
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastQuestionStem: String = ""
    private var lastAnswerTimeMs: Long = 0L
    private var isAnsweringInProgress = false

    var isContinuousRunning: Boolean = false
        private set
    var isKkmMode: Boolean = false
        private set
    var answeredCount: Int = 0
        private set
    var totalQuestionsEstimate: Int = 30

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        onStatusChangedListener?.invoke(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!prefs.autoAnswerEnabled) return
        if (!isContinuousRunning && prefs.autoAnswerMode != Prefs.MODE_AUTO_CLICK) return

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            checkAndAutoAnswerDebounced()
        }
    }

    override fun onInterrupt() {
        isContinuousRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        onStatusChangedListener?.invoke(false)
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
        isAnsweringInProgress = false
    }

    private fun checkAndAutoAnswerDebounced() {
        if (isAnsweringInProgress) return
        val now = System.currentTimeMillis()
        val tempoDelay = prefs.autoAnswerDelayMs.toLong().coerceAtLeast(1000L)
        if (now - lastAnswerTimeMs < tempoDelay) return

        triggerAutoAnswer { _, _ -> }
    }

    /**
     * Memindai tampilan layar aplikasi dosen, mendeteksi soal & pilihan A/B/C/D,
     * memecahkan jawaban via [AbcdSolver], lalu melakukan klik otomatis secara SENYAP
     * (tanpa notifikasi/toast samaran yang mencurigakan).
     */
    fun triggerAutoAnswer(onDone: (Boolean, String) -> Unit) {
        val root = rootInActiveWindow ?: run {
            onDone(false, "Tidak dapat mengakses konten layar.")
            return
        }

        val scanResult = scanScreenNodes(root)
        if (scanResult == null) {
            onDone(false, "Tidak ada soal pilihan ganda (A/B/C/D) terdeteksi di layar.")
            return
        }

        val (question, optionNodes) = scanResult

        // Cegah klik berulang pada soal yang sama dalam jeda tempo
        val tempo = prefs.autoAnswerDelayMs.toLong().coerceAtLeast(1000L)
        if (question.questionText == lastQuestionStem && System.currentTimeMillis() - lastAnswerTimeMs < tempo) {
            onDone(false, "Soal ini baru saja dijawab.")
            return
        }

        isAnsweringInProgress = true

        AbcdSolver.solve(question, prefs) { solution ->
            mainHandler.post {
                isAnsweringInProgress = false
                onAnswerResultListener?.invoke(solution)

                if (solution.ok && solution.selectedOption != null) {
                    var targetChar = solution.selectedOption

                    // Jika mode KKM 80 aktif, sesekali pilih opsi realistis lain (~20% kasus) agar skor natural 24/30
                    if (isKkmMode && (answeredCount % 5 == 3)) {
                        val otherOptions = optionNodes.keys.filter { it != targetChar }
                        if (otherOptions.isNotEmpty()) {
                            targetChar = otherOptions.first()
                        }
                    }

                    lastQuestionStem = question.questionText
                    lastAnswerTimeMs = System.currentTimeMillis()
                    answeredCount++
                    onProgressUpdateListener?.invoke(answeredCount, totalQuestionsEstimate)

                    val targetNodeInfo = optionNodes[targetChar]
                    val delay = prefs.autoAnswerDelayMs.toLong().coerceAtLeast(0L)

                    if (targetNodeInfo != null) {
                        mainHandler.postDelayed({
                            clickNode(targetNodeInfo.node) { success ->
                                copyToClipboard(solution)
                                val msg = "Terpilih [${targetChar}]"
                                onDone(success, msg)
                            }
                        }, delay)
                    } else {
                        copyToClipboard(solution)
                        onDone(true, "Kunci [${targetChar}] disalin")
                    }
                } else {
                    onDone(false, solution.error ?: "Gagal menentukan jawaban.")
                }
            }
        }
    }

    private data class ScannedNode(
        val node: AccessibilityNodeInfo,
        val text: String,
        val bounds: Rect
    )

    private fun scanScreenNodes(root: AccessibilityNodeInfo): Pair<AbcdQuestion, Map<Char, ScannedNode>>? {
        val allTextNodes = mutableListOf<ScannedNode>()
        collectTextNodes(root, allTextNodes)

        if (allTextNodes.size < 2) return null

        val optionRegex = Regex("""^[\(\[]?([A-Ea-e])[\)\]\.\:\-]\s*(.*)$""")
        val optionNodes = LinkedHashMap<Char, ScannedNode>()
        val optionsMap = LinkedHashMap<Char, String>()
        val nonOptionTexts = mutableListOf<String>()

        for (item in allTextNodes) {
            val match = optionRegex.find(item.text)
            if (match != null) {
                val optChar = match.groupValues[1].uppercase(Locale.ROOT)[0]
                val optVal = match.groupValues[2].trim().ifEmpty { item.text }
                optionNodes[optChar] = item
                optionsMap[optChar] = optVal
            } else {
                nonOptionTexts.add(item.text)
            }
        }

        // Cek jika huruf opsi (A, B, C, D) dan teks jawabannya berada pada node bersebelahan
        if (optionsMap.size < 2) {
            for (i in 0 until allTextNodes.size - 1) {
                val cur = allTextNodes[i].text.trim()
                if (cur.length in 1..2 && cur.first().isLetter()) {
                    val c = cur.first().uppercaseChar()
                    if (c in 'A'..'E') {
                        val next = allTextNodes[i + 1]
                        if (!optionNodes.containsKey(c)) {
                            optionNodes[c] = next
                            optionsMap[c] = next.text.trim()
                        }
                    }
                }
            }
        }

        if (optionsMap.size >= 2) {
            val stem = nonOptionTexts.filter { it.length > 5 }.joinToString(" ").trim()
                .ifEmpty { "Pertanyaan CBT" }
            return Pair(
                AbcdQuestion(questionText = stem, options = optionsMap),
                optionNodes
            )
        }

        return null
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo?, list: MutableList<ScannedNode>) {
        if (node == null || !node.isVisibleToUser) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val effectiveText = if (!text.isNullOrEmpty()) text else desc

        if (!effectiveText.isNullOrEmpty()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            list.add(ScannedNode(node, effectiveText, rect))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            collectTextNodes(child, list)
        }
    }

    /**
     * Mengeksekusi klik langsung tanpa animasi/notifikasi yang mencurigakan.
     */
    private fun clickNode(node: AccessibilityNodeInfo, onComplete: (Boolean) -> Unit) {
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

        // Gestur fisik senyap pada koordinat layar view
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()

        if (x > 0 && y > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 80)
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
        } else {
            onComplete(false)
        }
    }

    private fun copyToClipboard(solution: AbcdSolutionResult) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = "Kunci: [${solution.selectedOption}] ${solution.optionText}"
        cm.setPrimaryClip(ClipData.newPlainText("Kunci CBT", text))
    }

    /**
     * Tangkap layar senyap (Android 11+) tanpa memunculkan dialog "Bagikan layar".
     */
    fun captureRegionSilent(cropX: Int, cropY: Int, cropW: Int, cropH: Int, onFallback: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onFallback()
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
                                    ScreenOcrProcessor.processCroppedBitmap(applicationContext, cropped)
                                } else {
                                    onFallback()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                onFallback()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            onFallback()
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                onFallback()
            }
        }, 120)
    }
}
