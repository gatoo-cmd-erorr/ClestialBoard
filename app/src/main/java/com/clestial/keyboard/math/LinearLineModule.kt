package com.clestial.keyboard.math

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.max

/**
 * Modul 10 (Aljabar): Persamaan Garis Lurus dan Fungsi Linear.
 * Mendukung 9 sub-fitur lengkap (a–i):
 * - Gradien dari dua titik dan dari persamaan garis
 * - Persamaan garis melalui 1 titik + gradien atau 2 titik (bentuk y = mx + c dan ax + by + c = 0)
 * - Garis sejajar dan garis tegak lurus melalui titik P
 * - Titik potong sumbu-x dan sumbu-y
 * - Fungsi linear f(x) = ax + b (nilai f(k), invers f(x)=n, tentukan a & b dari f(p) & f(q))
 * - Titik potong dua garis (mendelegasikan langsung ke SpldvModule)
 */
class LinearLineModule : MathModule {

    override val id = "garis_linear"
    override val name = "Persamaan Garis Lurus & Fungsi Linear"
    override val group = MathGroup.ALJABAR
    override val description = "Gradien, persamaan garis, sejajar, tegak lurus, titik potong sumbu, fungsi linear f(x), dan perpotongan 2 garis."

    override val fields = listOf(
        InputFieldSpec(
            key = "mode",
            label = "Sub-fitur",
            type = FieldType.CHOICE,
            choices = listOf(
                MODE_GRAD_TITIK,
                MODE_GRAD_PERS,
                MODE_GARIS_TITIK_M,
                MODE_GARIS_DUA_TITIK,
                MODE_SEJAJAR,
                MODE_TEGAK_LURUS,
                MODE_TITIK_POTONG_SUMBU,
                MODE_FUNGSI_LINEAR,
                MODE_POTONG_DUA_GARIS
            ),
            defaultValue = MODE_GRAD_TITIK
        ),
        InputFieldSpec("garis1", "Persamaan garis 1 / f(x) (misal: y = 2x + 1 atau 2x + 3y - 6 = 0)", "2x + 3y - 6 = 0", FieldType.TEXT, required = false),
        InputFieldSpec("titik1", "Titik 1 / P atau f(p) (misal: (1, 2) atau f(1)=5)", "(1, 2)", FieldType.TEXT, required = false),
        InputFieldSpec("titik2", "Titik 2 atau f(q) (misal: (3, 8) atau f(3)=9)", "(3, 8)", FieldType.TEXT, required = false),
        InputFieldSpec("m_nilai", "Gradien m / nilai k untuk hitung f(k)", "", FieldType.TEXT, required = false),
        InputFieldSpec("b_nilai", "Nilai target n untuk mencari x jika f(x) = n", "", FieldType.TEXT, required = false),
        InputFieldSpec("garis2", "Persamaan garis 2 (untuk perpotongan 2 garis)", "", FieldType.TEXT, required = false)
    )

    data class NormalizedLine(val a: ExactFraction, val b: ExactFraction, val c: ExactFraction) {
        val isVertical: Boolean get() = b.isZero
        val isHorizontal: Boolean get() = a.isZero

        fun slopeOrNull(): ExactFraction? = if (b.isZero) null else a.negate() / b

        fun yInterceptOrNull(): ExactFraction? = if (b.isZero) null else c.negate() / b

        fun xInterceptOrNull(): ExactFraction? = if (a.isZero) null else c.negate() / a

        fun formatGeneral(): String {
            val sb = StringBuilder()
            var first = true
            if (!a.isZero) {
                sb.append(ExactMath.formatTerm(a, "x", first))
                first = false
            }
            if (!b.isZero) {
                sb.append(ExactMath.formatTerm(b, "y", first))
                first = false
            }
            if (!c.isZero) {
                sb.append(ExactMath.formatTerm(c, "", first))
                first = false
            }
            if (first) sb.append("0")
            sb.append(" = 0")
            return sb.toString()
        }

        fun formatSlopeIntercept(): String {
            if (isVertical) {
                val xVal = c.negate() / a
                return "x = ${xVal.toFracString()}"
            }
            val m = slopeOrNull()!!
            val y0 = yInterceptOrNull()!!
            if (m.isZero) return "y = ${y0.toFracString()}"
            val xPart = when {
                m.isOne -> "x"
                m == ExactFraction.MINUS_ONE -> "-x"
                m.isInteger -> "${m.num}x"
                else -> "${m.toFracString()} x"
            }
            return when {
                y0.isZero -> "y = $xPart"
                y0.signum > 0 -> "y = $xPart + ${y0.toFracString()}"
                else -> "y = $xPart - ${y0.abs().toFracString()}"
            }
        }

        fun evalLhs(x: ExactFraction, y: ExactFraction): ExactFraction = a * x + b * y + c
    }

    override fun solve(input: Map<String, String>): SolveResult {
        val mode = input["mode"]?.trim().orEmpty().ifEmpty { MODE_GRAD_TITIK }
        return when (mode) {
            MODE_GRAD_TITIK -> solveGradientTwoPoints(input)
            MODE_GRAD_PERS -> solveGradientFromEquation(input)
            MODE_GARIS_TITIK_M -> solveLinePointSlope(input)
            MODE_GARIS_DUA_TITIK -> solveLineTwoPoints(input)
            MODE_SEJAJAR -> solveParallel(input)
            MODE_TEGAK_LURUS -> solvePerpendicular(input)
            MODE_TITIK_POTONG_SUMBU -> solveIntercepts(input)
            MODE_FUNGSI_LINEAR -> solveLinearFunction(input)
            MODE_POTONG_DUA_GARIS -> solveIntersectionViaSpldv(input)
            else -> SolveResult.fail("Sub-fitur modul 10 tidak dikenal: $mode")
        }
    }

