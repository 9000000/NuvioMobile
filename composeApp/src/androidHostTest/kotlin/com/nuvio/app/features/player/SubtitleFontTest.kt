package com.nuvio.app.features.player

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtitleFontTest {

    @Test
    fun testEnsureBundledSubtitleFontsExtractsFiles() {
        val context = RuntimeEnvironment.getApplication()
        ensureBundledSubtitleFonts(context)
        val fontsDir = File(context.filesDir, "fonts")
        assertTrue(fontsDir.exists())
    }
}
