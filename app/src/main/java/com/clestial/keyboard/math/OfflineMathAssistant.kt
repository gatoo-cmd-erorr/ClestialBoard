package com.clestial.keyboard.math

/**
 * Penjawab matematika offline untuk "Tanya AI" ketika API Key / Proxy AI belum diisi:
 * otomatis mengenali SPLDV (2 persamaan x, y), PLSV/PtLSV, Persamaan Kuadrat,
 * maupun ekspresi Kalkulator/Kombinasi/Permutasi dan menyelesaikannya secara eksak.
 */
object OfflineMathAssistant {

    fun trySolveMathQuestion(raw: String): String? {
        val text = raw.trim()
            .replace("−", "-")
            .replace("–", "-")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("_", " ")
        if (text.isEmpty()) return null

        // 1. Cek SPLDV: dua persamaan mengandung x dan y (dipisah "dan", ";", ",", atau baris baru)
        trySolveSpldvText(text)?.let { return it }

        // 2. Cek Persamaan Kuadrat (mengandung x^2 atau x²)
        if (text.contains("x^2", ignoreCase = true) || text.contains("x²", ignoreCase = true)) {
            trySolveQuadraticText(text)?.let { return it }
        }

        // 3. Cek PLSV / Pertidaksamaan satu variabel
        val eqMatch = Regex("""([0-9a-zA-Z\s\+\-\*\/\(\)\.]+(?:<=|>=|≤|≥|=|<|>)[0-9a-zA-Z\s\+\-\*\/\(\)\.]+)""").find(text)
        if (eqMatch != null) {
            val candidate = eqMatch.groupValues[1].trim()
            val plsvRes = PlsvModule().solve(mapOf("persamaan" to candidate))
            if (plsvRes.ok) {
                return formatModuleResult("PLSV / Pertidaksamaan (Offline Eksak)", plsvRes)
            }
        }

        // 4. Cek ekspresi kalkulator langsung (termasuk 5C2, C(5,2), 5 kombinasi 2, 5P2, 5!, akar, pangkat)
        val directCalc = ScientificCalculator.evaluate(text, degreeMode = true)
        if (directCalc.ok) {
            return "🧮 Hasil Kalkulator (Offline Eksak):\nJawaban: ${directCalc.display}"
        }
        val strippedCalc = text
            .replace(Regex("""\b(berapa|hasil|hitung|hitunglah|nilai|tentukan|dari|adalah)\b""", RegexOption.IGNORE_CASE), " ")
            .replace("?", "")
            .replace("=", "")
            .trim()
        if (strippedCalc.isNotEmpty() && strippedCalc != text) {
            val calcRes2 = ScientificCalculator.evaluate(strippedCalc, degreeMode = true)
            if (calcRes2.ok) {
                return "🧮 Hasil Kalkulator (Offline Eksak):\nEkspresi: $strippedCalc\nJawaban: ${calcRes2.display}"
            }
        }

        return null
    }

    private fun trySolveSpldvText(text: String): String? {
        val eqRegex = Regex("""([0-9\+\-\*\.\s]*[xXyY][0-9a-zA-Z\+\-\*\.\s]*=[0-9\+\-\.\s]+)""")
        val matches = eqRegex.findAll(text).map { it.groupValues[1].trim() }.toList()
        if (matches.size < 2) return null

        val p1 = parseLinearXY(matches[0]) ?: return null
        val p2 = parseLinearXY(matches[1]) ?: return null

        val res = SpldvModule().solve(
            mapOf(
                "a1" to p1.first, "b1" to p1.second, "c1" to p1.third,
                "a2" to p2.first, "b2" to p2.second, "c2" to p2.third
            )
        )
        return if (res.ok) formatModuleResult("SPLDV (Offline Eksak)", res) else null
    }