    // ---- a) Gradien dari dua titik ----

    private fun solveGradientTwoPoints(input: Map<String, String>): SolveResult {
        val (p1, e1) = parseSinglePoint(input["titik1"], "Titik 1 (x₁, y₁)")
        if (e1 != null) return SolveResult.fail(e1)
        val (p2, e2) = parseSinglePoint(input["titik2"], "Titik 2 (x₂, y₂)")
        if (e2 != null) return SolveResult.fail(e2)
        val (x1, y1) = p1!!
        val (x2, y2) = p2!!

        if (x1 == x2 && y1 == y2) {
            return SolveResult.fail("Kedua titik sama (${x1}, ${y1}), gradien tidak dapat ditentukan dari satu titik.")
        }

        val steps = ArrayList<String>()
        steps.add("Rumus gradien dua titik: m = (y₂ - y₁) / (x₂ - x₁)")
        steps.add("Substitusi (${x1}, ${y1}) dan (${x2}, ${y2}): m = (${y2} - (${y1})) / (${x2} - (${x1}))")

        if (x1 == x2) {
            steps.add("Penyebut x₂ - x₁ = 0 → pembagian oleh 0 tidak terdefinisi.")
            steps.add("Garis merupakan garis vertikal x = ${x1.toFracString()}.")
            val line = normalizeIntegerLine(ExactFraction.ONE, ExactFraction.ZERO, x1.negate())
            val (plot, svg) = buildPlotAndSvg(line, x1 to y1, "(${x1}, ${y1})")
            return SolveResult(
                ok = true,
                steps = steps,
                answer = "Gradien tidak terdefinisi (garis vertikal x = ${x1.toFracString()})",
                check = "Semua titik pada garis memiliki absis x = ${x1.toFracString()} (${x1} == ${x2}).",
                svgSketch = svg,
                plotSpec = plot
            )
        }

        val dy = y2 - y1
        val dx = x2 - x1
        val m = dy / dx
        steps.add("Hitung: m = (${dy.toFracString()}) / (${dx.toFracString()}) = ${m.toFracString()}")
        if (m.isZero) {
            steps.add("Karena y₁ = y₂, garis horizontal dengan gradien 0.")
        }

        val line = normalizeIntegerLine(dy.negate(), dx, dx * y1 - dy * x1)
        val (plot, svg) = buildPlotAndSvg(line, x1 to y1, "(${x1}, ${y1})")
        val ans = if (m.isZero) "m = 0 (garis horizontal y = ${y1.toFracString()})" else "m = ${m.toFracString()}"
        val checkY = y1 + m * (x2 - x1)
        return SolveResult(
            ok = true,
            steps = steps,
            answer = ans,
            check = "Cek y₁ + m(x₂ - x₁) = ${y1} + (${m})(${dx}) = ${checkY} (cocok dengan y₂ = ${y2}).",
            svgSketch = svg,
            plotSpec = plot
        )
    }

    // ---- b) Gradien dari persamaan garis ----

    private fun solveGradientFromEquation(input: Map<String, String>): SolveResult {
        val raw = input["garis1"]?.trim().orEmpty()
        val (line, err) = parseLineEquation(raw)
        if (err != null) return SolveResult.fail(err)
        line!!

        val steps = ArrayList<String>()
        steps.add("Bentuk umum ternormalisasi: ${line.formatGeneral()}")

        if (line.isVertical) {
            val xVal = line.xInterceptOrNull()!!
            steps.add("Karena koefisien y (b) = 0, persamaan berbentuk garis vertikal x = ${xVal.toFracString()}.")
            steps.add("Gradien garis vertikal tidak terdefinisi.")
            val (plot, svg) = buildPlotAndSvg(line)
            return SolveResult(
                ok = true,
                steps = steps,
                answer = "Gradien tidak terdefinisi (garis vertikal x = ${xVal.toFracString()})",
                check = "Substitusi titik (${xVal}, 0): ${line.a}·(${xVal}) + ${line.c} = ${line.evalLhs(xVal, ExactFraction.ZERO)} = 0.",
                svgSketch = svg,
                plotSpec = plot
            )
        }

        val m = line.slopeOrNull()!!
        val expl = line.formatSlopeIntercept()
        steps.add("Rumus gradien dari ax + by + c = 0: m = -a / b")
        steps.add("Substitusi a = ${line.a}, b = ${line.b}: m = -(${line.a}) / (${line.b}) = ${m.toFracString()}")
        steps.add("Bentuk eksplisit: $expl")
        val xInt = line.xInterceptOrNull()
        val yInt = line.yInterceptOrNull()!!
        val intInfo = buildString {
            if (xInt != null) append("Potong sumbu-x: (${xInt}, 0); ") else append("Tidak memotong sumbu-x; ")
            append("Potong sumbu-y: (0, ${yInt})")
        }
        steps.add(intInfo)

        val (plot, svg) = buildPlotAndSvg(line)
        val ans = "m = ${m.toFracString()}; $expl"
        val checkY = yInt
        return SolveResult(
            ok = true,
            steps = steps,
            answer = ans,
            check = "Cek titik potong sumbu-y (0, ${checkY}): ${line.formatGeneral()} → ${line.evalLhs(ExactFraction.ZERO, checkY)} = 0.",
            svgSketch = svg,
            plotSpec = plot
        )
    }

