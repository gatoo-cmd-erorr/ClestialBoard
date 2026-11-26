package com.clestial.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.clestial.keyboard.math.AiRouterClient
import com.clestial.keyboard.math.FieldType
import com.clestial.keyboard.math.FormulaCards
import com.clestial.keyboard.math.LineSketchView
import com.clestial.keyboard.math.MathModule
import com.clestial.keyboard.math.MathRegistry
import com.clestial.keyboard.math.SolveResult

/**
 * Layar Kalkulator Matematika SMP Kelas 9 berbasis modul eksak,
 * terintegrasi dengan AI 9router (tafsir soal cerita -> konfirmasi -> solver -> penjelasan).
 */
class MathModulesActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var moduleMenuContainer: LinearLayout
    private lateinit var tvActiveTitle: TextView
    private lateinit var tvActiveDesc: TextView
    private lateinit var fieldsContainer: LinearLayout
    private lateinit var resultCard: View
    private lateinit var tvResultHeader: TextView
    private lateinit var tvResultAnswer: TextView
    private lateinit var tvResultSteps: TextView
    private lateinit var tvResultCheck: TextView
    private lateinit var btnCopyResult: TextView
    private lateinit var btnExplainAi: TextView
    private lateinit var tvAiExplain: TextView

    private lateinit var etStory: EditText
    private lateinit var btnParseStory: TextView
    private lateinit var tvStoryStatus: TextView
    private lateinit var storyConfirmBox: View
    private lateinit var tvStoryPreview: TextView
    private lateinit var btnConfirmStory: TextView
    private lateinit var btnEditStory: TextView
    private lateinit var etProxyUrl: EditText
    private lateinit var lineSketchView: LineSketchView
    private lateinit var btnToggleAllFormulas: TextView
    private lateinit var formulaListContainer: LinearLayout

    private var activeModule: MathModule = MathRegistry.modules.first()
    private val moduleRowViews = HashMap<String, TextView>()
    private val inputGetters = LinkedHashMap<String, () -> String>()
    private val inputSetters = LinkedHashMap<String, (String) -> Unit>()
    private var lastSolveResult: SolveResult? = null
    private var pendingAiIntent: AiRouterClient.ParsedMathIntent? = null
    private var showAllFormulas = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_math_modules)
        applySystemBars()

        bindViews()
        buildModuleMenu()
        selectModule(activeModule)
        setupListeners()
    }

    private fun bindViews() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        moduleMenuContainer = findViewById(R.id.moduleMenuContainer)
        tvActiveTitle = findViewById(R.id.tvActiveModuleTitle)
        tvActiveDesc = findViewById(R.id.tvActiveModuleDesc)
        fieldsContainer = findViewById(R.id.fieldsContainer)
        resultCard = findViewById(R.id.resultCard)
        tvResultHeader = findViewById(R.id.tvResultHeader)
        tvResultAnswer = findViewById(R.id.tvResultAnswer)
        tvResultSteps = findViewById(R.id.tvResultSteps)
        tvResultCheck = findViewById(R.id.tvResultCheck)
        btnCopyResult = findViewById(R.id.btnCopyResult)
        btnExplainAi = findViewById(R.id.btnExplainAi)
        tvAiExplain = findViewById(R.id.tvAiExplain)
        lineSketchView = findViewById(R.id.lineSketchView)
        btnToggleAllFormulas = findViewById(R.id.btnToggleAllFormulas)
        formulaListContainer = findViewById(R.id.formulaListContainer)

        etStory = findViewById(R.id.etStory)
        btnParseStory = findViewById(R.id.btnParseStory)
        tvStoryStatus = findViewById(R.id.tvStoryStatus)
        storyConfirmBox = findViewById(R.id.storyConfirmBox)
        tvStoryPreview = findViewById(R.id.tvStoryPreview)
        btnConfirmStory = findViewById(R.id.btnConfirmStory)
        btnEditStory = findViewById(R.id.btnEditStory)

        etProxyUrl = findViewById(R.id.etProxyUrl)
        etProxyUrl.setText(prefs.aiProxyUrl)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnSolve).setOnClickListener { runSolver() }

        btnCopyResult.setOnClickListener {
            val res = lastSolveResult ?: return@setOnClickListener
            if (!res.ok) return@setOnClickListener
            val text = buildString {
                appendLine(activeModule.name)
                appendLine("Jawaban: ${res.answer}")
                appendLine("Langkah:")
                res.steps.forEachIndexed { idx, s -> appendLine("${idx + 1}. $s") }
                appendLine("Cek: ${res.check}")
            }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("ClestialMath", text))
            Toast.makeText(this, "Hasil disalin ke papan klip", Toast.LENGTH_SHORT).show()
        }

        btnParseStory.setOnClickListener {
            val story = etStory.text.toString().trim()
            if (story.isEmpty()) {
                Toast.makeText(this, "Tulis soal cerita terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvStoryStatus.visibility = View.VISIBLE
            tvStoryStatus.text = getString(R.string.ai_busy)
            storyConfirmBox.visibility = View.GONE
            btnParseStory.isEnabled = false
            AiRouterClient.parseStoryProblem(prefs, story) { parsed ->
                btnParseStory.isEnabled = true
                if (!parsed.ok || parsed.module == null) {
                    tvStoryStatus.text = parsed.error
                    pendingAiIntent = null
                    return@parseStoryProblem
                }
                pendingAiIntent = parsed
                tvStoryStatus.visibility = View.GONE
                val paramsText = parsed.inputs.entries.joinToString(", ") { "${it.key}=${it.value}" }
                tvStoryPreview.text = "Tafsiran AI (${parsed.module.name}):\n$paramsText"
                storyConfirmBox.visibility = View.VISIBLE
            }
        }

        btnConfirmStory.setOnClickListener {
            val intent = pendingAiIntent ?: return@setOnClickListener
            val mod = intent.module ?: return@setOnClickListener
            selectModule(mod, prefill = intent.inputs)
            runSolver()
        }

        btnEditStory.setOnClickListener {
            val intent = pendingAiIntent ?: return@setOnClickListener
            val mod = intent.module ?: return@setOnClickListener
            selectModule(mod, prefill = intent.inputs)
            storyConfirmBox.visibility = View.GONE
        }

        btnExplainAi.setOnClickListener {
            val res = lastSolveResult ?: return@setOnClickListener
            if (!res.ok) return@setOnClickListener
            tvAiExplain.visibility = View.VISIBLE
            tvAiExplain.text = getString(R.string.ai_busy)
            btnExplainAi.isEnabled = false
            AiRouterClient.explainSolution(
                prefs,
                activeModule.id,
                etStory.text.toString(),
                res
            ) { ai ->
                btnExplainAi.isEnabled = true
                tvAiExplain.text = if (ai.ok) ai.text else ai.error
            }
        }

        findViewById<View>(R.id.btnSaveProxy).setOnClickListener {
            prefs.aiProxyUrl = etProxyUrl.text.toString()
            Toast.makeText(this, "URL Proxy disimpan: ${prefs.aiProxyUrl}", Toast.LENGTH_SHORT).show()
        }

        btnToggleAllFormulas.setOnClickListener {
            showAllFormulas = !showAllFormulas
            btnToggleAllFormulas.setText(
                if (showAllFormulas) R.string.math_formula_show_active else R.string.math_formula_show_all
            )
            renderFormulas()
        }
    }

    private fun buildModuleMenu() {
        moduleMenuContainer.removeAllViews()
        moduleRowViews.clear()
        for ((group, list) in MathRegistry.byGroup()) {
            val hdr = TextView(this).apply {
                text = group.title
                setTextColor(ContextCompat.getColor(context, R.color.muted))
                textSize = 13f
                setPadding(dp(4), dp(10), dp(4), dp(6))
            }
            moduleMenuContainer.addView(hdr)

            for (mod in list) {
                val tile = TextView(this).apply {
                    text = mod.name
                    setTextColor(ContextCompat.getColor(context, R.color.fg))
                    textSize = 14f
                    setPadding(dp(16), dp(11), dp(16), dp(14))
                    setBackgroundResource(R.drawable.bg_opt)
                    setOnClickListener { selectModule(mod) }
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(8)
                moduleMenuContainer.addView(tile, lp)
                moduleRowViews[mod.id] = tile
            }
        }
    }

    private fun selectModule(module: MathModule, prefill: Map<String, String>? = null) {
        activeModule = module
        for ((id, view) in moduleRowViews) {
            val pl = view.paddingLeft
            val pt = view.paddingTop
            val pr = view.paddingRight
            val pb = view.paddingBottom
            view.setBackgroundResource(if (id == module.id) R.drawable.bg_opt_selected else R.drawable.bg_opt)
            view.setPadding(pl, pt, pr, pb)
        }

        tvActiveTitle.text = module.name
        tvActiveDesc.text = module.description
        fieldsContainer.removeAllViews()
        inputGetters.clear()
        inputSetters.clear()
        resultCard.visibility = View.GONE
        tvAiExplain.visibility = View.GONE
        lineSketchView.setPlot(null)
        renderFormulas()

        for (spec in module.fields) {
            val label = TextView(this).apply {
                text = spec.label
                setTextColor(ContextCompat.getColor(context, R.color.muted))
                textSize = 12f
                setPadding(0, dp(8), 0, dp(4))
            }
            fieldsContainer.addView(label)

            if (spec.type == FieldType.CHOICE && spec.choices.isNotEmpty()) {
                var selected = prefill?.get(spec.key)?.takeIf { it in spec.choices }
                    ?: spec.defaultValue.ifEmpty { spec.choices.first() }
                val choiceRow = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val buttons = ArrayList<TextView>()
                fun renderChoices() {
                    for (btn in buttons) {
                        val isSel = btn.tag == selected
                        val pl = btn.paddingLeft
                        val pt = btn.paddingTop
                        val pr = btn.paddingRight
                        val pb = btn.paddingBottom
                        btn.setBackgroundResource(if (isSel) R.drawable.bg_opt_selected else R.drawable.bg_opt)
                        btn.setPadding(pl, pt, pr, pb)
                    }
                }
                for (c in spec.choices) {
                    val btn = TextView(this).apply {
                        text = c
                        tag = c
                        textSize = 13f
                        setTextColor(ContextCompat.getColor(context, R.color.fg))
                        setPadding(dp(12), dp(8), dp(12), dp(11))
                        setOnClickListener {
                            selected = c
                            renderChoices()
                        }
                    }
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.bottomMargin = dp(4)
                    choiceRow.addView(btn, lp)
                    buttons.add(btn)
                }
                renderChoices()
                fieldsContainer.addView(choiceRow)
                inputGetters[spec.key] = { selected }
                inputSetters[spec.key] = { v -> if (v in spec.choices) { selected = v; renderChoices() } }
            } else {
                val edit = EditText(this).apply {
                    setBackgroundResource(R.drawable.bg_input)
                    hint = spec.hint
                    setText(prefill?.get(spec.key) ?: spec.defaultValue)
                    setTextColor(ContextCompat.getColor(context, R.color.fg))
                    setHintTextColor(ContextCompat.getColor(context, R.color.muted))
                    textSize = 14f
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                fieldsContainer.addView(edit, lp)
                inputGetters[spec.key] = { edit.text.toString() }
                inputSetters[spec.key] = { v -> edit.setText(v) }
            }
        }
    }

    private fun runSolver() {
        val values = LinkedHashMap<String, String>()
        for ((k, getter) in inputGetters) values[k] = getter()
        val res = activeModule.solve(values)
        lastSolveResult = res
        resultCard.visibility = View.VISIBLE
        tvAiExplain.visibility = View.GONE

        if (!res.ok) {
            lineSketchView.setPlot(null)
            tvResultHeader.text = "Input Tidak Valid"
            tvResultAnswer.text = res.error
            tvResultSteps.visibility = View.GONE
            tvResultCheck.visibility = View.GONE
            btnCopyResult.visibility = View.GONE
            btnExplainAi.visibility = View.GONE
            return
        }

        lineSketchView.setPlot(res.plotSpec)
        tvResultHeader.text = "Hasil Penyelesaian"
        tvResultAnswer.text = res.answer
        tvResultSteps.visibility = View.VISIBLE
        tvResultSteps.text = res.steps.mapIndexed { idx, s -> "${idx + 1}. $s" }.joinToString("\n")
        tvResultCheck.visibility = View.VISIBLE
        tvResultCheck.text = "Cek: ${res.check}"
        btnCopyResult.visibility = View.VISIBLE
        btnExplainAi.visibility = View.VISIBLE
    }

    private fun renderFormulas() {
        formulaListContainer.removeAllViews()
        val list = if (showAllFormulas) FormulaCards.all() else FormulaCards.forModule(activeModule.id)
        for (card in list) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_input)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val titleView = TextView(this).apply {
                text = card.title
                setTextColor(ContextCompat.getColor(context, R.color.fg))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val formulaView = TextView(this).apply {
                text = card.formula
                setTextColor(ContextCompat.getColor(context, R.color.fg))
                textSize = 12f
                setPadding(0, dp(4), 0, dp(4))
            }
            val notesView = TextView(this).apply {
                text = card.notes
                setTextColor(ContextCompat.getColor(context, R.color.muted))
                textSize = 11.5f
            }
            box.addView(titleView)
            box.addView(formulaView)
            box.addView(notesView)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            formulaListContainer.addView(box, lp)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
