package com.clestial.keyboard.floating

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.clestial.keyboard.Prefs
import com.clestial.keyboard.autoanswer.AbcdSolver
import com.clestial.keyboard.autoanswer.AutoScannerService
import com.clestial.keyboard.math.OfflineMathAssistant
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Pemroses OCR bersama untuk hasil tangkapan kotak seleksi (baik via Accessibility silent screenshot
 * maupun MediaProjection), lalu otomatis membuka kembali pop-up melayang dengan hasil jawaban.
 */
object ScreenOcrProcessor {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun processCroppedBitmap(context: Context, bitmap: Bitmap) {
        val appCtx = context.applicationContext
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text.trim()
                val prefs = Prefs(appCtx)
                val abcdQ = if (prefs.autoAnswerEnabled) AbcdSolver.parseFromText(rawText) else null

                if (abcdQ != null) {
                    mainHandler.post {
                        Toast.makeText(appCtx, "🎯 Soal ABCD terdeteksi! Memecahkan jawaban...", Toast.LENGTH_SHORT).show()
                    }
                    AbcdSolver.solve(abcdQ, prefs) { solution ->
                        val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val summary = if (solution.ok) {
                            "🎯 Kunci: [${solution.selectedOption}] ${solution.optionText}\n" +
                                "Sumber: ${solution.source}\n" +
                                "Penjelasan: ${solution.explanation}"
                        } else {
                            "Error: ${solution.error ?: "Gagal memproses soal."}"
                        }

                        cm.setPrimaryClip(ClipData.newPlainText("Kunci Jawaban", summary))

                        val service = AutoScannerService.instance
                        if (solution.ok && service != null && prefs.autoAnswerMode == Prefs.MODE_AUTO_CLICK) {
                            service.triggerAutoAnswer { _, _ -> }
                        }

                        Toast.makeText(
                            appCtx,
                            if (solution.ok) "🎯 Kunci: [${solution.selectedOption}] ${solution.optionText}" else "Gagal memecahkan soal ABCD",
                            Toast.LENGTH_LONG
                        ).show()

                        FloatingMenuManager.showAutoAnswer(appCtx, summary)
                    }
                } else {
                    val cleanText = rawText.replace("\n", " ").trim()
                    if (cleanText.isNotEmpty()) {
                        // Cek apakah soal matematika bisa langsung diselesaikan offline
                        val offlineAns = OfflineMathAssistant.trySolveMathQuestion(cleanText)
                        if (offlineAns != null) {
                            FloatingMenuManager.showAiResult(appCtx, cleanText, offlineAns)
                        } else {
                            Toast.makeText(appCtx, "Teks terbaca: ${cleanText.take(60)}...", Toast.LENGTH_SHORT).show()
                            FloatingMenuManager.searchGoogle(appCtx, cleanText)
                        }
                    } else {
                        Toast.makeText(appCtx, "Tidak ada teks terbaca di dalam kotak seleksi", Toast.LENGTH_SHORT).show()
                        FloatingMenuManager.show(appCtx)
                    }
                }
            }
            .addOnFailureListener { e ->
                mainHandler.post {
                    Toast.makeText(appCtx, "OCR Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                    FloatingMenuManager.show(appCtx)
                }
            }
    }
}