    // ---- c) Persamaan garis melalui 1 titik dengan gradien m ----

    private fun solveLinePointSlope(input: Map<String, String>): SolveResult {
        val (pt, ep) = parseSinglePoint(input["titik1"], "Titik (x₁, y₁)")
        if (ep != null) return SolveResult.fail(ep)
        val (x1, y1) = pt!!

        val mRaw = input["m_nilai"]?.trim().orEmpty()
        if (mRaw.isEmpty()) return SolveResult.fail("Masukkan nilai gradien m (atau 'tak terdefinisi' untuk garis vertikal).")
        val steps = ArrayList<String>()

        if (mRaw.contains("tak terdefinisi", ignoreCase = true) || mRaw.equals("undefined", ignoreCase = true) || mRaw == "∞") {
            val line = normalizeIntegerLine(ExactFraction.ONE, ExactFraction.ZERO, x1.negate())
            steps.add("Gradien tak terdefinisi menandakan garis vertikal melalui x = ${x1}.")
            steps.add("Persamaan: x = ${x1} atau ${line.formatGeneral()}")
            val (plot, svg) = buildPlotAndSvg(line, pt, "(${x1}, ${y1})")
            return SolveResult(
                ok = true,
                steps = steps,
                answer = "x = ${x1} (bentuk umum: ${line.formatGeneral()})",
                check = "Substitusi (${x1}, ${y1}) ke ${line.formatGeneral()} → ${line.evalLhs(x1, y1)} = 0.",
                svgSketch = svg,
                plotSpec = plot
            )
        }

        val m = ExactFraction.parse(mRaw)
            ?: return SolveResult.fail("Format gradien \"$mRaw\" tidak valid.")
        return buildFromPointAndSlope(pt, m, steps)
    }

    // ---- d) Persamaan garis melalui 2 titik ----

    private fun solveLineTwoPoints(input: Map<String, String>): SolveResult {
        val (p1, e1) = parseSinglePoint(input["titik1"], "Titik 1 (x₁, y₁)")
        if (e1 != null) return SolveResult.fail(e1)
        val (p2, e2) = parseSinglePoint(input["titik2"], "Titik 2 (x₂, y₂)")
        if (e2 != null) return SolveResult.fail(e2)
        val (x1, y1) = p1!!
        val (x2, y2) = p2!!

        if (x1 == x2 && y1 == y2) {
            return SolveResult.fail("Dua titik yang sama (${x1}, ${y1}) tidak menentukan garis unik.")
        }

        val steps = ArrayList<String>()
        steps.add("1. Cari gradien dari (${x1}, ${y1}) dan (${x2}, ${y2}):")
        steps.add("   Rumus: m = (y₂ - y₁) / (x₂ - x₁) = (${y2} - (${y1})) / (${x2} - (${x1}))")

        if (x1 == x2) {
            val line = normalizeIntegerLine(ExactFraction.ONE, ExactFraction.ZERO, x1.negate())
            steps.add("   Penyebut = 0 (gradien tak terdefinisi) → garis vertikal x = ${x1}")
            steps.add("   Bentuk umum: ${line.formatGeneral()}")
            val (plot, svg) = buildPlotAndSvg(line, p1, "(${x1}, ${y1})")
            return SolveResult(
                ok = true,
                steps = steps,
                answer = "x = ${x1} (gradien tak terdefinisi); bentuk umum: ${line.formatGeneral()}",
                check = "Cek kedua titik: x₁ = ${x1} dan x₂ = ${x2} sama-sama memenuhi x = ${x1}.",
                svgSketch = svg,
                plotSpec = plot
            )
        }

        val m = (y2 - y1) / (x2 - x1)
        steps.add("   Hasil gradien: m = ${m.toFracString()}")
        val res = buildFromPointAndSlope(p1, m, steps)
        val cVal = y1 - m * x1
        val line = normalizeIntegerLine(m.negate(), ExactFraction.ONE, cVal.negate())
        val v2 = line.evalLhs(x2, y2)
        return res.copy(
            check = "Cek titik (${x1}, ${y1}) → ${line.evalLhs(x1, y1)} = 0; titik (${x2}, ${y2}) → $v2 = 0 (Sesuai)."
        )
    }

    // ---- e) Garis sejajar melalui titik P ----

