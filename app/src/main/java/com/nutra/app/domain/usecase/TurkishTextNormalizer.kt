package com.nutra.app.domain.usecase

object TurkishTextNormalizer {
    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
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
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    fun matchesQuery(targetText: String, query: String): Boolean {
        val normQuery = normalize(query)
        if (normQuery.isEmpty()) return true
        val normTarget = normalize(targetText)
        val tokens = normQuery.split(' ').filter { it.isNotEmpty() }
        return tokens.all { token -> normTarget.contains(token) }
    }
}
