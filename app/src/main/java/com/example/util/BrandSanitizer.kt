package com.example.util

/**
 * Ensures no third-party brand labels ("Gemini", "Google", "Alphabet") appear in user-visible
 * UI text, spoken voice transcripts, or error banners, keeping the experience exclusively branded
 * under JASHX AI & KRS Gold & Silver.
 */
object BrandSanitizer {

    private val forbiddenBrandReplacements: List<Pair<Regex, String>> = listOf(
        Regex("""(?i)\bpowered\s+by\s+google\s+gemini(?:\s+\d+(?:\.\d+)?(?:\s+(?:flash|pro|live|lite|argon))?)?\b""") to "powered by JASHX AI",
        Regex("""(?i)\bgoogle\s+gemini(?:\s+\d+(?:\.\d+)?(?:\s+(?:flash|pro|live|lite|argon))?)?\b""") to "JASHX AI",
        Regex("""(?i)\bgemini(?:\s+\d+(?:\.\d+)?(?:\s+(?:flash|pro|live|lite|argon))?)?\b""") to "JASHX AI",
        Regex("""(?i)\bgoogle\s+search\b""") to "Live Web Search",
        Regex("""(?i)\bgoogle\s+news\b""") to "Live News Desk",
        Regex("""(?i)\bgoogle\s+cloud\b""") to "Cloud Infrastructure",
        Regex("""(?i)\bgoogle\s+account\b""") to "Cloud Account",
        Regex("""(?i)\bgoogle\s+sign[- ]?in\b""") to "One-Tap Cloud Sign-In",
        Regex("""(?i)\bgoogle\s+play\b""") to "App Services",
        Regex("""(?i)\bgoogle\b""") to "Cloud",
        Regex("""(?i)\balphabet\s+inc\.?""") to "Major Tech Holdings",
        Regex("""(?i)\bJASHX AI\s*\(\s*JASHX AI\s*\)""") to "JASHX AI",
        Regex("""(?i)\bJASHX AI\s+JASHX AI\b""") to "JASHX AI"
    )

    fun sanitizeForUserDisplay(text: String): String {
        if (text.isBlank()) return text
        var result = text
        for ((pattern, replacement) in forbiddenBrandReplacements) {
            result = pattern.replace(result, replacement)
        }
        return result
    }
}