    private fun solveParallel(input: Map<String, String>): SolveResult {
        val (ref, er) = parseLineEquation(input["garis1"]?.trim().orEmpty())
        if (er != null) return SolveResult.fail(er)
        val (p, ep) = parseSinglePoint(input["titik1"], "Titik P")
        if (ep != null) return SolveResult.fail(ep)
        ref!!; p!!

        val steps = ArrayList<String>()
        steps.add("Garis acuan: ${ref.formatSlopeIntercept()} (bentuk umum: ${ref.formatGeneral()})")
        steps.add("Syarat dua garis sejajar: gradien sama (m₂ = m₁).")

        if (ref.isVertical) {
            val line = normalizeIntegerLine(ExactFraction.ONE, ExactFraction.ZERO, p.first.negate())
            steps.add("Garis acuan vertikal, sehingga garis sejajar melalui (${p.first}, ${p.second}) juga vertikal: x = ${p.first}")
            val (plot, svg) = buildPlotAndSvg(line, p, "P(${p.first}, ${p.second})")
            return SolveResult(
                ok = true,
                steps = steps,
                answer = "x = ${p.first} (bentuk umum: ${line.formatGeneral()})",
                check = "Cek P(${p.first}, ${p.second}): ${line.formatGeneral()} → ${line.evalLhs(p.first, p.second)} = 0.",
                svgSketch = svg,
                plotSpec = plot
            )
        }

        val m = ref.slopeOrNull()!!
        steps.add("Gradien acuan m₁ = ${m.toFracString()} → m₂ = ${m.toFracString()}")
        return buildFromPointAndSlope(p, m, steps, pointName = "P")
    }

    // ---- f) Garis tegak lurus melalui titik P ----

    private fun solvePerpendicular(input: Map<String, String>): SolveResult {
        val (ref, er) = parseLineEquation(input["garis1"]?.trim().orEmpty())
        if (er != null) return SolveResult.fail(er)
        val (p, ep) = parseSinglePoint(input["titik1"], "Titik P")
        if (ep != null) return SolveResult.fail(ep)
        ref!!; p!!

        val steps = ArrayList<String>()
        steps.add("Garis acuan: ${ref.formatSlopeIntercept()} (bentuk umum: ${ref.formatGeneral()})")

        if (ref.isVertical) {
            steps.add("Garis acuan vertikal (gradien tak terdefinisi) → garis tegak lurusnya horizontal (m₂ = 0).")
            return buildFromPointAndSlope(p, ExactFraction.ZERO, steps, pointName = "P")
        }

        val m1 = ref.slopeOrNull()!!
        if (m1.isZero) {
            val line = normalizeIntegerLine(ExactFraction.ONE, ExactFraction.ZERO, p.first.negate())
            steps.add("Garis acuan horizontal (m₁ = 0) → garis tegak lurusnya vertikal melalui (${p.first}, ${p.second}):")
            steps.add("Persamaan: x = ${p.first} (bentuk umum: ${line.formatGeneral()})")
            val (plot, svg) = buildPlotAndSvg(line, p, "P(${p.first}, ${p.second})")
            return SolveResult(
                ok = true,
                steps = steps,
                answer = "x = ${p.first} (bentuk umum: ${line.formatGeneral()})",
                check = "Garis horizontal (y = konstan) tegak lurus dengan garis vertikal x = ${p.first}, dan P terpenuhi.",
                svgSketch = svg,
                plotSpec = plot
            )
        }

        val m2 = ExactFraction.MINUS_ONE / m1
        steps.add("Syarat tegak lurus: m₁ × m₂ = -1 → m₂ = -1 / (${m1.toFracString()}) = ${m2.toFracString()}")
        val res = buildFromPointAndSlope(p, m2, steps, pointName = "P")
        return res.copy(
            check = "${res.check}; m₁ × m₂ = (${m1}) × (${m2}) = ${(m1 * m2)}"
        )
    }

    // ---- g) Titik potong dengan sumbu-x dan sumbu-y ----

    private fun solveIntercepts(input: Map<String, String>): SolveResult {
        val (line, err) = parseLineEquation(input["garis1"]?.trim().orEmpty())
        if (err != null) return SolveResult.fail(err)
        line!!

        val steps = ArrayList<String>()
        steps.add("Persamaan garis: ${line.formatGeneral()} (${line.formatSlopeIntercept()})")

        val xInt = line.xInterceptOrNull()
        val yInt = line.yInterceptOrNull()

        steps.add("1. Titik potong sumbu-x (syarat y = 0):")
        val xStr = if (xInt == null) {
            steps.add("   ${line.b}·y + ${line.c} = 0 (koefisien x = 0) → garis sejajar sumbu-x, tidak memotong sumbu-x.")
            "tidak memotong sumbu-x"
        } else {
            steps.add("   Substitusi y = 0: ${ExactMath.formatTerm(line.a, "x", true)} + (${line.c}) = 0 → x = ${xInt.toFracString()}")
            steps.add("   Titik potong sumbu-x: (${xInt.toFracString()}, 0)")
            "(${xInt.toFracString()}, 0)"
        }

        steps.add("2. Titik potong sumbu-y (syarat x = 0):")
        val yStr = if (yInt == null) {
            steps.add("   koefisien y = 0 → garis sejajar sumbu-y, tidak memotong sumbu-y.")
            "tidak memotong sumbu-y"
        } else {
            steps.add("   Substitusi x = 0: ${ExactMath.formatTerm(line.b, "y", true)} + (${line.c}) = 0 → y = ${yInt.toFracString()}")
            steps.add("   Titik potong sumbu-y: (0, ${yInt.toFracString()})")
            "(0, ${yInt.toFracString()})"
        }

        val checkParts = ArrayList<String>()
        if (xInt != null) checkParts.add("(${xInt}, 0) → ${line.evalLhs(xInt, ExactFraction.ZERO)} = 0")
        if (yInt != null) checkParts.add("(0, ${yInt}) → ${line.evalLhs(ExactFraction.ZERO, yInt)} = 0")

        val (plot, svg) = buildPlotAndSvg(line)
        return SolveResult(
            ok = true,
            steps = steps,
            answer = "Potong sumbu-x: $xStr; Potong sumbu-y: $yStr",
            check = "Substitusi ke ${line.formatGeneral()}: ${checkParts.joinToString("; ")}.",
            svgSketch = svg,
            plotSpec = plot
        )
    }

