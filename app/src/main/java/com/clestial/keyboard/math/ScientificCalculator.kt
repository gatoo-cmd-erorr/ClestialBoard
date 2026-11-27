package com.clestial.keyboard.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Mesin evaluasi ekspresi untuk kalkulator umum & ilmiah di keyboard:
 * mendukung +, -, ×, ÷, %, (), pangkat (^, ², ³), akar kuadrat (√), akar pangkat tiga (∛),
 * akar pangkat n (n√x), kebalikan (1/x), faktorial (n!), kombinasi (nCr / C(n,r)),
 * permutasi (nPr / P(n,r)), π, sin, cos, tan, log, ln.
 *
 * Operasi rasional dasar menjaga pecahan eksak lewat [ExactFraction] bila memungkinkan,
 * dan fungsi transendental menggunakan presisi tinggi dengan pembersihan derau angka.
 */
object ScientificCalculator {

    data class CalcOutput(
        val ok: Boolean,
        val display: String = "",
        val insertable: String = "",
        val error: String = ""
    )

    fun evaluate(rawExpr: String, degreeMode: Boolean = true): CalcOutput {
        val expr = rawExpr.trim()
        if (expr.isEmpty()) return CalcOutput(false, error = "Ekspresi kosong")

        return try {
            // Cek kasus akar tunggal murni (misal √72 atau ∛27) untuk menampilkan bentuk akar sederhana.
            val singleSqrt = Regex("""^√\(?(\d+)\)?$""").matchEntire(expr.replace(" ", ""))
            if (singleSqrt != null) {
                val n = singleSqrt.groupValues[1].toBigInteger()
                val (out, inn) = ExactMath.simplifySqrt(n)
                val exact = ExactMath.formatRadicalTerm(out, inn)
                val approx = QuadraticSurd(ExactFraction.ZERO, ExactFraction.of(out, BigInteger.ONE), inn)
                    .toDecimalApprox(6)
                return if (inn == BigInteger.ONE) {
                    CalcOutput(true, exact, exact)
                } else {
                    CalcOutput(true, "$exact (≈ $approx)", exact)
                }
            }

            // Cek kasus akar pangkat tiga tunggal (misal ∛27 atau ∛54) untuk bentuk eksak.
            val singleCbrt = Regex("""^∛\(?(\d+)\)?$""").matchEntire(expr.replace(" ", ""))
            if (singleCbrt != null) {
                val n = singleCbrt.groupValues[1].toBigInteger()
                val (out, inn) = ExactMath.simplifyCbrt(n)
                val exact = when {
                    out == BigInteger.ZERO -> "0"
                    inn == BigInteger.ONE -> out.toString()
                    out == BigInteger.ONE -> "∛$inn"
                    else -> "${out}∛$inn"
                }
                val num = n.toDouble().pow(1.0 / 3.0)
                return if (inn == BigInteger.ONE) {
                    CalcOutput(true, exact, exact)
                } else {
                    CalcOutput(true, "$exact (≈ ${formatNumber(num)})", exact)
                }
            }

            // Cek kombinasi / permutasi / faktorial murni agar nilai BigInteger besar tetap presisi eksak.
            val directCombPerm = tryDirectCombPermOrFact(expr)
            if (directCombPerm != null) {
                return directCombPerm
            }

            // Cek evaluasi eksak rasional murni terlebih dahulu jika tidak memakai fungsi ilmiah.
            val parser = ExprParser(expr, degreeMode)
            val value = parser.parse()
            if (value.isNaN() || value.isInfinite()) {
                return CalcOutput(false, error = "Hasil tidak terdefinisi")
            }

            val clean = formatNumber(value)
            // Jika ekspresi murni aritmetika rasional tanpa desimal irasional, sertakan bentuk pecahan bila relevan.
            val exactRational = tryExactRational(expr)
            val display = if (exactRational != null && !exactRational.isInteger && exactRational.den <= BigInteger.valueOf(10_000)) {
                "${exactRational.toFracString()} ($clean)"
            } else {
                clean
            }
            CalcOutput(true, display, clean)
        } catch (e: IllegalArgumentException) {
            CalcOutput(false, error = e.message ?: "Ekspresi tidak valid")
        } catch (e: Exception) {
            CalcOutput(false, error = "Format ekspresi salah")
        }
    }

