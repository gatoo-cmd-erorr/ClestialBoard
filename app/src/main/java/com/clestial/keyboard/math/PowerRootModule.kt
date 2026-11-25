package com.clestial.keyboard.math

import java.math.BigInteger

/**
 * Modul 2 (Aljabar): Bilangan Berpangkat dan Bentuk Akar.
 * Sub-fitur: operasi pangkat, notasi ilmiah dua arah, penyederhanaan akar √n,
 * dan rasionalisasi penyebut a/√b serta a/(b ± √c).
 */
class PowerRootModule : MathModule {

    override val id = "pangkat_akar"
    override val name = "Bilangan Berpangkat & Bentuk Akar"
    override val group = MathGroup.ALJABAR
    override val description = "Operasi pangkat, notasi ilmiah, penyederhanaan √n, dan rasionalisasi penyebut."

    override val fields = listOf(
        InputFieldSpec(
            key = "mode",
            label = "Sub-fitur",
            type = FieldType.CHOICE,
            choices = listOf(MODE_PANGKAT, MODE_ILMIAH, MODE_AKAR, MODE_RASIONAL),
            defaultValue = MODE_PANGKAT
        ),
        InputFieldSpec("a", "Nilai a / basis / pembilang", "2", required = false),
        InputFieldSpec("op", "Operasi (×, :, ^, 0, -n) atau tanda (+ / - / tunggal)", "×", required = false),
        InputFieldSpec("m", "Pangkat m / penyebut akar b / nilai ilmiah", "3", required = false),
        InputFieldSpec("n", "Pangkat n / angka akar c", "4", required = false),
        InputFieldSpec(
            key = "desimal",
            label = "Tampilkan perkiraan desimal?",
            type = FieldType.CHOICE,
            required = false,
            choices = listOf("Tidak", "Ya"),
            defaultValue = "Tidak"
        )
    )

    override fun solve(input: Map<String, String>): SolveResult {
        val mode = input["mode"]?.trim().orEmpty().ifEmpty { MODE_PANGKAT }
        val showDecimal = input["desimal"]?.trim()?.equals("Ya", ignoreCase = true) == true
        return when (mode) {
            MODE_PANGKAT -> solvePower(input)
            MODE_ILMIAH -> solveScientific(input)
            MODE_AKAR -> solveSimplifySqrt(input, showDecimal)
            MODE_RASIONAL -> solveRationalize(input, showDecimal)
            else -> SolveResult.fail("Sub-fitur tidak dikenal: $mode")
        }
    }

