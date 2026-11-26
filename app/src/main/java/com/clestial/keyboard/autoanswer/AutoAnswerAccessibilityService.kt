package com.clestial.keyboard.autoanswer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.clestial.keyboard.Prefs
import java.util.Locale

class AutoAnswerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoAnswerAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null

        var onStatusChangedListener: ((Boolean) -> Unit)? = null
        var onAnswerResultListener: ((AbcdSolutionResult) -> Unit)? = null
    }

    private lateinit var prefs: Prefs
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastQuestionStem: String = ""
    private var lastAnswerTimeMs: Long = 0L
    private var isAnsweringInProgress = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        onStatusChangedListener?.invoke(true)
        Toast.makeText(this, "Asisten Auto Jawab ABCD Aktif!", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!prefs.autoAnswerEnabled) return
        if (prefs.autoAnswerMode != Prefs.MODE_AUTO_CLICK) return

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            // Evaluasi otomatis jika jendela aplikasi kuis/ujian berganti soal
            checkAndAutoAnswerDebounced()
        }
    }

    override fun onInterrupt() {
        // Layanan terhenti sementara oleh sistem
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        onStatusChangedListener?.invoke(false)
    }

    private fun checkAndAutoAnswerDebounced() {
        if (isAnsweringInProgress) return
        val now = System.currentTimeMillis()
        if (now - lastAnswerTimeMs < 3500) return

        triggerAutoAnswer { _, _ -> }
    }

    /**
     * Memindai tampilan layar saat ini, mengenali soal & pilihan A/B/C/D,
     * memecahkan jawaban via [AbcdSolver], lalu mengeksekusi klik otomatis atau menyalin kunci.
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

        // Cegah klik berulang pada soal yang sama
        if (question.questionText == lastQuestionStem && System.currentTimeMillis() - lastAnswerTimeMs < 3500) {
            onDone(false, "Soal ini baru saja dijawab.")
            return
        }

        isAnsweringInProgress = true

        AbcdSolver.solve(question, prefs) { solution ->
            mainHandler.post {
                isAnsweringInProgress = false
                onAnswerResultListener?.invoke(solution)

                if (solution.ok && solution.selectedOption != null) {
                    val targetChar = solution.selectedOption
                    lastQuestionStem = question.questionText
                    lastAnswerTimeMs = System.currentTimeMillis()

                    val targetNodeInfo = optionNodes[targetChar]
                    val delay = prefs.autoAnswerDelayMs.toLong().coerceAtLeast(0L)

                    if (prefs.autoAnswerMode == Prefs.MODE_AUTO_CLICK && targetNodeInfo != null) {
                        mainHandler.postDelayed({
                            clickNode(targetNodeInfo.node) { success ->
                                val msg = if (success) {
                                    "🎯 Terjawab otomatis: [${targetChar}] ${solution.optionText}"
                                } else {
                                    "🎯 Kunci: [${targetChar}] (Gagal klik langsung, kunci disalin)"
                                }
                                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                                copyToClipboard(solution)
                                onDone(success, msg)
                            }
                        }, delay)
                    } else {
                        // Mode Kunci Saja
                        copyToClipboard(solution)
                        val msg = "🎯 Kunci Jawaban: [${targetChar}] ${solution.optionText} (Disalin)"
                        Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                        onDone(true, msg)
                    }
                } else {
                    onDone(false, solution.error ?: "Gagal menentukan jawaban yang tepat.")
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

        // Cek jika ada tombol radio / pilihan yang terpisah antara huruf label dan teksnya
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
                .ifEmpty { "Pertanyaan Pilihan Ganda" }
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
     * Mengklik node di layar: mencoba ACTION_CLICK langsung,
     * parent ACTION_CLICK, atau tap gestur layar [dispatchGesture] jika view tidak clickable.
     */
    private fun clickNode(node: AccessibilityNodeInfo, onComplete: (Boolean) -> Unit) {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            onComplete(true)
            return
        }

        // Cek parent clickable
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                onComplete(true)
                return
            }
            parent = parent.parent
        }

        // Fallback: Gestur tap fisik pada koordinat layar view
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()

        if (x > 0 && y > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 100)
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
        val text = "Kunci: [${solution.selectedOption}] ${solution.optionText}\nPenjelasan: ${solution.explanation}"
        cm.setPrimaryClip(ClipData.newPlainText("Kunci Jawaban Soal", text))
    }
}
