package com.clestial.keyboard.math

/**
 * Modul 1 (Aljabar): Sistem Persamaan Linear Dua Variabel (SPLDV).
 * Menyelesaikan a1*x + b1*y = c1 dan a2*x + b2*y = c2 dengan metode gabungan eliminasi-substitusi.
 */
class SpldvModule : MathModule {

    override val id = "spldv"
    override val name = "SPLDV (Sistem Persamaan Linear)"
    override val group = MathGroup.ALJABAR
    override val description = "Selesaikan a₁x + b₁y = c₁ dan a₂x + b₂y = c₂ dengan eliminasi & substitusi."

    override val fields = listOf(
        InputFieldSpec("a1", "a₁ (koefisien x pers. 1)", "2"),
        InputFieldSpec("b1", "b₁ (koefisien y pers. 1)", "3"),
        InputFieldSpec("c1", "c₁ (konstanta pers. 1)", "12"),
        InputFieldSpec("a2", "a₂ (koefisien x pers. 2)", "3"),
        InputFieldSpec("b2", "b₂ (koefisien y pers. 2)", "2"),
        InputFieldSpec("c2", "c₂ (konstanta pers. 2)", "13")
    )

    override fun solve(input: Map<String, String>): SolveResult {
        val (a1, e1) = InputChecks.requireFraction(input, "a1", "a₁")
        if (e1 != null) return SolveResult.fail(e1)
        val (b1, e2) = InputChecks.requireFraction(input, "b1", "b₁")
        if (e2 != null) return SolveResult.fail(e2)
        val (c1, e3) = InputChecks.requireFraction(input, "c1", "c₁")
        if (e3 != null) return SolveResult.fail(e3)
        val (a2, e4) = InputChecks.requireFraction(input, "a2", "a₂")
        if (e4 != null) return SolveResult.fail(e4)
        val (b2, e5) = InputChecks.requireFraction(input, "b2", "b₂")
        if (e5 != null) return SolveResult.fail(e5)
        val (c2, e6) = InputChecks.requireFraction(input, "c2", "c₂")
        if (e6 != null) return SolveResult.fail(e6)

        a1!!; b1!!; c1!!; a2!!; b2!!; c2!!

        if (a1.isZero && b1.isZero) {
            return SolveResult.fail("Koefisien x dan y pada persamaan (1) tidak boleh sama-sama nol.")
        }
        if (a2.isZero && b2.isZero) {
            return SolveResult.fail("Koefisien x dan y pada persamaan (2) tidak boleh sama-sama nol.")
        }

        val eq1Str = "${ExactMath.formatLinearLhs(a1, "x", b1, "y")} = ${c1.toFracString()}"
        val eq2Str = "${ExactMath.formatLinearLhs(a2, "x", b2, "y")} = ${c2.toFracString()}"

        val steps = ArrayList<String>()
        steps.add("Persamaan (1): $eq1Str")
        steps.add("Persamaan (2): $eq2Str")

        val det = a1 * b2 - a2 * b1
        if (det.isZero) {
            steps.add("Hitung determinan: D = a₁·b₂ - a₂·b₁ = (${a1})(${b2}) - (${a2})(${b1}) = 0")
            val sameRatio = (a1 * c2 == a2 * c1) && (b1 * c2 == b2 * c1)
            return if (sameRatio) {
                steps.add("Karena a₁·c₂ = a₂·c₁ dan b₁·c₂ = b₂·c₁, kedua garis berimpit.")
                SolveResult(
                    ok = true,
                    steps = steps,
                    answer = "Solusi tak hingga banyak",
                    check = "Kedua persamaan merupakan kelipatan yang ekuivalen ($eq1Str dan $eq2Str)."
                )
            } else {
                steps.add("Karena perbandingan konstanta berbeda, kedua garis sejajar.")
                SolveResult(
                    ok = true,
                    steps = steps,
                    answer = "Tidak ada solusi",
                    check = "Tidak ada pasangan (x, y) yang memenuhi kedua persamaan sekaligus."
                )
            }
        }

        val x: ExactFraction
        val y: ExactFraction

        if (b1.isZero || b2.isZero) {
            steps.add("Salah satu koefisien y bernilai 0, eliminasi y dilewati dan x dihitung langsung:")
            if (b1.isZero) {
                x = c1 / a1
                steps.add("Dari (1): (${a1.toFracString()})x = ${c1.toFracString()} → x = ${x.toFracString()}")
                val rhsY = c2 - a2 * x
                y = rhsY / b2
                steps.add("Substitusi x = ${x.toFracString()} ke (2): ${ExactMath.formatTerm(a2, "", true)}·(${x.toFracString()})${ExactMath.formatTerm(b2, "y", false)} = ${c2.toFracString()}")
                steps.add("y = (${rhsY.toFracString()}) / (${b2.toFracString()}) = ${y.toFracString()}")
            } else {
                x = c2 / a2
                steps.add("Dari (2): (${a2.toFracString()})x = ${c2.toFracString()} → x = ${x.toFracString()}")
                val rhsY = c1 - a1 * x
                y = rhsY / b1
                steps.add("Substitusi x = ${x.toFracString()} ke (1): ${ExactMath.formatTerm(a1, "", true)}·(${x.toFracString()})${ExactMath.formatTerm(b1, "y", false)} = ${c1.toFracString()}")
                steps.add("y = (${rhsY.toFracString()}) / (${b1.toFracString()}) = ${y.toFracString()}")
            }
        } else {
            val m1a = a1 * b2
            val m1b = b1 * b2
            val m1c = c1 * b2
            val m2a = a2 * b1
            val m2b = b2 * b1
            val m2c = c2 * b1

            steps.add("Eliminasi y (kalikan pers. 1 dengan ${b2.toFracString()}, pers. 2 dengan ${b1.toFracString()}):")
            steps.add("  (1') ${ExactMath.formatLinearLhs(m1a, "x", m1b, "y")} = ${m1c.toFracString()}")
            steps.add("  (2') ${ExactMath.formatLinearLhs(m2a, "x", m2b, "y")} = ${m2c.toFracString()}")

            val diffA = m1a - m2a
            val diffC = m1c - m2c
            x = diffC / diffA
            steps.add("Kurangkan (1') - (2'): (${diffA.toFracString()})x = ${diffC.toFracString()} → x = ${x.toFracString()}")

            val rhsY = c1 - a1 * x
            y = rhsY / b1
            steps.add("Substitusi x = ${x.toFracString()} ke pers. (1):")
            steps.add("  (${a1.toFracString()})(${x.toFracString()})${ExactMath.formatTerm(b1, "y", false)} = ${c1.toFracString()}")
            steps.add("  y = (${rhsY.toFracString()}) / (${b1.toFracString()}) = ${y.toFracString()}")
        }

        val check1 = a1 * x + b1 * y
        val check2 = a2 * x + b2 * y
        val okCheck = check1 == c1 && check2 == c2
        val checkStr = "Pers. (1): (${a1.toFracString()})(${x.toFracString()}) + (${b1.toFracString()})(${y.toFracString()}) = ${check1.toFracString()} (sesuai ${c1.toFracString()}); " +
            "Pers. (2): (${a2.toFracString()})(${x.toFracString()}) + (${b2.toFracString()})(${y.toFracString()}) = ${check2.toFracString()} (sesuai ${c2.toFracString()}) → " +
            if (okCheck) "Benar" else "Tidak cocok"

        return SolveResult(
            ok = true,
            steps = steps,
            answer = "x = ${x.toFracString()}, y = ${y.toFracString()}",
            check = checkStr
        )
    }
}
