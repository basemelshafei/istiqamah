package com.basem.qiyam.shots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.basem.qiyam.MainActivity
import com.basem.qiyam.ui.Motion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * Renders every screen with sample data into app/build/screenshots (a typical 393dp phone),
 * in light and dark mode, so each build can be looked at before installing.
 */

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h2600dp-xxhdpi")
class PhoneScreenshots {
    init { Motion.live = false }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun allScreens() {
        Shots.seed(rule.activity)
        Shots.save(rule, "phone_1_today")
        Shots.tab(rule, "tab_deen"); Shots.save(rule, "phone_2_deen")
        Shots.tab(rule, "tab_body"); Shots.save(rule, "phone_3_body")
        Shots.tab(rule, "tab_career"); Shots.save(rule, "phone_4_career")
        Shots.tab(rule, "tab_wealth"); Shots.save(rule, "phone_5_wealth")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h2600dp-night-xxhdpi")
class PhoneScreenshotsDark {
    init { Motion.live = false }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun allScreens() {
        Shots.seed(rule.activity)
        Shots.save(rule, "dark_1_today")
        Shots.tab(rule, "tab_deen"); Shots.save(rule, "dark_2_deen")
        Shots.tab(rule, "tab_body"); Shots.save(rule, "dark_3_body")
        Shots.tab(rule, "tab_career"); Shots.save(rule, "dark_4_career")
        Shots.tab(rule, "tab_wealth"); Shots.save(rule, "dark_5_wealth")
    }
}

/** Largest-text check: the most common accessibility setting that breaks layouts. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h2600dp-xxhdpi", fontScale = 1.3f)
class LargeTextScreenshots {
    init { Motion.live = false }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun keyScreens() {
        Shots.seed(rule.activity)
        Shots.save(rule, "large_1_today")
        Shots.tab(rule, "tab_career"); Shots.save(rule, "large_4_career")
        Shots.tab(rule, "tab_wealth"); Shots.save(rule, "large_5_wealth")
    }
}

/** A small phone (320dp wide), where cramped layouts show first. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h2600dp-xhdpi")
class SmallPhoneScreenshots {
    init { Motion.live = false }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun today() {
        Shots.seed(rule.activity)
        Shots.save(rule, "small_1_today")
    }
}
