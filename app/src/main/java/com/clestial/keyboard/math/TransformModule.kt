package com.clestial.keyboard.math

/**
 * Modul 5 (Geometri): Transformasi Geometri (Translasi, Refleksi, Rotasi, Dilatasi).
 * Mendukung satu atau banyak titik sekaligus serta pusat O(0,0) maupun pusat sembarang (p, q).
 */
class TransformModule : MathModule {

    override val id = "transformasi"
    override val name = "Transformasi Geometri"
    override val group = MathGroup.GEOMETRI
    override val description = "Translasi, refleksi, rotasi (90°/180°/270°), dan dilatasi untuk satu/banyak titik."

    override val fields = listOf(
        InputFieldSpec("titik", "Koordinat titik (pisahkan ; untuk banyak titik)", "(3,4)", FieldType.TEXT),
        InputFieldSpec(
            key = "jenis",
            label = "Jenis transformasi",
            type = FieldType.CHOICE,
            choices = listOf(KIND_TRANSLASI, KIND_REFLEKSI, KIND_ROTASI, KIND_DILATASI),
            defaultValue = KIND_TRANSLASI
        ),
        InputFieldSpec("param1", "Parameter 1 (a / garis / sudut / k)", "2", FieldType.TEXT, required = false),
        InputFieldSpec("param2", "Parameter 2 (b / nilai h atau k / arah)", "-1", FieldType.TEXT, required = false),
        InputFieldSpec("pusat", "Pusat (p, q) opsional, default (0,0)", "(0,0)", FieldType.TEXT, required = false)
    )