    private fun tryDirectCombPermOrFact(raw: String): CalcOutput? {
        val trimmed = raw.trim()
        val infixMatch = Regex("""^(\d+)\s*(kombinasi|permutasi|ncr|npr|c|p)\s*(\d+)$""", RegexOption.IGNORE_CASE)
            .matchEntire(trimmed)
        if (infixMatch != null) {
            val n = infixMatch.groupValues[1].toIntOrNull() ?: return null
            val op = infixMatch.groupValues[2].lowercase()
            val r = infixMatch.groupValues[3].toIntOrNull() ?: return null
            if (n > 500 || r > 500) throw IllegalArgumentException("Nilai maksimal 500")
            val big = if (op in setOf("c", "ncr", "kombinasi")) exactComb(n, r) else exactPerm(n, r)
            return CalcOutput(true, big.toString(), big.toString())
        }
        val fnMatch = Regex("""^(kombinasi|permutasi|ncr|npr|c|p)\s*(?:\(\s*(\d+)\s*[,;]\s*(\d+)\s*\)|\s+(\d+)\s+(\d+))$""", RegexOption.IGNORE_CASE)
            .matchEntire(trimmed)
        if (fnMatch != null) {
            val op = fnMatch.groupValues[1].lowercase()
            val nStr = fnMatch.groupValues[2].ifEmpty { fnMatch.groupValues[4] }
            val rStr = fnMatch.groupValues[3].ifEmpty { fnMatch.groupValues[5] }
            val n = nStr.toIntOrNull() ?: return null
            val r = rStr.toIntOrNull() ?: return null
            if (n > 500 || r > 500) throw IllegalArgumentException("Nilai maksimal 500")
            val big = if (op in setOf("c", "ncr", "kombinasi")) exactComb(n, r) else exactPerm(n, r)
            return CalcOutput(true, big.toString(), big.toString())
        }
        val factMatch = Regex("""^(\d+)\s*!$""").matchEntire(trimmed)
        if (factMatch != null) {
            val n = factMatch.groupValues[1].toIntOrNull() ?: return null
            if (n > 500) throw IllegalArgumentException("Nilai n maksimal 500")
            var res = BigInteger.ONE
            for (i in 2..n) res = res.multiply(BigInteger.valueOf(i.toLong()))
            return CalcOutput(true, res.toString(), res.toString())
        }
        return null
    }

    private fun exactComb(n: Int, r: Int): BigInteger {
        if (r > n) throw IllegalArgumentException("Untuk kombinasi C(n, r), nilai r ($r) tidak boleh > n ($n)")
        val k = minOf(r, n - r)
        var res = BigInteger.ONE
        for (i in 1..k) {
            res = res.multiply(BigInteger.valueOf((n - k + i).toLong())).divide(BigInteger.valueOf(i.toLong()))
        }
        return res
    }

    private fun exactPerm(n: Int, r: Int): BigInteger {
        if (r > n) throw IllegalArgumentException("Untuk permutasi P(n, r), nilai r ($r) tidak boleh > n ($n)")
        var res = BigInteger.ONE
        for (i in 0 until r) {
            res = res.multiply(BigInteger.valueOf((n - i).toLong()))
        }
        return res
    }

    private fun tryExactRational(expr: String): ExactFraction? {
        if (expr.any { it in "√∛πe" } || Regex("[a-zA-Z]").containsMatchIn(expr)) return null
        return try {
            RationalParser(expr).parse()
        } catch (e: Exception) {
            null
        }
    }

