package com.clestial.keyboard.math

/**
 * Modul 11 (Aljabar): Persamaan dan Pertidaksamaan Linear Satu Variabel (PLSV & PtLSV).
 * Mendukung bentuk umum ax + b = cx + d, tanda pertidaksamaan (<, <=, >, >=, ≤, ≥),
 * tanda kurung dengan langkah distributif (misal 2(x - 3) = 4x + 2),
 * kasus khusus 0x = c (tak ada solusi / semua bilangan real),
 * pembalikan tanda pertidaksamaan saat dibagi bilangan negatif, garis bilangan teks, dan cek substitusi asli.
 */
class PlsvModule : MathModule {

    override val id = "plsv_ptlsv"
    override val name = "PLSV & Pertidaksamaan Linear"
    override val group = MathGroup.ALJABAR
    override val description = "Selesaikan persamaan (=) & pertidaksamaan (<, <=, >, >=) satu variabel, termasuk bentuk berkurung dan garis bilangan."

    override val fields = listOf(
        InputFieldSpec(
            key = "persamaan",
            label = "Persamaan / Pertidaksamaan (misal: 2(x - 3) = 4x + 2 atau -2x + 4 > 10)",
            hint = "3x + 5 = 20",
            type = FieldType.TEXT,
            defaultValue = "2(x - 3) = 4x + 2"
        )
    )

    private enum class RelOp(val symbol: String) {
        EQ("="),
        LT("<"),
        LE("<="),
        GT(">"),
        GE(">=");

        fun flipped(): RelOp = when (this) {
            EQ -> EQ
            LT -> GT
            LE -> GE
            GT -> LT
            GE -> LE
        }

        fun displaySymbol(): String = when (this) {
            EQ -> "="
            LT -> "<"
            LE -> "≤"
            GT -> ">"
            GE -> "≥"
        }

        fun holds(lhs: ExactFraction, rhs: ExactFraction): Boolean = when (this) {
            EQ -> lhs == rhs
            LT -> lhs < rhs
            LE -> lhs <= rhs
            GT -> lhs > rhs
            GE -> lhs >= rhs
        }
    }

    private sealed class AstNode {
        data class Num(val v: ExactFraction) : AstNode()
        data class Var(val name: Char) : AstNode()
        data class Neg(val inner: AstNode) : AstNode()
        data class Paren(val inner: AstNode) : AstNode()
        data class Bin(val op: Char, val left: AstNode, val right: AstNode) : AstNode()
    }

    private data class LinearPoly(
        val xCoeff: ExactFraction,
        val constVal: ExactFraction,
        val varName: Char?,
        val hadParens: Boolean
    )

