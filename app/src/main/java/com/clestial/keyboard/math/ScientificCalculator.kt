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
 * akar pangkat n (n√x), kebalikan (1/x), π, sin, cos, tan, log, ln.
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
            var x = parsePower()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; x *= parsePower() }
                    '/' -> {
                        pos++
                        val d = parsePower()
                        if (d == 0.0) throw IllegalArgumentException("Pembagian dengan nol")
                        x /= d
                    }
                    else -> return x
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
            val base = raw.replace(" ", "")
                .replace('×', '*')
                .replace('÷', '/')
                .replace(':', '/')
                .replace('−', '-')
                .replace(',', '.')
            // Sisipkan perkalian implisit sederhana: 2π -> 2*π, 2( -> 2*(, )( -> )*(, 2sin -> 2*sin
            val sb = StringBuilder()
            for (i in base.indices) {
                val c = base[i]
                if (i > 0) {
                    val prev = base[i - 1]
                    val prevEndsVal = prev.isDigit() || prev == ')' || prev == 'π' || prev == '%' || prev == '²' || prev == '³'
                    val currStartsGroup = c == '(' || c == 'π' || c == '∛' || (c.isLetter() && !prev.isLetter())
                    if (prevEndsVal && currStartsGroup) {
                        sb.append('*')
                    }
                }
                sb.append(c)
            }
            return sb.toString()
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
