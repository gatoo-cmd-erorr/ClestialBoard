package com.clestial.keyboard

/**
 * Mesin Aksara Jawa (Hanacaraka):
 * - 20 Carakan (Aksara Nglegena), Aksara Swara, Aksara Ganten (Pa Cerek, Nga Lelet)
 * - Sandhangan Swara (wulu, suku, taling, taling tarung, pepet)
 * - Sandhangan Panyigeg (cecak, layar, wignyan, pangkon)
 * - Sandhangan Wyanjana (cakra, cakra keret, péngkal)
 * - Pembentuk Pasangan otomatis (Pangkon \uA9C0 + Carakan)
 * - Transliterasi dua arah: Aksara Jawa <-> Latin
 */
object JavaneseScript {

    const val CH_CECak = '\uA981'      // ꦁ (-ng)
    const val CH_LAYAR = '\uA982'      // ꦂ (-r)
    const val CH_WIGNYAN = '\uA983'    // ꦃ (-h)
    const val CH_PA_CEREK = '\uA989'   // ꦉ (re)
    const val CH_NGA_LELET = '\uA98A'  // ꦊ (le)
    const val CH_CECAK_TELU = '\uA9B3' // ꦳
    const val CH_TARUNG = '\uA9B4'     // ꦴ
    const val CH_WULU = '\uA9B6'       // ꦶ (i)
    const val CH_SUKU = '\uA9B8'       // ꦸ (u)
    const val CH_TALING = '\uA9BA'     // ꦺ (é/è)
    const val CH_PEPET = '\uA9BC'      // ꦼ (e)
    const val CH_KERET = '\uA9BD'      // ꦽ (-re-)
    const val CH_PENGKAL = '\uA9BE'    // ꦾ (-y-)
    const val CH_CAKRA = '\uA9BF'      // ꦿ (-r-)
    const val CH_PANGKON = '\uA9C0'    // ꧀ (virama / pembentuk pasangan)
    const val CH_PADA_LINGSA = '\uA9C8'// ꧈ (,)
    const val CH_PADA_LUNGSI = '\uA9C9'// ꧉ (.)
    const val CH_PADA_ADEG = '\uA9CB'  // ꧋
    const val CH_PADA_PANGKAT = '\uA9C7'// ꧇

    data class CarakanKey(val glyph: String, val latin: String, val baseConsonant: String)
    data class SandhanganKey(val label: String, val insert: String, val hint: String)

    val CARAKAN: List<CarakanKey> = listOf(
        CarakanKey("ꦲ", "ha", "h"),
        CarakanKey("ꦤ", "na", "n"),
        CarakanKey("ꦕ", "ca", "c"),
        CarakanKey("ꦫ", "ra", "r"),
        CarakanKey("ꦏ", "ka", "k"),
        CarakanKey("ꦢ", "da", "d"),
        CarakanKey("ꦠ", "ta", "t"),
        CarakanKey("ꦱ", "sa", "s"),
        CarakanKey("ꦮ", "wa", "w"),
        CarakanKey("ꦭ", "la", "l"),
        CarakanKey("ꦥ", "pa", "p"),
        CarakanKey("ꦝ", "dha", "dh"),
        CarakanKey("ꦗ", "ja", "j"),
        CarakanKey("ꦪ", "ya", "y"),
        CarakanKey("ꦚ", "nya", "ny"),
        CarakanKey("ꦩ", "ma", "m"),
        CarakanKey("ꦒ", "ga", "g"),
        CarakanKey("ꦧ", "ba", "b"),
        CarakanKey("ꦛ", "tha", "th"),
        CarakanKey("ꦔ", "nga", "ng")
    )

    val SANDHANGAN_UTAMA: List<SandhanganKey> = listOf(
        SandhanganKey("◌ꦶ", "\uA9B6", "i"),
        SandhanganKey("◌ꦸ", "\uA9B8", "u"),
        SandhanganKey("◌ꦺ", "\uA9BA", "é"),
        SandhanganKey("◌ꦺꦴ", "\uA9BA\uA9B4", "o"),
        SandhanganKey("◌ꦼ", "\uA9BC", "e"),
        SandhanganKey("◌ꦂ", "\uA982", "-r"),
        SandhanganKey("◌ꦃ", "\uA983", "-h"),
        SandhanganKey("◌ꦁ", "\uA981", "-ng"),
        SandhanganKey("◌ꦿ", "\uA9BF", "-r-"),
        SandhanganKey("◌꧀", "\uA9C0", "patèn")
    )

