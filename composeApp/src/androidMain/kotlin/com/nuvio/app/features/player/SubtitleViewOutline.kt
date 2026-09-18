package com.nuvio.app.features.player

import android.util.Log
import android.view.ViewTreeObserver
import androidx.media3.ui.SubtitleView
import java.lang.reflect.Field
import java.util.WeakHashMap

/**
 * Injects a custom outline (stroke) width into Media3's [SubtitleView] by patching
 * the internal [SubtitlePainter] instances via reflection.
 *
 * Media3 hardcodes outlineWidth = 2dp in SubtitlePainter and provides no public API to change it.
 * Painters are created lazily on draw passes when new cues arrive, so we attach an
 * [ViewTreeObserver.OnPreDrawListener] to guarantee that all active painters have the desired
 * outline width right before every draw pass.
 *
 * Reference: SubtitlePainter.java + CanvasSubtitleOutput.java in media3-ui 1.x
 */
private const val TAG = "SubtitleViewOutline"

/**
 * Stores the desired outline px per SubtitleView instance.
 */
private val subtitleViewDesiredOutlinePx = WeakHashMap<SubtitleView, Float>()

/**
 * Tracks attached OnPreDrawListener instances to avoid duplicate registration.
 */
private val subtitleViewPreDrawListeners = WeakHashMap<SubtitleView, ViewTreeObserver.OnPreDrawListener>()

/**
 * Sets the desired outline width on this [SubtitleView].
 *
 * This call is safe to invoke at any time.
 *
 * @param outlineWidthPx Desired stroke width in pixels. Pass 0 to disable outline.
 */
internal fun SubtitleView.applyOutlineWidthReflection(outlineWidthPx: Float) {
    subtitleViewDesiredOutlinePx[this] = outlineWidthPx

    // Ensure PreDraw hook is attached so newly spawned painters on cue updates are patched
    ensurePreDrawHookAttached()

    // Immediate patch for existing painters + redraw
    patchSubtitlePainters(outlineWidthPx, requestRedraw = true)
}

/**
 * Attaches an [ViewTreeObserver.OnPreDrawListener] to this [SubtitleView] if not already attached.
 */
private fun SubtitleView.ensurePreDrawHookAttached() {
    if (subtitleViewPreDrawListeners.containsKey(this)) return

    val listener = ViewTreeObserver.OnPreDrawListener {
        val desiredPx = subtitleViewDesiredOutlinePx[this]
        if (desiredPx != null) {
            // In pre-draw, patch painters without requesting redraw to avoid draw loops
            patchSubtitlePainters(desiredPx, requestRedraw = false)
        }
        true
    }

    viewTreeObserver?.let { observer ->
        if (observer.isAlive) {
            observer.addOnPreDrawListener(listener)
            subtitleViewPreDrawListeners[this] = listener
        }
    }

    // Also handle attach/detach cycles
    addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: android.view.View) {
            val vto = v.viewTreeObserver
            val l = subtitleViewPreDrawListeners[this@ensurePreDrawHookAttached]
            if (vto != null && vto.isAlive && l != null) {
                vto.removeOnPreDrawListener(l)
                vto.addOnPreDrawListener(l)
            }
        }

        override fun onViewDetachedFromWindow(v: android.view.View) {
            val vto = v.viewTreeObserver
            val l = subtitleViewPreDrawListeners[this@ensurePreDrawHookAttached]
            if (vto != null && vto.isAlive && l != null) {
                vto.removeOnPreDrawListener(l)
            }
        }
    })
}

/**
 * Directly patches all [SubtitlePainter] instances inside the [CanvasSubtitleOutput] of this view.
 */
private fun SubtitleView.patchSubtitlePainters(outlineWidthPx: Float, requestRedraw: Boolean) {
    try {
        val outputField = findFieldInHierarchy(SubtitleView::class.java, "output") ?: return
        outputField.isAccessible = true
        val output = outputField.get(this) ?: return

        val paintersField = findFieldInHierarchy(output.javaClass, "painters") ?: return
        paintersField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val painters = paintersField.get(output) as? List<*> ?: return

        if (painters.isEmpty()) return

        var patched = 0
        for (painter in painters) {
            if (painter == null) continue
            val owField = findFieldInHierarchy(painter.javaClass, "outlineWidth") ?: continue
            owField.isAccessible = true
            val current = owField.getFloat(painter)
            if (current != outlineWidthPx) {
                owField.setFloat(painter, outlineWidthPx)
                patched++
            }
        }
        if (patched > 0 && requestRedraw) {
            invalidate()
        }
    } catch (e: Exception) {
        Log.w(TAG, "patchSubtitlePainters failed: ${e.javaClass.simpleName}: ${e.message}")
    }
}

/**
 * Walks the class hierarchy (including superclasses) to find a declared field by [name].
 */
private fun findFieldInHierarchy(clazz: Class<*>?, name: String): Field? {
    var c: Class<*>? = clazz
    while (c != null && c != Any::class.java) {
        try {
            return c.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            c = c.superclass
        }
    }
    return null
}