    // ---- h) Fungsi linear f(x) = ax + b ----

    private fun solveLinearFunction(input: Map<String, String>): SolveResult {
        val rawFn = input["garis1"]?.trim().orEmpty()
        val rawT1 = input["titik1"]?.trim().orEmpty()
        val rawT2 = input["titik2"]?.trim().orEmpty()
        val rawK = input["m_nilai"]?.trim().orEmpty()
        val rawN = input["b_nilai"]?.trim().orEmpty()

        val steps = ArrayList<String>()
        val aCoeff: ExactFraction
        val bConst: ExactFraction
        var extraCheckNote = ""

        val pair1 = parseFunctionSampleOrPoint(rawT1)
        val pair2 = parseFunctionSampleOrPoint(rawT2)
        val hasExplicitSample = rawT1.contains("=") || rawT2.contains("=")
        val preferSamples = (pair1 != null && pair2 != null) &&
            (rawFn.isEmpty() || hasExplicitSample || rawFn == DEFAULT_LINE_SAMPLE)

        if (preferSamples) {
            // Mode menentukan a & b dari f(p) = vp dan f(q) = vq
            val (p, vp) = pair1
            val (q, vq) = pair2
            if (p == q) return SolveResult.fail("Nilai p dan q pada f(p) dan f(q) harus berbeda.")
            steps.add("Diketahui f(${p}) = ${vp} dan f(${q}) = ${vq} pada fungsi f(x) = ax + b:")
            steps.add("  f(${q}) - f(${p}) = a(${q} - ${p})")
            aCoeff = (vq - vp) / (q - p)
            steps.add("  a = (${vq} - (${vp})) / (${q} - (${p})) = ${aCoeff.toFracString()}")
            bConst = vp - aCoeff * p
            steps.add("Substitusi a = ${aCoeff} ke f(${p}) = a(${p}) + b = ${vp}:")
            steps.add("  b = ${vp} - (${aCoeff})(${p}) = ${bConst.toFracString()}")
            extraCheckNote = "f(${p}) = ${(aCoeff * p + bConst)}, f(${q}) = ${(aCoeff * q + bConst)}"
        } else if (rawFn.isNotEmpty()) {
            val normalizedExpr = if (rawFn.contains("=")) rawFn else "y = $rawFn"
            val (line, err) = parseLineEquation(normalizedExpr)
            if (err != null) return SolveResult.fail(err)
            if (line!!.isVertical) return SolveResult.fail("Bentuk vertikal x = c bukan merupakan fungsi y = f(x).")
            aCoeff = line.slopeOrNull()!!
            bConst = line.yInterceptOrNull()!!
            steps.add("Fungsi linear dari masukan: ${formatFx(aCoeff, bConst)} (a = ${aCoeff}, b = ${bConst})")
        } else {
            return SolveResult.fail("Masukkan rumus f(x) = ax + b di kolom garis 1, ATAU masukkan dua nilai f(p)=v1 dan f(q)=v2 pada Titik 1 & Titik 2.")
        }

        val fxText = formatFx(aCoeff, bConst)
        val ansParts = ArrayList<String>()
        ansParts.add(fxText)

        if (rawK.isNotEmpty()) {
            val k = ExactFraction.parse(rawK)
                ?: return SolveResult.fail("Nilai k (\"$rawK\") tidak valid.")
            val fk = aCoeff * k + bConst
            steps.add("Hitung f(${k}): f(${k}) = (${aCoeff})(${k}) + (${bConst}) = ${fk.toFracString()}")
            ansParts.add("f(${k.toFracString()}) = ${fk.toFracString()}")
        }

        if (rawN.isNotEmpty()) {
            val nVal = ExactFraction.parse(rawN)
                ?: return SolveResult.fail("Nilai target n (\"$rawN\") tidak valid.")
            if (aCoeff.isZero) {
                steps.add("Karena a = 0, fungsi konstan f(x) = ${bConst}. ${if (bConst == nVal) "Semua x memenuhi." else "Tidak ada x sehingga f(x) = ${nVal}."}")
                ansParts.add(if (bConst == nVal) "x ∈ ℝ" else "tidak ada x untuk f(x) = ${nVal}")
            } else {
                val xFound = (nVal - bConst) / aCoeff
                steps.add("Cari x saat f(x) = ${nVal}: (${aCoeff})x + (${bConst}) = ${nVal} → x = (${nVal} - (${bConst})) / (${aCoeff}) = ${xFound.toFracString()}")
                ansParts.add("f(x) = ${nVal.toFracString()} → x = ${xFound.toFracString()}")
            }
        }

        val line = normalizeIntegerLine(aCoeff.negate(), ExactFraction.ONE, bConst.negate())
        val (plot, svg) = buildPlotAndSvg(line)
        val checkStr = if (extraCheckNote.isNotEmpty()) {
            "Substitusi kembali ke $fxText: $extraCheckNote."
        } else {
            "Fungsi linear $fxText bergradien a = ${aCoeff} dan memotong sumbu-y di (0, ${bConst})."
        }
        return SolveResult(
            ok = true,
            steps = steps,
            answer = ansParts.joinToString("; "),
            check = checkStr,
            svgSketch = svg,
            plotSpec = plot
        )
    }