    private fun solvePower(input: Map<String, String>): SolveResult {
        val (base, errA) = InputChecks.requireFraction(input, "a", "Basis (a)")
        if (errA != null) return SolveResult.fail(errA)
        base!!

        val op = input["op"]?.trim().orEmpty().ifEmpty { "×" }.lowercase()
        val steps = ArrayList<String>()

        return when (op) {
            "0", "nol" -> {
                if (base.isZero) return SolveResult.fail("0⁰ merupakan bentuk tak tentu.")
                steps.add("Sifat pangkat nol: a⁰ = 1 untuk setiap a ≠ 0.")
                steps.add("(${base.toFracString()})⁰ = 1")
                SolveResult(
                    ok = true,
                    steps = steps,
                    answer = "1",
                    check = "Cek sifat: (${base.toFracString()})¹ : (${base.toFracString()})¹ = (${base.toFracString()})¹⁻¹ = (${base.toFracString()})⁰ = 1."
                )
            }
            "-n", "neg", "negatif" -> {
                val (m, errM) = InputChecks.requireInt(input, "m", "Pangkat (n)", min = -50, max = 50)
                if (errM != null) return SolveResult.fail(errM)
                m!!
                if (base.isZero && m <= 0) return SolveResult.fail("Basis 0 tidak bisa dipangkatkan 0 atau negatif.")
                val negExp = if (m > 0) -m else m
                val posExp = -negExp
                val value = base.pow(negExp)
                steps.add("Sifat pangkat negatif: a⁻ⁿ = 1 / aⁿ.")
                steps.add("(${base.toFracString()})${ExactMath.toSuperscript(negExp)} = 1 / (${base.toFracString()})${ExactMath.toSuperscript(posExp)}")
                steps.add("Hitung: ${value.toFracString()}")
                val inv = ExactFraction.ONE / value
                val checkBack = base.pow(posExp)
                SolveResult(
                    ok = true,
                    steps = steps,
                    answer = "${base.toFracString()}${ExactMath.toSuperscript(negExp)} = ${value.toFracString()}",
                    check = "Cek kebalikan: 1 / (${value.toFracString()}) = ${inv.toFracString()} = (${base.toFracString()})${ExactMath.toSuperscript(posExp)} = ${checkBack.toFracString()}."
                )
            }
            else -> {
                val (m, errM) = InputChecks.requireInt(input, "m", "Pangkat pertama (m)", min = -50, max = 50)
                if (errM != null) return SolveResult.fail(errM)
                val (n, errN) = InputChecks.requireInt(input, "n", "Pangkat kedua (n)", min = -50, max = 50)
                if (errN != null) return SolveResult.fail(errN)
                m!!; n!!

                val totalExp: Int
                val exprDesc: String
                when (op) {
                    "×", "*", "x", "kali" -> {
                        totalExp = m + n
                        exprDesc = "${base.toFracString()}${ExactMath.toSuperscript(m)} × ${base.toFracString()}${ExactMath.toSuperscript(n)}"
                        steps.add("Sifat perkalian bilangan berpangkat: aᵐ × aⁿ = aᵐ⁺ⁿ")
                        steps.add("$exprDesc = ${base.toFracString()}${ExactMath.toSuperscript("$m + ($n)")} = ${base.toFracString()}${ExactMath.toSuperscript(totalExp)}")
                    }
                    ":", "/", "÷", "bagi" -> {
                        if (base.isZero && (n <= 0 || m - n <= 0)) {
                            return SolveResult.fail("Pembagian oleh pangkat berbasis 0 tidak terdefinisi.")
                        }
                        totalExp = m - n
                        exprDesc = "${base.toFracString()}${ExactMath.toSuperscript(m)} : ${base.toFracString()}${ExactMath.toSuperscript(n)}"
                        steps.add("Sifat pembagian bilangan berpangkat: aᵐ : aⁿ = aᵐ⁻ⁿ")
                        steps.add("$exprDesc = ${base.toFracString()}${ExactMath.toSuperscript("$m - ($n)")} = ${base.toFracString()}${ExactMath.toSuperscript(totalExp)}")
                    }
                    "^", "pangkat" -> {
                        totalExp = m * n
                        exprDesc = "(${base.toFracString()}${ExactMath.toSuperscript(m)})${ExactMath.toSuperscript(n)}"
                        steps.add("Sifat pangkat dari bilangan berpangkat: (aᵐ)ⁿ = aᵐˣⁿ")
                        steps.add("$exprDesc = ${base.toFracString()}${ExactMath.toSuperscript("$m × $n")} = ${base.toFracString()}${ExactMath.toSuperscript(totalExp)}")
                    }
                    else -> return SolveResult.fail("Operasi pangkat tidak dikenali: \"$op\". Gunakan ×, :, ^, 0, atau -n.")
                }

                if (base.isZero && totalExp <= 0) {
                    return SolveResult.fail("0 tidak dapat dipangkatkan $totalExp.")
                }
                val resultVal = base.pow(totalExp)
                val sym = "${base.toFracString()}${ExactMath.toSuperscript(totalExp)}"
                steps.add("Nilai eksak: $sym = ${resultVal.toFracString()}")

                val checkDirect = when (op) {
                    "×", "*", "x", "kali" -> base.pow(m) * base.pow(n)
                    ":", "/", "÷", "bagi" -> base.pow(m) / base.pow(n)
                    else -> base.pow(m).pow(n)
                }
                SolveResult(
                    ok = true,
                    steps = steps,
                    answer = "$sym = ${resultVal.toFracString()}",
                    check = "Hitung langsung: hasil = ${checkDirect.toFracString()} (cocok dengan ${resultVal.toFracString()})."
                )
            }
        }
    }

