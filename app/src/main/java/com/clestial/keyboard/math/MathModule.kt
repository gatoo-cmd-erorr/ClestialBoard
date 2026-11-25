package com.clestial.keyboard.math

/** Kelompok materi matematika SMP kelas 9. */
enum class MathGroup(val title: String) {
    ALJABAR("A. Aljabar"),
    GEOMETRI("B. Geometri"),
    STATISTIKA_PELUANG("C. Statistika dan Peluang")
}

/** Tipe kolom input untuk formulir modul dinamis. */
enum class FieldType {
    NUMBER,
    INTEGER,
    CHOICE,
    TEXT
}

/** Definisi satu kolom input pada modul matematika. */
data class InputFieldSpec(
    val key: String,
    val label: String,
    val hint: String = "",
    val type: FieldType = FieldType.NUMBER,
    val required: Boolean = true,
    val choices: List<String> = emptyList(),
    val defaultValue: String = ""
)

/** Spesifikasi plot garis lurus ax + by + c = 0 beserta titik potong sumbu untuk UI & SVG. */
data class LinePlotSpec(
    val a: Double,
    val b: Double,
    val c: Double,
    val xInterceptLabel: String? = null,
    val yInterceptLabel: String? = null,
    val equationLabel: String = "",
    val xIntVal: Double? = null,
    val yIntVal: Double? = null,
    val pointX: Double? = null,
    val pointY: Double? = null,
    val pointLabel: String? = null
)

/** Hasil standar setiap modul: ok, langkah[], jawaban, cek, error, serta sketsa garis opsional. */
data class SolveResult(
    val ok: Boolean,
    val steps: List<String> = emptyList(),
    val answer: String = "",
    val check: String = "",
    val error: String = "",
    val svgSketch: String = "",
    val plotSpec: LinePlotSpec? = null
) {
    companion object {
        fun fail(message: String): SolveResult = SolveResult(ok = false, error = message)
    }
}

/**
 * Kontrak seragam setiap materi kalkulator matematika SMP kelas 9.
 * Menambah materi baru cukup membuat satu kelas pengimplementasi interface ini
 * lalu mendaftarkannya di [MathRegistry].
 */
interface MathModule {
    val id: String
    val name: String
    val group: MathGroup
    val description: String
    val fields: List<InputFieldSpec>
    fun solve(input: Map<String, String>): SolveResult
}

/** Pembantu validasi input bersama antar modul. */
internal object InputChecks {

    fun requireFraction(
        input: Map<String, String>,
        key: String,
        label: String,
        allowZero: Boolean = true
    ): Pair<ExactFraction?, String?> {
        val raw = input[key]?.trim().orEmpty()
        if (raw.isEmpty()) return null to "Kolom \"$label\" tidak boleh kosong."
        val parsed = ExactFraction.parse(raw)
            ?: return null to "Format \"$label\" tidak valid (\"$raw\"). Gunakan angka, pecahan (a/b), atau desimal (misal 2,5)."
        if (!allowZero && parsed.isZero) {
            return null to "\"$label\" tidak boleh bernilai 0."
        }
        return parsed to null
    }

    fun optionalFraction(
        input: Map<String, String>,
        key: String,
        label: String
    ): Pair<ExactFraction?, String?> {
        val raw = input[key]?.trim().orEmpty()
        if (raw.isEmpty()) return null to null
        val parsed = ExactFraction.parse(raw)
            ?: return null to "Format \"$label\" tidak valid (\"$raw\")."
        return parsed to null
    }

    fun requireInt(
        input: Map<String, String>,
        key: String,
        label: String,
        min: Int? = null,
        max: Int? = null
    ): Pair<Int?, String?> {
        val raw = input[key]?.trim().orEmpty()
        if (raw.isEmpty()) return null to "Kolom \"$label\" tidak boleh kosong."
        val parsed = raw.toIntOrNull()
            ?: return null to "\"$label\" harus berupa bilangan bulat."
        if (min != null && parsed < min) return null to "\"$label\" minimal harus $min."
        if (max != null && parsed > max) return null to "\"$label\" maksimal harus $max."
        return parsed to null
    }
}
