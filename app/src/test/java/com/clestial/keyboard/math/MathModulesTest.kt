package com.clestial.keyboard.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class MathModulesTest {

    // ---- Presisi & ExactFraction ----

    @Test
    fun exactFraction_normalizesNegativeDenomAndDecimals() {
        val f1 = ExactFraction.parse("-7/-3")
        assertNotNull(f1)
        assertEquals("7/3", f1!!.toFracString())

        val f2 = ExactFraction.parse("7/-3")!!
        assertEquals("-7/3", f2.toFracString())

        val dec = ExactFraction.parse("2,5")!!
        assertEquals("5/2", dec.toFracString())

        val big = ExactFraction.parse("12345678901234567890")!!
        assertEquals(BigInteger("12345678901234567890"), big.num)
        assertEquals(BigInteger.ONE, big.den)

        assertNull(ExactFraction.parse(""))
        assertNull(ExactFraction.parse("abc"))
        assertNull(ExactFraction.parse("1/0"))
    }

    // ---- 1. SPLDV ----

    @Test
    fun spldv_allRequiredCases() {
        val m = SpldvModule()

        // Normal integer solution: 2x + 3y = 12, 3x + 2y = 13 -> x=3, y=2
        val r1 = m.solve(mapOf("a1" to "2", "b1" to "3", "c1" to "12", "a2" to "3", "b2" to "2", "c2" to "13"))
        assertTrue(r1.ok)
        assertTrue(r1.answer.contains("x = 3") && r1.answer.contains("y = 2"))
        assertTrue(r1.check.isNotEmpty())

        // Exact fractional solution: 3x + 2y = 9, 3x - y = 6 -> x=7/3, y=1
        val r2 = m.solve(mapOf("a1" to "3", "b1" to "2", "c1" to "9", "a2" to "3", "b2" to "-1", "c2" to "6"))
        assertTrue(r2.ok)
        assertTrue(r2.answer.contains("7/3"))

        // Parallel / no solution
        val r3 = m.solve(mapOf("a1" to "2", "b1" to "4", "c1" to "5", "a2" to "1", "b2" to "2", "c2" to "3"))
        assertTrue(r3.ok)
        assertTrue(r3.answer.contains("tidak ada solusi", ignoreCase = true))

        // Coincident / infinite solutions
        val r4 = m.solve(mapOf("a1" to "2", "b1" to "4", "c1" to "6", "a2" to "1", "b2" to "2", "c2" to "3"))
        assertTrue(r4.ok)
        assertTrue(r4.answer.contains("tak hingga banyak", ignoreCase = true))

        // One b = 0: 2x + 0y = 8, x + y = 7 -> x=4, y=3
        val r5 = m.solve(mapOf("a1" to "2", "b1" to "0", "c1" to "8", "a2" to "1", "b2" to "1", "c2" to "7"))
        assertTrue(r5.ok)
        assertTrue(r5.answer.contains("x = 4") && r5.answer.contains("y = 3"))

        // Both a and b zero in one equation
        val r6 = m.solve(mapOf("a1" to "0", "b1" to "0", "c1" to "5", "a2" to "1", "b2" to "1", "c2" to "2"))
        assertFalse(r6.ok)
        assertTrue(r6.error.contains("nol"))
    }

    // ---- 2. Pangkat dan Akar ----

    @Test
    fun powerRoot_allRequiredCases() {
        val m = PowerRootModule()

        val p1 = m.solve(mapOf("mode" to PowerRootModule.MODE_PANGKAT, "a" to "2", "op" to "×", "m" to "3", "n" to "4"))
        assertTrue(p1.ok)
        assertTrue(p1.answer.contains("128"))

        val p2 = m.solve(mapOf("mode" to PowerRootModule.MODE_PANGKAT, "a" to "3", "op" to "-n", "m" to "2"))
        assertTrue(p2.ok)
        assertTrue(p2.answer.contains("1/9"))

        val s1 = m.solve(mapOf("mode" to PowerRootModule.MODE_ILMIAH, "a" to "0,00045"))
        assertTrue(s1.ok)
        assertTrue(s1.answer.contains("4,5") && s1.answer.contains("10⁻⁴"))

        val s2 = m.solve(mapOf("mode" to PowerRootModule.MODE_ILMIAH, "a" to "4,5 x 10^-4"))
        assertTrue(s2.ok)
        assertEquals("0,00045", s2.answer)

        val sq = m.solve(mapOf("mode" to PowerRootModule.MODE_AKAR, "n" to "72"))
        assertTrue(sq.ok)
        assertEquals("6√2", sq.answer)

        val rat1 = m.solve(mapOf("mode" to PowerRootModule.MODE_RASIONAL, "a" to "6", "op" to "tunggal", "m" to "3"))
        assertTrue(rat1.ok)
        assertEquals("2√3", rat1.answer)

        val rat2 = m.solve(mapOf("mode" to PowerRootModule.MODE_RASIONAL, "a" to "1", "op" to "+", "m" to "3", "n" to "2"))
        assertTrue(rat2.ok)
        assertEquals("(3 - √2)/7", rat2.answer)
    }

    // ---- 3. Persamaan Kuadrat ----

    @Test
    fun quadraticEq_allRequiredCases() {
        val m = QuadraticEqModule()

        // 2 rational roots + factorization: x^2 - 5x + 6 = 0
        val q1 = m.solve(mapOf("a" to "1", "b" to "-5", "c" to "6"))
        assertTrue(q1.ok)
        assertTrue(q1.answer.contains("3") && q1.answer.contains("2"))
        assertTrue(q1.steps.any { it.contains("Pemfaktoran", ignoreCase = true) })

        // Double root: x^2 - 6x + 9 = 0
        val q2 = m.solve(mapOf("a" to "1", "b" to "-6", "c" to "9"))
        assertTrue(q2.ok)
        assertTrue(q2.steps.any { it.contains("kembar", ignoreCase = true) })
        assertTrue(q2.answer.contains("3"))

        // Radical roots: x^2 - 6x + 4 = 0 -> 3 ± √5
        val q3 = m.solve(mapOf("a" to "1", "b" to "-6", "c" to "4"))
        assertTrue(q3.ok)
        assertTrue(q3.answer.contains("3 ± √5"))

        // D < 0 -> no real roots
        val q4 = m.solve(mapOf("a" to "1", "b" to "0", "c" to "4"))
        assertTrue(q4.ok)
        assertTrue(q4.answer.contains("tidak ada akar real", ignoreCase = true))

        // a = 0 rejected
        val q5 = m.solve(mapOf("a" to "0", "b" to "2", "c" to "1"))
        assertFalse(q5.ok)
    }

    // ---- 4. Fungsi Kuadrat ----

    @Test
    fun quadraticFunc_upwardAndDownward() {
        val m = QuadraticFuncModule()

        // y = x^2 - 4x + 3 -> opens upward, vertex (2, -1), min = -1, x-intercepts (1,0) & (3,0)
        val f1 = m.solve(mapOf("a" to "1", "b" to "-4", "c" to "3", "x_eval" to "5"))
        assertTrue(f1.ok)
        assertTrue(f1.steps.any { it.contains("terbuka ke atas") })
        assertTrue(f1.answer.contains("(2, -1)") && f1.answer.contains("minimum = -1"))
        assertTrue(f1.steps.any { it.contains("f(5) = 8") })

        // Downward parabola: y = -2x^2 + 4x + 6
        val f2 = m.solve(mapOf("a" to "-2", "b" to "4", "c" to "6"))
        assertTrue(f2.ok)
        assertTrue(f2.steps.any { it.contains("terbuka ke bawah") })
        assertTrue(f2.answer.contains("maksimum = 8"))
    }

    // ---- 5. Transformasi Geometri ----

    @Test
    fun transform_allModesAndCenter() {
        val m = TransformModule()

        val t = m.solve(mapOf("titik" to "(3,4)", "jenis" to TransformModule.KIND_TRANSLASI, "param1" to "2", "param2" to "-1"))
        assertTrue(t.ok)
        assertTrue(t.answer.contains("(5, 3)"))

        val rx = m.solve(mapOf("titik" to "(3,4)", "jenis" to TransformModule.KIND_REFLEKSI, "param1" to "sumbu-x"))
        assertTrue(rx.ok)
        assertTrue(rx.answer.contains("(3, -4)"))

        val rg = m.solve(mapOf("titik" to "(3,4)", "jenis" to TransformModule.KIND_REFLEKSI, "param1" to "x = 2"))
        assertTrue(rg.ok)
        assertTrue(rg.answer.contains("(1, 4)"))

        val rot = m.solve(mapOf("titik" to "(2,3)", "jenis" to TransformModule.KIND_ROTASI, "param1" to "90", "param2" to "searah"))
        assertTrue(rot.ok)
        assertTrue(rot.answer.contains("(3, -2)"))

        val dil = m.solve(mapOf("titik" to "(3,2)", "jenis" to TransformModule.KIND_DILATASI, "param1" to "2", "pusat" to "(1,1)"))
        assertTrue(dil.ok)
        assertTrue(dil.answer.contains("(5, 3)"))

        val multi = m.solve(mapOf("titik" to "(1,2); (3,4)", "jenis" to TransformModule.KIND_REFLEKSI, "param1" to "y = x"))
        assertTrue(multi.ok)
        assertTrue(multi.answer.contains("(2, 1)") && multi.answer.contains("(4, 3)"))
    }

    // ---- 6. Kekongruenan & Kesebangunan ----

    @Test
    fun similarity_allModes() {
        val m = SimilarityModule()

        val sim = m.solve(mapOf("mode" to SimilarityModule.MODE_SEBANGUN, "t1" to "3, 4, 5", "t2" to "6, 8, 10"))
        assertTrue(sim.ok)
        assertTrue(sim.answer.contains("Sebangun") && sim.answer.contains("2"))

        val prop = m.solve(mapOf("mode" to SimilarityModule.MODE_SENILAI, "t1" to "4", "t2" to "6", "c" to "10"))
        assertTrue(prop.ok)
        assertTrue(prop.answer.contains("x = 15"))

        val cong = m.solve(mapOf("mode" to SimilarityModule.MODE_KONGRUEN_SSS, "t1" to "5, 6, 7", "t2" to "7, 5, 6"))
        assertTrue(cong.ok)
        assertTrue(cong.answer.contains("Kongruen"))

        val badTri = m.solve(mapOf("mode" to SimilarityModule.MODE_KONGRUEN_SSS, "t1" to "1, 2, 10", "t2" to "1, 2, 10"))
        assertFalse(badTri.ok)
    }

    // ---- 7. Bangun Ruang Sisi Lengkung ----

    @Test
    fun curvedSolid_allShapesAndPiModes() {
        val m = CurvedSolidModule()

        // Tabung r=7, t=10 with symbol pi default
        val cylSym = m.solve(mapOf("bangun" to CurvedSolidModule.SHAPE_TABUNG, "r" to "7", "t" to "10"))
        assertTrue(cylSym.ok)
        assertTrue(cylSym.answer.contains("490π"))
        assertTrue(cylSym.steps.any { it.contains("22/7") })

        // Tabung with 22/7 numeric separated cleanly
        val cylNum = m.solve(mapOf("bangun" to CurvedSolidModule.SHAPE_TABUNG, "r" to "7", "t" to "10", "pi_mode" to PiMode.FRACTION_22_7.label))
        assertTrue(cylNum.ok)
        assertTrue(cylNum.answer.contains("490π") && cylNum.answer.contains("1540"))

        // Kerucut r=6, s=10 -> t=8, V=96pi
        val cone = m.solve(mapOf("bangun" to CurvedSolidModule.SHAPE_KERUCUT, "r" to "6", "t" to "10", "input_kerucut" to "r dan s"))
        assertTrue(cone.ok)
        assertTrue(cone.answer.contains("t = 8") && cone.answer.contains("96π"))

        // Bola r=3 -> V=36pi, L=36pi
        val sphere = m.solve(mapOf("bangun" to CurvedSolidModule.SHAPE_BOLA, "r" to "3"))
        assertTrue(sphere.ok)
        assertTrue(sphere.answer.contains("36π"))

        // Find unknown radius from sphere volume 288pi -> r=6
        val unk = m.solve(mapOf("bangun" to CurvedSolidModule.SHAPE_BOLA, "mode" to CurvedSolidModule.MODE_CARI_UNSUR, "volume" to "288π"))
        assertTrue(unk.ok)
        assertEquals("r = 6", unk.answer)
    }

    // ---- 8. Statistika ----

    @Test
    fun statistics_exactMeanQuartilesModeRange() {
        val m = StatisticsModule()
        val s1 = m.solve(mapOf("data" to "2, 3, 3, 4, 5, 6, 8"))
        assertTrue(s1.ok)
        assertTrue(s1.answer.contains("Mean = 31/7"))
        assertTrue(s1.answer.contains("Median = 4"))
        assertTrue(s1.answer.contains("Modus = 3"))
        assertTrue(s1.answer.contains("Jangkauan = 6"))
        assertTrue(s1.answer.contains("Q₁ = 3"))
        assertTrue(s1.answer.contains("Q₃ = 6"))
        assertTrue(s1.answer.contains("IQR = 3"))

        val sFreq = m.solve(mapOf("data" to "5:2, 7:3, 9:1"))
        assertTrue(sFreq.ok)
        assertTrue(sFreq.answer.contains("Mean = 20/3") && sFreq.answer.contains("Modus = 7"))
    }

    // ---- 9. Peluang ----

    @Test
    fun probability_allModes() {
        val m = ProbabilityModule()

        val d1 = m.solve(mapOf("mode" to ProbabilityModule.MODE_DADU_1, "kejadian" to ProbabilityModule.EV_PRIMA))
        assertTrue(d1.ok)
        assertTrue(d1.answer.contains("1/2"))

        val d2 = m.solve(mapOf("mode" to ProbabilityModule.MODE_DADU_2, "kejadian" to ProbabilityModule.EV_JUMLAH_EQ, "b" to "7"))
        assertTrue(d2.ok)
        assertTrue(d2.answer.contains("1/6"))

        val coin = m.solve(mapOf("mode" to ProbabilityModule.MODE_KOIN, "a" to "3", "kejadian" to ProbabilityModule.EV_TEPAT_ANGKA, "b" to "2"))
        assertTrue(coin.ok)
        assertTrue(coin.answer.contains("3/8"))

        val fh = m.solve(mapOf("mode" to ProbabilityModule.MODE_FH, "a" to "1/6", "b" to "120"))
        assertTrue(fh.ok)
        assertTrue(fh.answer.contains("20"))
    }

    // ---- 10. Persamaan Garis Lurus & Fungsi Linear ----

    @Test
    fun linearLine_allRequiredCases() {
        val m = LinearLineModule()

        // Titik (1,2) dan (3,8) -> m = 3, persamaan y = 3x - 1
        val grad = m.solve(mapOf("mode" to LinearLineModule.MODE_GRAD_TITIK, "titik1" to "(1,2)", "titik2" to "(3,8)"))
        assertTrue(grad.ok)
        assertTrue(grad.answer.contains("m = 3"))

        val eqTwo = m.solve(mapOf("mode" to LinearLineModule.MODE_GARIS_DUA_TITIK, "titik1" to "(1,2)", "titik2" to "(3,8)"))
        assertTrue(eqTwo.ok)
        assertTrue(eqTwo.answer.contains("y = 3x - 1") && eqTwo.answer.contains("3x - y - 1 = 0"))
        assertTrue(eqTwo.svgSketch.contains("<svg") && eqTwo.plotSpec != null)

        // 2x + 3y - 6 = 0 -> m = -2/3, potong sumbu-x (3,0), potong sumbu-y (0,2)
        val fromEq = m.solve(mapOf("mode" to LinearLineModule.MODE_GRAD_PERS, "garis1" to "2x + 3y - 6 = 0"))
        assertTrue(fromEq.ok)
        assertTrue(fromEq.answer.contains("m = -2/3"))

        val intercepts = m.solve(mapOf("mode" to LinearLineModule.MODE_TITIK_POTONG_SUMBU, "garis1" to "2x + 3y - 6 = 0"))
        assertTrue(intercepts.ok)
        assertTrue(intercepts.answer.contains("(3, 0)") && intercepts.answer.contains("(0, 2)"))

        // Sejajar y = 2x + 1 melalui (3,4) -> y = 2x - 2
        val parallel = m.solve(mapOf("mode" to LinearLineModule.MODE_SEJAJAR, "garis1" to "y = 2x + 1", "titik1" to "(3,4)"))
        assertTrue(parallel.ok)
        assertTrue(parallel.answer.contains("y = 2x - 2"))

        // Tegak lurus y = 2x + 1 melalui (4,3) -> y = -1/2 x + 5
        val perp = m.solve(mapOf("mode" to LinearLineModule.MODE_TEGAK_LURUS, "garis1" to "y = 2x + 1", "titik1" to "(4,3)"))
        assertTrue(perp.ok)
        assertTrue(perp.answer.contains("y = -1/2 x + 5"))

        // Titik (2,1) dan (2,5) -> garis vertikal x = 2, gradien tidak terdefinisi
        val vert = m.solve(mapOf("mode" to LinearLineModule.MODE_GRAD_TITIK, "titik1" to "(2,1)", "titik2" to "(2,5)"))
        assertTrue(vert.ok)
        assertTrue(vert.answer.contains("x = 2") && vert.answer.contains("tidak terdefinisi", ignoreCase = true))

        // Fungsi linear f(x) = 2x + 3 -> f(4) = 11, f(x) = 9 -> x = 3
        val fnEval = m.solve(mapOf("mode" to LinearLineModule.MODE_FUNGSI_LINEAR, "garis1" to "f(x) = 2x + 3", "m_nilai" to "4", "b_nilai" to "9"))
        assertTrue(fnEval.ok)
        assertTrue(fnEval.answer.contains("f(4) = 11") && fnEval.answer.contains("x = 3"))

        // f(1) = 5 dan f(3) = 9 -> f(x) = 2x + 3
        val fnFind = m.solve(mapOf("mode" to LinearLineModule.MODE_FUNGSI_LINEAR, "titik1" to "f(1)=5", "titik2" to "f(3)=9"))
        assertTrue(fnFind.ok)
        assertTrue(fnFind.answer.contains("f(x) = 2x + 3"))

        // Titik potong dua garis didelegasikan ke SPLDV
        val inter = m.solve(mapOf("mode" to LinearLineModule.MODE_POTONG_DUA_GARIS, "garis1" to "2x + 3y = 12", "garis2" to "3x + 2y = 13"))
        assertTrue(inter.ok)
        assertTrue(inter.answer.contains("(3, 2)"))
    }

    // ---- 11. PLSV & Pertidaksamaan Linear Satu Variabel ----

    @Test
    fun plsv_allRequiredCasesAndValidation() {
        val m = PlsvModule()

        val r1 = m.solve(mapOf("persamaan" to "3x + 5 = 20"))
        assertTrue(r1.ok)
        assertEquals("x = 5", r1.answer)

        val r2 = m.solve(mapOf("persamaan" to "2(x - 3) = 4x + 2"))
        assertTrue(r2.ok)
        assertEquals("x = -4", r2.answer)
        assertTrue(r2.steps.any { it.contains("distributif", ignoreCase = true) })

        val r3 = m.solve(mapOf("persamaan" to "3x + 2 = 3x + 5"))
        assertTrue(r3.ok)
        assertTrue(r3.answer.contains("Tidak ada solusi", ignoreCase = true))

        val r4 = m.solve(mapOf("persamaan" to "2x + 4 = 2(x + 2)"))
        assertTrue(r4.ok)
        assertTrue(r4.answer.contains("Semua bilangan real", ignoreCase = true))

        // -2x + 4 > 10 -> x < -3 (tanda dibalik karena dibagi bilangan negatif)
        val r5 = m.solve(mapOf("persamaan" to "-2x + 4 > 10"))
        assertTrue(r5.ok)
        assertTrue(r5.answer.contains("x < -3") && r5.answer.contains("<====o----"))
        assertTrue(r5.steps.any { it.contains("tanda dibalik karena dibagi bilangan negatif", ignoreCase = true) })
        assertTrue(r5.check.contains("Uji dalam HP") && r5.check.contains("Uji luar HP"))

        // 3x - 5 <= 7 -> x <= 4
        val r6 = m.solve(mapOf("persamaan" to "3x - 5 <= 7"))
        assertTrue(r6.ok)
        assertTrue(r6.answer.contains("x <= 4") && r6.answer.contains("<====●----"))

        // Invalid inputs rejected cleanly
        assertFalse(m.solve(mapOf("persamaan" to "")).ok)
        assertFalse(m.solve(mapOf("persamaan" to "halo dunia")).ok)
        assertFalse(m.solve(mapOf("persamaan" to "3x + 5")).ok)
        assertFalse(m.solve(mapOf("persamaan" to "x + y = 4")).ok)
    }

    // ---- Registry, Kartu Rumus & Validasi AI ----

    @Test
    fun registryAndAiValidation_workStrictly() {
        assertEquals(11, MathRegistry.modules.size)
        for (mod in MathRegistry.modules) {
            assertTrue(mod.id.isNotBlank())
            assertTrue(mod.fields.isNotEmpty())
        }

        // Kartu rumus Modul 10 dan Modul 11 tersedia lengkap
        val c10 = FormulaCards.forModule("garis_linear")
        val c11 = FormulaCards.forModule("plsv_ptlsv")
        assertTrue(c10.size >= 4)
        assertTrue(c11.size >= 2)
        assertTrue(c10.any { it.title.contains("Gradien", ignoreCase = true) })
        assertTrue(c10.any { it.formula.contains("m₁ × m₂ = -1") })
        assertTrue(c11.any { it.formula.contains("TANDA DIBALIK", ignoreCase = true) })

        val goodJson = """{"modul": "spldv", "input": {"a1": "2", "b1": "3", "c1": "12", "a2": "3", "b2": "2", "c2": "13"}}"""
        val okIntent = AiRouterClient.validateAiModuleJson(goodJson)
        assertTrue(okIntent.ok)
        assertEquals("spldv", okIntent.module?.id)
        assertTrue(okIntent.previewResult?.answer?.contains("x = 3") == true)

        val okPlsv = AiRouterClient.validateAiModuleJson("""{"modul": "plsv_ptlsv", "input": {"persamaan": "-2x + 4 > 10"}}""")
        assertTrue(okPlsv.ok)
        assertTrue(okPlsv.previewResult?.answer?.contains("x < -3") == true)

        val badMod = AiRouterClient.validateAiModuleJson("""{"modul": "integral_kuliah", "input": {}}""")
        assertFalse(badMod.ok)

        val badField = AiRouterClient.validateAiModuleJson("""{"modul": "persamaan_kuadrat", "input": {"a": "0", "b": "2", "c": "1"}}""")
        assertFalse(badField.ok)
    }
}
