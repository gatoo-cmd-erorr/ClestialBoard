package com.clestial.keyboard.math

import java.math.BigInteger

/**
 * Modul 7 (Geometri): Bangun Ruang Sisi Lengkung (Tabung, Kerucut, Bola).
 * Menampilkan hasil dalam π eksak secara default serta hasil numerik terpisah bila π = 22/7 atau 3,14 dipilih.
 * Mendukung mode mencari unsur belum diketahui (r atau t) dari volume yang diketahui.
 */
class CurvedSolidModule : MathModule {

    override val id = "bangun_ruang"
    override val name = "Bangun Ruang Sisi Lengkung"
    override val group = MathGroup.GEOMETRI
    override val description = "Volume & luas permukaan Tabung, Kerucut, dan Bola dengan π eksak / 22/7 / 3,14."

    override val fields = listOf(
        InputFieldSpec(
            key = "bangun",
            label = "Bangun ruang",
            type = FieldType.CHOICE,
            choices = listOf(SHAPE_TABUNG, SHAPE_KERUCUT, SHAPE_BOLA),
            defaultValue = SHAPE_TABUNG
        ),
        InputFieldSpec(
            key = "mode",
            label = "Mode perhitungan",
            type = FieldType.CHOICE,
            choices = listOf(MODE_HITUNG, MODE_CARI_UNSUR),
            defaultValue = MODE_HITUNG
        ),
        InputFieldSpec("r", "Jari-jari (r) [atau kosongkan jika dicari]", "7", required = false),
        InputFieldSpec("t", "Tinggi (t) [atau s bila mode r & s; atau kosongkan jika dicari]", "10", required = false),
        InputFieldSpec(
            key = "input_kerucut",
            label = "Pasangan input kerucut",
            type = FieldType.CHOICE,
            required = false,
            choices = listOf("r dan t", "r dan s (pelukis)"),
            defaultValue = "r dan t"
        ),
        InputFieldSpec("volume", "Volume diketahui (tanpa π atau angka biasa untuk cari unsur)", "", required = false),
        InputFieldSpec(
            key = "pi_mode",
            label = "Nilai π",
            type = FieldType.CHOICE,
            required = false,
            choices = listOf(PiMode.SYMBOL.label, PiMode.FRACTION_22_7.label, PiMode.DECIMAL_3_14.label),
            defaultValue = PiMode.SYMBOL.label
        )
    )

    override fun solve(input: Map<String, String>): SolveResult {
        val shape = input["bangun"]?.trim().orEmpty().ifEmpty { SHAPE_TABUNG }
        val mode = input["mode"]?.trim().orEmpty().ifEmpty { MODE_HITUNG }
        val piMode = PiMode.fromChoice(input["pi_mode"])

        if (mode == MODE_CARI_UNSUR) {
            return solveUnknown(shape, input, piMode)
        }

        return when (shape) {
            SHAPE_TABUNG -> solveCylinder(input, piMode)
            SHAPE_KERUCUT -> solveCone(input, piMode)
            SHAPE_BOLA -> solveSphere(input, piMode)
            else -> SolveResult.fail("Bangun ruang tidak dikenal: $shape")
        }
    }

    private fun piFactor(piMode: PiMode): ExactFraction? = when (piMode) {
        PiMode.SYMBOL -> null
        PiMode.FRACTION_22_7 -> ExactFraction.PI_22_7
        PiMode.DECIMAL_3_14 -> ExactFraction.PI_3_14
    }

    private fun formatDualPi(coeff: ExactFraction, piMode: PiMode): String {
        val sym = ExactMath.formatPiExact(coeff)
        val numPi = piFactor(piMode) ?: return sym
        val valNum = coeff * numPi
        return "$sym | numerik (π = ${piMode.label}): ${valNum.toExactDisplay()}"
    }

