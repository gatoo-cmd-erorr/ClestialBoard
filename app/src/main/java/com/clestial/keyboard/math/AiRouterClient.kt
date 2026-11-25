package com.clestial.keyboard.math

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Klien AI untuk terhubung ke proxy tipis 9router (tanpa menyimpan API key di klien Android).
 * Menangani timeout, retry terbatas, rate limit sisi klien, dan validasi ketat output JSON
 * terhadap [MathRegistry] serta aturan validasi tiap modul.
 */
object AiRouterClient {

    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val MAX_RETRIES = 1
    private const val RATE_WINDOW_MS = 60_000L
    private const val MAX_PER_MINUTE = 10

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val requestTimestamps = ArrayList<Long>()

    data class ParsedMathIntent(
        val ok: Boolean,
        val module: MathModule? = null,
        val inputs: Map<String, String> = emptyMap(),
        val previewResult: SolveResult? = null,
        val model: String = "",
        val error: String = ""
    )

    data class TextAiResult(
        val ok: Boolean,
        val text: String = "",
        val model: String = "",
        val error: String = ""
    )

    /** Cek dan catat rate limit lokal di UI thread. */
    @Synchronized
    private fun acquireRateSlot(): Boolean {
        val now = System.currentTimeMillis()
        requestTimestamps.removeAll { now - it >= RATE_WINDOW_MS }
        if (requestTimestamps.size >= MAX_PER_MINUTE) return false
        requestTimestamps.add(now)
        return true
    }

