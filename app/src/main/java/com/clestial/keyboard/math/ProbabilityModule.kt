package com.clestial.keyboard.math

/**
 * Modul 9 (Peluang):
 * - Peluang kejadian P(A) = n(A)/n(S) dan komplemen P(A') = 1 - P(A)
 * - Ruang sampel 1–2 dadu dan 1–3 koin dengan kejadian pilihan
 * - Frekuensi harapan Fh = P(A) × banyak percobaan
 */
class ProbabilityModule : MathModule {

    override val id = "peluang"
    override val name = "Peluang & Frekuensi Harapan"
    override val group = MathGroup.STATISTIKA_PELUANG
    override val description = "Hitung P(A), komplemen, ruang sampel dadu/koin, dan frekuensi harapan."

    override val fields = listOf(
        InputFieldSpec(
            key = "mode",
            label = "Sub-fitur",
            type = FieldType.CHOICE,
            choices = listOf(MODE_DASAR, MODE_DADU_1, MODE_DADU_2, MODE_KOIN, MODE_FH),
            defaultValue = MODE_DASAR
        ),
        InputFieldSpec("a", "n(A) / P(A) / jumlah koin (1-3)", "1", required = false),
        InputFieldSpec("b", "n(S) / banyak percobaan N / target angka k", "6", required = false),
        InputFieldSpec(
            key = "kejadian",
            label = "Jenis kejadian (untuk dadu/koin)",
            type = FieldType.CHOICE,
            required = false,
            choices = listOf(EV_GENAP, EV_GANJIL, EV_PRIMA, EV_JUMLAH_EQ, EV_JUMLAH_GE, EV_JUMLAH_LE, EV_KEMBAR, EV_TEPAT_ANGKA, EV_MIN_ANGKA),
            defaultValue = EV_GENAP
        ),
        InputFieldSpec("percobaan", "Banyak percobaan N (opsional untuk Fh)", "", required = false)
    )

    override fun solve(input: Map<String, String>): SolveResult {
        val mode = input["mode"]?.trim().orEmpty().ifEmpty { MODE_DASAR }
        return when (mode) {
            MODE_DASAR -> solveBasic(input)
            MODE_DADU_1 -> solveDice(1, input)
            MODE_DADU_2 -> solveDice(2, input)
            MODE_KOIN -> solveCoins(input)
            MODE_FH -> solveExpectedFrequency(input)
            else -> SolveResult.fail("Sub-fitur peluang tidak dikenal: $mode")
        }
    }

    private fun solveBasic(input: Map<String, String>): SolveResult {
        val (na, ea) = InputChecks.requireInt(input, "a", "n(A)", min = 0)
        if (ea != null) return SolveResult.fail(ea)
        val (ns, es) = InputChecks.requireInt(input, "b", "n(S)", min = 1)
        if (es != null) return SolveResult.fail(es)
        na!!; ns!!
        if (na > ns) return SolveResult.fail("n(A) = $na tidak boleh lebih besar dari n(S) = $ns.")

        val p = ExactFraction.of(na.toLong(), ns.toLong())
        val comp = ExactFraction.ONE - p
        val steps = ArrayList<String>()
        steps.add("Rumus peluang: P(A) = n(A) / n(S)")
        steps.add("P(A) = $na / $ns = ${p.toFracString()}")
        steps.add("Peluang komplemen: P(A') = 1 - P(A) = ${comp.toFracString()}")

        val nTrialsOpt = input["percobaan"]?.trim().orEmpty()
        var extra = ""
        if (nTrialsOpt.isNotEmpty()) {
            val nTrials = nTrialsOpt.toIntOrNull()
                ?: return SolveResult.fail("Banyak percobaan harus bilangan bulat.")
            if (nTrials < 1) return SolveResult.fail("Banyak percobaan minimal 1.")
            val fh = p * ExactFraction.of(nTrials.toLong())
            steps.add("Frekuensi harapan: Fh = P(A) × N = ${p.toFracString()} × $nTrials = ${fh.toFracString()}")
            extra = ", Fh = ${fh.toFracString()}"
        }

        return SolveResult(
            ok = true,
            steps = steps,
            answer = "P(A) = ${p.toFracString()}, P(A') = ${comp.toFracString()}$extra",
            check = "Cek komplemen: P(A) + P(A') = ${p.toFracString()} + ${comp.toFracString()} = ${(p + comp).toFracString()}."
        )
    }

