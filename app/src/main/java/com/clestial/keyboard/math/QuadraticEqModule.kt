package com.clestial.keyboard.math

import java.math.BigInteger

/**
 * Modul 3 (Aljabar): Persamaan Kuadrat ax² + bx + c = 0 (a ≠ 0).
 * Menggunakan perhitungan eksak untuk diskriminan, rumus ABC, jumlah/hasil kali akar,
 * penyederhanaan bentuk akar, dan alternatif pemfaktoran bila rasional.
 */
class QuadraticEqModule : MathModule {

    override val id = "persamaan_kuadrat"
    override val name = "Persamaan Kuadrat"
    override val group = MathGroup.ALJABAR
    override val description = "Cari akar ax² + bx + c = 0, diskriminan D, pemfaktoran, serta jumlah & hasil kali akar."

    override val fields = listOf(
        InputFieldSpec("a", "a (koefisien x²)", "1"),
        InputFieldSpec("b", "b (koefisien x)", "-5"),
        InputFieldSpec("c", "c (konstanta)", "6"),
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
        val (a, e1) = InputChecks.requireFraction(input, "a", "a (koefisien x²)", allowZero = false)
        if (e1 != null) return SolveResult.fail(e1)
        val (b, e2) = InputChecks.requireFraction(input, "b", "b (koefisien x)")
        if (e2 != null) return SolveResult.fail(e2)
        val (c, e3) = InputChecks.requireFraction(input, "c", "c (konstanta)")
        if (e3 != null) return SolveResult.fail(e3)
        a!!; b!!; c!!

        val showDecimal = input["desimal"]?.trim()?.equals("Ya", ignoreCase = true) == true
        val eqStr = "${ExactMath.formatQuadraticExpr(a, b, c)} = 0"
        val steps = ArrayList<String>()
        steps.add("Persamaan: $eqStr")

        val disc = b * b - ExactFraction.of(4) * a * c
        steps.add("Hitung diskriminan: D = b² - 4ac = (${b.toFracString()})² - 4(${a.toFracString()})(${c.toFracString()}) = ${disc.toFracString()}")

        val sumRoots = (b.negate()) / a
        val prodRoots = c / a

        if (disc.signum < 0) {
            steps.add("Karena D < 0, persamaan tidak memiliki akar real.")
            steps.add("Jumlah akar (-b/a) = ${sumRoots.toFracString()}, hasil kali akar (c/a) = ${prodRoots.toFracString()}")
            return SolveResult(
                ok = true,
                steps = steps,
                answer = "Tidak ada akar real",
                check = "D = ${disc.toFracString()} < 0 → grafik y = ${ExactMath.formatQuadraticExpr(a, b, c)} tidak memotong sumbu-x."
            )
        }

        if (disc.isZero) {
            val root = b.negate() / (ExactFraction.of(2) * a)
            steps.add("Karena D = 0, persamaan memiliki satu akar kembar real.")
            steps.add("x = -b / (2a) = -(${b.toFracString()}) / (2·${a.toFracString()}) = ${root.toFracString()}")
            steps.add("Alternatif pemfaktoran: ${formatFactorPair(a, root, root)}")
            steps.add("Jumlah akar (-b/a) = ${sumRoots.toFracString()}, hasil kali akar (c/a) = ${prodRoots.toFracString()}")

            val eval = a * root * root + b * root + c
            return SolveResult(
                ok = true,
                steps = steps,
                answer = "x = ${root.toFracString()} (akar kembar)",
                check = "Substitusi x = ${root.toFracString()}: hasil = ${eval.toFracString()} (cocok = 0); x₁+x₂ = ${(root + root).toFracString()}, x₁·x₂ = ${(root * root).toFracString()}."
            )
        }

        // D > 0
        val sqrtD = ExactMath.simplifyFractionSqrt(disc).normalized()
        val twoA = ExactFraction.of(2) * a
        val center = b.negate() / twoA

        if (sqrtD.isPureRational) {
            val dRoot = sqrtD.rational
            val x1 = center + dRoot / twoA
            val x2 = center - dRoot / twoA
            steps.add("Karena D = ${disc.toFracString()} > 0 dan kuadrat sempurna (√D = ${dRoot.toFracString()}), ada dua akar rasional.")
            steps.add("Rumus ABC: x = (-b ± √D) / (2a)")
            steps.add("x₁ = (${b.negate().toFracString()} + ${dRoot.toFracString()}) / (${twoA.toFracString()}) = ${x1.toFracString()}")
            steps.add("x₂ = (${b.negate().toFracString()} - ${dRoot.toFracString()}) / (${twoA.toFracString()}) = ${x2.toFracString()}")
            steps.add("Alternatif pemfaktoran: ${formatFactorPair(a, x1, x2)}")
            steps.add("Jumlah akar (-b/a) = ${sumRoots.toFracString()}, hasil kali akar (c/a) = ${prodRoots.toFracString()}")

            val eval1 = a * x1 * x1 + b * x1 + c
            val eval2 = a * x2 * x2 + b * x2 + c
            val ordered = if (x1 <= x2) "${x1.toFracString()} dan ${x2.toFracString()}" else "${x2.toFracString()} dan ${x1.toFracString()}"
            return SolveResult(
                ok = true,
                steps = steps,
                answer = "x = $ordered",
                check = "f(${x1.toFracString()}) = ${eval1.toFracString()}, f(${x2.toFracString()}) = ${eval2.toFracString()}; x₁+x₂ = ${(x1 + x2).toFracString()}, x₁·x₂ = ${(x1 * x2).toFracString()}."
            )
        } else {
            val halfRad = sqrtD.radicalCoeff / twoA
            val surd1 = QuadraticSurd(center, halfRad, sqrtD.radicand)
            val surd2 = QuadraticSurd(center, halfRad.negate(), sqrtD.radicand)
            steps.add("Karena D = ${disc.toFracString()} > 0 bukan kuadrat sempurna, akar berbentuk akar yang disederhanakan:")
            steps.add("√D = ${sqrtD.format()}")
            steps.add("x = (-b ± √D) / (2a) = ${surd1.formatPlusMinus()}")
            steps.add("x₁ = ${surd1.format()}, x₂ = ${surd2.format()}")
            steps.add("Jumlah akar (-b/a) = ${sumRoots.toFracString()}, hasil kali akar (c/a) = ${prodRoots.toFracString()}")

            // Cek aljabar eksak untuk (center ± halfRad√r)
            val checkSum = center + center
            val checkProd = center * center - halfRad * halfRad * ExactFraction.of(sqrtD.radicand, BigInteger.ONE)
            val ans = if (showDecimal) {
                "x = ${surd1.formatPlusMinus()} (≈ ${surd1.toDecimalApprox()} dan ${surd2.toDecimalApprox()})"
            } else {
                "x = ${surd1.formatPlusMinus()}"
            }
            return SolveResult(
                ok = true,
                steps = steps,
                answer = ans,
                check = "Verifikasi Vieta: x₁+x₂ = ${checkSum.toFracString()} (sesuai -b/a = ${sumRoots.toFracString()}), x₁·x₂ = ${checkProd.toFracString()} (sesuai c/a = ${prodRoots.toFracString()})."
            )
        }
    }

    private fun formatFactorPair(a: ExactFraction, r1: ExactFraction, r2: ExactFraction): String {
        val f1 = formatSingleFactor(r1)
        val f2 = formatSingleFactor(r2)
        return when {
            a.isOne -> "$f1$f2 = 0"
            a == ExactFraction.MINUS_ONE -> "-$f1$f2 = 0"
            else -> "${a.toFracString()}$f1$f2 = 0"
        }
    }

    private fun formatSingleFactor(root: ExactFraction): String {
        val neg = root.negate()
        return when {
            neg.isZero -> "x"
            neg.signum > 0 -> "(x + ${neg.toFracString()})"
            else -> "(x - ${neg.abs().toFracString()})"
        }
    }
}