    private fun solveCylinder(input: Map<String, String>, piMode: PiMode): SolveResult {
        val (r, er) = InputChecks.requireFraction(input, "r", "Jari-jari (r)")
        if (er != null) return SolveResult.fail(er)
        val (t, et) = InputChecks.requireFraction(input, "t", "Tinggi (t)")
        if (et != null) return SolveResult.fail(et)
        if (r!!.signum <= 0) return SolveResult.fail("Jari-jari (r) harus > 0.")
        if (t!!.signum <= 0) return SolveResult.fail("Tinggi (t) harus > 0.")

        val volCoeff = r * r * t
        val lsCoeff = ExactFraction.of(2) * r * t
        val lpCoeff = ExactFraction.of(2) * r * (r + t)

        val steps = ArrayList<String>()
        if (ExactMath.suggests22Per7(r) && piMode != PiMode.FRACTION_22_7) {
            steps.add("Saran: karena r = $r merupakan kelipatan 7, perhitungan numerik disarankan memakai π = 22/7.")
        }
        steps.add("Tabung: r = ${r.toFracString()}, t = ${t.toFracString()}")
        steps.add("1. Volume: V = π·r²·t = π·(${r.toFracString()})²·(${t.toFracString()}) = ${ExactMath.formatPiExact(volCoeff)}")
        steps.add("2. Luas selimut: Ls = 2·π·r·t = 2·π·(${r.toFracString()})·(${t.toFracString()}) = ${ExactMath.formatPiExact(lsCoeff)}")
        steps.add("3. Luas permukaan: Lp = 2·π·r·(r + t) = 2·π·(${r.toFracString()})·(${(r + t).toFracString()}) = ${ExactMath.formatPiExact(lpCoeff)}")

        val piNum = piFactor(piMode)
        if (piNum != null) {
            steps.add("Nilai numerik dengan π = ${piMode.label}:")
            steps.add("  V = ${(volCoeff * piNum).toExactDisplay()}")
            steps.add("  Ls = ${(lsCoeff * piNum).toExactDisplay()}")
            steps.add("  Lp = ${(lpCoeff * piNum).toExactDisplay()}")
        }

        return SolveResult(
            ok = true,
            steps = steps,
            answer = "V = ${formatDualPi(volCoeff, piMode)}; Ls = ${formatDualPi(lsCoeff, piMode)}; Lp = ${formatDualPi(lpCoeff, piMode)}",
            check = "Cek hubungan: Lp - Ls = ${ExactMath.formatPiExact(lpCoeff - lsCoeff)} = 2·π·r² (luas alas + tutup)."
        )
    }

    private fun solveCone(input: Map<String, String>, piMode: PiMode): SolveResult {
        val (r, er) = InputChecks.requireFraction(input, "r", "Jari-jari (r)")
        if (er != null) return SolveResult.fail(er)
        val (secondVal, et) = InputChecks.requireFraction(input, "t", "Tinggi (t) atau Garis pelukis (s)")
        if (et != null) return SolveResult.fail(et)
        if (r!!.signum <= 0 || secondVal!!.signum <= 0) {
            return SolveResult.fail("Jari-jari dan tinggi/pelukis kerucut harus > 0.")
        }

        val usingS = input["input_kerucut"]?.contains("s", ignoreCase = true) == true
        val steps = ArrayList<String>()
        if (ExactMath.suggests22Per7(r) && piMode != PiMode.FRACTION_22_7) {
            steps.add("Saran: karena r = $r merupakan kelipatan 7, perhitungan numerik disarankan memakai π = 22/7.")
        }

        val tSurd: QuadraticSurd
        val sSurd: QuadraticSurd

        if (!usingS) {
            val t = secondVal
            tSurd = QuadraticSurd(t, ExactFraction.ZERO, BigInteger.ONE)
            val sSq = r * r + t * t
            sSurd = ExactMath.simplifyFractionSqrt(sSq).normalized()
            steps.add("Kerucut: r = ${r.toFracString()}, t = ${t.toFracString()}")
            steps.add("1. Garis pelukis: s = √(r² + t²) = √(${(r * r).toFracString()} + ${(t * t).toFracString()}) = √(${sSq.toFracString()}) = ${sSurd.format()}")
        } else {
            val s = secondVal
            if (s <= r) return SolveResult.fail("Garis pelukis (s) harus lebih panjang dari jari-jari (r).")
            sSurd = QuadraticSurd(s, ExactFraction.ZERO, BigInteger.ONE)
            val tSq = s * s - r * r
            tSurd = ExactMath.simplifyFractionSqrt(tSq).normalized()
            steps.add("Kerucut: r = ${r.toFracString()}, s = ${s.toFracString()}")
            steps.add("1. Tinggi kerucut: t = √(s² - r²) = √(${(s * s).toFracString()} - ${(r * r).toFracString()}) = √(${tSq.toFracString()}) = ${tSurd.format()}")
        }

        val oneThird = ExactFraction.of(1, 3)
        val vText: String
        if (tSurd.isPureRational) {
            val volCoeff = oneThird * r * r * tSurd.rational
            vText = formatDualPi(volCoeff, piMode)
            steps.add("2. Volume: V = 1/3·π·r²·t = ${ExactMath.formatPiExact(volCoeff)}")
        } else {
            val volRad = QuadraticSurd(ExactFraction.ZERO, oneThird * r * r * tSurd.radicalCoeff, tSurd.radicand)
            vText = "${volRad.format()}π"
            steps.add("2. Volume: V = 1/3·π·r²·t = $vText")
        }

        val lsText: String
        val lpText: String
        if (sSurd.isPureRational) {
            val sRat = sSurd.rational
            val lsCoeff = r * sRat
            val lpCoeff = r * (r + sRat)
            lsText = formatDualPi(lsCoeff, piMode)
            lpText = formatDualPi(lpCoeff, piMode)
            steps.add("3. Luas selimut: Ls = π·r·s = ${ExactMath.formatPiExact(lsCoeff)}")
            steps.add("4. Luas permukaan: Lp = π·r·(r + s) = ${ExactMath.formatPiExact(lpCoeff)}")
        } else {
            val lsRad = QuadraticSurd(ExactFraction.ZERO, r * sSurd.radicalCoeff, sSurd.radicand)
            val lpRad = QuadraticSurd(r * r, r * sSurd.radicalCoeff, sSurd.radicand)
            lsText = "${lsRad.format()}π"
            lpText = "(${lpRad.format()})π"
            steps.add("3. Luas selimut: Ls = π·r·s = $lsText")
            steps.add("4. Luas permukaan: Lp = π·r² + π·r·s = $lpText")
        }

        return SolveResult(
            ok = true,
            steps = steps,
            answer = (if (!usingS) "s = ${sSurd.format()}" else "t = ${tSurd.format()}") +
                "; V = $vText; Ls = $lsText; Lp = $lpText",
            check = "Cek Pythagoras kerucut: r² + t² = ${(r * r).toFracString()} + (${tSurd.format()})² = (${sSurd.format()})² = s²."
        )
    }