    val SANDHANGAN_LANJUT: List<SandhanganKey> = listOf(
        SandhanganKey("ꦉ", "\uA989", "re"),
        SandhanganKey("ꦊ", "\uA98A", "le"),
        SandhanganKey("◌ꦽ", "\uA9BD", "-re-"),
        SandhanganKey("◌ꦾ", "\uA9BE", "-y-"),
        SandhanganKey("◌꦳", "\uA9B3", "telu"),
        SandhanganKey("ꦄ", "\uA984", "A"),
        SandhanganKey("ꦆ", "\uA986", "I"),
        SandhanganKey("ꦈ", "\uA988", "U"),
        SandhanganKey("ꦌ", "\uA98C", "E"),
        SandhanganKey("ꦎ", "\uA98E", "O")
    )

    val JAVANESE_DIGITS: List<SandhanganKey> = (0..9).map { d ->
        SandhanganKey(('꧐'.code + d).toChar().toString(), ('꧐'.code + d).toChar().toString(), d.toString())
    }

    private val CARAKAN_TO_CONS: Map<Char, String> = CARAKAN.associate { it.glyph[0] to it.baseConsonant } + mapOf(
        '\uA991' to "kh", '\uA993' to "g", '\uA996' to "c", '\uA998' to "ny", '\uA99F' to "n",
        '\uA9A1' to "t", '\uA9A3' to "d", '\uA9A6' to "p", '\uA9A8' to "b", '\uA9AF' to "s"
    )

    private val SWARA_TO_VOWEL: Map<Char, String> = mapOf(
        '\uA984' to "a", '\uA986' to "i", '\uA988' to "u", '\uA98C' to "é", '\uA98E' to "o",
        CH_PA_CEREK to "re", CH_NGA_LELET to "le"
    )

    private val CONS_TO_CARAKAN: Map<String, Char> = CARAKAN.associate { it.baseConsonant to it.glyph[0] }

    /**
     * Sisipkan pasangan untuk Carakan [carakanGlyph] pada [current] di posisi [cursor]:
     * memastikan tepat satu pangkon `\uA9C0` mendahului huruf Carakan sehingga terbentuk Pasangan.
     */
    fun buildPasanganInsertion(current: String, cursor: Int, carakanGlyph: String): String {
        val pos = cursor.coerceIn(0, current.length)
        val hasPangkonBefore = pos > 0 && current[pos - 1] == CH_PANGKON
        return if (hasPangkonBefore) carakanGlyph else "$CH_PANGKON$carakanGlyph"
    }

    /** Hapus satu code point Unicode sebelum [cursor] (mendukung sandhangan gabung & pangkon). */
    fun deleteOneCodePoint(text: String, cursor: Int): Pair<String, Int> {
        val pos = cursor.coerceIn(0, text.length)
        if (pos == 0) return text to 0
        var start = pos - 1
        if (start > 0 && Character.isLowSurrogate(text[start]) && Character.isHighSurrogate(text[start - 1])) {
            start -= 1
        }
        return text.removeRange(start, pos) to start
    }

