package com.codex.markdownreader.ui

import io.noties.markwon.ext.latex.JLatexMathNode
import io.noties.markwon.inlineparser.MarkwonInlineParser
import org.commonmark.node.Paragraph
import org.commonmark.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexInlineProcessorTest {
    @Test
    fun parsesSingleDollarFormulaAsLatexNode() {
        val dollar = '$'
        val document = parser().parse("其中 ${dollar}p_i\\ge 0${dollar}。")
        val paragraph = document.firstChild as Paragraph
        val formula = paragraph.firstChild?.next as JLatexMathNode

        assertEquals("p_i\\ge 0", formula.latex())
    }

    @Test
    fun parsesDoubleDollarFormulaInsideParagraphAsLatexNode() {
        val dollar = '$'
        val document = parser().parse("前文 ${dollar}${dollar}\\alpha+\\beta${dollar}${dollar} 后文")
        val paragraph = document.firstChild as Paragraph
        val formula = paragraph.firstChild?.next as JLatexMathNode

        assertTrue(formula.latex().contains("\\alpha"))
    }

    @Test
    fun parsesInlineFormulasFromTheReportedDocument() {
        val dollar = '$'
        val source = """
            $dollar k=0,1,2 $dollar 与 $dollar X $dollar。
            $dollar R(s_1,s_2)=F_2(F_3^{-1}(1-s_2))-F_2(F_1^{-1}(s_1)) $dollar
            其中 $dollar p_i=\\mathrm{d}F_0(z_i) $dollar、$dollar F_0 $dollar、$dollar z_i $dollar，满足 $dollar p_i\\ge 0 $dollar。
            $dollar \\sum_{i=1}^{n}p_i=1 $dollar，且 $dollar \\theta=(\\theta_1^{\\mathsf T},\\theta_2^{\\mathsf T})^{\\mathsf T} $dollar。
        """.trimIndent()

        val document = parser().parse(normalizeInlineLatex(source))
        val formulas = mutableListOf<JLatexMathNode>()
        collectLatexNodes(document, formulas)

        assertEquals(9, formulas.size)
        assertTrue(formulas.any { it.latex().contains("\\mathrm{d}") })
        assertTrue(formulas.any { it.latex().contains("\\sum") })
        assertTrue(formulas.any { it.latex().contains("\\mathsf T") })
    }

    private fun collectLatexNodes(node: org.commonmark.node.Node, output: MutableList<JLatexMathNode>) {
        var child = node.firstChild
        while (child != null) {
            if (child is JLatexMathNode) output += child
            collectLatexNodes(child, output)
            child = child.next
        }
    }

    private fun parser(): Parser = Parser.builder()
        .inlineParserFactory(
            MarkwonInlineParser.factoryBuilder()
                .addInlineProcessor(LatexInlineProcessor())
                .build()
        )
        .build()
}
