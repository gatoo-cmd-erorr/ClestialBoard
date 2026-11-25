package com.clestial.keyboard.math

/**
 * Registry terpusat seluruh modul matematika SMP kelas 9.
 * Untuk menambah materi baru: cukup buat satu file kelas pengimplementasi [MathModule]
 * dan tambahkan ke daftar [modules] di bawah tanpa mengubah kode UI/AI lain.
 */
object MathRegistry {

    val modules: List<MathModule> = listOf(
        SpldvModule(),
        PowerRootModule(),
        QuadraticEqModule(),
        QuadraticFuncModule(),
        LinearLineModule(),
        PlsvModule(),
        TransformModule(),
        SimilarityModule(),
        CurvedSolidModule(),
        StatisticsModule(),
        ProbabilityModule()
    )

    private val byIdMap: Map<String, MathModule> = modules.associateBy { it.id }

    fun find(id: String): MathModule? = byIdMap[id.trim()]

    fun byGroup(): Map<MathGroup, List<MathModule>> =
        MathGroup.entries.associateWith { grp -> modules.filter { it.group == grp } }
}