    override fun solve(input: Map<String, String>): SolveResult {
        val rawPts = input["titik"]?.trim().orEmpty()
        val points = parsePoints(rawPts)
            ?: return SolveResult.fail("Format koordinat titik tidak valid. Contoh: (3,4) atau (1,2); (3,4); (5,1).")
        if (points.isEmpty()) return SolveResult.fail("Masukkan minimal satu koordinat titik (x, y).")

        val jenis = input["jenis"]?.trim().orEmpty().ifEmpty { KIND_TRANSLASI }
        val p1Raw = input["param1"]?.trim().orEmpty()
        val p2Raw = input["param2"]?.trim().orEmpty()
        val pusatRaw = input["pusat"]?.trim().orEmpty()
        val center = if (pusatRaw.isEmpty()) {
            ExactFraction.ZERO to ExactFraction.ZERO
        } else {
            val cPts = parsePoints(pusatRaw)
            if (cPts == null || cPts.size != 1) {
                return SolveResult.fail("Format pusat (p, q) tidak valid. Contoh: (1,1) atau kosongkan untuk (0,0).")
            }
            cPts[0]
        }

        val steps = ArrayList<String>()
        val results = ArrayList<Pair<ExactFraction, ExactFraction>>()
        val p = center.first
        val q = center.second
        val isOrigin = p.isZero && q.isZero

        when (jenis) {
            KIND_TRANSLASI -> {
                val (a, e1) = InputChecks.requireFraction(input, "param1", "Pergeseran a (sumbu x)")
                if (e1 != null) return SolveResult.fail(e1)
                val (b, e2) = InputChecks.requireFraction(input, "param2", "Pergeseran b (sumbu y)")
                if (e2 != null) return SolveResult.fail(e2)
                a!!; b!!
                steps.add("Rumus translasi T(${a.toFracString()}, ${b.toFracString()}): (x', y') = (x + ${a.toFracString()}, y + ${b.toFracString()})")
                for ((idx, pt) in points.withIndex()) {
                    val nx = pt.first + a
                    val ny = pt.second + b
                    results.add(nx to ny)
                    steps.add("Titik ${pointName(idx)}(${pt.first}, ${pt.second}) → (${nx}, ${ny})")
                }
                val firstPt = points[0]
                val firstRes = results[0]
                return SolveResult(
                    ok = true,
                    steps = steps,
                    answer = formatPointsResult(results),
                    check = "Translasi balik T(${a.negate()}, ${b.negate()}) pada ${pointName(0)}'(${firstRes.first}, ${firstRes.second}) menghasilkan (${firstRes.first - a}, ${firstRes.second - b}) = (${firstPt.first}, ${firstPt.second})."
                )
            }

            KIND_REFLEKSI -> {
                val axis = p1Raw.lowercase().replace("sumbu-", "sumbu ").ifEmpty { "sumbu x" }
                val hVal: ExactFraction?
                when (axis) {
                    "sumbu x", "x" -> {
                        hVal = null
                        steps.add("Rumus refleksi terhadap sumbu-x: (x', y') = (x, -y)")
                    }
                    "sumbu y", "y" -> {
                        hVal = null
                        steps.add("Rumus refleksi terhadap sumbu-y: (x', y') = (-x, y)")
                    }
                    "y = x", "y=x" -> {
                        hVal = null
                        steps.add("Rumus refleksi terhadap garis y = x: (x', y') = (y, x)")
                    }
                    "y = -x", "y=-x" -> {
                        hVal = null
                        steps.add("Rumus refleksi terhadap garis y = -x: (x', y') = (-y, -x)")
                    }
                    "x = h", "x=h", "x =" -> {
                        val (h, eh) = InputChecks.requireFraction(input, "param2", "Nilai h untuk garis x = h")
                        if (eh != null) return SolveResult.fail(eh)
                        hVal = h!!
                        steps.add("Rumus refleksi terhadap garis x = ${h.toFracString()}: (x', y') = (2·${h.toFracString()} - x, y)")
                    }
                    "y = k", "y=k", "y =" -> {
                        val (k, ek) = InputChecks.requireFraction(input, "param2", "Nilai k untuk garis y = k")
                        if (ek != null) return SolveResult.fail(ek)
                        hVal = k!!
                        steps.add("Rumus refleksi terhadap garis y = ${k.toFracString()}: (x', y') = (x, 2·${k.toFracString()} - y)")
                    }
                    else -> {
                        // Dukung penulisan langsung seperti "x = 2" atau "y = -3" pada param1.
                        val eqMatch = Regex("""^([xy])\s*=\s*(.+)$""").matchEntire(axis)
                        if (eqMatch != null) {
                            val v = ExactFraction.parse(eqMatch.groupValues[2])
                                ?: return SolveResult.fail("Nilai konstanta pada garis refleksi \"$p1Raw\" tidak valid.")
                            hVal = v
                            val varChar = eqMatch.groupValues[1]
                            steps.add("Rumus refleksi terhadap garis $varChar = ${v.toFracString()}:")
                        } else {
                            return SolveResult.fail("Jenis refleksi tidak dikenal: \"$p1Raw\". Pilih sumbu x, sumbu y, y = x, y = -x, x = h, atau y = k.")
                        }
                    }
                }

                val two = ExactFraction.of(2)
                for ((idx, pt) in points.withIndex()) {
                    val (x, y) = pt
                    val res = when {
                        axis == "sumbu x" || axis == "x" -> x to y.negate()
                        axis == "sumbu y" || axis == "y" -> x.negate() to y
                        axis == "y = x" || axis == "y=x" -> y to x
                        axis == "y = -x" || axis == "y=-x" -> y.negate() to x.negate()
                        axis.startsWith("x") -> (two * hVal!! - x) to y
                        else -> x to (two * hVal!! - y)
                    }
                    results.add(res)
                    steps.add("Titik ${pointName(idx)}(${x}, ${y}) → (${res.first}, ${res.second})")
                }
                return SolveResult(
                    ok = true,
                    steps = steps,
                    answer = formatPointsResult(results),
                    check = "Refleksi bersifat involusi: merefleksikan bayangan terhadap cermin yang sama mengembalikan titik ke (${points[0].first}, ${points[0].second})."
                )
            }

            KIND_ROTASI -> {
                val deg = p1Raw.replace("°", "").trim().toIntOrNull()
                    ?: return SolveResult.fail("Sudut rotasi harus 90, 180, atau 270 derajat.")
                if (deg != 90 && deg != 180 && deg != 270) {
                    return SolveResult.fail("Sudut rotasi yang didukung adalah 90, 180, atau 270 derajat.")
                }
                val dir = p2Raw.lowercase()
                val clockwise = dir.contains("searah") || dir == "cw" || dir == "-"
                val effCcw = when {
                    deg == 180 -> 180
                    deg == 90 && !clockwise -> 90
                    deg == 90 && clockwise -> 270
                    deg == 270 && !clockwise -> 270
                    else -> 90
                }
                val dirLabel = if (deg == 180) "180°" else if (clockwise) "$deg° searah jarum jam" else "$deg° berlawanan arah jarum jam"
                steps.add("Rotasi $dirLabel dengan pusat (${p}, ${q}):")
                if (!isOrigin) {
                    steps.add("Langkah umum: (1) geser ke pusat (dx, dy) = (x - p, y - q), (2) putar terhadap O, (3) geser kembali + (p, q).")
                }

                for ((idx, pt) in points.withIndex()) {
                    val dx = pt.first - p
                    val dy = pt.second - q
                    val (rx, ry) = when (effCcw) {
                        90 -> dy.negate() to dx
                        180 -> dx.negate() to dy.negate()
                        else -> dy to dx.negate()
                    }
                    val nx = rx + p
                    val ny = ry + q
                    results.add(nx to ny)
                    if (isOrigin) {
                        steps.add("Titik ${pointName(idx)}(${pt.first}, ${pt.second}) → (${nx}, ${ny})")
                    } else {
                        steps.add("Titik ${pointName(idx)}(${pt.first}, ${pt.second}): geser (${dx}, ${dy}) → putar (${rx}, ${ry}) → geser kembali (${nx}, ${ny})")
                    }
                }
                val dSqBefore = (points[0].first - p) * (points[0].first - p) + (points[0].second - q) * (points[0].second - q)
                val dSqAfter = (results[0].first - p) * (results[0].first - p) + (results[0].second - q) * (results[0].second - q)
                return SolveResult(
                    ok = true,
                    steps = steps,
                    answer = formatPointsResult(results),
                    check = "Jarak kuadrat ke pusat terjaga: d² sebelum = ${dSqBefore}, d² sesudah = ${dSqAfter} (sama)."
                )
            }

            KIND_DILATASI -> {
                val (k, ek) = InputChecks.requireFraction(input, "param1", "Faktor skala (k)", allowZero = false)
                if (ek != null) return SolveResult.fail(ek)
                k!!
                steps.add("Dilatasi faktor skala k = ${k.toFracString()} dengan pusat (${p}, ${q}):")
                if (!isOrigin) {
                    steps.add("Langkah: (1) geser (dx, dy) = (x - p, y - q), (2) kalikan k, (3) geser kembali + (p, q).")
                } else {
                    steps.add("Rumus: (x', y') = (${k.toFracString()}·x, ${k.toFracString()}·y)")
                }
                for ((idx, pt) in points.withIndex()) {
                    val dx = pt.first - p
                    val dy = pt.second - q
                    val sx = k * dx
                    val sy = k * dy
                    val nx = sx + p
                    val ny = sy + q
                    results.add(nx to ny)
                    if (isOrigin) {
                        steps.add("Titik ${pointName(idx)}(${pt.first}, ${pt.second}) → (${nx}, ${ny})")
                    } else {
                        steps.add("Titik ${pointName(idx)}(${pt.first}, ${pt.second}): geser (${dx}, ${dy}) → skala (${sx}, ${sy}) → geser kembali (${nx}, ${ny})")
                    }
                }
                val dSqBefore = (points[0].first - p) * (points[0].first - p) + (points[0].second - q) * (points[0].second - q)
                val dSqAfter = (results[0].first - p) * (results[0].first - p) + (results[0].second - q) * (results[0].second - q)
                val expectedAfter = k * k * dSqBefore
                return SolveResult(
                    ok = true,
                    steps = steps,
                    answer = formatPointsResult(results),
                    check = "Jarak kuadrat ke pusat menjadi k²·d² = ${expectedAfter}, sesuai hasil (${dSqAfter})."
                )
            }

            else -> return SolveResult.fail("Jenis transformasi tidak dikenali: \"$jenis\".")
        }
    }

