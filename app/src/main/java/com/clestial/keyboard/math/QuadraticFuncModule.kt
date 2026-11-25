package com.clestial.keyboard.math

/**
 * Modul 4 (Aljabar): Fungsi Kuadrat y = ax² + bx + c (a ≠ 0).
 * Menghitung arah parabola, sumbu simetri, titik puncak, nilai maks/min,
 * titik potong sumbu-y dan sumbu-x, serta nilai f(x) opsional.
 */
class QuadraticFuncModule : MathModule {

    override val id = "fungsi_kuadrat"
    override val name = "Fungsi Kuadrat"
    override val group = MathGroup.ALJABAR
    override val description = "Analisis y = ax² + bx + c: arah parabola, sumbu simetri, puncak, dan titik potong."

    override val fields = listOf(
        InputFieldSpec("a", "a (koefisien x²)", "1"),
        InputFieldSpec("b", "b (koefisien x)", "-4"),
        InputFieldSpec("c", "c (konstanta)", "3"),
        InputFieldSpec("x_eval", "Nilai x tertentu (opsional)", "", required = false)
    )

    override fun solve(input: Map<String, String>): SolveResult {
        val (a, e1) = InputChecks.requireFraction(input, "a", "a (koefisien x²)", allowZero = false)
        if (e1 != null) return SolveResult.fail(e1)
        val (b, e2) = InputChecks.requireFraction(input, "b", "b (koefisien x)")
        if (e2 != null) return SolveResult.fail(e2)
        val (c, e3) = InputChecks.requireFraction(input, "c", "c (konstanta)")
        if (e3 != null) return SolveResult.fail(e3)
        val (xEval, e4) = InputChecks.optionalFraction(input, "x_eval", "Nilai x tertentu")
        if (e4 != null) return SolveResult.fail(e4)
        a!!; b!!; c!!

        val steps = ArrayList<String>()
        val fnExpr = "y = ${ExactMath.formatQuadraticExpr(a, b, c)}"
        steps.add("Fungsi kuadrat: $fnExpr")

        val openDir = if (a.signum > 0) "terbuka ke atas (memiliki nilai minimum)" else "terbuka ke bawah (memiliki nilai maksimum)"
        val extLabel = if (a.signum > 0) "minimum" else "maksimum"
        steps.add("1. Arah parabola: karena a = ${a.toFracString()} ${if (a.signum > 0) "> 0" else "< 0"}, parabola $openDir.")

        val xp = b.negate() / (ExactFraction.of(2) * a)
        val disc = b * b - ExactFraction.of(4) * a * c
        val yp = disc.negate() / (ExactFraction.of(4) * a)
        steps.add("2. Sumbu simetri: x = -b / (2a) = -(${b.toFracString()}) / (2·${a.toFracString()}) = ${xp.toFracString()}")
        steps.add("3. Diskriminan: D = b² - 4ac = ${disc.toFracString()}")
        steps.add("4. Titik puncak: (xₚ, yₚ) = (${xp.toFracString()}, ${yp.toFracString()}), nilai $extLabel = ${yp.toFracString()}")
        steps.add("5. Titik potong sumbu-y (x = 0): (0, ${c.toFracString()})")

        val xInterceptsText: String = when {
            disc.signum < 0 -> {
                steps.add("6. Titik potong sumbu-x: karena D < 0, parabola tidak memotong sumbu-x.")
                "tidak memotong sumbu-x"
            }
            disc.isZero -> {
                steps.add("6. Titik potong sumbu-x (menyinggung di puncak): (${xp.toFracString()}, 0)")
                "(${xp.toFracString()}, 0)"
            }
            else -> {
                val sqrtD = ExactMath.simplifyFractionSqrt(disc).normalized()
                val twoA = ExactFraction.of(2) * a
                if (sqrtD.isPureRational) {
                    val r1 = xp + sqrtD.rational / twoA
                    val r2 = xp - sqrtD.rational / twoA
                    val (lo, hi) = if (r1 <= r2) r1 to r2 else r2 to r1
                    steps.add("6. Titik potong sumbu-x (y = 0): (${lo.toFracString()}, 0) dan (${hi.toFracString()}, 0)")
                    "(${lo.toFracString()}, 0) dan (${hi.toFracString()}, 0)"
                } else {
                    val surd = QuadraticSurd(xp, sqrtD.radicalCoeff / twoA, sqrtD.radicand)
                    val sText = surd.formatPlusMinus()
                    steps.add("6. Titik potong sumbu-x (y = 0): ($sText, 0)")
                    "($sText, 0)"
                }
            }
        }

        var extraAns = ""
        if (xEval != null) {
            val yEval = a * xEval * xEval + b * xEval + c
            steps.add("7. Nilai fungsi di x = ${xEval.toFracString()}: f(${xEval.toFracString()}) = ${yEval.toFracString()}")
            extraAns = "; f(${xEval.toFracString()}) = ${yEval.toFracString()}"
        }

        val evalPeak = a * xp * xp + b * xp + c
        return SolveResult(
            ok = true,
            steps = steps,
            answer = "Parabola $openDir; sumbu simetri x = ${xp.toFracString()}; puncak (${xp.toFracString()}, ${yp.toFracString()}); nilai $extLabel = ${yp.toFracString()}; potong y (0, ${c.toFracString()}); potong x $xInterceptsText$extraAns",
            check = "Substitusi xₚ = ${xp.toFracString()} ke fungsi: f(${xp.toFracString()}) = ${evalPeak.toFracString()} (sesuai yₚ = ${yp.toFracString()})."
        )
    }
}