    // ---- i) Titik potong dua garis -> delegasi ke SpldvModule ----

    private fun solveIntersectionViaSpldv(input: Map<String, String>): SolveResult {
        val (l1, e1) = parseLineEquation(input["garis1"]?.trim().orEmpty(), "Garis 1")
        if (e1 != null) return SolveResult.fail(e1)
        val (l2, e2) = parseLineEquation(input["garis2"]?.trim().orEmpty(), "Garis 2")
        if (e2 != null) return SolveResult.fail(e2)
        l1!!; l2!!

        val spldvInput = mapOf(
            "a1" to l1.a.toFracString(),
            "b1" to l1.b.toFracString(),
            "c1" to l1.c.negate().toFracString(),
            "a2" to l2.a.toFracString(),
            "b2" to l2.b.toFracString(),
            "c2" to l2.c.negate().toFracString()
        )
        val spldvRes = SpldvModule().solve(spldvInput)
        if (!spldvRes.ok) return spldvRes

        val steps = ArrayList<String>()
        steps.add("Ubah kedua garis ke bentuk SPLDV ax + by = c dan selesaikan dengan solver SPLDV:")
        steps.addAll(spldvRes.steps)

        val xMatch = Regex("""x\s*=\s*([^,\s]+)""").find(spldvRes.answer)?.groupValues?.get(1)
        val yMatch = Regex("""y\s*=\s*([^,\s]+)""").find(spldvRes.answer)?.groupValues?.get(1)
        val answer = if (xMatch != null && yMatch != null) {
            "Titik potong: ($xMatch, $yMatch)"
        } else {
            spldvRes.answer
        }

        val interPt = if (xMatch != null && yMatch != null) {
            val px = ExactFraction.parse(xMatch)
            val py = ExactFraction.parse(yMatch)
            if (px != null && py != null) px to py else null
        } else null
        val (plot, svg) = buildPlotAndSvg(l1, interPt, if (interPt != null) "(${interPt.first}, ${interPt.second})" else null)

        return SolveResult(
            ok = true,
            steps = steps,
            answer = answer,
            check = spldvRes.check,
            svgSketch = svg,
            plotSpec = plot
        )
    }

    // ---- Pembantu internal ----

    private fun buildFromPointAndSlope(
        pt: Pair<ExactFraction, ExactFraction>,
        m: ExactFraction,
        steps: ArrayList<String>,
        pointName: String = ""
    ): SolveResult {
        val (x1, y1) = pt
        val cVal = y1 - m * x1
        val line = normalizeIntegerLine(m.negate(), ExactFraction.ONE, cVal.negate())

        steps.add("Rumus persamaan garis: y - y₁ = m(x - x₁)")
        steps.add("Substitusi titik $pointName(${x1}, ${y1}) dan m = ${m}:")
        steps.add("  y - (${y1}) = (${m})(x - (${x1}))")
        val yExplicit = line.formatSlopeIntercept()
        val general = line.formatGeneral()
        steps.add("Bentuk eksplisit: $yExplicit")
        steps.add("Bentuk umum (koefisien bulat, a positif): $general")

        val (plot, svg) = buildPlotAndSvg(line, pt, "$pointName(${x1}, ${y1})")
        return SolveResult(
            ok = true,
            steps = steps,
            answer = "$yExplicit | bentuk umum: $general",
            check = "Substitusi $pointName(${x1}, ${y1}) ke $general → ${line.evalLhs(x1, y1)} = 0 (Cocok).",
            svgSketch = svg,
            plotSpec = plot
        )
    }

    private fun formatFx(a: ExactFraction, b: ExactFraction): String {
        if (a.isZero) return "f(x) = ${b.toFracString()}"
        val xPart = when {
            a.isOne -> "x"
            a == ExactFraction.MINUS_ONE -> "-x"
            a.isInteger -> "${a.num}x"
            else -> "${a.toFracString()} x"
        }
        return when {
            b.isZero -> "f(x) = $xPart"
            b.signum > 0 -> "f(x) = $xPart + ${b.toFracString()}"
            else -> "f(x) = $xPart - ${b.abs().toFracString()}"
        }
    }

    private fun normalizeIntegerLine(a: ExactFraction, b: ExactFraction, c: ExactFraction): NormalizedLine {
        val lcm1 = lcm(a.den, b.den)
        val lcmAll = lcm(lcm1, c.den)
        var ai = a.num.multiply(lcmAll.divide(a.den))
        var bi = b.num.multiply(lcmAll.divide(b.den))
        var ci = c.num.multiply(lcmAll.divide(c.den))

        val g = ai.abs().gcd(bi.abs()).gcd(ci.abs())
        if (g > BigInteger.ONE) {
            ai = ai.divide(g)
            bi = bi.divide(g)
            ci = ci.divide(g)
        }
        val flip = (ai.signum() < 0) || (ai == BigInteger.ZERO && bi.signum() < 0)
        if (flip) {
            ai = ai.negate()
            bi = bi.negate()
            ci = ci.negate()
        }
        return NormalizedLine(ExactFraction.of(ai, BigInteger.ONE), ExactFraction.of(bi, BigInteger.ONE), ExactFraction.of(ci, BigInteger.ONE))
    }