    /**
     * Validasi murni (dapat diuji unit tanpa jaringan):
     * Memastikan JSON memiliki skema {"modul": "...", "input": {...}},
     * modul ada di [MathRegistry], dan seluruh nilai input lolos validasi modul.
     */
    fun validateAiModuleJson(rawJson: String): ParsedMathIntent {
        val obj = extractJsonObject(rawJson)
            ?: return ParsedMathIntent(
                ok = false,
                error = "Respons AI bukan JSON yang valid. Silakan pilih modul dan isi angka secara manual."
            )

        val moduleId = obj.optString("modul", "").trim()
        val module = MathRegistry.find(moduleId)
            ?: return ParsedMathIntent(
                ok = false,
                error = "Modul \"$moduleId\" tidak dikenali di registry. Silakan pilih modul secara manual."
            )

        val inputObj = obj.optJSONObject("input")
            ?: return ParsedMathIntent(
                ok = false,
                error = "Data input dari AI tidak lengkap. Silakan isi angka secara manual."
            )

        val allowedKeys = module.fields.map { it.key }.toSet()
        val cleanInputs = LinkedHashMap<String, String>()
        val keys = inputObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k !in allowedKeys) continue
            val v = inputObj.opt(k) ?: continue
            if (v is JSONObject || v is JSONArray) {
                return ParsedMathIntent(
                    ok = false,
                    error = "Format nilai \"$k\" dari AI tidak valid. Silakan isi manual."
                )
            }
            if (v != JSONObject.NULL) {
                cleanInputs[k] = v.toString().trim()
            }
        }

        // Jalankan validasi & solver kode untuk memastikan angka lolos seluruh aturan modul.
        val dryRun = module.solve(cleanInputs)
        if (!dryRun.ok) {
            return ParsedMathIntent(
                ok = false,
                error = "Tafsiran AI tidak lolos validasi modul (${module.name}): ${dryRun.error} Silakan isi secara manual."
            )
        }

        return ParsedMathIntent(
            ok = true,
            module = module,
            inputs = cleanInputs,
            previewResult = dryRun
        )
    }

    /** Ekstrak objek JSON pertama dari teks (aman terhadap code fence ```json). */
    fun extractJsonObject(raw: String): JSONObject? {
        var s = raw.trim()
        val fenceMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE).find(s)
        if (fenceMatch != null) s = fenceMatch.groupValues[1].trim()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(s.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }

    /** Langkah 1 & 2 alur soal cerita: AI memetakan soal ke JSON modul untuk dikonfirmasi pengguna. */
    fun parseStoryProblem(proxyBaseUrl: String, storyText: String, onDone: (ParsedMathIntent) -> Unit) {
        val cleanText = storyText.trim()
        if (cleanText.isEmpty()) {
            onDone(ParsedMathIntent(false, error = "Masukkan soal cerita terlebih dahulu."))
            return
        }
        if (!acquireRateSlot()) {
            onDone(ParsedMathIntent(false, error = "Batas permintaan tercapai. Tunggu sebentar atau gunakan mode manual."))
            return
        }
        val base = sanitizeProxyUrl(proxyBaseUrl)
        if (base == null) {
            onDone(ParsedMathIntent(false, error = "URL proxy AI tidak valid."))
            return
        }

        executor.execute {
            val reqJson = JSONObject().put("text", cleanText.take(2000)).toString()
            val net = postWithRetry("$base/api/math/parse", reqJson)
            val result = if (!net.ok) {
                ParsedMathIntent(false, error = net.error)
            } else {
                val validated = validateAiModuleJson(net.text)
                validated.copy(model = extractModel(net.text))
            }
            mainHandler.post { onDone(result) }
        }
    }

    /** Langkah 5 alur soal cerita: AI menjelaskan langkah & jawaban yang sudah dihitung solver. */
    fun explainSolution(
        proxyBaseUrl: String,
        moduleId: String,
        storyText: String,
        solveResult: SolveResult,
        onDone: (TextAiResult) -> Unit
    ) {
        if (!acquireRateSlot()) {
            onDone(TextAiResult(false, error = "Batas permintaan tercapai."))
            return
        }
        val base = sanitizeProxyUrl(proxyBaseUrl)
        if (base == null) {
            onDone(TextAiResult(false, error = "URL proxy AI tidak valid."))
            return
        }

        executor.execute {
            val reqJson = JSONObject()
                .put("modul", moduleId)
                .put("soal", storyText.take(2000))
                .put("steps", JSONArray(solveResult.steps))
                .put("answer", solveResult.answer)
                .put("check", solveResult.check)
                .toString()
            val net = postWithRetry("$base/api/math/explain", reqJson)
            val out = if (!net.ok) {
                net
            } else {
                try {
                    val obj = JSONObject(net.text)
                    TextAiResult(
                        ok = obj.optBoolean("ok", false),
                        text = obj.optString("explanation", ""),
                        model = obj.optString("model", ""),
                        error = obj.optString("error", "Penjelasan AI kosong.")
                    )
                } catch (e: Exception) {
                    TextAiResult(false, error = "Format respons penjelasan tidak valid.")
                }
            }
            mainHandler.post { onDone(out) }
        }
    }

    /** Tanya AI umum dari keyboard / layar belajar melalui proxy 9router. */
    fun askQuestion(proxyBaseUrl: String, question: String, onDone: (TextAiResult) -> Unit) {
        val clean = question.trim()
        if (clean.isEmpty()) {
            onDone(TextAiResult(false, error = "Ketik pertanyaan terlebih dahulu."))
            return
        }
        if (!acquireRateSlot()) {
            onDone(TextAiResult(false, error = "Batas permintaan tercapai. Tunggu sebentar."))
            return
        }
        val base = sanitizeProxyUrl(proxyBaseUrl)
        if (base == null) {
            onDone(TextAiResult(false, error = "URL proxy AI tidak valid."))
            return
        }

        executor.execute {
            val reqJson = JSONObject().put("question", clean.take(2000)).toString()
            val net = postWithRetry("$base/api/ask", reqJson)
            val out = if (!net.ok) {
                net
            } else {
                try {
                    val obj = JSONObject(net.text)
                    TextAiResult(
                        ok = obj.optBoolean("ok", false),
                        text = obj.optString("reply", ""),
                        model = obj.optString("model", ""),
                        error = obj.optString("error", "Respons AI kosong.")
                    )
                } catch (e: Exception) {
                    TextAiResult(false, error = "Format respons AI tidak valid.")
                }
            }
            mainHandler.post { onDone(out) }
        }
    }

    private fun extractModel(rawJson: String): String = try {
        JSONObject(rawJson).optString("model", "")
    } catch (e: Exception) {
        ""
    }

    private fun sanitizeProxyUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else null
    }

    private fun postWithRetry(endpoint: String, jsonBody: String): TextAiResult {
        var lastError = "Server AI tidak terjangkau. Kalkulator manual tetap dapat digunakan."
        for (attempt in 0..MAX_RETRIES) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respText = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() } ?: ""
                if (code in 200..299) {
                    return TextAiResult(ok = true, text = respText)
                }
                val errMsg = try {
                    JSONObject(respText).optString("error", "Server AI mengembalikan error ($code).")
                } catch (e: Exception) {
                    "Server AI mengembalikan error ($code)."
                }
                lastError = errMsg
                if (code != 429 && code < 500) {
                    return TextAiResult(ok = false, error = lastError)
                }
            } catch (e: Exception) {
                lastError = "Proxy AI tidak aktif atau timeout. Kalkulator manual tetap berjalan penuh."
            } finally {
                conn?.disconnect()
            }
        }
        return TextAiResult(ok = false, error = lastError)
    }
}