    /** Terjemahkan/transliterasi teks Aksara Jawa ke huruf Latin. */
    fun toLatin(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder()
        var i = 0
        val n = input.length

        while (i < n) {
            val ch = input[i]

            // Dukungan urutan visual Taling (ꦺ) diketik mendahului Carakan
            if (ch == CH_TALING && i + 1 < n && CARAKAN_TO_CONS.containsKey(input[i + 1])) {
                val nextCons = CARAKAN_TO_CONS[input[i + 1]]!!
                i += 2
                var v = "é"
                if (i < n && input[i] == CH_TARUNG) {
                    v = "o"
                    i++
                }
                var coda = ""
                if (i < n) {
                    when (input[i]) {
                        CH_CECak -> { coda = "ng"; i++ }
                        CH_LAYAR -> { coda = "r"; i++ }
                        CH_WIGNYAN -> { coda = "h"; i++ }
                    }
                }
                sb.append(if (nextCons == "h") "" else nextCons)
                sb.append(v)
                sb.append(coda)
                continue
            }

            val cons = CARAKAN_TO_CONS[ch]
            if (cons != null) {
                i++
                var cluster = ""
                var vowel = "a"
                var coda = ""
                var hasCecakTelu = false
                var killed = false

                // Kumpulkan seluruh tanda gabung setelah aksara dasar.
                while (i < n) {
                    val m = input[i]
                    when (m) {
                        CH_CECAK_TELU -> { hasCecakTelu = true; i++ }
                        CH_CAKRA -> { cluster += "r"; i++ }
                        CH_PENGKAL -> { cluster += "y"; i++ }
                        CH_KERET -> { vowel = "re"; i++ }
                        CH_WULU, '\uA9B7' -> { vowel = "i"; i++ }
                        CH_SUKU, '\uA9B9' -> { vowel = "u"; i++ }
                        CH_TALING -> {
                            i++
                            if (i < n && input[i] == CH_TARUNG) {
                                vowel = "o"
                                i++
                            } else {
                                vowel = "é"
                            }
                        }
                        CH_TARUNG -> { vowel = "o"; i++ }
                        '\uA9BB' -> { vowel = "o"; i++ }
                        CH_PEPET -> { vowel = "e"; i++ }
                        CH_CECak -> { coda = "ng"; i++ }
                        CH_LAYAR -> { coda = "r"; i++ }
                        CH_WIGNYAN -> { coda = "h"; i++ }
                        CH_PANGKON -> { killed = true; i++; break }
                        else -> break
                    }
                }

                val mappedCons = if (hasCecakTelu) mapForeignConsonant(cons) else if (cons == "h") "" else cons
                sb.append(mappedCons)
                sb.append(cluster)
                if (!killed) sb.append(vowel)
                sb.append(coda)
                continue
            }

            val swara = SWARA_TO_VOWEL[ch]
            if (swara != null) {
                sb.append(swara)
                i++
                continue
            }

            when (ch) {
                CH_CECak -> { sb.append("ng"); i++ }
                CH_LAYAR -> { sb.append("r"); i++ }
                CH_WIGNYAN -> { sb.append("h"); i++ }
                CH_PADA_LINGSA -> { sb.append(", "); i++ }
                CH_PADA_LUNGSI -> { sb.append(". "); i++ }
                CH_PADA_ADEG, '\uA9CA' -> { i++ }
                CH_PADA_PANGKAT -> { sb.append(':'); i++ }
                in '\uA9D0'..'\uA9D9' -> { sb.append(('0'.code + (ch.code - '\uA9D0'.code)).toChar()); i++ }
                else -> { sb.append(ch); i++ }
            }
        }
        return sb.toString().replace(Regex(" +"), " ").trim()
    }

    private fun mapForeignConsonant(base: String): String = when (base) {
        "k" -> "kh"
        "d" -> "dz"
        "p" -> "f"
        "j" -> "z"
        "g" -> "gh"
        else -> base
    }

    /**
     * Terjemahkan/transliterasi teks Latin (Bahasa Jawa / Indonesia) menjadi Aksara Jawa Unicode,
     * menerapkan Pa Cerek, Nga Lelet, Cakra, Péngkal, Sandhangan Swara, Panyigeg, dan Pasangan.
     */
    fun toJavanese(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder()
        val lower = input.lowercase()
        var i = 0
        val n = lower.length

        while (i < n) {
            val ch = lower[i]
            if (ch.isWhitespace()) {
                sb.append(' ')
                i++
                continue
            }
            if (ch in '0'..'9') {
                sb.append(('\uA9D0'.code + (ch.code - '0'.code)).toChar())
                i++
                continue
            }
            if (ch == ',') { sb.append(CH_PADA_LINGSA); i++; continue }
            if (ch == '.') { sb.append(CH_PADA_LUNGSI); i++; continue }

            // Cek apakah awal kata / posisi vokal mandiri.
            if (isVowelChar(ch)) {
                val vowelRes = readVowel(lower, i)
                val v = vowelRes.first
                i = vowelRes.second
                sb.append('\uA9B2') // Ha sebagai penyangga vokal awal
                appendVowelSign(sb, v)
                // Cek penutup suku kata (sigeg) bila setelahnya konsonan mati.
                i = appendCodaIfAny(lower, i, sb)
                continue
            }

            // Baca konsonan utama (termasuk digraf ng, ny, dh, th).
            val c1 = readConsonant(lower, i)
            if (c1 == null) {
                sb.append(ch)
                i++
                continue
            }
            val cons = c1.first
            i = c1.second

            // Cek kluster wyanjana: -r- (Cakra) atau -y- (Péngkal) sebelum vokal.
            var medial: Char? = null
            if (i < n && (lower[i] == 'r' || lower[i] == 'y') && i + 1 < n && isVowelChar(lower[i + 1])) {
                // Jangan pecah kalau r/y sendiri memang konsonan berikutnya setelah pangkon.
                if (cons !in setOf("r", "y", "h", "ng")) {
                    medial = if (lower[i] == 'r') CH_CAKRA else CH_PENGKAL
                    i++
                }
            }

            // Kasus khusus Pa Cerek (re) dan Nga Lelet (le) bila tidak didahului sigeg kompleks.
            if (medial == null && (cons == "r" || cons == "l") && i < n && lower[i] == 'e') {
                sb.append(if (cons == "r") CH_PA_CEREK else CH_NGA_LELET)
                i++
                i = appendCodaIfAny(lower, i, sb)
                continue
            }

            val baseGlyph = CONS_TO_CARAKAN[cons] ?: '\uA9B2'
            if (i < n && isVowelChar(lower[i])) {
                val (v, nextIdx) = readVowel(lower, i)
                i = nextIdx
                sb.append(baseGlyph)
                if (medial != null) {
                    if (medial == CH_CAKRA && v == "e") {
                        sb.append(CH_KERET)
                    } else {
                        sb.append(medial)
                        appendVowelSign(sb, v)
                    }
                } else {
                    appendVowelSign(sb, v)
                }
                i = appendCodaIfAny(lower, i, sb)
            } else {
                // Konsonan mati tanpa vokal.
                val atWordStart = sb.isEmpty() || sb.last() == ' '
                if (!atWordStart && cons == "ng") {
                    sb.append(CH_CECak)
                } else if (!atWordStart && cons == "r") {
                    sb.append(CH_LAYAR)
                } else if (!atWordStart && cons == "h") {
                    sb.append(CH_WIGNYAN)
                } else {
                    sb.append(baseGlyph)
                    sb.append(CH_PANGKON)
                }
            }
        }
        return sb.toString()
    }

