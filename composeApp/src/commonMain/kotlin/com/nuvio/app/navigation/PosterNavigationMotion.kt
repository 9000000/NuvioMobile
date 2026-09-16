package com.nuvio.app.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.nuvio.app.core.ui.LocalLowEndPerformanceMode
import com.nuvio.app.core.ui.PosterOpenMotion
import kotlinx.coroutines.flow.first

@Composable
internal fun PosterNavigationMotion(
    request: PosterNavigationRequest?,
    incoming: Boolean,
    outgoing: Boolean,
    state: PosterNavigationState,
) {
    val transition = LocalNavAnimatedContentScope.current.transition
    var retainedIncoming by remember { mutableStateOf<Boolean?>(null) }
    val animationIncoming = if (incoming || outgoing) incoming else retainedIncoming
    if (animationIncoming == null) return

    val isLowEnd = LocalLowEndPerformanceMode.current
    val motionDuration = if (isLowEnd) 0 else PosterOpenMotion.DurationMillis
    val elapsed = transition.animateFloat(
        transitionSpec = { tween(motionDuration, easing = LinearEasing) },
        label = "posterNavigationElapsed",
    ) { visibility -> elapsedTarget(animationIncoming, visibility, isLowEnd) }

    SideEffect {
        if (incoming || outgoing) retainedIncoming = incoming
        if (incoming) request?.clock = elapsed
    }

    LaunchedEffect(request, incoming) {
        if (incoming && request != null) {
            snapshotFlow { request.elapsedMillis }.first { it >= PosterOpenMotion.ContentDelayMillis }
            request.revealContent()
        }
    }

    LaunchedEffect(request, incoming, outgoing, transition.currentState, transition.targetState, transition.isRunning, isLowEnd) {
        val settled = transition.currentState == transition.targetState && !transition.isRunning &&
            elapsed.value == elapsedTarget(animationIncoming, transition.targetState, isLowEnd)
        if (settled) {
            if (incoming && transition.targetState == EnterExitState.Visible && request != null) {
                state.complete(request)
            }
            if (!incoming && !outgoing) retainedIncoming = null
        }
    }
}

private fun elapsedTarget(incoming: Boolean, visibility: EnterExitState, isLowEnd: Boolean = false): Float {
    if (isLowEnd) return 0f
    val atStart = if (incoming) visibility == EnterExitState.PreEnter else visibility == EnterExitState.Visible
    return if (atStart) 0f else PosterOpenMotion.DurationMillis.toFloat()
}