    override fun solve(input: Map<String, String>): SolveResult {
        val rawInput = input["persamaan"]?.trim().orEmpty()
        if (rawInput.isEmpty()) {
            return SolveResult.fail("Masukkan persamaan atau pertidaksamaan terlebih dahulu (misal 3x + 5 = 20 atau -2x + 4 > 10).")
        }
        val norm = rawInput
            .replace("−", "-")
            .replace("–", "-")
            .replace("≤", "<=")
            .replace("≥", ">=")
            .replace("×", "*")
            .replace("÷", "/")
            .trim()

        val (lhsRaw, op, rhsRaw) = splitRelation(norm)
            ?: return SolveResult.fail("Format tidak valid. Gunakan tepat satu tanda pembanding (=, <, <=, >, >=).")

        val lhsAst = try {
            LinearAstParser(lhsRaw).parse()
        } catch (e: IllegalArgumentException) {
            return SolveResult.fail("Ruas kiri tidak valid: ${e.message}")
        }
        val rhsAst = try {
            LinearAstParser(rhsRaw).parse()
        } catch (e: IllegalArgumentException) {
            return SolveResult.fail("Ruas kanan tidak valid: ${e.message}")
        }

        val lhsPoly = try {
            toLinearPoly(lhsAst)
        } catch (e: IllegalArgumentException) {
            return SolveResult.fail("Ruas kiri bukan bentuk linear satu variabel: ${e.message}")
        }
        val rhsPoly = try {
            toLinearPoly(rhsAst)
        } catch (e: IllegalArgumentException) {
            return SolveResult.fail("Ruas kanan bukan bentuk linear satu variabel: ${e.message}")
        }

        val varChar = lhsPoly.varName ?: rhsPoly.varName ?: 'x'
        if (lhsPoly.varName != null && rhsPoly.varName != null && lhsPoly.varName != rhsPoly.varName) {
            return SolveResult.fail("Ditemukan lebih dari satu variabel ('${lhsPoly.varName}' dan '${rhsPoly.varName}'). Modul ini khusus satu variabel.")
        }

        val steps = ArrayList<String>()
        steps.add("Soal: $rawInput")

        val opStr = op.symbol
        if (lhsPoly.hadParens || rhsPoly.hadParens) {
            val lExp = formatLinearPoly(lhsPoly.xCoeff, lhsPoly.constVal, varChar)
            val rExp = formatLinearPoly(rhsPoly.xCoeff, rhsPoly.constVal, varChar)
            steps.add("Hilangkan kurung dengan sifat distributif: $lExp $opStr $rExp")
        }

        val netX = lhsPoly.xCoeff - rhsPoly.xCoeff
        val netConst = rhsPoly.constVal - lhsPoly.constVal

        val xSideMove = ExactMath.formatLinearLhs(lhsPoly.xCoeff, varChar.toString(), rhsPoly.xCoeff.negate(), varChar.toString())
        val cSideMove = ExactMath.formatLinearLhs(rhsPoly.constVal, "", lhsPoly.constVal.negate(), "")
        steps.add("Pindahkan suku $varChar ke ruas kiri dan konstanta ke ruas kanan: $xSideMove $opStr $cSideMove")
        steps.add("Sederhanakan: ${ExactMath.formatTerm(netX, varChar.toString(), true).ifEmpty { "0$varChar" }} $opStr ${netConst.toFracString()}")

        if (netX.isZero) {
            val alwaysTrue = op.holds(ExactFraction.ZERO, netConst)
            val statusText = if (alwaysTrue) {
                if (op == RelOp.EQ) {
                    "0$varChar = 0 selalu benar untuk setiap $varChar."
                } else {
                    "Pernyataan 0 $opStr ${netConst.toFracString()} selalu benar."
                }
            } else {
                if (op == RelOp.EQ) {
                    "0$varChar = ${netConst.toFracString()} tidak mungkin terpenuhi."
                } else {
                    "Pernyataan 0 $opStr ${netConst.toFracString()} selalu salah."
                }
            }
            steps.add(statusText)
            val answer = if (alwaysTrue) "Semua bilangan real ($varChar ∈ ℝ)" else "Tidak ada solusi"
            val tVal = ExactFraction.ONE
            val lCheck = evalAst(lhsAst, varChar, tVal)
            val rCheck = evalAst(rhsAst, varChar, tVal)
            val checkMsg = "Uji substitusi $varChar = 1 ke soal awal: ruas kiri = ${lCheck.toFracString()}, ruas kanan = ${rCheck.toFracString()} → " +
                if (alwaysTrue) "Terpenuhi untuk semua nilai real." else "Tidak memenuhi (${lCheck} tidak $opStr ${rCheck})."
            return SolveResult(ok = true, steps = steps, answer = answer, check = checkMsg)
        }

        val sol = netConst / netX
        val solStr = sol.toFracString()

        if (op == RelOp.EQ) {
            steps.add("Bagi kedua ruas dengan ${netX.toFracString()}:")
            steps.add("$varChar = (${netConst.toFracString()}) / (${netX.toFracString()}) = $solStr")

            val lCheck = evalAst(lhsAst, varChar, sol)
            val rCheck = evalAst(rhsAst, varChar, sol)
            val checkMsg = "Substitusi $varChar = $solStr ke soal asli: ruas kiri = ${lCheck.toFracString()}, ruas kanan = ${rCheck.toFracString()} → " +
                if (lCheck == rCheck) "Benar" else "Tidak cocok"

            return SolveResult(
                ok = true,
                steps = steps,
                answer = "$varChar = $solStr",
                check = checkMsg
            )
        }

        // Kasus pertidaksamaan: cek apakah pembagi bernilai negatif
        val finalOp = if (netX.signum < 0) op.flipped() else op
        if (netX.signum < 0) {
            steps.add("Bagi kedua ruas dengan ${netX.toFracString()} (tanda dibalik karena dibagi bilangan negatif):")
        } else {
            steps.add("Bagi kedua ruas dengan ${netX.toFracString()} (tanda tetap karena dibagi bilangan positif):")
        }
        val ineqResult = "$varChar ${finalOp.symbol} $solStr"
        val numberLine = formatNumberLine(finalOp, sol)
        steps.add(ineqResult)
        steps.add("Garis bilangan: $numberLine")

        // Cek: uji satu nilai di dalam himpunan penyelesaian dan satu nilai di luar himpunan penyelesaian
        val insideVal = when (finalOp) {
            RelOp.LT, RelOp.LE -> sol - ExactFraction.ONE
            else -> sol + ExactFraction.ONE
        }
        val outsideVal = when (finalOp) {
            RelOp.LT, RelOp.LE -> sol + ExactFraction.ONE
            else -> sol - ExactFraction.ONE
        }
        val inL = evalAst(lhsAst, varChar, insideVal)
        val inR = evalAst(rhsAst, varChar, insideVal)
        val inHolds = op.holds(inL, inR)
        val outL = evalAst(lhsAst, varChar, outsideVal)
        val outR = evalAst(rhsAst, varChar, outsideVal)
        val outFails = !op.holds(outL, outR)

        val checkMsg = "Uji dalam HP ($varChar = ${insideVal.toFracString()}): ${inL.toFracString()} ${op.symbol} ${inR.toFracString()} (${if (inHolds) "Benar" else "Salah"}); " +
            "Uji luar HP ($varChar = ${outsideVal.toFracString()}): ${outL.toFracString()} ${op.symbol} ${outR.toFracString()} (${if (outFails) "Tidak memenuhi" else "Memenuhi"})."

        return SolveResult(
            ok = true,
            steps = steps,
            answer = "$ineqResult\nGaris bilangan: $numberLine",
            check = checkMsg
        )
    }

