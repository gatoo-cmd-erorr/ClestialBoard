package com.clestial.keyboard

import com.clestial.keyboard.math.ScientificCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaneseAndCalcTest {

    @Test
    fun javaneseScript_transliterationAndCombining() {
        // Basic Aksara -> Latin with sandhangan
        val sapaIki = "ꦱꦥꦲꦶꦏꦶ"
        assertEquals("sapaiki", JavaneseScript.toLatin(sapaIki))

        // Word with taling tarung (o) & cecak (-ng): "kothong" / "wong" -> ꦮꦺꦴꦁ
        val wong = "\uA9AE\uA9BA\uA9B4\uA981" // wa + taling + tarung + cecak
        assertEquals("wong", JavaneseScript.toLatin(wong))

        // Sandhangan wulu + suku + pepet + layar + wignyan: "bisa turu seger"
        val latinSample = "bisa turu"
        val jawa = JavaneseScript.toJavanese(latinSample)
        assertTrue(jawa.isNotEmpty())
        assertEquals("bisa turu", JavaneseScript.toLatin(jawa))

        // Pasangan combining helper avoids duplicating pangkon
        val cur = "ꦲꦤ"
        val ins1 = JavaneseScript.buildPasanganInsertion(cur, cur.length, "ꦏ")
        assertEquals("${JavaneseScript.CH_PANGKON}ꦏ", ins1)

        val withPangkon = "ꦲꦤ${JavaneseScript.CH_PANGKON}"
        val ins2 = JavaneseScript.buildPasanganInsertion(withPangkon, withPangkon.length, "ꦏ")
        assertEquals("ꦏ", ins2)

        // Delete removes one code point cleanly
        val (afterDel, nextCur) = JavaneseScript.deleteOneCodePoint("ꦏ\uA9B6", 2)
        assertEquals("ꦏ", afterDel)
        assertEquals(1, nextCur)

        // Javanese digits
        assertEquals("2026", JavaneseScript.toLatin("꧒꧐꧒꧖"))

        // Pasangan and wyanjana cluster tests
        val kna = "ꦏ${JavaneseScript.CH_PANGKON}ꦤ" // ka + pangkon + na -> kna
        assertEquals("kna", JavaneseScript.toLatin(kna))

        val kring = "ꦏ${JavaneseScript.CH_CAKRA}${JavaneseScript.CH_WULU}${JavaneseScript.CH_CECak}" // ka + cakra + wulu + cecak -> kring
        assertEquals("kring", JavaneseScript.toLatin(kring))

        val kreta = "ꦏ${JavaneseScript.CH_KERET}ꦠ" // ka + keret + ta -> kreta
        assertEquals("kreta", JavaneseScript.toLatin(kreta))

        val kya = "ꦏ${JavaneseScript.CH_PENGKAL}" // ka + pengkal -> kya
        assertEquals("kya", JavaneseScript.toLatin(kya))

        val visualKe = "${JavaneseScript.CH_TALING}ꦏ" // visual taling + ka -> ké
        assertEquals("ké", JavaneseScript.toLatin(visualKe))
    }

    @Test
    fun scientificCalculator_rootsPowersTrigAndRational() {
        val sqrt72 = ScientificCalculator.evaluate("√72")
        assertTrue(sqrt72.ok)
        assertEquals("6√2", sqrt72.insertable)
        assertTrue(sqrt72.display.contains("6√2"))

        val cbrt54 = ScientificCalculator.evaluate("∛54")
        assertTrue(cbrt54.ok)
        assertEquals("3∛2", cbrt54.insertable)

        val fourthRoot = ScientificCalculator.evaluate("4√16")
        assertTrue(fourthRoot.ok)
        assertEquals("2", fourthRoot.insertable)

        val powExpr = ScientificCalculator.evaluate("2^3 + 3²")
        assertTrue(powExpr.ok)
        assertEquals("17", powExpr.insertable)

        val rational = ScientificCalculator.evaluate("1/2 + 1/3")
        assertTrue(rational.ok)
        assertTrue(rational.display.contains("5/6"))

        val sin30 = ScientificCalculator.evaluate("sin(30)", degreeMode = true)
        assertTrue(sin30.ok)
        assertEquals("0,5", sin30.insertable)

        val log100 = ScientificCalculator.evaluate("log(100)")
        assertTrue(log100.ok)
        assertEquals("2", log100.insertable)

        val pct = ScientificCalculator.evaluate("50%")
        assertTrue(pct.ok)
        assertEquals("0,5", pct.insertable)

        val divZero = ScientificCalculator.evaluate("5÷0")
        assertFalse(divZero.ok)
        assertTrue(divZero.error.contains("nol", ignoreCase = true))

        // Kombinasi, Permutasi & Faktorial
        val fact5 = ScientificCalculator.evaluate("5!")
        assertTrue(fact5.ok)
        assertEquals("120", fact5.insertable)

        val combInfix = ScientificCalculator.evaluate("5C2")
        assertTrue(combInfix.ok)
        assertEquals("10", combInfix.insertable)

        val combFn = ScientificCalculator.evaluate("C(6, 2)")
        assertTrue(combFn.ok)
        assertEquals("15", combFn.insertable)

        val permInfix = ScientificCalculator.evaluate("5P2")
        assertTrue(permInfix.ok)
        assertEquals("20", permInfix.insertable)

        val permFn = ScientificCalculator.evaluate("nPr(6, 2)")
        assertTrue(permFn.ok)
        assertEquals("30", permFn.insertable)

        val combined = ScientificCalculator.evaluate("5C2 + 3!")
        assertTrue(combined.ok)
        assertEquals("16", combined.insertable)

        val badComb = ScientificCalculator.evaluate("3C5")
        assertFalse(badComb.ok)
    }
}
