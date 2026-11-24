package com.shod.client

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import com.shod.client.ui.theme.ShodTheme
import org.junit.Rule
import org.junit.Test

/** Smoke test for the current Compose theme; does not depend on removed V2 UI modules. */
class CurrentUiSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun currentThemeRendersContent() {
        compose.setContent {
            ShodTheme(themeMode = "dark") {
                Text("Shod CI smoke")
            }
        }

        compose.onNodeWithText("Shod CI smoke").assertIsDisplayed()
    }
}