    private fun solveSphere(input: Map<String, String>, piMode: PiMode): SolveResult {
        val (r, er) = InputChecks.requireFraction(input, "r", "Jari-jari (r)")
        if (er != null) return SolveResult.fail(er)
        if (r!!.signum <= 0) return SolveResult.fail("Jari-jari bola harus > 0.")

        val volCoeff = ExactFraction.of(4, 3) * r * r * r
        val lpCoeff = ExactFraction.of(4) * r * r

        val steps = ArrayList<String>()
        if (ExactMath.suggests22Per7(r) && piMode != PiMode.FRACTION_22_7) {
            steps.add("Saran: karena r = $r kelipatan 7, disarankan π = 22/7 untuk nilai numerik.")
        }
        steps.add("Bola: r = ${r.toFracString()}")
        steps.add("1. Volume: V = 4/3·π·r³ = 4/3·π·(${r.toFracString()})³ = ${ExactMath.formatPiExact(volCoeff)}")
        steps.add("2. Luas permukaan: Lp = 4·π·r² = 4·π·(${r.toFracString()})² = ${ExactMath.formatPiExact(lpCoeff)}")

        val piNum = piFactor(piMode)
        if (piNum != null) {
            steps.add("Nilai numerik dengan π = ${piMode.label}:")
            steps.add("  V = ${(volCoeff * piNum).toExactDisplay()}")
            steps.add("  Lp = ${(lpCoeff * piNum).toExactDisplay()}")
        }

        return SolveResult(
            ok = true,
            steps = steps,
            answer = "V = ${formatDualPi(volCoeff, piMode)}; L = ${formatDualPi(lpCoeff, piMode)}",
            check = "Cek rasio bola: 3·V / L = (4·π·r³) / (4·π·r²) = r = ${r.toFracString()}."
        )
    }

