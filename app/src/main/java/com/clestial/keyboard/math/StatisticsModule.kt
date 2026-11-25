package com.clestial.keyboard.math

/**
 * Modul 8 (Statistika): Mengolah daftar data tunggal atau pasangan nilai:frekuensi.
 * Menghitung mean (pecahan eksak & campuran), median, modus, jangkauan,
 * kuartil Q1, Q2, Q3 (metode median dua belahan), dan jangkauan interkuartil (IQR).
 */
class StatisticsModule : MathModule {

    override val id = "statistika"
    override val name = "Statistika"
    override val group = MathGroup.STATISTIKA_PELUANG
    override val description = "Mean eksak, median, modus, jangkauan, Q₁, Q₂, Q₃, dan jangkauan interkuartil."

    override val fields = listOf(
        InputFieldSpec(
            key = "data",
            label = "Daftar data (pisahkan koma/spasi) atau nilai:frekuensi",
            hint = "2, 3, 3, 4, 5, 6, 8",
            type = FieldType.TEXT
        )
    )

    override fun solve(input: Map<String, String>): SolveResult {
        val raw = input["data"]?.trim().orEmpty()
        if (raw.isEmpty()) return SolveResult.fail("Masukkan daftar data terlebih dahulu (misal 2, 3, 3, 4, 5, 6, 8).")

        val values = parseDataList(raw)
            ?: return SolveResult.fail("Format data tidak valid. Gunakan angka dipisah koma/spasi atau pasangan nilai:frekuensi.")
        if (values.isEmpty()) return SolveResult.fail("Daftar data kosong.")

        val sorted = values.sorted()
        val n = sorted.size
        val sum = sorted.fold(ExactFraction.ZERO) { acc, v -> acc + v }
        val mean = sum / ExactFraction.of(n.toLong())

        val q2 = medianOf(sorted)
        val q1: ExactFraction?
        val q3: ExactFraction?
        val lower: List<ExactFraction>
        val upper: List<ExactFraction>
        if (n >= 2) {
            val mid = n / 2
            lower = sorted.subList(0, mid)
            upper = if (n % 2 == 1) sorted.subList(mid + 1, n) else sorted.subList(mid, n)
            q1 = medianOf(lower)
            q3 = medianOf(upper)
        } else {
            lower = emptyList()
            upper = emptyList()
            q1 = sorted[0]
            q3 = sorted[0]
        }

        val iqr = q3 - q1
        val range = sorted.last() - sorted.first()

        // Modus
        val freqMap = LinkedHashMap<ExactFraction, Int>()
        for (v in sorted) freqMap[v] = (freqMap[v] ?: 0) + 1
        val maxFreq = freqMap.values.maxOrNull() ?: 0
        val modes = if (maxFreq <= 1 || freqMap.values.distinct().size == 1 && freqMap.size > 1) {
            emptyList()
        } else {
            freqMap.filterValues { it == maxFreq }.keys.toList()
        }
        val modeText = if (modes.isEmpty()) "Tidak ada" else modes.joinToString(", ") { it.toFracString() }

        val steps = ArrayList<String>()
        steps.add("1. Urutkan data ($n data): ${sorted.joinToString(", ") { it.toFracString() }}")
        steps.add("2. Jumlah data = ${sorted.joinToString(" + ") { it.toFracString() }} = ${sum.toFracString()}")
        steps.add("3. Mean = ${sum.toFracString()} / $n = ${mean.toExactDisplay()}")
        steps.add("4. Median (Q₂): ${if (n % 2 == 1) "data ke-${n / 2 + 1}" else "rata-rata data ke-${n / 2} dan ke-${n / 2 + 1}"} = ${q2.toFracString()}")
        steps.add("5. Modus (frekuensi terbanyak = $maxFreq): $modeText")
        steps.add("6. Jangkauan = maks - min = ${sorted.last().toFracString()} - ${sorted.first().toFracString()} = ${range.toFracString()}")
        if (n >= 2) {
            steps.add("7. Kuartil (belahan bawah [${lower.joinToString(", ") { it.toFracString() }}] dan atas [${upper.joinToString(", ") { it.toFracString() }}]):")
            steps.add("   Q₁ = ${q1.toFracString()}, Q₂ = ${q2.toFracString()}, Q₃ = ${q3.toFracString()}")
            steps.add("8. Jangkauan interkuartil = Q₃ - Q₁ = ${q3.toFracString()} - ${q1.toFracString()} = ${iqr.toFracString()}")
        }

        val checkSum = mean * ExactFraction.of(n.toLong())
        return SolveResult(
            ok = true,
            steps = steps,
            answer = "Mean = ${mean.toExactDisplay()}, Median = ${q2.toFracString()}, Modus = $modeText, Jangkauan = ${range.toFracString()}, Q₁ = ${q1.toFracString()}, Q₂ = ${q2.toFracString()}, Q₃ = ${q3.toFracString()}, Jangkauan interkuartil = ${iqr.toFracString()}",
            check = "Cek mean: n × mean = $n × (${mean.toFracString()}) = ${checkSum.toFracString()} (sesuai jumlah data ${sum.toFracString()}); Q₁ ≤ Q₂ ≤ Q₃ terpenuhi."
        )
    }

    private fun medianOf(sorted: List<ExactFraction>): ExactFraction {
        val n = sorted.size
        if (n == 0) return ExactFraction.ZERO
        return if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            (sorted[n / 2 - 1] + sorted[n / 2]) / ExactFraction.of(2)
        }
    }

    private fun parseDataList(raw: String): List<ExactFraction>? {
        val tokens = if (raw.contains(';')) {
            raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        } else if (raw.contains(',') && !raw.contains(' ')) {
            raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            raw.split(Regex("[,\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
        }
        if (tokens.isEmpty()) return null

        val out = ArrayList<ExactFraction>()
        for (tok in tokens) {
            if (tok.contains(':')) {
                val kv = tok.split(':')
                if (kv.size != 2) return null
                val v = ExactFraction.parse(kv[0]) ?: return null
                val f = kv[1].trim().toIntOrNull() ?: return null
                if (f <= 0 || f > 10_000) return null
                repeat(f) { out.add(v) }
            } else {
                val v = ExactFraction.parse(tok) ?: return null
                out.add(v)
            }
        }
        return out
    }
}