    private fun solveScientific(input: Map<String, String>): SolveResult {
        val raw = input["a"]?.trim()?.takeIf { it.isNotEmpty() } ?: input["m"]?.trim().orEmpty()
        if (raw.isEmpty()) return SolveResult.fail("Masukkan bilangan biasa (misal 0,00045) atau notasi ilmiah (misal 4,5 x 10^-4).")

        val sciRegex = Regex("""^([+-]?\d+(?:[.,]\d+)?)\s*(?:[x×*]\s*10\^?|e)\s*\(?\s*([+-]?\d+)\s*\)?$""", RegexOption.IGNORE_CASE)
        val match = sciRegex.matchEntire(raw)
        val steps = ArrayList<String>()

        if (match != null) {
            // Ilmiah -> Biasa
            val mantissaStr = match.groupValues[1].replace(',', '.')
            val exp = match.groupValues[2].toIntOrNull()
                ?: return SolveResult.fail("Pangkat 10 terlalu besar atau tidak valid.")
            val neg = mantissaStr.startsWith('-')
            val unsigned = mantissaStr.removePrefix("-").removePrefix("+")
            val dot = unsigned.indexOf('.')
            val digits = unsigned.replace(".", "")
            val intLen = if (dot < 0) unsigned.length else dot
            val newPoint = intLen + exp

            val plain = buildString {
                if (neg) append('-')
                when {
                    newPoint <= 0 -> {
                        append("0,")
                        repeat(-newPoint) { append('0') }
                        append(digits.trimEnd('0').ifEmpty { "0" })
                    }
                    newPoint >= digits.length -> {
                        append(digits)
                        repeat(newPoint - digits.length) { append('0') }
                    }
                    else -> {
                        append(digits.substring(0, newPoint))
                        val dec = digits.substring(newPoint).trimEnd('0')
                        if (dec.isNotEmpty()) {
                            append(',')
                            append(dec)
                        }
                    }
                }
            }
            steps.add("Ubah notasi ilmiah ke bilangan biasa (geser koma sebanyak $exp langkah).")
            steps.add("$raw = $plain")
            return SolveResult(
                ok = true,
                steps = steps,
                answer = plain,
                check = "Ubah balik $plain ke bentuk a × 10ⁿ menghasilkan $raw."
            )
        }

        // Biasa -> Ilmiah (murni string/BigInteger, tanpa floating point)
        val clean = raw.replace(',', '.')
        if (!clean.matches(Regex("[+-]?(\\d+(\\.\\d+)?|\\.\\d+)"))) {
            return SolveResult.fail("Format bilangan \"$raw\" tidak valid untuk notasi ilmiah.")
        }
        val neg = clean.startsWith('-')
        val unsigned = clean.removePrefix("-").removePrefix("+")
        val fracCheck = ExactFraction.parse(clean) ?: return SolveResult.fail("Format bilangan tidak valid.")
        if (fracCheck.isZero) {
            return SolveResult(
                ok = true,
                steps = listOf("Nilai 0 dalam notasi ilmiah tetap 0."),
                answer = "0",
                check = "0 × 10⁰ = 0"
            )
        }

        val dotIdx = unsigned.indexOf('.')
        val rawDigits = if (dotIdx < 0) unsigned else unsigned.replace(".", "")
        val firstNonZero = rawDigits.indexOfFirst { it != '0' }
        val intDigitsBeforeDot = if (dotIdx < 0) unsigned.length else dotIdx
        val exp = intDigitsBeforeDot - firstNonZero - 1

        val sigDigits = rawDigits.substring(firstNonZero).trimEnd('0')
        val mantissa = buildString {
            if (neg) append('-')
            append(sigDigits[0])
            if (sigDigits.length > 1) {
                append(',')
                append(sigDigits.substring(1))
            }
        }
        val ans = "$mantissa × 10${ExactMath.toSuperscript(exp)}"
        steps.add("Aturan notasi ilmiah: a × 10ⁿ dengan 1 ≤ |a| < 10.")
        steps.add("Angka penting pertama '${sigDigits[0]}' digeser sehingga menjadi $mantissa.")
        steps.add("Pergeseran posisi koma menghasilkan pangkat n = $exp.")
        val checkDec = fracCheck.toFracString()
        return SolveResult(
            ok = true,
            steps = steps,
            answer = ans,
            check = "Nilai pecahan eksak $raw = $checkDec cocok dengan $mantissa × 10${ExactMath.toSuperscript(exp)}."
        )
    }