    private fun lcm(a: BigInteger, b: BigInteger): BigInteger = a.divide(a.gcd(b)).multiply(b)

    private fun parseSinglePoint(raw: String?, label: String): Pair<Pair<ExactFraction, ExactFraction>?, String?> {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null to "Kolom \"$label\" tidak boleh kosong."
        val cleaned = s.removePrefix("(").removeSuffix(")").trim()
        val parts = cleaned.split(';').firstOrNull()?.split(',') ?: emptyList()
        if (parts.size != 2) {
            return null to "Format \"$label\" harus berupa (x, y), contoh: (1, 2)."
        }
        val x = ExactFraction.parse(parts[0]) ?: return null to "Nilai x pada \"$label\" tidak valid."
        val y = ExactFraction.parse(parts[1]) ?: return null to "Nilai y pada \"$label\" tidak valid."
        return (x to y) to null
    }

    private fun parseFunctionSampleOrPoint(raw: String): Pair<ExactFraction, ExactFraction>? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val fnMatch = Regex("""^[a-zA-Z]\s*\(\s*([^)]+)\s*\)\s*=\s*(.+)$""").matchEntire(s)
        if (fnMatch != null) {
            val x = ExactFraction.parse(fnMatch.groupValues[1]) ?: return null
            val y = ExactFraction.parse(fnMatch.groupValues[2]) ?: return null
            return x to y
        }
        return parseSinglePoint(s, "titik").first
    }

    internal fun parseLineEquation(raw: String, label: String = "Persamaan garis"): Pair<NormalizedLine?, String?> {
        if (raw.isBlank()) return null to "Kolom \"$label\" tidak boleh kosong."
        var s = raw.trim()
        s = s.replace(Regex("""^[fgh]\s*\(\s*x\s*\)""", RegexOption.IGNORE_CASE), "y")
        s = s.replace("−", "-").replace("–", "-")
        val sides = s.split('=')
        if (sides.size != 2 || sides[0].isBlank() || sides[1].isBlank()) {
            return null to "Format \"$label\" harus memuat tepat satu tanda '=', contoh: 2x + 3y - 6 = 0 atau y = 2x + 1."
        }
        val lhs = parse2VarLinearExpr(sides[0])
            ?: return null to "Ruas kiri \"$label\" bukan bentuk linear ax + by + c yang valid."
        val rhs = parse2VarLinearExpr(sides[1])
            ?: return null to "Ruas kanan \"$label\" bukan bentuk linear ax + by + c yang valid."

        val a = lhs.first - rhs.first
        val b = lhs.second - rhs.second
        val c = lhs.third - rhs.third
        if (a.isZero && b.isZero) {
            return null to "Persamaan \"$label\" tidak memiliki variabel x maupun y."
        }
        return normalizeIntegerLine(a, b, c) to null
    }

    private fun parse2VarLinearExpr(expr: String): Triple<ExactFraction, ExactFraction, ExactFraction>? {
        // Samakan bentuk "-1/2 x" menjadi "-1/2x" dan pisahkan suku pada + / -
        var s = expr.replace(Regex("""\s+"""), "")
        if (s.isEmpty()) return null
        // Ganti "-x" / "+y" dengan tanda terpisah yang aman saat split
        val terms = ArrayList<String>()
        var start = 0
        for (i in 1 until s.length) {
            if ((s[i] == '+' || s[i] == '-') && s[i - 1] != '(' && s[i - 1] != '/') {
                terms.add(s.substring(start, i))
                start = i
            }
        }
        terms.add(s.substring(start))

        var ax = ExactFraction.ZERO
        var by = ExactFraction.ZERO
        var cc = ExactFraction.ZERO

        for (rawTerm in terms) {
            if (rawTerm.isEmpty()) return null
            val t = rawTerm.removePrefix("+")
            if (t.contains("x", ignoreCase = true) && t.contains("y", ignoreCase = true)) return null

            val varChar = when {
                t.contains("x", ignoreCase = true) -> 'x'
                t.contains("y", ignoreCase = true) -> 'y'
                else -> null
            }

            if (varChar == null) {
                val cVal = ExactFraction.parse(t) ?: return null
                cc += cVal
            } else {
                val idx = t.indexOf(varChar, ignoreCase = true)
                var before = t.substring(0, idx)
                if (before.startsWith("(") && before.endsWith(")")) {
                    before = before.substring(1, before.length - 1)
                }
                val after = t.substring(idx + 1)
                if (after.isNotEmpty() && !after.startsWith("/")) return null
                val numCoeffStr = when (before) {
                    "" -> "1"
                    "-" -> "-1"
                    else -> before
                }
                val baseCoeff = ExactFraction.parse(numCoeffStr) ?: return null
                val coeff = if (after.startsWith("/")) {
                    val den = ExactFraction.parse(after.substring(1)) ?: return null
                    if (den.isZero) return null
                    baseCoeff / den
                } else {
                    baseCoeff
                }
                if (varChar == 'x') ax += coeff else by += coeff
            }
        }
        return Triple(ax, by, cc)
    }

    private fun buildPlotAndSvg(
        line: NormalizedLine,
        point: Pair<ExactFraction, ExactFraction>? = null,
        pointLabel: String? = null
    ): Pair<LinePlotSpec, String> {
        val xInt = line.xInterceptOrNull()
        val yInt = line.yInterceptOrNull()
        val aD = line.a.toDoubleApprox()
        val bD = line.b.toDoubleApprox()
        val cD = line.c.toDoubleApprox()
        val xIntD = xInt?.toDoubleApprox()
        val yIntD = yInt?.toDoubleApprox()
        val pxD = point?.first?.toDoubleApprox()
        val pyD = point?.second?.toDoubleApprox()
        val eqLabel = line.formatSlopeIntercept()

        val spec = LinePlotSpec(
            a = aD,
            b = bD,
            c = cD,
            xInterceptLabel = xInt?.let { "(${it.toFracString()}, 0)" },
            yInterceptLabel = yInt?.let { "(0, ${it.toFracString()})" },
            equationLabel = eqLabel,
            xIntVal = xIntD,
            yIntVal = yIntD,
            pointX = pxD,
            pointY = pyD,
            pointLabel = pointLabel
        )
        val svg = generateSvg(spec)
        return spec to svg
    }

    private fun generateSvg(spec: LinePlotSpec): String {
        val w = 260
        val h = 180
        val cx = w / 2.0
        val cy = h / 2.0
        var maxCoord = 5.0
        spec.xIntVal?.let { if (it.isFinite()) maxCoord = max(maxCoord, abs(it) * 1.4) }
        spec.yIntVal?.let { if (it.isFinite()) maxCoord = max(maxCoord, abs(it) * 1.4) }
        spec.pointX?.let { if (it.isFinite()) maxCoord = max(maxCoord, abs(it) * 1.4) }
        spec.pointY?.let { if (it.isFinite()) maxCoord = max(maxCoord, abs(it) * 1.4) }
        val scale = (minOf(w, h) / 2.0 - 20.0) / maxCoord

        fun sx(x: Double) = cx + x * scale
        fun sy(y: Double) = cy - y * scale

        val lineCoords: Pair<Pair<Double, Double>, Pair<Double, Double>>? = when {
            abs(spec.b) < 1e-9 && abs(spec.a) > 1e-9 -> {
                val xv = -spec.c / spec.a
                (xv to -maxCoord) to (xv to maxCoord)
            }
            abs(spec.b) > 1e-9 -> {
                val yLeft = (-spec.c - spec.a * (-maxCoord)) / spec.b
                val yRight = (-spec.c - spec.a * maxCoord) / spec.b
                (-maxCoord to yLeft) to (maxCoord to yRight)
            }
            else -> null
        }

        return buildString {
            append("""<svg viewBox="0 0 $w $h" xmlns="http://www.w3.org/2000/svg">""")
            append("""<line x1="12" y1="$cy" x2="${w - 12}" y2="$cy" stroke="#888" stroke-width="1"/>""")
            append("""<line x1="$cx" y1="12" x2="$cx" y2="${h - 12}" stroke="#888" stroke-width="1"/>""")
            if (lineCoords != null) {
                val (p1, p2) = lineCoords
                append("""<line x1="${sx(p1.first)}" y1="${sy(p1.second)}" x2="${sx(p2.first)}" y2="${sy(p2.second)}" stroke="#0A84FF" stroke-width="2.2"/>""")
            }
            spec.xIntVal?.let { xv ->
                val xPx = sx(xv)
                append("""<circle cx="$xPx" cy="$cy" r="3.5" fill="#FF3B30"/>""")
                append("""<text x="${xPx + 4}" y="${cy - 6}" font-size="10">${spec.xInterceptLabel}</text>""")
            }
            spec.yIntVal?.let { yv ->
                val yPx = sy(yv)
                append("""<circle cx="$cx" cy="$yPx" r="3.5" fill="#34C759"/>""")
                append("""<text x="${cx + 5}" y="${yPx - 4}" font-size="10">${spec.yInterceptLabel}</text>""")
            }
            if (spec.pointX != null && spec.pointY != null) {
                append("""<circle cx="${sx(spec.pointX)}" cy="${sy(spec.pointY)}" r="3.5" fill="#FF9500"/>""")
            }
            append("""</svg>""")
        }
    }

    private fun ExactFraction.toDoubleApprox(): Double =
        num.toDouble() / den.toDouble()

    companion object {
        private const val DEFAULT_LINE_SAMPLE = "2x + 3y - 6 = 0"
        const val MODE_GRAD_TITIK = "Gradien dari dua titik"
        const val MODE_GRAD_PERS = "Gradien dari persamaan garis"
        const val MODE_GARIS_TITIK_M = "Persamaan garis (1 titik & gradien m)"
        const val MODE_GARIS_DUA_TITIK = "Persamaan garis melalui 2 titik"
        const val MODE_SEJAJAR = "Garis sejajar melalui titik P"
        const val MODE_TEGAK_LURUS = "Garis tegak lurus melalui titik P"
        const val MODE_TITIK_POTONG_SUMBU = "Titik potong sumbu-x dan sumbu-y"
        const val MODE_FUNGSI_LINEAR = "Fungsi linear f(x) = ax + b"
        const val MODE_POTONG_DUA_GARIS = "Titik potong dua garis (SPLDV)"
    }
}
