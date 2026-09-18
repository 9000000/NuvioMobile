package com.nuvio.app.core.ui

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FloatingNavigationBarLowEndTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lowEndModeDisablesMovingBubbleAndRendersStaticItems() {
        var selectedIndex by mutableIntStateOf(0)
        val clicks = mutableListOf<Int>()

        compose.setContent {
            CompositionLocalProvider(LocalLowEndPerformanceMode provides true) {
                NuvioTheme(lowEndPerformanceMode = true) {
                    val items = listOf(
                        FloatingNavigationItem(
                            label = "Home",
                            selected = selectedIndex == 0,
                            onClick = {
                                clicks += 0
                                selectedIndex = 0
                            },
                            icon = Icons.Default.Home,
                        ),
                        FloatingNavigationItem(
                            label = "Profile",
                            selected = selectedIndex == 1,
                            onClick = {
                                clicks += 1
                                selectedIndex = 1
                            },
                            icon = Icons.Default.Person,
                        ),
                    )
                    FloatingNavigationBar(items = items)
                }
            }
        }

        compose.onNodeWithContentDescription("Profile").performClick()
        compose.runOnIdle {
            assertEquals(1, selectedIndex)
            assertEquals(listOf(1), clicks)
        }

        compose.onNodeWithContentDescription("Home").performClick()
        compose.runOnIdle {
            assertEquals(0, selectedIndex)
            assertEquals(listOf(1, 0), clicks)
        }
    }
}
