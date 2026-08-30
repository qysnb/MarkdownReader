package com.codex.markdownreader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownLatexNormalizerTest {
    @Test
    fun convertsSingleDollarInlineMathForMarkwon() {
        assertEquals("值 $$\\alpha$$ 可见", normalizeInlineLatex("值 $\\alpha$ 可见"))
    }

    @Test
    fun convertsTyporaStyleParenthesisAndBracketMath() {
        val dollar = '$'
        val source = "值 \\(x_{k1}\\) 和 \\[F_k(x)\\]"
        assertEquals("值 ${dollar}${dollar}x_{k1}${dollar}${dollar} 和 ${dollar}${dollar}F_k(x)${dollar}${dollar}", normalizeInlineLatex(source))
    }

    @Test
    fun convertsMultilineBracketMath() {
        val source = """\[
\frac{a}{b}
\]"""
        assertEquals("""$$
\frac{a}{b}
$$""", normalizeInlineLatex(source))
    }

    @Test
    fun convertsSeveralInlineFormulasInsideChineseText() {
        val dollar = '$'
        val source = "其中 ${dollar}p_i=\\mathrm{d}F_0(z_i)${dollar} 为基准分布 ${dollar}F_0${dollar} 在观测点 ${dollar}z_i${dollar} 处的跳度，满足 ${dollar}p_i\\ge 0${dollar}。"
        val expected = "其中 ${dollar}${dollar}p_i=\\mathrm{d}F_0(z_i)${dollar}${dollar} 为基准分布 ${dollar}${dollar}F_0${dollar}${dollar} 在观测点 ${dollar}${dollar}z_i${dollar}${dollar} 处的跳度，满足 ${dollar}${dollar}p_i\\ge 0${dollar}${dollar}。"
        assertEquals(expected, normalizeInlineLatex(source))
    }

    @Test
    fun preservesBlockMathAndDollarTextInCode() {
        val dollar = '$'
        val markdown = """${dollar}${dollar}\alpha + \beta${dollar}${dollar}
`${dollar}price${dollar}`
```
${dollar}x${dollar}
```"""
        assertEquals(markdown, normalizeInlineLatex(markdown))
    }

    @Test
    fun preservesEscapedAndUnmatchedDollarSigns() {
        val markdown = "\\$5 and ${'$'}open"
        assertEquals(markdown, normalizeInlineLatex(markdown))
    }

    @Test
    fun removesOptionalRowSpacingThatJLatexMathDoesNotConsume() {
        val source = """$$
\\begin{aligned}
a &= b \\\\[4pt]
c &= d \\\\[2pt]
\\end{aligned}
$$"""

        val expected = """$$
\\begin{aligned}
a &= b \\\\
c &= d \\\\
\\end{aligned}
$$"""

        assertEquals(expected, normalizeInlineLatex(source))
    }

    @Test
    fun removesOptionalRowSpacingFromRealTwoSlashMarkdownInput() {
        val source = """$$
\begin{aligned}
a &= b \\[4pt]
c &= d \\[2pt]
\end{aligned}
$$"""

        val expected = """$$
\begin{aligned}
a &= b \\
c &= d \\
\end{aligned}
$$"""

        assertEquals(expected, normalizeInlineLatex(source))
    }

    @Test
    fun adaptsCommonFractionAndBoldAliasesForJLatexMath() {
        val source = """$$
\tfrac{a}{b} + \dfrac{c}{d} + \bm{x}
$$"""

        val expected = """$$
\frac{a}{b} + \frac{c}{d} + \boldsymbol{x}
$$"""

        assertEquals(expected, normalizeInlineLatex(source))
    }

    @Test
    fun adaptsUnsupportedEllipsisCommandsForJLatexMath() {
        val source = "X_{k1},X_{k2},\\ldots,X_{kn_k},\\qquad k=0,1,2"

        assertEquals(
            "X_{k1},X_{k2},\\ldotp\\ldotp\\ldotp,X_{kn_k},\\qquad k=0,1,2",
            normalizeLatexSource(source)
        )
    }
}