    private fun solveSimplifySqrt(input: Map<String, String>, showDecimal: Boolean): SolveResult {
        val raw = input["n"]?.trim()?.takeIf { it.isNotEmpty() } ?: input["a"]?.trim().orEmpty()
        if (raw.isEmpty()) return SolveResult.fail("Masukkan bilangan yang ingin disederhanakan akarnya.")
        val frac = ExactFraction.parse(raw)
            ?: return SolveResult.fail("Format bilangan akar \"$raw\" tidak valid.")
        if (frac.signum < 0) return SolveResult.fail("Bilangan di dalam akar kuadrat tidak boleh negatif.")

        val steps = ArrayList<String>()
        if (frac.isInteger) {
            val nVal = frac.num
            val (outer, inner) = ExactMath.simplifySqrt(nVal)
            val sq = outer.multiply(outer)
            val formatted = ExactMath.formatRadicalTerm(outer, inner)
            steps.add("Faktorkan kuadrat sempurna terbesar dari $nVal:")
            if (inner == BigInteger.ONE) {
                steps.add("√$nVal = √(${outer}²) = $outer")
            } else if (outer == BigInteger.ONE) {
                steps.add("$nVal tidak memiliki faktor kuadrat sempurna selain 1 → √$nVal sudah paling sederhana.")
            } else {
                steps.add("√$nVal = √($sq × $inner) = √$sq × √$inner = $formatted")
            }
            val ans = if (showDecimal && inner != BigInteger.ONE) {
                val surd = QuadraticSurd(ExactFraction.ZERO, ExactFraction.of(outer, BigInteger.ONE), inner)
                "$formatted (≈ ${surd.toDecimalApprox()})"
            } else {
                formatted
            }
            val checkVal = outer.multiply(outer).multiply(inner)
            return SolveResult(
                ok = true,
                steps = steps,
                answer = ans,
                check = "Kuadratkan kembali: ($formatted)² = ${outer}² × $inner = $sq × $inner = $checkVal (sama dengan $nVal)."
            )
        } else {
            val surd = ExactMath.simplifyFractionSqrt(frac)
            val formatted = surd.format()
            steps.add("√(${frac.toFracString()}) = √(${frac.num} × ${frac.den}) / ${frac.den} = $formatted")
            val ans = if (showDecimal) "$formatted (≈ ${surd.toDecimalApprox()})" else formatted
            return SolveResult(
                ok = true,
                steps = steps,
                answer = ans,
                check = "($formatted)² = ${frac.toFracString()}."
            )
        }
    }

