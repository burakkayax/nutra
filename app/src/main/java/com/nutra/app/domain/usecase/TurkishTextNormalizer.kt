package com.nutra.app.domain.usecase

object TurkishTextNormalizer {
    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val len = input.length
        val sb = StringBuilder(len)
        var prevSpace = true
        for (i in 0 until len) {
            val ch = input[i]
            // Collapse apostrophes inside brand/food names (e.g. "Lay's" / "Lay’s" -> "lays")
            if (ch == '\'' || ch == '’' || ch == '‘' || ch == '`') {
                continue
            }
            val isDecimalComma = ch == ',' && i > 0 && i + 1 < len &&
                input[i - 1].isDigit() && input[i + 1].isDigit()
            val isSeparator = ch.isWhitespace() ||
                (ch == ',' && !isDecimalComma) ||
                ch == ';' || ch == '(' || ch == ')' || ch == '[' || ch == ']' ||
                ch == '/' || ch == '-' || ch == '–' || ch == '—' || ch == '&' || ch == '·'
            if (isSeparator) {
                if (!prevSpace) {
                    sb.append(' ')
                    prevSpace = true
                }
                continue
            }
            prevSpace = false
            val mapped = when (ch) {
                'ı', 'I', 'i', 'İ', 'î', 'Î' -> 'i'
                'ğ', 'Ğ' -> 'g'
                'ü', 'Ü', 'û', 'Û' -> 'u'
                'ş', 'Ş' -> 's'
                'ö', 'Ö' -> 'o'
                'ç', 'Ç' -> 'c'
                'â', 'Â' -> 'a'
                else -> ch.lowercaseChar()
            }
            sb.append(mapped)
        }
        if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
            sb.setLength(sb.length - 1)
        }
        return sb.toString()
    }

    fun tokenizeNormalized(normalizedQuery: String): List<String> {
        if (normalizedQuery.isEmpty()) return emptyList()
        val firstSpace = normalizedQuery.indexOf(' ')
        if (firstSpace < 0) return listOf(normalizedQuery)
        val tokens = ArrayList<String>(4)
        var start = 0
        val len = normalizedQuery.length
        while (start < len) {
            while (start < len && normalizedQuery[start] == ' ') start++
            if (start >= len) break
            var end = normalizedQuery.indexOf(' ', start)
            if (end < 0) end = len
            tokens.add(normalizedQuery.substring(start, end))
            start = end + 1
        }
        return tokens
    }

    fun matchesNormalizedTokens(normalizedTarget: String, tokens: List<String>): Boolean {
        for (i in tokens.indices) {
            if (!normalizedTarget.contains(tokens[i])) return false
        }
        return true
    }

    fun matchesQuery(targetText: String, query: String): Boolean {
        val normQuery = normalize(query)
        if (normQuery.isEmpty()) return true
        val normTarget = normalize(targetText)
        val tokens = tokenizeNormalized(normQuery)
        return matchesNormalizedTokens(normTarget, tokens)
    }
}