    private fun solveUnknown(shape: String, input: Map<String, String>, piMode: PiMode): SolveResult {
        val rawVol = input["volume"]?.trim().orEmpty()
        if (rawVol.isEmpty()) return SolveResult.fail("Masukkan volume yang diketahui untuk mencari unsur.")

        val hasPiSymbol = rawVol.contains("π") || rawVol.contains("pi", ignoreCase = true)
        val cleanVolNum = rawVol.replace("π", "")
            .replace("pi", "", ignoreCase = true)
            .replace("·", "")
            .replace("*", "")
            .trim()
            .ifEmpty { "1" }
        val vParsed = ExactFraction.parse(cleanVolNum)
            ?: return SolveResult.fail("Format volume \"$rawVol\" tidak valid.")
        if (vParsed.signum <= 0) return SolveResult.fail("Volume harus > 0.")

        // Jika input tidak mengandung simbol π dan user memilih 22/7 atau 3,14, bagi dengan π tersebut.
        val vCoeffOverPi = if (hasPiSymbol) {
            vParsed
        } else {
            val pf = piFactor(piMode) ?: return SolveResult.fail("Untuk volume tanpa simbol π, pilih π = 22/7 atau 3,14 (atau sertakan π pada volume).")
            vParsed / pf
        }

        val (rOpt, er) = InputChecks.optionalFraction(input, "r", "Jari-jari (r)")
        if (er != null) return SolveResult.fail(er)
        val (tOpt, et) = InputChecks.optionalFraction(input, "t", "Tinggi (t)")
        if (et != null) return SolveResult.fail(et)

        val steps = ArrayList<String>()
        return when (shape) {
            SHAPE_TABUNG -> {
                if (rOpt == null && tOpt != null) {
                    if (tOpt.signum <= 0) return SolveResult.fail("Tinggi harus > 0.")
                    val rSq = vCoeffOverPi / tOpt
                    val rSurd = ExactMath.simplifyFractionSqrt(rSq)
                    steps.add("Diketahui V = ${ExactMath.formatPiExact(vCoeffOverPi)} dan t = $tOpt")
                    steps.add("r² = V / (π·t) = ${rSq.toFracString()} → r = ${rSurd.format()}")
                    SolveResult(
                        ok = true,
                        steps = steps,
                        answer = "r = ${rSurd.format()}",
                        check = "Cek V = π·(${rSurd.format()})²·$tOpt = ${ExactMath.formatPiExact(vCoeffOverPi)}."
                    )
                } else if (rOpt != null && tOpt == null) {
                    if (rOpt.signum <= 0) return SolveResult.fail("Jari-jari harus > 0.")
                    val tVal = vCoeffOverPi / (rOpt * rOpt)
                    steps.add("Diketahui V = ${ExactMath.formatPiExact(vCoeffOverPi)} dan r = $rOpt")
                    steps.add("t = V / (π·r²) = ${tVal.toFracString()}")
                    SolveResult(
                        ok = true,
                        steps = steps,
                        answer = "t = ${tVal.toFracString()}",
                        check = "Cek V = π·(${rOpt})²·(${tVal}) = ${ExactMath.formatPiExact(rOpt * rOpt * tVal)}."
                    )
                } else {
                    SolveResult.fail("Isi tepat salah satu antara r atau t (kosongkan yang ingin dicari).")
                }
            }
            SHAPE_KERUCUT -> {
                val three = ExactFraction.of(3)
                if (rOpt == null && tOpt != null) {
                    if (tOpt.signum <= 0) return SolveResult.fail("Tinggi harus > 0.")
                    val rSq = (three * vCoeffOverPi) / tOpt
                    val rSurd = ExactMath.simplifyFractionSqrt(rSq)
                    steps.add("r² = 3V / (π·t) = ${rSq.toFracString()} → r = ${rSurd.format()}")
                    SolveResult(
                        ok = true,
                        steps = steps,
                        answer = "r = ${rSurd.format()}",
                        check = "Cek V = 1/3·π·(${rSurd.format()})²·$tOpt = ${ExactMath.formatPiExact(vCoeffOverPi)}."
                    )
                } else if (rOpt != null && tOpt == null) {
                    if (rOpt.signum <= 0) return SolveResult.fail("Jari-jari harus > 0.")
                    val tVal = (three * vCoeffOverPi) / (rOpt * rOpt)
                    steps.add("t = 3V / (π·r²) = ${tVal.toFracString()}")
                    SolveResult(
                        ok = true,
                        steps = steps,
                        answer = "t = ${tVal.toFracString()}",
                        check = "Cek V = 1/3·π·(${rOpt})²·(${tVal}) = ${ExactMath.formatPiExact(ExactFraction.of(1, 3) * rOpt * rOpt * tVal)}."
                    )
                } else {
                    SolveResult.fail("Isi tepat salah satu antara r atau t untuk kerucut.")
                }
            }
            else -> {
                // Bola: cari r dari V = 4/3 π r^3
                val rCube = (ExactFraction.of(3, 4)) * vCoeffOverPi
                val prod = rCube.num.multiply(rCube.den).multiply(rCube.den)
                val (outer, inner) = ExactMath.simplifyCbrt(prod)
                val rText = if (inner == BigInteger.ONE) {
                    ExactFraction.of(outer, rCube.den).toFracString()
                } else {
                    val cFrac = ExactFraction.of(outer, rCube.den)
                    val coeffStr = if (cFrac.isOne) "" else if (cFrac.isInteger) "${cFrac.num}" else "(${cFrac.toFracString()})·"
                    "${coeffStr}∛$inner"
                }
                steps.add("r³ = 3V / (4π) = ${rCube.toFracString()}")
                steps.add("r = ∛(${rCube.toFracString()}) = $rText")
                SolveResult(
                    ok = true,
                    steps = steps,
                    answer = "r = $rText",
                    check = "Cek r³ = ($rText)³ = ${rCube.toFracString()}."
                )
            }
        }
    }

    companion object {
        const val SHAPE_TABUNG = "Tabung"
        const val SHAPE_KERUCUT = "Kerucut"
        const val SHAPE_BOLA = "Bola"
        const val MODE_HITUNG = "Hitung V, Ls, Lp"
        const val MODE_CARI_UNSUR = "Cari unsur belum diketahui"
    }
}