    private fun formatNumber(v: Double): String {
        val roundedZero = if (abs(v) < 1e-12) 0.0 else v
        val bd = BigDecimal.valueOf(roundedZero)
            .round(MathContext(12, RoundingMode.HALF_UP))
            .stripTrailingZeros()
        val s = if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
        return s.replace('.', ',')
    }

    private class ExprParser(raw: String, private val deg: Boolean) {
        private val s = normalize(raw)
        private var pos = 0

        fun parse(): Double {
            val v = parseAddSub()
            if (pos < s.length) throw IllegalArgumentException("Karakter tak dikenal '${s[pos]}'")
            return v
        }

        private fun parseAddSub(): Double {
            var x = parseMulDiv()
            while (pos < s.length) {
                when (s[pos]) {
                    '+' -> { pos++; x += parseMulDiv() }
                    '-' -> { pos++; x -= parseMulDiv() }
                    else -> return x
                }
            }
            return x
        }

        private fun parseMulDiv(): Double {
            var x = parseCombPerm()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; x *= parseCombPerm() }
                    '/' -> {
                        pos++
                        val d = parseCombPerm()
                        if (d == 0.0) throw IllegalArgumentException("Pembagian dengan nol")
                        x /= d
                    }
                    else -> return x
                }
            }
            return x
        }

        private fun parseCombPerm(): Double {
            var x = parsePower()
            while (pos < s.length) {
                val ch = s[pos]
                if (ch == 'C' || ch == 'P') {
                    pos++
                    val r = parsePower()
                    val nInt = toNonNegInt(x, "n")
                    val rInt = toNonNegInt(r, "r")
                    x = if (ch == 'C') combBig(nInt, rInt).toDouble() else permBig(nInt, rInt).toDouble()
                } else {
                    break
                }
            }
            return x
        }

        private fun parsePower(): Double {
            var x = parseUnary()
            // Akar pangkat n: n√x
            if (pos < s.length && s[pos] == '√') {
                pos++
                val base = parsePower()
                if (x == 0.0) throw IllegalArgumentException("Derajat akar tidak boleh 0")
                if (base < 0.0) {
                    val xi = x.toInt()
                    if (x == xi.toDouble() && xi % 2 != 0) {
                        x = -(-base).pow(1.0 / x)
                    } else {
                        throw IllegalArgumentException("Akar genap bilangan negatif tidak real")
                    }
                } else {
                    x = base.pow(1.0 / x)
                }
            } else if (pos < s.length && s[pos] == '^') {
                pos++
                val exp = parsePower()
                x = x.pow(exp)
            }
            return x
        }

        private fun parseUnary(): Double {
            if (pos < s.length && s[pos] == '+') {
                pos++
                return parseUnary()
            }
            if (pos < s.length && s[pos] == '-') {
                pos++
                return -parseUnary()
            }
            return parsePostfix()
        }

        private fun parsePostfix(): Double {
            var x = parsePrimary()
            while (pos < s.length) {
                when (s[pos]) {
                    '%' -> { pos++; x /= 100.0 }
                    '²' -> { pos++; x *= x }
                    '³' -> { pos++; x = x * x * x }
                    '!' -> {
                        pos++
                        val nInt = toNonNegInt(x, "n")
                        x = factorialBig(nInt).toDouble()
                    }
                    else -> break
                }
            }
            return x
        }

        private fun parsePrimary(): Double {
            if (pos >= s.length) throw IllegalArgumentException("Ekspresi belum lengkap")
            val ch = s[pos]

            if (ch == '(') {
                pos++
                val v = parseAddSub()
                if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException("Kurung tutup ')' kurang")
                pos++
                return v
            }
            if (ch == '√') {
                pos++
                val arg = parseUnary()
                if (arg < 0) throw IllegalArgumentException("Akar kuadrat bilangan negatif tidak real")
                return sqrt(arg)
            }
            if (ch == '∛') {
                pos++
                val arg = parseUnary()
                return if (arg < 0) -(-arg).pow(1.0 / 3.0) else arg.pow(1.0 / 3.0)
            }
            if (ch == 'π') {
                pos++
                return PI
            }
            if (ch == 'e' && (pos + 1 >= s.length || !s[pos + 1].isLetter())) {
                pos++
                return kotlin.math.E
            }
            if (ch.isDigit() || ch == '.') {
                val start = pos
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                return s.substring(start, pos).toDoubleOrNull()
                    ?: throw IllegalArgumentException("Format angka salah")
            }
            if (ch.isLetter()) {
                val start = pos
                while (pos < s.length && s[pos].isLetter()) pos++
                val name = s.substring(start, pos).lowercase()
                if (name in setOf("c", "ncr", "kombinasi", "p", "npr", "permutasi")) {
                    if (pos >= s.length || s[pos] != '(') {
                        throw IllegalArgumentException("Gunakan format $name(n, r) atau nCr / nPr")
                    }
                    pos++ // '('
                    val nVal = parseAddSub()
                    if (pos >= s.length || s[pos] != ';') {
                        throw IllegalArgumentException("Pisahkan n dan r dengan koma, misal C(5, 2)")
                    }
                    pos++ // ';'
                    val rVal = parseAddSub()
                    if (pos >= s.length || s[pos] != ')') {
                        throw IllegalArgumentException("Kurung tutup ')' kurang")
                    }
                    pos++ // ')'
                    val nInt = toNonNegInt(nVal, "n")
                    val rInt = toNonNegInt(rVal, "r")
                    return if (name in setOf("c", "ncr", "kombinasi")) {
                        combBig(nInt, rInt).toDouble()
                    } else {
                        permBig(nInt, rInt).toDouble()
                    }
                }
                val arg = if (pos < s.length && s[pos] == '(') {
                    pos++
                    val v = parseAddSub()
                    if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException("Kurung tutup ')' kurang")
                    pos++
                    v
                } else {
                    parseUnary()
                }
                return applyFunc(name, arg)
            }
            throw IllegalArgumentException("Simbol tidak valid '$ch'")
        }

        private fun toNonNegInt(v: Double, label: String): Int {
            if (v.isNaN() || v.isInfinite() || v < 0.0 || abs(v - v.toLong().toDouble()) > 1e-9) {
                throw IllegalArgumentException("Nilai $label harus bilangan bulat ≥ 0")
            }
            if (v > 170.0) throw IllegalArgumentException("Nilai $label maksimal 170")
            return v.toLong().toInt()
        }

        private fun factorialBig(n: Int): BigInteger {
            var res = BigInteger.ONE
            for (i in 2..n) res = res.multiply(BigInteger.valueOf(i.toLong()))
            return res
        }

        private fun combBig(n: Int, r: Int): BigInteger {
            if (r > n) throw IllegalArgumentException("Untuk kombinasi C(n, r), nilai r ($r) tidak boleh > n ($n)")
            val k = minOf(r, n - r)
            var res = BigInteger.ONE
            for (i in 1..k) {
                res = res.multiply(BigInteger.valueOf((n - k + i).toLong()))
                    .divide(BigInteger.valueOf(i.toLong()))
            }
            return res
        }

        private fun permBig(n: Int, r: Int): BigInteger {
            if (r > n) throw IllegalArgumentException("Untuk permutasi P(n, r), nilai r ($r) tidak boleh > n ($n)")
            var res = BigInteger.ONE
            for (i in 0 until r) {
                res = res.multiply(BigInteger.valueOf((n - i).toLong()))
            }
            return res
        }

        private fun applyFunc(name: String, arg: Double): Double {
            val rad = if (deg) arg * PI / 180.0 else arg
            return when (name) {
                "sin" -> sin(rad)
                "cos" -> cos(rad)
                "tan" -> {
                    if (deg && ((abs(arg) % 180.0) == 90.0)) throw IllegalArgumentException("tan($arg°) tak terdefinisi")
                    tan(rad)
                }
                "log" -> {
                    if (arg <= 0) throw IllegalArgumentException("Logaritma butuh nilai > 0")
                    log10(arg)
                }
                "ln" -> {
                    if (arg <= 0) throw IllegalArgumentException("ln butuh nilai > 0")
                    ln(arg)
                }
                "sqrt", "akar" -> {
                    if (arg < 0) throw IllegalArgumentException("Akar negatif tidak real")
                    sqrt(arg)
                }
                "cbrt" -> if (arg < 0) -(-arg).pow(1.0 / 3.0) else arg.pow(1.0 / 3.0)
                "abs" -> abs(arg)
                else -> throw IllegalArgumentException("Fungsi \"$name\" tidak dikenal")
            }
        }

        private fun normalize(raw: String): String {
            var pre = raw.trim()
            val prefixSpace = Regex("""^\s*(kombinasi|permutasi|ncr|npr|c|p)\s+(\d+)\s+(\d+)\s*$""", RegexOption.IGNORE_CASE)
                .matchEntire(pre)
            if (prefixSpace != null) {
                pre = "${prefixSpace.groupValues[1]}(${prefixSpace.groupValues[2]},${prefixSpace.groupValues[3]})"
            }
            pre = pre.replace(Regex("""(?<=[\d\)\!])\s*kombinasi\s*(?=[\d\(])""", RegexOption.IGNORE_CASE), "C")
                .replace(Regex("""(?<=[\d\)\!])\s*permutasi\s*(?=[\d\(])""", RegexOption.IGNORE_CASE), "P")

            var base = pre.replace(" ", "")
                .replace('×', '*')
                .replace('÷', '/')
                .replace(':', '/')
                .replace('−', '-')
                .replace("nCr", "C", ignoreCase = true)
                .replace("nPr", "P", ignoreCase = true)

            // Bedakan koma pemisah argumen pada C(n,r) / P(n,r) / kombinasi(n,r) / permutasi(n,r)
            // dengan koma desimal (misal 2,5 -> 2.5).
            val argCommaConverted = StringBuilder(base.length)
            var i = 0
            while (i < base.length) {
                val restLower = base.substring(i).lowercase()
                val fnMatch = listOf("kombinasi(", "permutasi(", "ncr(", "npr(", "c(", "p(")
                    .firstOrNull { restLower.startsWith(it) && (i == 0 || !base[i - 1].isLetter()) }
                if (fnMatch != null) {
                    val fnName = base.substring(i, i + fnMatch.length)
                    argCommaConverted.append(fnName)
                    i += fnMatch.length
                    var depth = 1
                    while (i < base.length && depth > 0) {
                        val c = base[i]
                        when {
                            c == '(' -> { depth++; argCommaConverted.append(c) }
                            c == ')' -> { depth--; argCommaConverted.append(c) }
                            c == ',' && depth == 1 -> argCommaConverted.append(';')
                            c == ',' -> argCommaConverted.append('.')
                            else -> argCommaConverted.append(c)
                        }
                        i++
                    }
                } else {
                    val c = base[i]
                    argCommaConverted.append(if (c == ',') '.' else c)
                    i++
                }
            }
            base = argCommaConverted.toString()

            // Ubah huruf c/p di antara angka atau kurung (misal 5c2 / (3+2)c2) jadi huruf besar C / P
            val infixNormalized = StringBuilder(base.length)
            for (idx in base.indices) {
                val c = base[idx]
                val prev = if (idx > 0) base[idx - 1] else '\u0000'
                val next = if (idx + 1 < base.length) base[idx + 1] else '\u0000'
                val isInfixCP = (c == 'c' || c == 'p' || c == 'C' || c == 'P') &&
                    (prev.isDigit() || prev == ')' || prev == '!') &&
                    (next.isDigit() || next == '(')
                if (isInfixCP) {
                    infixNormalized.append(c.uppercaseChar())
                } else {
                    infixNormalized.append(c)
                }
            }
            base = infixNormalized.toString()

            // Sisipkan perkalian implisit sederhana: 2π -> 2*π, 2( -> 2*(, )( -> )*(, 2sin -> 2*sin
            val sb = StringBuilder()
            for (idx in base.indices) {
                val c = base[idx]
                if (idx > 0) {
                    val prev = base[idx - 1]
                    val prevEndsVal = prev.isDigit() || prev == ')' || prev == 'π' || prev == '%' || prev == '²' || prev == '³' || prev == '!'
                    val isInfixCombPerm = (c == 'C' || c == 'P') &&
                        (idx + 1 < base.length && (base[idx + 1] != '(' || !hasTopLevelArgSep(base, idx + 1)))
                    val currStartsGroup = !isInfixCombPerm &&
                        (c == '(' || c == 'π' || c == '∛' || (c.isLetter() && !prev.isLetter()))
                    if (prevEndsVal && currStartsGroup) {
                        sb.append('*')
                    }
                }
                sb.append(c)
            }
            return sb.toString()
        }

        private fun hasTopLevelArgSep(str: String, openParenIdx: Int): Boolean {
            if (openParenIdx >= str.length || str[openParenIdx] != '(') return false
            var depth = 0
            for (j in openParenIdx until str.length) {
                when (str[j]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return false
                    }
                    ';' -> if (depth == 1) return true
                }
            }
            return false
        }
    }

    private class RationalParser(raw: String) {
        private val s = raw.replace(" ", "")
            .replace('×', '*')
            .replace('÷', '/')
            .replace(':', '/')
            .replace('−', '-')
            .replace(',', '.')
        private var pos = 0

        fun parse(): ExactFraction {
            val v = parseAddSub()
            if (pos < s.length) throw IllegalArgumentException("Sisa karakter")
            return v
        }

        private fun parseAddSub(): ExactFraction {
            var x = parseMulDiv()
            while (pos < s.length) {
                when (s[pos]) {
                    '+' -> { pos++; x += parseMulDiv() }
                    '-' -> { pos++; x -= parseMulDiv() }
                    else -> return x
                }
            }
            return x
        }

        private fun parseMulDiv(): ExactFraction {
            var x = parsePow()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; x *= parsePow() }
                    '/' -> { pos++; x /= parsePow() }
                    else -> return x
                }
            }
            return x
        }

        private fun parsePow(): ExactFraction {
            var x = parseUnary()
            if (pos < s.length && s[pos] == '^') {
                pos++
                val exp = parsePow()
                if (!exp.isInteger || exp.num.abs() > BigInteger.valueOf(30)) {
                    throw IllegalArgumentException("Pangkat bukan bulat kecil")
                }
                x = x.pow(exp.num.toInt())
            }
            return x
        }

        private fun parseUnary(): ExactFraction {
            if (pos < s.length && s[pos] == '+') { pos++; return parseUnary() }
            if (pos < s.length && s[pos] == '-') { pos++; return parseUnary().negate() }
            return parsePostfix()
        }

        private fun parsePostfix(): ExactFraction {
            var x = parsePrimary()
            val hundred = ExactFraction.of(100)
            while (pos < s.length) {
                when (s[pos]) {
                    '%' -> { pos++; x /= hundred }
                    '²' -> { pos++; x *= x }
                    '³' -> { pos++; x = x * x * x }
                    else -> break
                }
            }
            return x
        }

        private fun parsePrimary(): ExactFraction {
            if (pos < s.length && s[pos] == '(') {
                pos++
                val v = parseAddSub()
                if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException("Kurung")
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("Bukan angka")
            return ExactFraction.parse(s.substring(start, pos))
                ?: throw IllegalArgumentException("Format")
        }
    }
}