    private fun appendCodaIfAny(s: String, idx: Int, sb: StringBuilder): Int {
        val n = s.length
        if (idx >= n) return idx
        val c = readConsonant(s, idx) ?: return idx
        val cons = c.first
        val afterCons = c.second
        // Jika konsonan diikuti vokal atau wyanjana+vokal, itu bukan coda melainkan onset suku kata berikutnya.
        if (afterCons < n && isVowelChar(s[afterCons])) return idx
        if (afterCons + 1 < n && (s[afterCons] == 'r' || s[afterCons] == 'y') && isVowelChar(s[afterCons + 1])) {
            if (cons !in setOf("r", "y", "h", "ng")) return idx
        }
        // Konsonan adalah coda (penutup suku kata).
        when (cons) {
            "ng" -> sb.append(CH_CECak)
            "r" -> sb.append(CH_LAYAR)
            "h" -> sb.append(CH_WIGNYAN)
            else -> {
                val glyph = CONS_TO_CARAKAN[cons] ?: return idx
                sb.append(glyph)
                sb.append(CH_PANGKON)
            }
        }
        return afterCons
    }

    private fun readConsonant(s: String, i: Int): Pair<String, Int>? {
        if (i >= s.length) return null
        if (i + 1 < s.length) {
            val di = s.substring(i, i + 2)
            if (di in setOf("ng", "ny", "dh", "th")) return di to (i + 2)
        }
        val ch = s[i]
        return when (ch) {
            'h', 'n', 'c', 'r', 'k', 'd', 't', 's', 'w', 'l', 'p', 'j', 'y', 'm', 'g', 'b' -> ch.toString() to (i + 1)
            'f', 'v' -> "p" to (i + 1)
            'z' -> "j" to (i + 1)
            'q' -> "k" to (i + 1)
            else -> null
        }
    }

    private fun isVowelChar(c: Char): Boolean = c in setOf('a', 'i', 'u', 'e', 'é', 'è', 'o')

    private fun readVowel(s: String, i: Int): Pair<String, Int> {
        val c = s[i]
        val v = when (c) {
            'é', 'è' -> "é"
            'e' -> "e"
            else -> c.toString()
        }
        return v to (i + 1)
    }

    private fun appendVowelSign(sb: StringBuilder, v: String) {
        when (v) {
            "i" -> sb.append(CH_WULU)
            "u" -> sb.append(CH_SUKU)
            "é" -> sb.append(CH_TALING)
            "o" -> { sb.append(CH_TALING); sb.append(CH_TARUNG) }
            "e" -> sb.append(CH_PEPET)
            else -> Unit // 'a' inheren
        }
    }
}
