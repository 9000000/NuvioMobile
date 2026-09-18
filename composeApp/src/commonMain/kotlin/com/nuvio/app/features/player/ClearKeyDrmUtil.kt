package com.nuvio.app.features.player

object ClearKeyDrmUtil {

    /**
     * Parses a ClearKey DRM key string into a W3C ClearKey JSON response.
     * Supported formats:
     * 1. KID:KEY (Hex format, e.g., "e7b9e078...:a38f...").
     * 2. Multiple KID:KEY pairs separated by comma or semicolon.
     * 3. Pre-formatted W3C JSON: `{"keys":[{"kty":"oct","k":"...","kid":"..."}]}`.
     *
     * Returns null if key cannot be parsed.
     */
    fun buildClearKeyJson(drmKey: String): String? {
        val trimmed = drmKey.trim()
        if (trimmed.isEmpty()) return null

        // If it's already a W3C JSON format, return as is
        if (trimmed.startsWith("{") && trimmed.contains("\"keys\"")) {
            return trimmed
        }

        // Parse key pairs: KID:KEY
        val pairs = trimmed.split(',', ';').map(String::trim).filter(String::isNotBlank)
        val keyEntries = mutableListOf<String>()

        for (pair in pairs) {
            val parts = pair.split(':')
            if (parts.size == 2) {
                val kidHex = parts[0].trim().replace("-", "")
                val keyHex = parts[1].trim().replace("-", "")
                if (kidHex.isNotEmpty() && keyHex.isNotEmpty()) {
                    val kidB64 = hexToBase64Url(kidHex) ?: continue
                    val keyB64 = hexToBase64Url(keyHex) ?: continue
                    keyEntries += """{"kty":"oct","k":"$keyB64","kid":"$kidB64"}"""
                }
            }
        }

        if (keyEntries.isEmpty()) return null
        return """{"keys":[${keyEntries.joinToString(",")}],"type":"temporary"}"""
    }

    private fun hexToBase64Url(hex: String): String? {
        val clean = hex.trim()
        if (clean.length % 2 != 0) return null
        val bytes = ByteArray(clean.length / 2)
        for (i in clean.indices step 2) {
            val d1 = clean[i].digitToIntOrNull(16) ?: return null
            val d2 = clean[i + 1].digitToIntOrNull(16) ?: return null
            bytes[i / 2] = ((d1 shl 4) or d2).toByte()
        }
        return base64UrlEncode(bytes)
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1

            val c0 = b0 ushr 2
            val c1 = ((b0 and 0x03) shl 4) or (if (b1 != -1) b1 ushr 4 else 0)
            sb.append(table[c0])
            sb.append(table[c1])

            if (b1 != -1) {
                val c2 = ((b1 and 0x0F) shl 2) or (if (b2 != -1) b2 ushr 6 else 0)
                sb.append(table[c2])
            }
            if (b2 != -1) {
                val c3 = b2 and 0x3F
                sb.append(table[c3])
            }
            i += 3
        }
        return sb.toString()
    }
}
