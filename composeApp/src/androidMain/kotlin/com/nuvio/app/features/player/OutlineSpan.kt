package com.nuvio.app.features.player

import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import androidx.annotation.Px
import androidx.media3.common.text.Cue

import androidx.media3.common.text.LanguageFeatureSpan

/**
 * Custom [CharacterStyle] that overrides [TextPaint.strokeWidth] during SubtitlePainter draw passes.
 * When CaptionStyleCompat has EDGE_TYPE_OUTLINE, SubtitlePainter draws the stroke layout first.
 * The StaticLayout invokes updateDrawState on all spans, allowing this span to dynamically
 * control the outline stroke width on ExoPlayer.
 *
 * It implements [LanguageFeatureSpan] so that [androidx.media3.ui.SubtitleView] does not strip
 * this span when [androidx.media3.ui.SubtitleView.setApplyEmbeddedStyles] is false.
 *
 * Reference: https://github.com/androidx/media/pull/1840 & NuvioTV / Cloudstream Subtitle engine
 */
class OutlineSpan(@param:Px val outlineWidth: Float) : CharacterStyle(), LanguageFeatureSpan {
    override fun updateDrawState(tp: TextPaint?) {
        tp?.strokeWidth = outlineWidth
        tp?.strokeJoin = android.graphics.Paint.Join.ROUND
    }
}

/**
 * Applies or removes [OutlineSpan] from a [Cue]'s text.
 */
internal fun Cue.applyOutlineWidth(outlineEnabled: Boolean, outlineWidth: Int): Cue {
    val cueText = text ?: return this
    val spannable = if (cueText is Spannable) cueText else SpannableString.valueOf(cueText)
    val existingSpans = spannable.getSpans(0, spannable.length, OutlineSpan::class.java)
    for (span in existingSpans) {
        spannable.removeSpan(span)
    }
    if (outlineEnabled && outlineWidth > 0 && spannable.isNotEmpty()) {
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        val strokeWidthPx = (outlineWidth * density).coerceAtLeast(1f)
        spannable.setSpan(
            OutlineSpan(strokeWidthPx),
            0,
            spannable.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return buildUpon().setText(spannable).build()
}
