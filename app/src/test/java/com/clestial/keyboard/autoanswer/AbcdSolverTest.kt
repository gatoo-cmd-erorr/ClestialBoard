package com.clestial.keyboard.autoanswer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbcdSolverTest {

    @Test
    fun testParseFromText_multiLine() {
        val sample = """
            1. Struktur data yang menggunakan prinsip LIFO (Last In First Out) adalah...
            A. Queue
            B. Stack
            C. Tree
            D. Graph
        """.trimIndent()

        val parsed = AbcdSolver.parseFromText(sample)
        assertNotNull(parsed)
        assertEquals(4, parsed!!.options.size)
        assertEquals("Queue", parsed.options['A'])
        assertEquals("Stack", parsed.options['B'])
        assertEquals("Tree", parsed.options['C'])
        assertEquals("Graph", parsed.options['D'])
        assertTrue(parsed.questionText.contains("LIFO"))
    }

    @Test
    fun testParseFromText_inline() {
        val sample = "Nilai dari 2x + 5 = 15 adalah... A. 2 B. 5 C. 10 D. 20"
        val parsed = AbcdSolver.parseFromText(sample)
        assertNotNull(parsed)
        assertEquals(4, parsed!!.options.size)
        assertEquals("2", parsed.options['A'])
        assertEquals("5", parsed.options['B'])
        assertEquals("10", parsed.options['C'])
        assertEquals("20", parsed.options['D'])
    }

    @Test
    fun testSolveLocally_mathPlsv() {
        val q = AbcdQuestion(
            questionText = "Tentukan nilai x dari persamaan linear 2x + 5 = 15:",
            options = mapOf('A' to "3", 'B' to "5", 'C' to "7", 'D' to "10")
        )
        val res = AbcdSolver.solveLocally(q)
        assertTrue(res.ok)
        assertEquals('B', res.selectedOption)
        assertEquals("5", res.optionText)
    }

    @Test
    fun testSolveLocally_computerScience() {
        val q = AbcdQuestion(
            questionText = "Struktur data yang menerapkan prinsip LIFO (Last In First Out) adalah:",
            options = mapOf('A' to "Queue", 'B' to "Stack", 'C' to "Array", 'D' to "Linked List")
        )
        val res = AbcdSolver.solveLocally(q)
        assertTrue(res.ok)
        assertEquals('B', res.selectedOption)
        assertEquals("Stack", res.optionText)
    }

    @Test
    fun testParseAiAnswer_strict() {
        val aiResponse = """
            KUNCI: C
            PENJELASAN: Kompleksitas waktu algoritma binary search adalah O(log n) karena ruang pencarian dibagi dua pada setiap langkah iterasi.
        """.trimIndent()

        val options = mapOf('A' to "O(1)", 'B' to "O(n)", 'C' to "O(log n)", 'D' to "O(n^2)")
        val parsed = AbcdSolver.parseAiAnswer(aiResponse, options)
        assertNotNull(parsed)
        assertEquals('C', parsed!!.selectedOption)
        assertEquals("O(log n)", parsed.optionText)
        assertTrue(parsed.explanation.contains("O(log n)"))
    }

    @Test
    fun testSolveLocally_spldvSystem() {
        val q = AbcdQuestion(
            questionText = "Penyelesaian dari sistem persamaan 2x - y = 7 dan x + 3y = -7 adalah ....",
            options = mapOf(
                'A' to "x = 2 dan y = -3",
                'B' to "x = -2 dan y = 3",
                'C' to "x = 3 dan y = -1",
                'D' to "x = 2 dan y = 3"
            )
        )
        val res = AbcdSolver.solveLocally(q)
        assertTrue(res.ok)
        assertEquals('A', res.selectedOption)

        val offline = com.clestial.keyboard.math.OfflineMathAssistant.trySolveMathQuestion("2x - y = 7 dan x + 3y = -7")
        assertNotNull(offline)
        assertTrue(offline!!.contains("x = 2") && offline.contains("y = -3"))
    }
}