    private fun solveDice(count: Int, input: Map<String, String>): SolveResult {
        val ev = input["kejadian"]?.trim().orEmpty().ifEmpty { if (count == 1) EV_GENAP else EV_JUMLAH_EQ }
        val steps = ArrayList<String>()

        if (count == 1) {
            val space = (1..6).toList()
            val kTarget = input["b"]?.trim()?.toIntOrNull()
            val matched = when (ev) {
                EV_GENAP -> space.filter { it % 2 == 0 }
                EV_GANJIL -> space.filter { it % 2 != 0 }
                EV_PRIMA -> space.filter { it in setOf(2, 3, 5) }
                EV_JUMLAH_EQ -> {
                    val k = kTarget ?: return SolveResult.fail("Masukkan nilai k pada kolom target.")
                    space.filter { it == k }
                }
                EV_JUMLAH_GE -> {
                    val k = kTarget ?: return SolveResult.fail("Masukkan nilai k pada kolom target.")
                    space.filter { it >= k }
                }
                EV_JUMLAH_LE -> {
                    val k = kTarget ?: return SolveResult.fail("Masukkan nilai k pada kolom target.")
                    space.filter { it <= k }
                }
                else -> return SolveResult.fail("Kejadian \"$ev\" tidak berlaku untuk 1 dadu.")
            }
            val p = ExactFraction.of(matched.size.toLong(), 6L)
            val comp = ExactFraction.ONE - p
            steps.add("Ruang sampel 1 dadu S = {1, 2, 3, 4, 5, 6}, n(S) = 6")
            steps.add("Kejadian \"$ev\" = {${matched.joinToString(", ")}}, n(A) = ${matched.size}")
            steps.add("P(A) = ${matched.size}/6 = ${p.toFracString()}, P(A') = ${comp.toFracString()}")
            return SolveResult(
                ok = true,
                steps = steps,
                answer = "P(A) = ${p.toFracString()}, P(A') = ${comp.toFracString()}",
                check = "P(A) + P(A') = ${(p + comp).toFracString()}; n(A) = ${matched.size} dari 6 hasil."
            )
        }

        // 2 dadu
        val space = ArrayList<Pair<Int, Int>>(36)
        for (d1 in 1..6) for (d2 in 1..6) space.add(d1 to d2)
        val kTarget = input["b"]?.trim()?.toIntOrNull()

        val matched = when (ev) {
            EV_JUMLAH_EQ -> {
                val k = kTarget ?: return SolveResult.fail("Masukkan jumlah mata dadu target k (2 sampai 12).")
                space.filter { it.first + it.second == k }
            }
            EV_JUMLAH_GE -> {
                val k = kTarget ?: return SolveResult.fail("Masukkan nilai minimal k.")
                space.filter { it.first + it.second >= k }
            }
            EV_JUMLAH_LE -> {
                val k = kTarget ?: return SolveResult.fail("Masukkan nilai maksimal k.")
                space.filter { it.first + it.second <= k }
            }
            EV_KEMBAR -> space.filter { it.first == it.second }
            EV_GENAP -> space.filter { (it.first + it.second) % 2 == 0 }
            EV_GANJIL -> space.filter { (it.first + it.second) % 2 != 0 }
            else -> return SolveResult.fail("Kejadian \"$ev\" tidak berlaku untuk 2 dadu.")
        }

        val p = ExactFraction.of(matched.size.toLong(), 36L)
        val comp = ExactFraction.ONE - p
        steps.add("Ruang sampel 2 dadu: n(S) = 6 × 6 = 36 pasangan (1,1)..(6,6)")
        steps.add("Kejadian yang memenuhi (${matched.size}): ${matched.joinToString(", ") { "(${it.first},${it.second})" }}")
        steps.add("P(A) = ${matched.size}/36 = ${p.toFracString()}, P(A') = ${comp.toFracString()}")

        return SolveResult(
            ok = true,
            steps = steps,
            answer = "P(A) = ${matched.size}/36 = ${p.toFracString()}, P(A') = ${comp.toFracString()}",
            check = "n(A) + n(A') = ${matched.size} + ${36 - matched.size} = 36 = n(S)."
        )
    }