    private fun formatNumberLine(op: RelOp, bound: ExactFraction): String {
        val b = bound.toFracString()
        return when (op) {
            RelOp.LT -> "<====o----  (titik batas $b kosong)"
            RelOp.LE -> "<====●----  (titik batas $b penuh)"
            RelOp.GT -> "----o====>  (titik batas $b kosong)"
            RelOp.GE -> "----●====>  (titik batas $b penuh)"
            RelOp.EQ -> "----●----   ($b)"
        }
    }

    private fun splitRelation(s: String): Triple<String, RelOp, String>? {
        val ops = listOf("<=" to RelOp.LE, ">=" to RelOp.GE, "=" to RelOp.EQ, "<" to RelOp.LT, ">" to RelOp.GT)
        var foundIdx = -1
        var foundOp: RelOp? = null
        var foundLen = 0
        var i = 0
        while (i < s.length) {
            val matched = ops.firstOrNull { (sym, _) -> s.startsWith(sym, i) }
            if (matched != null) {
                if (foundOp != null) return null // lebih dari satu operator
                foundIdx = i
                foundOp = matched.second
                foundLen = matched.first.length
                i += foundLen
            } else {
                i++
            }
        }
        if (foundOp == null) return null
        val lhs = s.substring(0, foundIdx).trim()
        val rhs = s.substring(foundIdx + foundLen).trim()
        if (lhs.isEmpty() || rhs.isEmpty()) return null
        return Triple(lhs, foundOp, rhs)
    }

    private fun formatLinearPoly(xCoeff: ExactFraction, c: ExactFraction, varChar: Char): String =
        ExactMath.formatLinearLhs(xCoeff, varChar.toString(), c, "")