    /** Mem-parsing bentuk ax + by = c (atau ax - by = c, y = ax + c) menjadi Triple(a, b, c). */
    fun parseLinearXY(eq: String): Triple<String, String, String>? {
        val clean = eq.replace(" ", "").lowercase()
        val parts = clean.split("=")
        if (parts.size != 2) return null
        val lhs = parseLinearSide(parts[0]) ?: return null
        val rhs = parseLinearSide(parts[1]) ?: return null
        // Pindahkan semua x, y ke kiri dan konstanta ke kanan: (ax - rhsA)x + (by - rhsB)y = rhsC - lhsC
        val a = lhs.first - rhs.first
        val b = lhs.second - rhs.second
        val c = rhs.third - lhs.third
        if (a == 0.0 && b == 0.0) return null
        return Triple(fmtNum(a), fmtNum(b), fmtNum(c))
    }

    private fun parseLinearSide(side: String): Triple<Double, Double, Double>? {
        if (side.isEmpty()) return Triple(0.0, 0.0, 0.0)
        var s = side
        if (s[0] != '+' && s[0] != '-') s = "+$s"
        val tokenRegex = Regex("""([+-](?:\d+(?:\.\d+)?)?)([xy]?)""")
        var a = 0.0
        var b = 0.0
        var c = 0.0
        var idx = 0
        while (idx < s.length) {
            val m = tokenRegex.matchAt(s, idx) ?: return null
            if (m.value.isEmpty() || (m.value in listOf("+", "-") && m.groupValues[2].isEmpty())) return null
            val numRaw = m.groupValues[1]
            val variable = m.groupValues[2]
            val coeff = when (numRaw) {
                "+" -> 1.0
                "-" -> -1.0
                else -> numRaw.toDoubleOrNull() ?: return null
            }
            when (variable) {
                "x" -> a += coeff
                "y" -> b += coeff
                "" -> c += coeff
            }
            idx = m.range.last + 1
        }
        return Triple(a, b, c)
    }

    private fun trySolveQuadraticText(text: String): String? {
        val eqRegex = Regex("""([0-9a-zA-Z\^\²\+\-\.\s]+=\s*[0-9\+\-\.\s]+)""")
        val m = eqRegex.find(text) ?: return null
        val clean = m.groupValues[1].replace(" ", "").replace("²", "^2").lowercase()
        val parts = clean.split("=")
        if (parts.size != 2 || parts[1] != "0") return null
        var s = parts[0]
        if (s.isEmpty()) return null
        if (s[0] != '+' && s[0] != '-') s = "+$s"

        val tokenRegex = Regex("""([+-](?:\d+(?:\.\d+)?)?)(x\^2|x)?""")
        var a = 0.0
        var b = 0.0
        var c = 0.0
        var idx = 0
        while (idx < s.length) {
            val tm = tokenRegex.matchAt(s, idx) ?: return null
            if (tm.value.isEmpty()) return null
            val numRaw = tm.groupValues[1]
            val v = tm.groupValues[2]
            val coeff = when (numRaw) {
                "+" -> 1.0
                "-" -> -1.0
                else -> numRaw.toDoubleOrNull() ?: return null
            }
            when (v) {
                "x^2" -> a += coeff
                "x" -> b += coeff
                "" -> c += coeff
            }
            idx = tm.range.last + 1
        }
        if (a == 0.0) return null
        val res = QuadraticEqModule().solve(mapOf("a" to fmtNum(a), "b" to fmtNum(b), "c" to fmtNum(c)))
        return if (res.ok) formatModuleResult("Persamaan Kuadrat (Offline Eksak)", res) else null
    }

    private fun fmtNum(v: Double): String {
        val l = v.toLong()
        return if (v == l.toDouble()) l.toString() else v.toString()
    }

    private fun formatModuleResult(title: String, res: SolveResult): String = buildString {
        append("🧮 $title\n")
        append("Jawaban: ").append(res.answer).append("\n\n")
        if (res.steps.isNotEmpty()) {
            append("Langkah:\n")
            res.steps.forEachIndexed { i, step -> append("${i + 1}. $step\n") }
        }
        if (res.check.isNotEmpty()) {
            append("\nCek: ").append(res.check)
        }
    }.trim()
}