    private fun pointName(idx: Int): String = ('A' + (idx % 26)).toString()

    private fun formatPointsResult(list: List<Pair<ExactFraction, ExactFraction>>): String =
        if (list.size == 1) {
            "(${list[0].first.toFracString()}, ${list[0].second.toFracString()})"
        } else {
            list.mapIndexed { i, p -> "${pointName(i)}'(${p.first.toFracString()}, ${p.second.toFracString()})" }
                .joinToString(", ")
        }

    /** Parse daftar titik seperti "(3,4)", "(1,2); (3,4)", atau "3,4". */
    private fun parsePoints(raw: String): List<Pair<ExactFraction, ExactFraction>>? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val parenMatches = Regex("""\(\s*([^(),]+)\s*,\s*([^(),]+)\s*\)""").findAll(s).toList()
        if (parenMatches.isNotEmpty()) {
            val out = ArrayList<Pair<ExactFraction, ExactFraction>>()
            for (m in parenMatches) {
                val x = ExactFraction.parse(m.groupValues[1]) ?: return null
                val y = ExactFraction.parse(m.groupValues[2]) ?: return null
                out.add(x to y)
            }
            return out
        }
        val chunks = s.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<Pair<ExactFraction, ExactFraction>>()
        for (chunk in chunks) {
            val parts = chunk.split(',').map { it.trim() }
            if (parts.size != 2) return null
            val x = ExactFraction.parse(parts[0]) ?: return null
            val y = ExactFraction.parse(parts[1]) ?: return null
            out.add(x to y)
        }
        return out
    }

    companion object {
        const val KIND_TRANSLASI = "Translasi"
        const val KIND_REFLEKSI = "Refleksi"
        const val KIND_ROTASI = "Rotasi"
        const val KIND_DILATASI = "Dilatasi"
    }
}
