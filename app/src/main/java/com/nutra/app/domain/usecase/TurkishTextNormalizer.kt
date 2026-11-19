package com.nutra.app.domain.usecase

object TurkishTextNormalizer {
    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder(input.length)
        var prevSpace = true
        for (i in 0 until input.length) {
            val ch = input[i]
            if (ch.isWhitespace()) {
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