    private fun toLinearPoly(node: AstNode): LinearPoly = when (node) {
        is AstNode.Num -> LinearPoly(ExactFraction.ZERO, node.v, null, false)
        is AstNode.Var -> LinearPoly(ExactFraction.ONE, ExactFraction.ZERO, node.name, false)
        is AstNode.Neg -> {
            val inner = toLinearPoly(node.inner)
            LinearPoly(inner.xCoeff.negate(), inner.constVal.negate(), inner.varName, inner.hadParens)
        }
        is AstNode.Paren -> {
            val inner = toLinearPoly(node.inner)
            inner.copy(hadParens = true)
        }
        is AstNode.Bin -> {
            val l = toLinearPoly(node.left)
            val r = toLinearPoly(node.right)
            val combinedVar = when {
                l.varName == null -> r.varName
                r.varName == null -> l.varName
                l.varName == r.varName -> l.varName
                else -> throw IllegalArgumentException("lebih dari satu huruf variabel ('${l.varName}' dan '${r.varName}').")
            }
            val parens = l.hadParens || r.hadParens
            when (node.op) {
                '+' -> LinearPoly(l.xCoeff + r.xCoeff, l.constVal + r.constVal, combinedVar, parens)
                '-' -> LinearPoly(l.xCoeff - r.xCoeff, l.constVal - r.constVal, combinedVar, parens)
                '*' -> {
                    if (!l.xCoeff.isZero && !r.xCoeff.isZero) {
                        throw IllegalArgumentException("perkalian antar variabel menghasilkan bentuk kuadrat/non-linear.")
                    }
                    if (l.xCoeff.isZero) {
                        LinearPoly(r.xCoeff * l.constVal, r.constVal * l.constVal, combinedVar, parens)
                    } else {
                        LinearPoly(l.xCoeff * r.constVal, l.constVal * r.constVal, combinedVar, parens)
                    }
                }
                '/' -> {
                    if (!r.xCoeff.isZero) {
                        throw IllegalArgumentException("variabel tidak boleh menjadi pembagi.")
                    }
                    if (r.constVal.isZero) {
                        throw IllegalArgumentException("pembagian dengan nol.")
                    }
                    LinearPoly(l.xCoeff / r.constVal, l.constVal / r.constVal, combinedVar, parens)
                }
                else -> throw IllegalArgumentException("operator '${node.op}' tidak dikenal.")
            }
        }
    }

    private fun evalAst(node: AstNode, varChar: Char, xVal: ExactFraction): ExactFraction = when (node) {
        is AstNode.Num -> node.v
        is AstNode.Var -> xVal
        is AstNode.Neg -> evalAst(node.inner, varChar, xVal).negate()
        is AstNode.Paren -> evalAst(node.inner, varChar, xVal)
        is AstNode.Bin -> {
            val l = evalAst(node.left, varChar, xVal)
            val r = evalAst(node.right, varChar, xVal)
            when (node.op) {
                '+' -> l + r
                '-' -> l - r
                '*' -> l * r
                '/' -> l / r
                else -> l
            }
        }
    }

    private class LinearAstParser(raw: String) {
        private val s = raw.replace(Regex("""\s+"""), "")
        private var pos = 0

        fun parse(): AstNode {
            if (s.isEmpty()) throw IllegalArgumentException("ruas kosong.")
            val res = parseAddSub()
            if (pos < s.length) throw IllegalArgumentException("karakter tak terduga '${s[pos]}'.")
            return res
        }

        private fun parseAddSub(): AstNode {
            var left = parseMulDiv()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                left = AstNode.Bin(op, left, parseMulDiv())
            }
            return left
        }

        private fun parseMulDiv(): AstNode {
            var left = parseUnary()
            while (pos < s.length) {
                val ch = s[pos]
                when {
                    ch == '*' || ch == '/' -> {
                        pos++
                        left = AstNode.Bin(ch, left, parseUnary())
                    }
                    // Perkalian implisit seperti 2(x - 3), 3x, (x+1)(2)
                    ch == '(' || ch.isLetter() -> {
                        left = AstNode.Bin('*', left, parseUnary())
                    }
                    else -> break
                }
            }
            return left
        }

        private fun parseUnary(): AstNode {
            if (pos < s.length && s[pos] == '+') {
                pos++
                return parseUnary()
            }
            if (pos < s.length && s[pos] == '-') {
                pos++
                return AstNode.Neg(parseUnary())
            }
            return parsePrimary()
        }

        private fun parsePrimary(): AstNode {
            if (pos >= s.length) throw IllegalArgumentException("ekspresi belum lengkap.")
            val ch = s[pos]
            if (ch == '(') {
                pos++
                val inner = parseAddSub()
                if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException("kurung tutup ')' kurang.")
                pos++
                return AstNode.Paren(inner)
            }
            if (ch.isDigit() || ch == '.' || ch == ',') {
                val start = pos
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.' || s[pos] == ',')) pos++
                val numStr = s.substring(start, pos)
                val parsed = ExactFraction.parse(numStr)
                    ?: throw IllegalArgumentException("format angka '$numStr' tidak valid.")
                return AstNode.Num(parsed)
            }
            if (ch.isLetter()) {
                val letter = ch.lowercaseChar()
                pos++
                if (pos < s.length && s[pos].isLetter()) {
                    throw IllegalArgumentException("kata/variabel '${s.substring(pos - 1)}' tidak valid.")
                }
                return AstNode.Var(letter)
            }
            throw IllegalArgumentException("simbol '$ch' tidak dikenal.")
        }
    }
}
