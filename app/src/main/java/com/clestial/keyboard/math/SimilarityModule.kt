package com.clestial.keyboard.math

/**
 * Modul 6 (Geometri): Kekongruenan dan Kesebangunan.
 * Sub-fitur:
 * 1) Cek dua segitiga sebangun (SSS) beserta faktor skala.
 * 2) Cari sisi dari perbandingan senilai a/b = c/x.
 * 3) Cek kekongruenan dua segitiga (SSS, SAS, ASA) beserta validasi ketaksamaan segitiga.
 */
class SimilarityModule : MathModule {

    override val id = "kesebangunan"
    override val name = "Kekongruenan & Kesebangunan"
    override val group = MathGroup.GEOMETRI
    override val description = "Cek kesebangunan/kekongruenan segitiga serta hitung sisi dari perbandingan senilai."

    override val fields = listOf(
        InputFieldSpec(
            key = "mode",
            label = "Sub-fitur",
            type = FieldType.CHOICE,
            choices = listOf(MODE_SEBANGUN, MODE_SENILAI, MODE_KONGRUEN_SSS, MODE_KONGRUEN_SAS, MODE_KONGRUEN_ASA),
            defaultValue = MODE_SEBANGUN
        ),
        InputFieldSpec("t1", "Segitiga 1 (3 nilai dipisah koma) atau nilai a", "3, 4, 5", FieldType.TEXT),
        InputFieldSpec("t2", "Segitiga 2 (3 nilai dipisah koma) atau nilai b", "6, 8, 10", FieldType.TEXT),
        InputFieldSpec("c", "Nilai c (khusus perbandingan senilai a/b = c/x)", "", required = false)
    )

    override fun solve(input: Map<String, String>): SolveResult {
        val mode = input["mode"]?.trim().orEmpty().ifEmpty { MODE_SEBANGUN }
        return when (mode) {
            MODE_SEBANGUN -> solveSimilarSss(input)
            MODE_SENILAI -> solveProportion(input)
            MODE_KONGRUEN_SSS -> solveCongruentSss(input)
            MODE_KONGRUEN_SAS -> solveCongruentSas(input)
            MODE_KONGRUEN_ASA -> solveCongruentAsa(input)
            else -> SolveResult.fail("Sub-fitur tidak dikenal: $mode")
        }
    }

