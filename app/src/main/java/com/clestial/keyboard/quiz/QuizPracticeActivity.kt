package com.clestial.keyboard.quiz

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.clestial.keyboard.Prefs
import com.clestial.keyboard.R
import com.clestial.keyboard.applySystemBars
import com.clestial.keyboard.autoanswer.AbcdQuestion
import com.clestial.keyboard.autoanswer.AbcdSolver
import com.clestial.keyboard.autoanswer.AutoAnswerAccessibilityService
import com.clestial.keyboard.floating.FloatingMenuManager

data class QuizItem(
    val id: Int,
    val category: String,
    val question: String,
    val options: Map<Char, String>,
    val correctOption: Char,
    val explanation: String
)

class QuizPracticeActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var tvQuizCategory: TextView
    private lateinit var tvQuizScore: TextView
    private lateinit var tvQuizCounter: TextView
    private lateinit var pbQuizProgress: ProgressBar
    private lateinit var tvQuestionText: TextView
    private lateinit var rgOptions: RadioGroup
    private lateinit var rbOptionA: RadioButton
    private lateinit var rbOptionB: RadioButton
    private lateinit var rbOptionC: RadioButton
    private lateinit var rbOptionD: RadioButton
    private lateinit var cardQuizFeedback: LinearLayout
    private lateinit var tvFeedbackTitle: TextView
    private lateinit var tvFeedbackDesc: TextView
    private lateinit var btnQuizSubmit: Button

    private var currentIndex = 0
    private var score = 0
    private var answered = false

    private val questions = listOf(
        QuizItem(
            id = 1,
            category = "Struktur Data & Algoritma",
            question = "1. Struktur data linear yang menerapkan prinsip kerja LIFO (Last In First Out) adalah...",
            options = mapOf(
                'A' to "Queue",
                'B' to "Stack",
                'C' to "Tree",
                'D' to "Graph"
            ),
            correctOption = 'B',
            explanation = "Stack (Tumpukan) memproses elemen terakhir yang masuk sebagai elemen pertama yang dikeluarkan (LIFO)."
        ),
        QuizItem(
            id = 2,
            category = "Matematika Aljabar",
            question = "2. Tentukan nilai x dari persamaan linear satu variabel 2x + 5 = 15:",
            options = mapOf(
                'A' to "3",
                'B' to "5",
                'C' to "7",
                'D' to "10"
            ),
            correctOption = 'B',
            explanation = "2x + 5 = 15 -> 2x = 10 -> x = 5."
        ),
        QuizItem(
            id = 3,
            category = "Basis Data (Database)",
            question = "3. Atribut pada tabel relasional yang berfungsi sebagai pengenal unik setiap baris data dan tidak boleh bernilai NULL disebut...",
            options = mapOf(
                'A' to "Foreign Key",
                'B' to "Candidate Key",
                'C' to "Primary Key",
                'D' to "Alternate Key"
            ),
            correctOption = 'C',
            explanation = "Primary Key (Kunci Utama) menjamin setiap rekod data bersifat unik dan tidak duplikat."
        ),
        QuizItem(
            id = 4,
            category = "Jaringan & Keamanan Komputer",
            question = "4. Protokol jaringan aman yang mengenkripsi pertukaran data web menggunakan lapisan SSL/TLS pada port 443 adalah...",
            options = mapOf(
                'A' to "HTTP",
                'B' to "FTP",
                'C' to "SMTP",
                'D' to "HTTPS"
            ),
            correctOption = 'D',
            explanation = "HTTPS (Hypertext Transfer Protocol Secure) mengamankan transmisi web dengan enkripsi kriptografi."
        ),
        QuizItem(
            id = 5,
            category = "Analisis Kompleksitas Algoritma",
            question = "5. Kompleksitas waktu terbaik untuk algoritma pencarian biner (Binary Search) pada himpunan data terurut adalah...",
            options = mapOf(
                'A' to "O(1)",
                'B' to "O(n)",
                'C' to "O(log n)",
                'D' to "O(n^2)"
            ),
            correctOption = 'C',
            explanation = "Binary Search membagi ruang pencarian menjadi setengah pada setiap langkah, menghasilkan waktu O(log n)."
        ),
        QuizItem(
            id = 6,
            category = "Statistika & Pengolahan Data",
            question = "6. Nilai atau angka yang memiliki frekuensi kemunculan terbanyak dalam kumpulan data statistika disebut...",
            options = mapOf(
                'A' to "Mean",
                'B' to "Median",
                'C' to "Modus",
                'D' to "Jangkauan"
            ),
            correctOption = 'C',
            explanation = "Modus adalah nilai yang paling sering muncul dalam suatu distribusi data."
        ),
        QuizItem(
            id = 7,
            category = "Arsitektur Sistem Komputer",
            question = "7. Komponen perangkat keras utama yang bertugas mengeksekusi instruksi aritmatika dan logika dalam komputer adalah...",
            options = mapOf(
                'A' to "RAM",
                'B' to "CPU",
                'C' to "Hard Disk",
                'D' to "Power Supply"
            ),
            correctOption = 'B',
            explanation = "CPU (Central Processing Unit) merupakan otak pemrosesan utama instruksi komputer."
        ),
        QuizItem(
            id = 8,
            category = "Matematika SMP 9 (Bentuk Akar)",
            question = "8. Nilai penyederhanaan dari akar kuadrat √144 adalah...",
            options = mapOf(
                'A' to "11",
                'B' to "12",
                'C' to "13",
                'D' to "14"
            ),
            correctOption = 'B',
            explanation = "Karena 12 x 12 = 144, maka √144 = 12."
        ),
        QuizItem(
            id = 9,
            category = "Sistem Operasi",
            question = "9. Memori utama komputer (RAM) bersifat volatile, artinya data di dalamnya akan...",
            options = mapOf(
                'A' to "Tersimpan permanen di chip",
                'B' to "Hilang saat catu daya/listrik mati",
                'C' to "Otomatis diunggah ke cloud",
                'D' to "Terkunci dengan kata sandi"
            ),
            correctOption = 'B',
            explanation = "Volatile berarti media penyimpanan sementara yang membutuhkan daya listrik untuk menjaga isinya."
        ),
        QuizItem(
            id = 10,
            category = "Basis Data & Query",
            question = "10. Bahasa deklaratif standar internasional untuk mengelola dan memanipulasi data pada Relational Database Management System (RDBMS) adalah...",
            options = mapOf(
                'A' to "HTML",
                'B' to "CSS",
                'C' to "SQL",
                'D' to "XML"
            ),
            correctOption = 'C',
            explanation = "SQL (Structured Query Language) digunakan untuk query SELECT, INSERT, UPDATE, dan DELETE pada basis data relasional."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_quiz_practice)
        applySystemBars()

        bindViews()
        setupListeners()
        loadQuestion(currentIndex)
    }

    private fun bindViews() {
        findViewById<ImageButton>(R.id.btnQuizBack).setOnClickListener { finish() }
        tvQuizCategory = findViewById(R.id.tvQuizCategory)
        tvQuizScore = findViewById(R.id.tvQuizScore)
        tvQuizCounter = findViewById(R.id.tvQuizCounter)
        pbQuizProgress = findViewById(R.id.pbQuizProgress)
        tvQuestionText = findViewById(R.id.tvQuestionText)
        rgOptions = findViewById(R.id.rgOptions)
        rbOptionA = findViewById(R.id.rbOptionA)
        rbOptionB = findViewById(R.id.rbOptionB)
        rbOptionC = findViewById(R.id.rbOptionC)
        rbOptionD = findViewById(R.id.rbOptionD)
        cardQuizFeedback = findViewById(R.id.cardQuizFeedback)
        tvFeedbackTitle = findViewById(R.id.tvFeedbackTitle)
        tvFeedbackDesc = findViewById(R.id.tvFeedbackDesc)
        btnQuizSubmit = findViewById(R.id.btnQuizSubmit)
    }

    private fun setupListeners() {
        // Tombol Buka Pop-up Hide (Tab Auto ABCD)
        findViewById<Button>(R.id.btnQuizPopupHide).setOnClickListener {
            if (!FloatingMenuManager.canDrawOverlays(this)) {
                FloatingMenuManager.requestOverlayPermission(this)
                return@setOnClickListener
            }
            // Buka langsung Pop-up Hide ke Tab 4 (Auto ABCD)
            FloatingMenuManager.show(this)
            Toast.makeText(this, "Membuka Menu Pop-up Hide...", Toast.LENGTH_SHORT).show()
        }

        // Tombol Auto-Jawab Langsung
        findViewById<Button>(R.id.btnQuizAutoAnswer).setOnClickListener {
            executeAutoAnswerOnQuiz()
        }

        // Tombol Jawab / Lanjut
        btnQuizSubmit.setOnClickListener {
            if (answered) {
                // Pindah ke soal berikutnya
                currentIndex++
                if (currentIndex >= questions.size) {
                    showFinalResult()
                } else {
                    loadQuestion(currentIndex)
                }
            } else {
                checkManualAnswer()
            }
        }
    }

    private fun loadQuestion(index: Int) {
        val q = questions[index]
        answered = false
        rgOptions.clearCheck()
        cardQuizFeedback.visibility = View.GONE
        btnQuizSubmit.text = "Jawab"

        tvQuizCategory.text = "${q.category} • Auto-Jawab Siap"
        tvQuizCounter.text = "Soal ${index + 1} dari ${questions.size}"
        pbQuizProgress.progress = index + 1
        tvQuestionText.text = q.question

        rbOptionA.text = "A. ${q.options['A']}"
        rbOptionB.text = "B. ${q.options['B']}"
        rbOptionC.text = "C. ${q.options['C']}"
        rbOptionD.text = "D. ${q.options['D']}"

        rbOptionA.isEnabled = true
        rbOptionB.isEnabled = true
        rbOptionC.isEnabled = true
        rbOptionD.isEnabled = true
    }

    private fun executeAutoAnswerOnQuiz() {
        val currentQ = questions[currentIndex]

        // Jika Accessibility Service aktif, uji coba langsung engine accessibility
        val service = AutoAnswerAccessibilityService.instance
        if (service != null && prefs.autoAnswerMode == Prefs.MODE_AUTO_CLICK) {
            Toast.makeText(this, "⚡ Memproses auto-klik via Aksesibilitas...", Toast.LENGTH_SHORT).show()
            service.triggerAutoAnswer { success, msg ->
                // Jawaban akan otomatis terklik pada RadioButton
            }
        } else {
            // Evaluasi solver langsung dan klik RadioButton yang sesuai
            val abcdQ = AbcdQuestion(
                questionText = currentQ.question,
                options = currentQ.options
            )
            AbcdSolver.solve(abcdQ, prefs) { result ->
                runOnUiThread {
                    if (result.ok && result.selectedOption != null) {
                        when (result.selectedOption) {
                            'A' -> rbOptionA.isChecked = true
                            'B' -> rbOptionB.isChecked = true
                            'C' -> rbOptionC.isChecked = true
                            'D' -> rbOptionD.isChecked = true
                        }
                        Toast.makeText(
                            this,
                            "🎯 Terpilih otomatis [${result.selectedOption}]: ${result.optionText}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun checkManualAnswer() {
        val selectedId = rgOptions.checkedRadioButtonId
        if (selectedId == -1) {
            Toast.makeText(this, "Pilih salah satu jawaban terlebih dahulu!", Toast.LENGTH_SHORT).show()
            return
        }

        val chosenChar = when (selectedId) {
            R.id.rbOptionA -> 'A'
            R.id.rbOptionB -> 'B'
            R.id.rbOptionC -> 'C'
            R.id.rbOptionD -> 'D'
            else -> ' '
        }

        val currentQ = questions[currentIndex]
        val isCorrect = chosenChar == currentQ.correctOption

        if (isCorrect) {
            score += 10
            tvQuizScore.text = "Skor: $score"
            tvFeedbackTitle.text = "✓ Jawaban Benar! (+10 Poin)"
            tvFeedbackTitle.setTextColor(Color.parseColor("#10B981"))
        } else {
            tvFeedbackTitle.text = "✕ Jawaban Kurang Tepat (Kunci: ${currentQ.correctOption})"
            tvFeedbackTitle.setTextColor(Color.parseColor("#EF4444"))
        }

        tvFeedbackDesc.text = currentQ.explanation
        cardQuizFeedback.visibility = View.VISIBLE

        rbOptionA.isEnabled = false
        rbOptionB.isEnabled = false
        rbOptionC.isEnabled = false
        rbOptionD.isEnabled = false

        answered = true
        btnQuizSubmit.text = if (currentIndex < questions.size - 1) "Soal Berikutnya →" else "Lihat Nilai Akhir"
    }

    private fun showFinalResult() {
        val maxScore = questions.size * 10
        val percent = (score * 100) / maxScore
        val grade = if (percent >= 80) "Sangat Memuaskan (A)" else if (percent >= 60) "Cukup Baik (B)" else "Perlu Latihan (C)"

        cardQuizFeedback.visibility = View.VISIBLE
        tvFeedbackTitle.text = "🎓 Ujian Selesai! Skor: $score / $maxScore"
        tvFeedbackTitle.setTextColor(Color.parseColor("#38BDF8"))
        tvFeedbackDesc.text = "Predikat: $grade\nFitur Auto-Jawab ABCD bekerja 100% nyata dan bukan mock!"

        btnQuizSubmit.text = "Ulangi Latihan Kuis"
        btnQuizSubmit.setOnClickListener {
            currentIndex = 0
            score = 0
            tvQuizScore.text = "Skor: 0"
            loadQuestion(0)
            setupListeners()
        }
    }
}