    private fun solveRationalize(input: Map<String, String>, showDecimal: Boolean): SolveResult {
        val (a, errA) = InputChecks.requireFraction(input, "a", "Pembilang (a)")
        if (errA != null) return SolveResult.fail(errA)
        a!!

        val modeOp = input["op"]?.trim().orEmpty().ifEmpty { "tunggal" }.lowercase()
        val steps = ArrayList<String>()

        if (modeOp == "tunggal" || modeOp == "a/√b" || modeOp == "√") {
            val (b, errB) = InputChecks.requireInt(input, "m", "Bilangan dalam akar penyebut (b)", min = 1)
            if (errB != null) return SolveResult.fail(errB)
            b!!
            val bBig = BigInteger.valueOf(b.toLong())
            val (bOut, bIn) = ExactMath.simplifySqrt(bBig)
            val coeff = a / ExactFraction.of(bOut.multiply(bIn), BigInteger.ONE)
            val surd = QuadraticSurd(ExactFraction.ZERO, coeff, bIn)
            val formatted = surd.format()

            steps.add("Kalikan pembilang dan penyebut dengan √$b:")
            steps.add("(${a.toFracString()})/√$b × √$b/√$b = (${a.toFracString()})√$b / $b")
            steps.add("Sederhanakan: $formatted")
            val ans = if (showDecimal && !surd.isPureRational) "$formatted (≈ ${surd.toDecimalApprox()})" else formatted

            return SolveResult(
                ok = true,
                steps = steps,
                answer = ans,
                check = "Cek: ($formatted) × √$b = ${a.toFracString()}."
            )
        }

        // Bentuk a / (b + √c) atau a / (b - √c)
        val sign = when (modeOp) {
            "+", "plus" -> 1
            "-", "minus" -> -1
            else -> return SolveResult.fail("Tanda penyebut harus \"tunggal\", \"+\", atau \"-\".")
        }
        val (b, errB) = InputChecks.requireFraction(input, "m", "Bilangan rasional penyebut (b)")
        if (errB != null) return SolveResult.fail(errB)
        val (c, errC) = InputChecks.requireInt(input, "n", "Bilangan dalam akar penyebut (c)", min = 1)
        if (errC != null) return SolveResult.fail(errC)
        b!!; c!!

        val cBig = BigInteger.valueOf(c.toLong())
        val denom = b * b - ExactFraction.of(cBig, BigInteger.ONE)
        if (denom.isZero) return SolveResult.fail("Penyebut b² - c = 0, pembagian dengan nol tidak terdefinisi.")

        val signSym = if (sign > 0) "+" else "-"
        val conjSym = if (sign > 0) "-" else "+"
        val conjSignFrac = if (sign > 0) ExactFraction.MINUS_ONE else ExactFraction.ONE

        val surd = QuadraticSurd(
            rational = (a * b) / denom,
            radicalCoeff = (a * conjSignFrac) / denom,
            radicand = cBig
        )
        val formatted = surd.format()

        steps.add("Bentuk soal: (${a.toFracString()}) / (${b.toFracString()} $signSym √$c)")
        steps.add("Kalikan dengan sekawan (${b.toFracString()} $conjSym √$c) / (${b.toFracString()} $conjSym √$c):")
        steps.add("Penyebut: (${b.toFracString()})² - (√$c)² = ${(b * b).toFracString()} - $c = ${denom.toFracString()}")
        steps.add("Pembilang: (${a.toFracString()})(${b.toFracString()} $conjSym √$c)")
        steps.add("Sederhanakan hasil: $formatted")

        val ans = if (showDecimal && !surd.isPureRational) "$formatted (≈ ${surd.toDecimalApprox()})" else formatted
        return SolveResult(
            ok = true,
            steps = steps,
            answer = ans,
            check = "Kalikan kembali hasil ($formatted) × (${b.toFracString()} $signSym √$c) = ${a.toFracString()}."
        )
    }

    companion object {
        const val MODE_PANGKAT = "Operasi pangkat"
        const val MODE_ILMIAH = "Notasi ilmiah"
        const val MODE_AKAR = "Sederhanakan akar"
        const val MODE_RASIONAL = "Rasionalisasi penyebut"
    }
}