    private fun parseTriple(raw: String?, label: String): Pair<List<ExactFraction>?, String?> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null to "Kolom \"$label\" tidak boleh kosong."
        val parts = text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != 3) {
            return null to "\"$label\" harus berisi tepat 3 angka yang dipisah koma (misal 3, 4, 5)."
        }
        val nums = ArrayList<ExactFraction>(3)
        for (p in parts) {
            val v = ExactFraction.parse(p) ?: return null to "Nilai \"$p\" pada \"$label\" tidak valid."
            if (v.signum <= 0) return null to "Semua ukuran pada \"$label\" harus lebih besar dari 0."
            nums.add(v)
        }
        return nums to null
    }

    private fun checkTriangleInequality(sides: List<ExactFraction>, label: String): String? {
        val s = sides.sorted()
        if (s[0] + s[1] <= s[2]) {
            return "Sisi ${sides.joinToString(", ")} pada $label tidak memenuhi ketaksamaan segitiga (${s[0]} + ${s[1]} ≤ ${s[2]})."
        }
        return null
    }

    private fun solveSimilarSss(input: Map<String, String>): SolveResult {
        val (raw1, e1) = parseTriple(input["t1"], "Segitiga 1")
        if (e1 != null) return SolveResult.fail(e1)
        val (raw2, e2) = parseTriple(input["t2"], "Segitiga 2")
        if (e2 != null) return SolveResult.fail(e2)

        checkTriangleInequality(raw1!!, "Segitiga 1")?.let { return SolveResult.fail(it) }
        checkTriangleInequality(raw2!!, "Segitiga 2")?.let { return SolveResult.fail(it) }

        val s1 = raw1.sorted()
        val s2 = raw2.sorted()
        val r0 = s2[0] / s1[0]
        val r1 = s2[1] / s1[1]
        val r2 = s2[2] / s1[2]

        val steps = listOf(
            "Urutkan sisi Segitiga 1 dari terpendek: ${s1.joinToString(", ")}",
            "Urutkan sisi Segitiga 2 dari terpendek: ${s2.joinToString(", ")}",
            "Bandingkan rasio sisi bersesuaian:",
            "  ${s2[0]}/${s1[0]} = ${r0.toFracString()}",
            "  ${s2[1]}/${s1[1]} = ${r1.toFracString()}",
            "  ${s2[2]}/${s1[2]} = ${r2.toFracString()}"
        )

        val similar = r0 == r1 && r1 == r2
        return if (similar) {
            SolveResult(
                ok = true,
                steps = steps,
                answer = "Sebangun, faktor skala = ${r0.toFracString()}",
                check = "Cek perkalian skala: ${s1[0]}×${r0}=${s1[0] * r0}, ${s1[1]}×${r0}=${s1[1] * r0}, ${s1[2]}×${r0}=${s1[2] * r0} (sesuai ${s2.joinToString(", ")})."
            )
        } else {
            SolveResult(
                ok = true,
                steps = steps,
                answer = "Tidak sebangun (perbandingan sisi tidak sama)",
                check = "Rasio ${r0}, ${r1}, ${r2} tidak bernilai sama seluruhnya."
            )
        }
    }

    private fun solveProportion(input: Map<String, String>): SolveResult {
        val (a, e1) = InputChecks.requireFraction(input, "t1", "Nilai a", allowZero = false)
        if (e1 != null) return SolveResult.fail(e1)
        val (b, e2) = InputChecks.requireFraction(input, "t2", "Nilai b", allowZero = false)
        if (e2 != null) return SolveResult.fail(e2)
        val (c, e3) = InputChecks.requireFraction(input, "c", "Nilai c", allowZero = false)
        if (e3 != null) return SolveResult.fail(e3)
        a!!; b!!; c!!

        val x = (b * c) / a
        val steps = listOf(
            "Perbandingan senilai: ${a.toFracString()} / ${b.toFracString()} = ${c.toFracString()} / x",
            "Kali silang: ${a.toFracString()} · x = ${b.toFracString()} · ${c.toFracString()}",
            "(${a.toFracString()})x = ${(b * c).toFracString()}",
            "x = ${(b * c).toFracString()} / (${a.toFracString()}) = ${x.toFracString()}"
        )
        return SolveResult(
            ok = true,
            steps = steps,
            answer = "x = ${x.toFracString()}",
            check = "Cek rasio: ${a}/${b} = ${(a / b).toFracString()} dan ${c}/${x} = ${(c / x).toFracString()} (sama)."
        )
    }

    private fun solveCongruentSss(input: Map<String, String>): SolveResult {
        val (raw1, e1) = parseTriple(input["t1"], "Segitiga 1 (sisi1, sisi2, sisi3)")
        if (e1 != null) return SolveResult.fail(e1)
        val (raw2, e2) = parseTriple(input["t2"], "Segitiga 2 (sisi1, sisi2, sisi3)")
        if (e2 != null) return SolveResult.fail(e2)

        checkTriangleInequality(raw1!!, "Segitiga 1")?.let { return SolveResult.fail(it) }
        checkTriangleInequality(raw2!!, "Segitiga 2")?.let { return SolveResult.fail(it) }

        val s1 = raw1.sorted()
        val s2 = raw2.sorted()
        val congruent = s1 == s2
        val steps = listOf(
            "Kriteria SSS (Sisi-Sisi-Sisi): ketiga sisi bersesuaian harus sama panjang.",
            "Sisi terurut Segitiga 1: ${s1.joinToString(", ")}",
            "Sisi terurut Segitiga 2: ${s2.joinToString(", ")}"
        )
        return SolveResult(
            ok = true,
            steps = steps,
            answer = if (congruent) "Kongruen (memenuhi kriteria SSS)" else "Tidak kongruen (panjang sisi tidak sama)",
            check = if (congruent) "Setiap pasangan sisi berurutan bernilai identik (${s1.joinToString(" = ")})." else "Terdapat sisi yang tidak sama panjang."
        )
    }

    private fun solveCongruentSas(input: Map<String, String>): SolveResult {
        val (t1, e1) = parseTriple(input["t1"], "Segitiga 1 (sisi1, sudut_apit, sisi2)")
        if (e1 != null) return SolveResult.fail(e1)
        val (t2, e2) = parseTriple(input["t2"], "Segitiga 2 (sisi1, sudut_apit, sisi2)")
        if (e2 != null) return SolveResult.fail(e2)
        t1!!; t2!!

        val deg180 = ExactFraction.of(180)
        if (t1[1] >= deg180 || t2[1] >= deg180) {
            return SolveResult.fail("Sudut apit segitiga harus berada di antara 0° dan 180°.")
        }
        val pair1 = listOf(t1[0], t1[2]).sorted()
        val pair2 = listOf(t2[0], t2[2]).sorted()
        val congruent = t1[1] == t2[1] && pair1 == pair2
        val steps = listOf(
            "Kriteria SAS (Sisi-Sudut-Sisi): dua sisi pengapit dan satu sudut apit sama besar.",
            "Segitiga 1: sisi ${t1[0]}, ${t1[2]} mengapit sudut ${t1[1]}°",
            "Segitiga 2: sisi ${t2[0]}, ${t2[2]} mengapit sudut ${t2[1]}°"
        )
        return SolveResult(
            ok = true,
            steps = steps,
            answer = if (congruent) "Kongruen (memenuhi kriteria SAS)" else "Tidak kongruen berdasarkan SAS",
            check = "Sudut apit ${t1[1]}° vs ${t2[1]}° dan pasangan sisi ${pair1.joinToString(",")} vs ${pair2.joinToString(",")}."
        )
    }

    private fun solveCongruentAsa(input: Map<String, String>): SolveResult {
        val (t1, e1) = parseTriple(input["t1"], "Segitiga 1 (sudut1, sisi_apit, sudut2)")
        if (e1 != null) return SolveResult.fail(e1)
        val (t2, e2) = parseTriple(input["t2"], "Segitiga 2 (sudut1, sisi_apit, sudut2)")
        if (e2 != null) return SolveResult.fail(e2)
        t1!!; t2!!

        val deg180 = ExactFraction.of(180)
        if (t1[0] + t1[2] >= deg180 || t2[0] + t2[2] >= deg180) {
            return SolveResult.fail("Jumlah dua sudut dalam segitiga harus kurang dari 180°.")
        }
        val ang1 = listOf(t1[0], t1[2]).sorted()
        val ang2 = listOf(t2[0], t2[2]).sorted()
        val congruent = t1[1] == t2[1] && ang1 == ang2
        val steps = listOf(
            "Kriteria ASA (Sudut-Sisi-Sudut): dua sudut dan sisi di antaranya sama besar.",
            "Segitiga 1: sudut ${t1[0]}° dan ${t1[2]}° mengapit sisi ${t1[1]}",
            "Segitiga 2: sudut ${t2[0]}° dan ${t2[2]}° mengapit sisi ${t2[1]}"
        )
        return SolveResult(
            ok = true,
            steps = steps,
            answer = if (congruent) "Kongruen (memenuhi kriteria ASA)" else "Tidak kongruen berdasarkan ASA",
            check = "Sisi apit ${t1[1]} vs ${t2[1]} dan pasangan sudut ${ang1.joinToString(",")}° vs ${ang2.joinToString(",")}°."
        )
    }

    companion object {
        const val MODE_SEBANGUN = "Cek sebangun (SSS)"
        const val MODE_SENILAI = "Perbandingan senilai"
        const val MODE_KONGRUEN_SSS = "Cek kongruen (SSS)"
        const val MODE_KONGRUEN_SAS = "Cek kongruen (SAS)"
        const val MODE_KONGRUEN_ASA = "Cek kongruen (ASA)"
    }
}
