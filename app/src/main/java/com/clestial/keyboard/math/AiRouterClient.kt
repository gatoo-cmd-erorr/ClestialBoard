package com.clestial.keyboard.math

import android.os.Handler
import android.os.Looper
import com.clestial.keyboard.Prefs
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
     * Validasi murni (dapat diuji unit tanpa jaringan & tanpa org.json Android):
     * Memastikan JSON memiliki skema {"modul": "...", "input": {...}},
     * modul ada di [MathRegistry], dan seluruh nilai input lolos validasi modul.
     */
    fun validateAiModuleJson(rawJson: String): ParsedMathIntent {
        val obj = extractJsonMap(rawJson)
            ?: return ParsedMathIntent(
                ok = false,
                error = "Respons AI bukan JSON yang valid. Silakan pilih modul dan isi angka secara manual."
            )

        val moduleId = (obj["modul"] as? String)?.trim().orEmpty()
        val module = MathRegistry.find(moduleId)
            ?: return ParsedMathIntent(
                ok = false,
                error = "Modul \"$moduleId\" tidak dikenali di registry. Silakan pilih modul secara manual."
            )

        @Suppress("UNCHECKED_CAST")
        val inputObj = obj["input"] as? Map<String, Any?>
            ?: return ParsedMathIntent(
                ok = false,
                error = "Data input dari AI tidak lengkap. Silakan isi angka secara manual."
            )

        val allowedKeys = module.fields.map { it.key }.toSet()
        val cleanInputs = LinkedHashMap<String, String>()
        for ((k, v) in inputObj) {
            if (k !in allowedKeys) continue
            if (v is Map<*, *> || v is List<*>) {
                return ParsedMathIntent(
                    ok = false,
                    error = "Format nilai \"$k\" dari AI tidak valid. Silakan isi manual."
                )
            }
            if (v != null) {
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

    /** Parser JSON murni Kotlin agar unit test JVM bebas dari stub org.json Android. */
    private fun extractJsonMap(raw: String): Map<String, Any?>? {
        var s = raw.trim()
        val fenceMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE).find(s)
        if (fenceMatch != null) s = fenceMatch.groupValues[1].trim()
        var pos = s.indexOf('{')
        while (pos >= 0) {
            val parsed = tryParseJsonObjectAt(s, pos)
            if (parsed != null) return parsed
            pos = s.indexOf('{', pos + 1)
        }
        return null
    }

    private fun tryParseJsonObjectAt(src: String, startIndex: Int): Map<String, Any?>? {
        val p = JsonReader(src, startIndex)
        return try {
            p.readObject()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private class JsonReader(private val s: String, start: Int = 0) {
        private var i = start

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun expect(ch: Char) {
            skipWs()
            if (i >= s.length || s[i] != ch) throw IllegalArgumentException("Expected $ch")
            i++
        }

        private fun readValue(): Any? {
            skipWs()
            if (i >= s.length) throw IllegalArgumentException("Unexpected EOF")
            return when (val ch = s[i]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> { expectLiteral("true"); true }
                'f' -> { expectLiteral("false"); false }
                'n' -> { expectLiteral("null"); null }
                '-', in '0'..'9' -> readNumberLexeme()
                else -> throw IllegalArgumentException("Unexpected char $ch")
            }
        }

        private fun expectLiteral(lit: String) {
            if (!s.startsWith(lit, i)) throw IllegalArgumentException("Expected $lit")
            i += lit.length
        }

        fun readObject(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                skipWs()
                if (i >= s.length || s[i] != '"') throw IllegalArgumentException("Expected key")
                val key = readString()
                expect(':')
                out[key] = readValue()
                skipWs()
                if (i >= s.length) throw IllegalArgumentException("Unterminated object")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw IllegalArgumentException("Bad object separator")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                out.add(readValue())
                skipWs()
                if (i >= s.length) throw IllegalArgumentException("Unterminated array")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw IllegalArgumentException("Bad array separator")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    if (i >= s.length) throw IllegalArgumentException("Bad escape")
                    when (val esc = s[i++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) throw IllegalArgumentException("Bad unicode")
                            val code = s.substring(i, i + 4).toIntOrNull(16)
                                ?: throw IllegalArgumentException("Bad hex")
                            sb.append(code.toChar())
                            i += 4
                        }
                        else -> throw IllegalArgumentException("Bad escape $esc")
                    }
                } else if (c.code < 0x20) {
                    throw IllegalArgumentException("Control char in string")
                } else {
                    sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun readNumberLexeme(): String {
            val start = i
            if (s[i] == '-') i++
            if (i >= s.length || s[i] !in '0'..'9') throw IllegalArgumentException("Bad number")
            if (s[i] == '0') {
                i++
            } else {
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (i < s.length && s[i] == '.') {
                i++
                if (i >= s.length || s[i] !in '0'..'9') throw IllegalArgumentException("Bad fraction")
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (i >= s.length || s[i] !in '0'..'9') throw IllegalArgumentException("Bad exponent")
                while (i < s.length && s[i] in '0'..'9') i++
            }
            return s.substring(start, i)
        }
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

    /** Tanya AI dengan preferensi pengguna (prioritas: ChatGPT akun/key, fallback: 9router). */
    fun askQuestion(prefs: Prefs, question: String, onDone: (TextAiResult) -> Unit) {
        val apiKey = prefs.chatGptApiKey.trim()
        if (apiKey.isNotEmpty()) {
            askChatGPT(apiKey, prefs.chatGptEndpoint, prefs.chatGptModel, question, onDone)
        } else {
            val base = sanitizeProxyUrl(prefs.aiProxyUrl)
            if (base != null && !base.contains("10.0.2.2")) {
                askQuestion(base, question, onDone)
            } else {
                onDone(TextAiResult(false, error = "Kunci API ChatGPT belum diisi. Masukkan OpenAI API Key (sk-...) melalui tombol Akun/Key di atas."))
            }
        }
    }

    /** Tafsir soal cerita dengan preferensi pengguna (prioritas: ChatGPT akun/key, fallback: 9router). */
    fun parseStoryProblem(prefs: Prefs, storyText: String, onDone: (ParsedMathIntent) -> Unit) {
        val apiKey = prefs.chatGptApiKey.trim()
        if (apiKey.isNotEmpty()) {
            parseStoryProblemChatGPT(apiKey, prefs.chatGptEndpoint, prefs.chatGptModel, storyText, onDone)
        } else {
            val base = sanitizeProxyUrl(prefs.aiProxyUrl)
            if (base != null && !base.contains("10.0.2.2")) {
                parseStoryProblem(base, storyText, onDone)
            } else {
                onDone(ParsedMathIntent(false, error = "Kunci API ChatGPT belum diisi. Masukkan OpenAI API Key (sk-...) melalui tombol Akun/Key."))
            }
        }
    }

    /** Penjelasan langkah dengan preferensi pengguna (prioritas: ChatGPT akun/key, fallback: 9router). */
    fun explainSolution(
        prefs: Prefs,
        moduleId: String,
        storyText: String,
        solveResult: SolveResult,
        onDone: (TextAiResult) -> Unit
    ) {
        val apiKey = prefs.chatGptApiKey.trim()
        if (apiKey.isNotEmpty()) {
            explainSolutionChatGPT(apiKey, prefs.chatGptEndpoint, prefs.chatGptModel, moduleId, storyText, solveResult, onDone)
        } else {
            val base = sanitizeProxyUrl(prefs.aiProxyUrl)
            if (base != null && !base.contains("10.0.2.2")) {
                explainSolution(base, moduleId, storyText, solveResult, onDone)
            } else {
                onDone(TextAiResult(false, error = "Kunci API ChatGPT belum diisi."))
            }
        }
    }

    /** Tanya langsung ke ChatGPT API (kompatibel OpenAI / browser-extension style). */
    fun askChatGPT(
        apiKey: String,
        endpoint: String = Prefs.DEFAULT_CHATGPT_ENDPOINT,
        model: String = Prefs.DEFAULT_CHATGPT_MODEL,
        question: String,
        onDone: (TextAiResult) -> Unit
    ) {
        val clean = question.trim()
        if (clean.isEmpty()) {
            onDone(TextAiResult(false, error = "Ketik pertanyaan terlebih dahulu."))
            return
        }
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            onDone(TextAiResult(false, error = "Kunci API ChatGPT belum diisi. Masukkan OpenAI API Key (sk-...) di tombol Key/Akun."))
            return
        }
        if (!acquireRateSlot()) {
            onDone(TextAiResult(false, error = "Batas permintaan tercapai. Tunggu sebentar."))
            return
        }

        executor.execute {
            val reqJson = JSONObject().apply {
                put("model", model.ifEmpty { Prefs.DEFAULT_CHATGPT_MODEL })
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Kamu adalah asisten AI di ClestialKeyboard untuk membantu siswa SMP belajar matematika dan tugas sekolah. Berikan jawaban yang ramah, ringkas, jelas, dan akurat dalam Bahasa Indonesia.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", clean.take(2000))
                    })
                }
                put("messages", messages)
                put("temperature", 0.6)
            }.toString()

            val ep = if (endpoint.isNotBlank()) endpoint.trim() else Prefs.DEFAULT_CHATGPT_ENDPOINT
            val res = postChatGpt(ep, cleanKey, reqJson)
            mainHandler.post { onDone(res) }
        }
    }

    /** Tafsir soal cerita langsung via ChatGPT API. */
    fun parseStoryProblemChatGPT(
        apiKey: String,
        endpoint: String = Prefs.DEFAULT_CHATGPT_ENDPOINT,
        model: String = Prefs.DEFAULT_CHATGPT_MODEL,
        storyText: String,
        onDone: (ParsedMathIntent) -> Unit
    ) {
        val clean = storyText.trim()
        if (clean.isEmpty()) {
            onDone(ParsedMathIntent(false, error = "Masukkan soal cerita terlebih dahulu."))
            return
        }
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            onDone(ParsedMathIntent(false, error = "Kunci API ChatGPT belum diisi. Masukkan OpenAI API Key (sk-...) di tombol Key/Akun."))
            return
        }
        if (!acquireRateSlot()) {
            onDone(ParsedMathIntent(false, error = "Batas permintaan tercapai. Tunggu sebentar."))
            return
        }

        executor.execute {
            val systemPrompt = """
Anda adalah parser soal matematika SMP kelas 9. Terjemahkan soal cerita berikut ke JSON valid untuk solver modul.
Format jawaban HARUS persis JSON murni:
{"modul": "<id_modul>", "input": {"<field>": "<value>"}}

Daftar modul:
- spldv (input: a1, b1, c1, a2, b2, c2)
- power_root (input: sub, a, m, n, x)
- quadratic_eq (input: a, b, c)
- quadratic_func (input: a, b, c, x)
- geom_trans (input: trans_type, sub, points, k, deg)
- congruence_sim (input: sub, s1, s2, s3, s4)
- curved_solid (input: solid, r, t, s, pi_mode)
- statistics (input: data)
- probability (input: jenis, n, target)
- linear_line (input: sub, x1, y1, x2, y2, a, b, c, m, k, p, q, fp, fq)
- plsv (input: expr)

Hanya kembalikan JSON tanpa penjelasan tambahan dan tanpa markdown fence.
""".trimIndent()

            val reqJson = JSONObject().apply {
                put("model", model.ifEmpty { Prefs.DEFAULT_CHATGPT_MODEL })
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", clean.take(2000))
                    })
                }
                put("messages", messages)
                put("temperature", 0.1)
            }.toString()

            val ep = if (endpoint.isNotBlank()) endpoint.trim() else Prefs.DEFAULT_CHATGPT_ENDPOINT
            val net = postChatGpt(ep, cleanKey, reqJson)
            val result = if (!net.ok) {
                ParsedMathIntent(false, error = net.error)
            } else {
                val validated = validateAiModuleJson(net.text)
                validated.copy(model = net.model)
            }
            mainHandler.post { onDone(result) }
        }
    }

    /** Penjelasan langkah penyelesaian soal via ChatGPT API. */
    fun explainSolutionChatGPT(
        apiKey: String,
        endpoint: String = Prefs.DEFAULT_CHATGPT_ENDPOINT,
        model: String = Prefs.DEFAULT_CHATGPT_MODEL,
        moduleId: String,
        storyText: String,
        solveResult: SolveResult,
        onDone: (TextAiResult) -> Unit
    ) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            onDone(TextAiResult(false, error = "Kunci API ChatGPT belum diisi."))
            return
        }
        if (!acquireRateSlot()) {
            onDone(TextAiResult(false, error = "Batas permintaan tercapai."))
            return
        }

        executor.execute {
            val prompt = """
Jelaskan langkah penyelesaian matematika berikut dengan gaya ramah dan mudah dipahami siswa SMP:
Modul: $moduleId
Soal: $storyText
Jawaban: ${solveResult.answer}
Langkah:
${solveResult.steps.joinToString("\n") { "- $it" }}
Cek: ${solveResult.check}
""".trimIndent()

            val reqJson = JSONObject().apply {
                put("model", model.ifEmpty { Prefs.DEFAULT_CHATGPT_MODEL })
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Kamu adalah guru matematika SMP yang ramah, menjelaskan langkah soal secara bertahap dan mudah dipahami.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                put("messages", messages)
                put("temperature", 0.5)
            }.toString()

            val ep = if (endpoint.isNotBlank()) endpoint.trim() else Prefs.DEFAULT_CHATGPT_ENDPOINT
            val res = postChatGpt(ep, cleanKey, reqJson)
            mainHandler.post { onDone(res) }
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

    private fun postChatGpt(endpoint: String, apiKey: String, jsonBody: String): TextAiResult {
        var lastError = "Gagal menghubungi layanan ChatGPT. Periksa koneksi internet Anda."
        for (attempt in 0..MAX_RETRIES) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respText = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() } ?: ""
                if (code in 200..299) {
                    val reply = try {
                        val root = JSONObject(respText)
                        val choices = root.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
                        } else {
                            ""
                        }
                    } catch (e: Exception) {
                        ""
                    }
                    if (reply.isNotEmpty()) {
                        return TextAiResult(ok = true, text = reply.trim(), model = extractModel(respText))
                    }
                    return TextAiResult(ok = false, error = "Respons ChatGPT kosong.")
                }
                val errMsg = try {
                    val root = JSONObject(respText)
                    val err = root.optJSONObject("error")
                    err?.optString("message") ?: "Error $code dari ChatGPT."
                } catch (e: Exception) {
                    when (code) {
                        401 -> "Kunci API ChatGPT tidak valid atau salah. Periksa kembali API Key Anda."
                        429 -> "Batas kuota ChatGPT tercapai (Rate Limit / Quota Exceeded)."
                        else -> "Server ChatGPT mengembalikan kode error $code."
                    }
                }
                lastError = errMsg
                if (code != 429 && code < 500) {
                    return TextAiResult(ok = false, error = lastError)
                }
            } catch (e: Exception) {
                lastError = "Koneksi ke ChatGPT gagal atau timeout. Periksa internet atau API Key."
            } finally {
                conn?.disconnect()
            }
        }
        return TextAiResult(ok = false, error = lastError)
    }
}
