package com.nuvio.app.features.player

import android.content.res.Resources
import android.text.Spanned
import androidx.media3.common.text.Cue
import androidx.media3.ui.SubtitleView
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtitleOutlineTest {
    @Test
    fun testOutlineSpanPreservationInSubtitleView() {
        val context = RuntimeEnvironment.getApplication()
        val cue = Cue.Builder().setText("Sample subtitle line").build()
        val styledCue = cue.applyOutlineWidth(outlineEnabled = true, outlineWidth = 4)

        val text = styledCue.text as? Spanned
        assertNotNull(text)
        val spans = text.getSpans(0, text.length, OutlineSpan::class.java)
        assertEquals(1, spans.size)

        val density = Resources.getSystem().displayMetrics.density
        assertEquals((4 * density).coerceAtLeast(1f), spans[0].outlineWidth)

        // SubtitleView with applyEmbeddedStyles = false must not strip OutlineSpan
        val subtitleView = SubtitleView(context)
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setCues(listOf(styledCue))

        val method = SubtitleView::class.java.getDeclaredMethod("getCuesWithStylingPreferencesApplied")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val outputCues = method.invoke(subtitleView) as List<Cue>
        val outputText = outputCues[0].text as? Spanned
        assertNotNull(outputText)
        val outputSpans = outputText.getSpans(0, outputText.length, OutlineSpan::class.java)
        assertEquals(1, outputSpans.size)
        assertTrue(outputSpans[0] is OutlineSpan)
    }

    @Test
    fun testOutlineDisabledRemovesSpan() {
        val cue = Cue.Builder().setText("Subtitle line").build()
        val styledCue = cue.applyOutlineWidth(outlineEnabled = true, outlineWidth = 4)
        val disabledCue = styledCue.applyOutlineWidth(outlineEnabled = false, outlineWidth = 4)

        val text = disabledCue.text as? Spanned
        val spans = text?.getSpans(0, text.length, OutlineSpan::class.java) ?: emptyArray()
        assertEquals(0, spans.size)
    }

    @Test
    fun testOutlineZeroWidthRemovesSpan() {
        val cue = Cue.Builder().setText("Subtitle line").build()
        val disabledCue = cue.applyOutlineWidth(outlineEnabled = true, outlineWidth = 0)

        val text = disabledCue.text as? Spanned
        val spans = text?.getSpans(0, text.length, OutlineSpan::class.java) ?: emptyArray()
        assertEquals(0, spans.size)
    }

    @Test
    fun testReapplyingOutlineWidthDoesNotDuplicateSpans() {
        val cue = Cue.Builder().setText("Subtitle line").build()
        val styledCue1 = cue.applyOutlineWidth(outlineEnabled = true, outlineWidth = 2)
        val styledCue2 = styledCue1.applyOutlineWidth(outlineEnabled = true, outlineWidth = 8)

        val text = styledCue2.text as? Spanned
        assertNotNull(text)
        val spans = text.getSpans(0, text.length, OutlineSpan::class.java)
        assertEquals(1, spans.size)
        val density = Resources.getSystem().displayMetrics.density
        assertEquals((8 * density).coerceAtLeast(1f), spans[0].outlineWidth)
    }

    @Test
    fun testApplyOutlineWidthReflectionDoesNotCrash() {
        val context = RuntimeEnvironment.getApplication()
        val subtitleView = SubtitleView(context)
        subtitleView.applyOutlineWidthReflection(6f)
        // Ensure repeated calls and 0f do not throw
        subtitleView.applyOutlineWidthReflection(0f)
    }
}
