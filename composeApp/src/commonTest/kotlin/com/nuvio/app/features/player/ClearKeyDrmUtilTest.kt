package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClearKeyDrmUtilTest {

    @Test
    fun returnsNullForBlankInput() {
        assertNull(ClearKeyDrmUtil.buildClearKeyJson(""))
        assertNull(ClearKeyDrmUtil.buildClearKeyJson("   "))
    }

    @Test
    fun returnsRawJsonWhenAlreadyW3cFormat() {
        val w3cJson = """{"keys":[{"kty":"oct","k":"MDEyMzQ1Njc4OWFiY2RlZg","kid":"MDEyMzQ1Njc4OWFiY2RlZg"}],"type":"temporary"}"""
        val result = ClearKeyDrmUtil.buildClearKeyJson(w3cJson)
        assertEquals(w3cJson, result)
    }

    @Test
    fun parsesSingleHexPair() {
        // Hex: 0123456789abcdef0123456789abcdef (16 bytes)
        val kidHex = "0123456789abcdef0123456789abcdef"
        val keyHex = "fedcba9876543210fedcba9876543210"
        val input = "$kidHex:$keyHex"

        val json = ClearKeyDrmUtil.buildClearKeyJson(input)
        assertNotNull(json)
        assertTrue(json.contains("\"keys\":["), "JSON should contain keys array")
        assertTrue(json.contains("\"kty\":\"oct\""), "Entry should have oct kty")
        assertTrue(json.contains("\"type\":\"temporary\""), "JSON should contain temporary type")
        assertTrue(json.contains("\"kid\":"), "Entry should contain kid")
        assertTrue(json.contains("\"k\":"), "Entry should contain k")
    }

    @Test
    fun parsesMultipleHexPairsSeparatedByCommaOrSemicolon() {
        val pair1 = "11111111111111111111111111111111:22222222222222222222222222222222"
        val pair2 = "33333333333333333333333333333333:44444444444444444444444444444444"
        val input = "$pair1,$pair2"

        val json = ClearKeyDrmUtil.buildClearKeyJson(input)
        assertNotNull(json)
        assertTrue(json.contains("EREREREREREREREREREREQ"), "Should contain base64url for 1111...")
        assertTrue(json.contains("IiIiIiIiIiIiIiIiIiIiIg"), "Should contain base64url for 2222...")
        assertTrue(json.contains("MzMzMzMzMzMzMzMzMzMzMw"), "Should contain base64url for 3333...")
        assertTrue(json.contains("RERERERERERERERERERERA"), "Should contain base64url for 4444...")
    }

    @Test
    fun stripsHyphensInHexUuid() {
        val kidUuid = "01234567-89ab-cdef-0123-456789abcdef"
        val keyUuid = "fedcba98-7654-3210-fedc-ba9876543210"
        val input = "$kidUuid:$keyUuid"

        val json = ClearKeyDrmUtil.buildClearKeyJson(input)
        assertNotNull(json)
        assertTrue(json.contains("\"kid\":"), "Should handle UUID formatted hex")
    }
}
