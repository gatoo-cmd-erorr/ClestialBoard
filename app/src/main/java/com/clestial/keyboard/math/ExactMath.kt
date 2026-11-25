package com.clestial.keyboard.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Pecahan eksak berbasis [BigInteger] tanpa floating point untuk perhitungan inti.
 * Penyebut selalu positif (> 0) dan pecahan selalu disederhanakan dengan FPB/GCD.
 */
class ExactFraction private constructor(
    val num: BigInteger,
    val den: BigInteger
) : Comparable<ExactFraction> {

    val isZero: Boolean get() = num == BigInteger.ZERO
    val isOne: Boolean get() = num == BigInteger.ONE && den == BigInteger.ONE
    val isInteger: Boolean get() = den == BigInteger.ONE
    val signum: Int get() = num.signum()

    operator fun plus(other: ExactFraction): ExactFraction {
        if (this.isZero) return other
        if (other.isZero) return this
        val g = den.gcd(other.den)
        val lcm = den.divide(g).multiply(other.den)
        val n = num.multiply(lcm.divide(den)).add(other.num.multiply(lcm.divide(other.den)))
        return of(n, lcm)
    }

    operator fun minus(other: ExactFraction): ExactFraction = plus(other.negate())

    operator fun times(other: ExactFraction): ExactFraction {
        if (this.isZero || other.isZero) return ZERO
        return of(num.multiply(other.num), den.multiply(other.den))
    }

    operator fun div(other: ExactFraction): ExactFraction {
        require(!other.isZero) { "Pembagian dengan nol tidak diizinkan." }
        return of(num.multiply(other.den), den.multiply(other.num))
    }

    fun negate(): ExactFraction = if (isZero) ZERO else ExactFraction(num.negate(), den)

    fun abs(): ExactFraction = if (num.signum() < 0) negate() else this

    fun pow(exp: Int): ExactFraction {
        return when {
            exp == 0 -> ONE
            exp > 0 -> of(num.pow(exp), den.pow(exp))
            else -> {
                require(!isZero) { "Nol tidak bisa dipangkatkan bilangan negatif." }
                val posExp = -exp
                of(den.pow(posExp), num.pow(posExp))
            }
        }
    }

    override fun compareTo(other: ExactFraction): Int =
        num.multiply(other.den).compareTo(other.num.multiply(den))

    override fun equals(other: Any?): Boolean =
        other is ExactFraction && num == other.num && den == other.den

    override fun hashCode(): Int = 31 * num.hashCode() + den.hashCode()

    /** Bentuk pecahan murni disederhanakan, misalnya "-2" atau "7/3". */
    fun toFracString(): String = if (isInteger) num.toString() else "$num/$den"

    /** Bentuk pecahan campuran jika |pembilang| > penyebut, misalnya "4 3/7". */
    fun toMixedString(): String {
        if (isInteger) return num.toString()
        val absN = num.abs()
        if (absN <= den) return toFracString()
        val whole = num.divide(den)
        val rem = absN.remainder(den)
        return "$whole $rem/$den"
    }

    /** Gabungan pecahan eksak dan pecahan campuran bila relevan, misalnya "31/7 (4 3/7)". */
    fun toExactDisplay(): String {
        if (isInteger) return num.toString()
        return if (num.abs() > den) "${toFracString()} (${toMixedString()})" else toFracString()
    }

    /** Konversi desimal terkendali hanya untuk tampilan opsional/aproksimasi (memakai BigDecimal). */
    fun toDecimalApprox(scale: Int = 4): String {
        if (isInteger) return num.toString()
        val bd = BigDecimal(num).divide(BigDecimal(den), scale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        val s = if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
        return s.replace('.', ',')
    }

    override fun toString(): String = toFracString()

    companion object {
        val ZERO = ExactFraction(BigInteger.ZERO, BigInteger.ONE)
        val ONE = ExactFraction(BigInteger.ONE, BigInteger.ONE)
        val MINUS_ONE = ExactFraction(BigInteger.ONE.negate(), BigInteger.ONE)
        val PI_22_7 = of(22, 7)
        val PI_3_14 = of(314, 100)

        fun of(num: Long, den: Long = 1L): ExactFraction =
            of(BigInteger.valueOf(num), BigInteger.valueOf(den))

        fun of(num: BigInteger, den: BigInteger = BigInteger.ONE): ExactFraction {
            require(den != BigInteger.ZERO) { "Penyebut tidak boleh nol." }
            if (num == BigInteger.ZERO) return ZERO
            var n = num
            var d = den
            if (d.signum() < 0) {
                n = n.negate()
                d = d.negate()
            }
            val g = n.abs().gcd(d)
            return ExactFraction(n.divide(g), d.divide(g))
        }

        /**
         * Parse bilangan bulat, desimal berhingga (titik atau koma), pecahan (a/b), atau campuran (k a/b)
         * tanpa floating point. Mengembalikan null bila format tidak valid.
         */
        fun parse(raw: String): ExactFraction? {
            val s = raw.trim().replace('−', '-').replace(',', '.')
            if (s.isEmpty()) return null

            // Pecahan campuran: "2 1/3" atau "-2 1/3"
            val spaceIdx = s.indexOf(' ')
            if (spaceIdx > 0) {
                val wholePart = s.substring(0, spaceIdx).trim()
                val fracPart = s.substring(spaceIdx + 1).trim()
                if (!wholePart.matches(Regex("[+-]?\\d+")) || !fracPart.contains('/')) return null
                val whole = wholePart.toBigIntegerOrNull() ?: return null
                val frac = parseSimpleFraction(fracPart) ?: return null
                if (frac.signum < 0) return null
                val absWhole = whole.abs()
                val combined = of(absWhole, BigInteger.ONE) + frac
                return if (wholePart.startsWith('-')) combined.negate() else combined
            }

            if (s.contains('/')) return parseSimpleFraction(s)

            // Desimal berhingga atau bilangan bulat: opsional +/-, digit, opsional .digit
            if (!s.matches(Regex("[+-]?(\\d+(\\.\\d+)?|\\.\\d+)"))) return null
            val neg = s.startsWith('-')
            val unsigned = if (s.startsWith('-') || s.startsWith('+')) s.substring(1) else s
            val dotIdx = unsigned.indexOf('.')
            val frac = if (dotIdx < 0) {
                val n = unsigned.toBigIntegerOrNull() ?: return null
                of(n, BigInteger.ONE)
            } else {
                val intStr = unsigned.substring(0, dotIdx).ifEmpty { "0" }
                val decStr = unsigned.substring(dotIdx + 1)
                if (decStr.isEmpty()) return null
                val combinedDigits = (intStr + decStr).toBigIntegerOrNull() ?: return null
                val scaleDen = BigInteger.TEN.pow(decStr.length)
                of(combinedDigits, scaleDen)
            }
            return if (neg) frac.negate() else frac
        }

        private fun parseSimpleFraction(s: String): ExactFraction? {
            val parts = s.split('/')
            if (parts.size != 2) return null
            val nStr = parts[0].trim()
            val dStr = parts[1].trim()
            if (!nStr.matches(Regex("[+-]?\\d+")) || !dStr.matches(Regex("[+-]?\\d+"))) return null
            val n = nStr.toBigIntegerOrNull() ?: return null
            val d = dStr.toBigIntegerOrNull() ?: return null
            if (d == BigInteger.ZERO) return null
            return of(n, d)
        }
    }
}

/** Utilitas bentuk akar, faktorisasi kuadrat/kubik sempurna, dan notasi matematika rapi. */
object ExactMath {

    /** Akar kuadrat bulat floor(√n) menggunakan metode Newton BigInteger (tanpa floating point). */
    fun isqrt(n: BigInteger): BigInteger {
        require(n.signum() >= 0) { "Bilangan di bawah akar tidak boleh negatif." }
        if (n == BigInteger.ZERO || n == BigInteger.ONE) return n
        var x = BigInteger.ONE.shiftLeft((n.bitLength() + 1) / 2)
        while (true) {
            val next = x.add(n.divide(x)).shiftRight(1)
            if (next >= x) return x
            x = next
        }
    }

    /** Akar pangkat tiga bulat floor(∛n) untuk n >= 0. */
    fun icbrt(n: BigInteger): BigInteger {
        require(n.signum() >= 0) { "Bilangan untuk akar pangkat tiga harus >= 0." }
        if (n == BigInteger.ZERO || n == BigInteger.ONE) return n
        var x = BigInteger.ONE.shiftLeft((n.bitLength() + 2) / 3)
        val two = BigInteger.valueOf(2)
        val three = BigInteger.valueOf(3)
        while (true) {
            val x2 = x.multiply(x)
            val next = two.multiply(x).add(n.divide(x2)).divide(three)
            if (next >= x) return x
            x = next
        }
    }

    /**
     * Menyederhanakan √n menjadi (outer, inner) sehingga √n = outer√inner
     * dan inner bebas kuadrat sempurna. Contoh: 72 -> (6, 2).
     */
    fun simplifySqrt(n: BigInteger): Pair<BigInteger, BigInteger> {
        require(n.signum() >= 0) { "Akar kuadrat bilangan negatif tidak terdefinisi di himpunan real." }
        if (n == BigInteger.ZERO) return BigInteger.ZERO to BigInteger.ONE
        if (n == BigInteger.ONE) return BigInteger.ONE to BigInteger.ONE

        val rootCheck = isqrt(n)
        if (rootCheck.multiply(rootCheck) == n) return rootCheck to BigInteger.ONE

        var outer = BigInteger.ONE
        var rem = n

        // Faktorisasi pangkat 2^2 = 4.
        val four = BigInteger.valueOf(4)
        val two = BigInteger.valueOf(2)
        while (rem.remainder(four) == BigInteger.ZERO) {
            outer = outer.multiply(two)
            rem = rem.divide(four)
        }

        var d = 3L
        val limit = 500_000L
        while (d <= limit) {
            val dBig = BigInteger.valueOf(d)
            val dSq = dBig.multiply(dBig)
            if (dSq > rem) break
            while (rem.remainder(dSq) == BigInteger.ZERO) {
                outer = outer.multiply(dBig)
                rem = rem.divide(dSq)
            }
            d += 2L
        }

        val remRoot = isqrt(rem)
        if (remRoot > BigInteger.ONE && remRoot.multiply(remRoot) == rem) {
            outer = outer.multiply(remRoot)
            rem = BigInteger.ONE
        }
        return outer to rem
    }

    /** Menyederhanakan ∛n menjadi (outer, inner) sehingga ∛n = outer∛inner. */
    fun simplifyCbrt(n: BigInteger): Pair<BigInteger, BigInteger> {
        require(n.signum() >= 0) { "Bilangan harus >= 0." }
        if (n == BigInteger.ZERO) return BigInteger.ZERO to BigInteger.ONE
        val rootCheck = icbrt(n)
        if (rootCheck.pow(3) == n) return rootCheck to BigInteger.ONE

        var outer = BigInteger.ONE
        var rem = n
        val eight = BigInteger.valueOf(8)
        val two = BigInteger.valueOf(2)
        while (rem.remainder(eight) == BigInteger.ZERO) {
            outer = outer.multiply(two)
            rem = rem.divide(eight)
        }
        var d = 3L
        while (d <= 50_000L) {
            val dBig = BigInteger.valueOf(d)
            val dCube = dBig.pow(3)
            if (dCube > rem) break
            while (rem.remainder(dCube) == BigInteger.ZERO) {
                outer = outer.multiply(dBig)
                rem = rem.divide(dCube)
            }
            d += 2L
        }
        return outer to rem
    }

    /** Menyederhanakan √(frac) = numOuter√rad / den. */
    fun simplifyFractionSqrt(f: ExactFraction): QuadraticSurd {
        require(f.signum >= 0) { "Akar kuadrat pecahan negatif tidak real." }
        if (f.isZero) return QuadraticSurd.ZERO
        // √(a/b) = √(a*b) / b
        val product = f.num.multiply(f.den)
        val (outer, inner) = simplifySqrt(product)
        val coeff = ExactFraction.of(outer, f.den)
        return QuadraticSurd(ExactFraction.ZERO, coeff, inner)
    }

    /** Format bilangan √n yang sudah disederhanakan: 6√2, 3, atau √5. */
    fun formatSimplifiedSqrt(n: BigInteger): String {
        val (outer, inner) = simplifySqrt(n)
        return formatRadicalTerm(outer, inner)
    }

    fun formatRadicalTerm(outer: BigInteger, inner: BigInteger): String {
        if (outer == BigInteger.ZERO || inner == BigInteger.ZERO) return "0"
        if (inner == BigInteger.ONE) return outer.toString()
        return when (outer) {
            BigInteger.ONE -> "√$inner"
            BigInteger.ONE.negate() -> "-√$inner"
            else -> "${outer}√$inner"
        }
    }

    /** Ubah pangkat bilangan bulat ke karakter superskrip rapi di layar HP, misal -4 -> ⁻⁴. */
    fun toSuperscript(exp: Int): String = toSuperscript(exp.toString())

    fun toSuperscript(text: String): String = buildString {
        for (ch in text) {
            append(
                when (ch) {
                    '0' -> '⁰'
                    '1' -> '¹'
                    '2' -> '²'
                    '3' -> '³'
                    '4' -> '⁴'
                    '5' -> '⁵'
                    '6' -> '⁶'
                    '7' -> '⁷'
                    '8' -> '⁸'
                    '9' -> '⁹'
                    '-' -> '⁻'
                    '+' -> '⁺'
                    '/' -> '⁄'
                    else -> ch
                }
            )
        }
    }

    /** Format suku pertama ekspresi aljabar, misal (1, "x") -> "x", (-1, "x") -> "-x", (0, "x") -> "". */
    fun formatTerm(coeff: ExactFraction, varName: String, isFirst: Boolean): String {
        if (coeff.isZero) return ""
        val absC = coeff.abs()
        val body = if (varName.isEmpty()) {
            absC.toFracString()
        } else if (absC.isOne) {
            varName
        } else if (absC.isInteger) {
            "${absC.num}$varName"
        } else {
            "(${absC.toFracString()})$varName"
        }
        return if (isFirst) {
            if (coeff.signum < 0) "-$body" else body
        } else {
            if (coeff.signum < 0) " - $body" else " + $body"
        }
    }

    /** Format kombinasi ax + by, menghindari "+ -3y" menjadi "- 3y". */
    fun formatLinearLhs(a: ExactFraction, varA: String, b: ExactFraction, varB: String): String {
        val parts = StringBuilder()
        var first = true
        if (!a.isZero) {
            parts.append(formatTerm(a, varA, first))
            first = false
        }
        if (!b.isZero) {
            parts.append(formatTerm(b, varB, first))
            first = false
        }
        if (first) parts.append("0")
        return parts.toString()
    }

    /** Format persamaan ax² + bx + c = 0 tanpa tanda ganda atau koefisien 1 pada variabel. */
    fun formatQuadraticExpr(a: ExactFraction, b: ExactFraction, c: ExactFraction): String {
        val sb = StringBuilder()
        var first = true
        if (!a.isZero) {
            sb.append(formatTerm(a, "x²", first))
            first = false
        }
        if (!b.isZero) {
            sb.append(formatTerm(b, "x", first))
            first = false
        }
        if (!c.isZero) {
            sb.append(formatTerm(c, "", first))
            first = false
        }
        if (first) sb.append("0")
        return sb.toString()
    }

    /** Format hasil dengan faktor π atau dengan π = 22/7 / 3,14 secara eksak. */
    fun formatPiExact(coeff: ExactFraction): String {
        if (coeff.isZero) return "0"
        if (coeff.isOne) return "π"
        if (coeff == ExactFraction.MINUS_ONE) return "-π"
        return if (coeff.isInteger) "${coeff.num}π" else "${coeff.toFracString()}π"
    }

    fun suggests22Per7(r: ExactFraction): Boolean {
        val seven = BigInteger.valueOf(7)
        return r.signum > 0 && r.isInteger && r.num.remainder(seven) == BigInteger.ZERO
    }
}

/** Mode tampilan konstanta π untuk modul Bangun Ruang. */
enum class PiMode(val label: String) {
    SYMBOL("Simbol π (eksak)"),
    FRACTION_22_7("22/7"),
    DECIMAL_3_14("3,14");

    companion object {
        fun fromChoice(raw: String?): PiMode = when (raw?.trim()?.lowercase()) {
            "22/7", "22per7", "fraction_22_7" -> FRACTION_22_7
            "3,14", "3.14", "decimal_3_14" -> DECIMAL_3_14
            else -> SYMBOL
        }
    }
}

/**
 * Representasi eksak bentuk (rational + radicalCoeff * √radicand).
 * Digunakan untuk akar persamaan kuadrat, rasionalisasi penyebut, dan tinggi/pelukis kerucut.
 */
data class QuadraticSurd(
    val rational: ExactFraction,
    val radicalCoeff: ExactFraction,
    val radicand: BigInteger
) {
    val isPureRational: Boolean get() = radicalCoeff.isZero || radicand == BigInteger.ONE

    fun normalized(): QuadraticSurd {
        if (radicalCoeff.isZero || radicand == BigInteger.ZERO) {
            return QuadraticSurd(rational, ExactFraction.ZERO, BigInteger.ONE)
        }
        val (outer, inner) = ExactMath.simplifySqrt(radicand)
        if (inner == BigInteger.ONE) {
            val merged = rational + (radicalCoeff * ExactFraction.of(outer, BigInteger.ONE))
            return QuadraticSurd(merged, ExactFraction.ZERO, BigInteger.ONE)
        }
        return QuadraticSurd(rational, radicalCoeff * ExactFraction.of(outer, BigInteger.ONE), inner)
    }

    fun format(): String {
        val norm = normalized()
        if (norm.radicalCoeff.isZero || norm.radicand == BigInteger.ONE) {
            return norm.rational.toFracString()
        }
        val lcmDen = lcm(norm.rational.den, norm.radicalCoeff.den)
        val aNum = norm.rational.num.multiply(lcmDen.divide(norm.rational.den))
        val bNum = norm.radicalCoeff.num.multiply(lcmDen.divide(norm.radicalCoeff.den))
        val g = aNum.abs().gcd(bNum.abs()).gcd(lcmDen)
        val a = aNum.divide(g)
        val b = bNum.divide(g)
        val d = lcmDen.divide(g)

        val radPart = ExactMath.formatRadicalTerm(b.abs(), norm.radicand)
        val body = when {
            a == BigInteger.ZERO -> if (b.signum() < 0) "-$radPart" else radPart
            b.signum() > 0 -> "$a + $radPart"
            else -> "$a - $radPart"
        }
        return if (d == BigInteger.ONE) {
            body
        } else if (a == BigInteger.ZERO) {
            "$body/$d"
        } else {
            "($body)/$d"
        }
    }

    /** Format bentuk gabungan p ± q√r atau (p ± q√r)/d. */
    fun formatPlusMinus(): String {
        val norm = normalized()
        if (norm.radicalCoeff.isZero || norm.radicand == BigInteger.ONE) {
            return norm.rational.toFracString()
        }
        val lcmDen = lcm(norm.rational.den, norm.radicalCoeff.den)
        val aNum = norm.rational.num.multiply(lcmDen.divide(norm.rational.den))
        val bNum = norm.radicalCoeff.num.multiply(lcmDen.divide(norm.radicalCoeff.den))
        val g = aNum.abs().gcd(bNum.abs()).gcd(lcmDen)
        val a = aNum.divide(g)
        val b = bNum.divide(g)
        val d = lcmDen.divide(g)

        val radPart = ExactMath.formatRadicalTerm(b.abs(), norm.radicand)
        val body = if (a == BigInteger.ZERO) "±$radPart" else "$a ± $radPart"
        return if (d == BigInteger.ONE) body else if (a == BigInteger.ZERO) "$body/$d" else "($body)/$d"
    }

    /** Nilai aproksimasi desimal opsional (hanya bila pengguna meminta tampilan desimal). */
    fun toDecimalApprox(scale: Int = 4): String {
        val norm = normalized()
        if (norm.radicalCoeff.isZero || norm.radicand == BigInteger.ONE) {
            return norm.rational.toDecimalApprox(scale)
        }
        // Gunakan ekspansi presisi tinggi dengan BigDecimal Newton sqrt.
        val workScale = scale + 8
        val rDec = BigDecimal(norm.radicand)
        var x = BigDecimal(ExactMath.isqrt(norm.radicand)).max(BigDecimal.ONE)
        val two = BigDecimal.valueOf(2)
        repeat(18) {
            x = x.add(rDec.divide(x, workScale, RoundingMode.HALF_UP))
                .divide(two, workScale, RoundingMode.HALF_UP)
        }
        val ratBd = BigDecimal(norm.rational.num)
            .divide(BigDecimal(norm.rational.den), workScale, RoundingMode.HALF_UP)
        val coeffBd = BigDecimal(norm.radicalCoeff.num)
            .divide(BigDecimal(norm.radicalCoeff.den), workScale, RoundingMode.HALF_UP)
        val total = ratBd.add(coeffBd.multiply(x))
            .setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        val s = if (total.scale() < 0) total.setScale(0).toPlainString() else total.toPlainString()
        return s.replace('.', ',')
    }

    companion object {
        val ZERO = QuadraticSurd(ExactFraction.ZERO, ExactFraction.ZERO, BigInteger.ONE)

        private fun lcm(a: BigInteger, b: BigInteger): BigInteger =
            a.divide(a.gcd(b)).multiply(b)
    }
}
