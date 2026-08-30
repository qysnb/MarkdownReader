package com.codex.markdownreader.ui

/** Converts common LaTeX delimiters to the syntax supported by Markwon ext-latex 4.6.2. */
internal fun normalizeInlineLatex(markdown: String): String {
    val output = StringBuilder(markdown.length)
    var fenced = false
    var bracketMath = false
    var dollarBlockLength = 0

    val lines = markdown.split('\n', ignoreCase = false, limit = Int.MAX_VALUE)
    lines.forEachIndexed { lineIndex, line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            fenced = !fenced
            output.append(line)
        } else if (fenced) {
            output.append(line)
        } else if (dollarBlockLength > 0) {
            if (isDollarBlockClosing(line, dollarBlockLength)) {
                output.append(line)
                dollarBlockLength = 0
            } else {
                output.append(normalizeLatexSource(line))
            }
        } else if (isDollarBlockOpening(line)) {
            dollarBlockLength = dollarDelimiterLength(line)
            output.append(line)
        } else {
            bracketMath = appendNormalizedLine(output, line, bracketMath)
        }
        if (lineIndex < lines.lastIndex) output.append('\n')
    }
    return output.toString()
}

private fun appendNormalizedLine(output: StringBuilder, line: String, bracketMathAtStart: Boolean): Boolean {
    var index = 0
    var inlineCode = false
    var bracketMath = bracketMathAtStart
    if (bracketMath) {
        val closing = line.indexOf("\\]")
        if (closing < 0) {
            output.append(normalizeLatexSource(line))
            return true
        }
        output.append(normalizeLatexSource(line.substring(0, closing)))
        output.append("$$")
        index = closing + 2
        bracketMath = false
    }
    while (index < line.length) {
        val character = line[index]
        if (character == '`') {
            inlineCode = !inlineCode
            output.append(character)
            index++
            continue
        }
        if (!inlineCode && line.startsWith("\\(", index)) {
            val closing = line.indexOf("\\)", index + 2)
            if (closing >= 0) {
                output.append("$$")
                output.append(normalizeLatexSource(line.substring(index + 2, closing)))
                output.append("$$")
                index = closing + 2
                continue
            }
        }
        if (!inlineCode && line.startsWith("\\[", index)) {
            val closing = line.indexOf("\\]", index + 2)
            if (closing >= 0) {
                output.append("$$")
                output.append(normalizeLatexSource(line.substring(index + 2, closing)))
                output.append("$$")
                index = closing + 2
                continue
            }
            output.append("$$")
            output.append(normalizeLatexSource(line.substring(index + 2)))
            return true
        }
        if (character != '$' || inlineCode || isEscaped(line, index)) {
            output.append(character)
            index++
            continue
        }
        if (index + 1 < line.length && line[index + 1] == '$') {
            val closing = findClosingDoubleDollar(line, index + 2)
            if (closing >= 0) {
                output.append("$$")
                output.append(normalizeLatexSource(line.substring(index + 2, closing)))
                output.append("$$")
                index = closing + 2
            } else {
                output.append("$$")
                index += 2
            }
            continue
        }

        val closing = findClosingDollar(line, index + 1, inlineCode)
        if (closing < 0) {
            output.append(character)
            index++
        } else {
            // Markwon 4.6.2's inline processor recognizes `$$...$$`, not `$...$`.
            output.append("$$")
            output.append(normalizeLatexSource(line.substring(index + 1, closing)))
            output.append("$$")
            index = closing + 1
        }
    }
    return bracketMath
}

private fun isDollarBlockOpening(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.length >= 2 && trimmed.all { it == '$' }
}

private fun dollarDelimiterLength(line: String): Int = line.trim().length

private fun isDollarBlockClosing(line: String, delimiterLength: Int): Boolean =
    line.trim().length == delimiterLength && line.trim().all { it == '$' }

private fun findClosingDoubleDollar(line: String, start: Int): Int {
    var index = start
    while (index + 1 < line.length) {
        if (line[index] == '$' && line[index + 1] == '$' && !isEscaped(line, index)) {
            return index
        }
        index++
    }
    return -1
}

private val latexRowSpacingPattern = Regex("""\\\\\*?[ \t]*\[[^\]\r\n]*\]""")
private val starredEnvironmentPattern = Regex("""\\(begin|end)\{(align|alignat|aligned|alignedat|equation|gather|gathered|multline|split|displaymath|math)\*\}""")
private val equationWrapperPattern = Regex("""\\(begin|end)\{(equation|displaymath|math)\}""")
private val operatorNameStarPattern = Regex("""\\operatorname\*[ \t]*\{""")
private val equationMetadataPattern = Regex("""\\(?:tag\*?|label)\{[^{}]*\}""")
private val equationMarkerPattern = Regex("""\\(?:notag|nonumber)""")
private val fractionAliasPattern = Regex("""\\(tfrac|dfrac)\b""")
private val boldSymbolAliasPattern = Regex("""\\bm\b""")
private val ellipsisAliasPattern = Regex("""\\(ldots|dots|cdots)\b""")

/**
 * Adapts common Typora/KaTeX-LaTeX conveniences to the smaller JLatexMath
 * grammar used by Markwon, without changing ordinary Markdown text.
 */
internal fun normalizeLatexSource(latex: String): String {
    var normalized = latex
    normalized = normalized.replace(latexRowSpacingPattern) { "\\\\" }
    normalized = normalized.replace(starredEnvironmentPattern) { match ->
        val kind = match.groupValues[1]
        val environment = match.groupValues[2]
        "\\" + kind + "{" + environment + "}"
    }
    normalized = normalized.replace(equationWrapperPattern) { "" }
    normalized = normalized.replace(operatorNameStarPattern) { "\\operatorname{" }
    normalized = normalized.replace(equationMetadataPattern) { "" }
    normalized = normalized.replace(equationMarkerPattern, "")
    normalized = normalized.replace(fractionAliasPattern) { "\\frac" }
    normalized = normalized.replace(boldSymbolAliasPattern) { "\\boldsymbol" }
    return normalized.replace(ellipsisAliasPattern) { "\\ldotp\\ldotp\\ldotp" }
}

private fun findClosingDollar(line: String, start: Int, inlineCode: Boolean): Int {
    if (inlineCode) return -1
    for (index in start until line.length) {
        if (line[index] == '$' && !isEscaped(line, index)) return index
    }
    return -1
}

private fun isEscaped(text: String, index: Int): Boolean {
    var backslashes = 0
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] == '\\') {
        backslashes++
        cursor--
    }
    return backslashes % 2 == 1
}