    private fun solveCoins(input: Map<String, String>): SolveResult {
        val (coins, ec) = InputChecks.requireInt(input, "a", "Jumlah koin", min = 1, max = 3)
        if (ec != null) return SolveResult.fail(ec)
        coins!!

        val ev = input["kejadian"]?.trim().orEmpty().ifEmpty { EV_TEPAT_ANGKA }
        val kTarget = input["b"]?.trim()?.toIntOrNull()

        val space = ArrayList<String>()
        val total = 1 shl coins
        for (mask in 0 until total) {
            val s = buildString {
                for (bit in coins - 1 downTo 0) {
                    append(if (((mask shr bit) and 1) == 0) 'A' else 'G')
                }
            }
            space.add(s)
        }

        val matched = when (ev) {
            EV_TEPAT_ANGKA -> {
                val k = kTarget ?: return SolveResult.fail("Masukkan jumlah Angka (k) yang diinginkan (0..$coins).")
                if (k !in 0..coins) return SolveResult.fail("Jumlah Angka harus antara 0 dan $coins.")
                space.filter { outcome -> outcome.count { it == 'A' } == k }
            }
            EV_MIN_ANGKA -> {
                val k = kTarget ?: return SolveResult.fail("Masukkan minimal jumlah Angka (k).")
                if (k !in 0..coins) return SolveResult.fail("Jumlah Angka harus antara 0 dan $coins.")
                space.filter { outcome -> outcome.count { it == 'A' } >= k }
            }
            EV_SEMUA_ANGKA -> space.filter { outcome -> outcome.all { it == 'A' } }
            EV_SEMUA_GAMBAR -> space.filter { outcome -> outcome.all { it == 'G' } }
            else -> return SolveResult.fail("Kejadian \"$ev\" tidak berlaku untuk koin.")
        }

        val p = ExactFraction.of(matched.size.toLong(), total.toLong())
        val comp = ExactFraction.ONE - p
        val steps = listOf(
            "Ruang sampel $coins koin (A = Angka, G = Gambar): {${space.joinToString(", ")}}, n(S) = 2${ExactMath.toSuperscript(coins)} = $total",
            "Kejadian memenuhi: {${matched.joinToString(", ")}}, n(A) = ${matched.size}",
            "P(A) = ${matched.size}/$total = ${p.toFracString()}, P(A') = ${comp.toFracString()}"
        )
        return SolveResult(
            ok = true,
            steps = steps,
            answer = "P(A) = ${p.toFracString()}, P(A') = ${comp.toFracString()}",
            check = "n(A) = ${matched.size} dari $total ruang sampel; P(A) + P(A') = ${(p + comp).toFracString()}."
        )
    }

    private fun solveExpectedFrequency(input: Map<String, String>): SolveResult {
        val (p, ep) = InputChecks.requireFraction(input, "a", "Peluang P(A)")
        if (ep != null) return SolveResult.fail(ep)
        if (p!!.signum < 0 || p > ExactFraction.ONE) {
            return SolveResult.fail("Peluang P(A) harus bernilai antara 0 dan 1.")
        }
        val (nTrials, en) = InputChecks.requireInt(input, "b", "Banyak percobaan (N)", min = 1)
        if (en != null) return SolveResult.fail(en)
        nTrials!!

        val fh = p * ExactFraction.of(nTrials.toLong())
        val steps = listOf(
            "Rumus frekuensi harapan: Fh = P(A) × banyak percobaan",
            "Fh = (${p.toFracString()}) × $nTrials = ${fh.toFracString()}"
        )
        return SolveResult(
            ok = true,
            steps = steps,
            answer = "Frekuensi harapan = ${fh.toExactDisplay()}",
            check = "Cek balik: Fh / N = (${fh.toFracString()}) / $nTrials = ${(fh / ExactFraction.of(nTrials.toLong())).toFracString()} (sama dengan P(A) = ${p.toFracString()})."
        )
    }

    companion object {
        const val MODE_DASAR = "Peluang P(A) = n(A)/n(S)"
        const val MODE_DADU_1 = "1 Dadu"
        const val MODE_DADU_2 = "2 Dadu"
        const val MODE_KOIN = "Koin (1-3)"
        const val MODE_FH = "Frekuensi harapan"

        const val EV_GENAP = "Mata genap"
        const val EV_GANJIL = "Mata ganjil"
        const val EV_PRIMA = "Mata prima"
        const val EV_JUMLAH_EQ = "Jumlah = k"
        const val EV_JUMLAH_GE = "Jumlah >= k"
        const val EV_JUMLAH_LE = "Jumlah <= k"
        const val EV_KEMBAR = "Mata kembar"
        const val EV_TEPAT_ANGKA = "Tepat k Angka"
        const val EV_MIN_ANGKA = "Minimal k Angka"
        const val EV_SEMUA_ANGKA = "Semua Angka"
        const val EV_SEMUA_GAMBAR = "Semua Gambar"
    }
}
