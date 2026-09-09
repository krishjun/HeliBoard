// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.settings.SettingsContainer
import helium314.keyboard.settings.screens.createGestureTypingSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiSwipeSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun sentenceConsentIsRegisteredWithoutNativeGestureLibrary() {
        val original = JniUtils.sHaveGestureLib
        try {
            JniUtils.sHaveGestureLib = false
            val registry = SettingsContainer(context)
            assertEquals(BuildConfig.AI_SWIPE_AVAILABLE, registry[AI_SWIPE_ENABLED] != null)
            assertNull(registry[Settings.PREF_GESTURE_INPUT])
            val keys = createGestureTypingSettings(context).map { it.key }
            assertEquals(if (BuildConfig.AI_SWIPE_AVAILABLE) listOf(AI_SWIPE_ENABLED, AI_SWIPE_SENTENCES)
                else emptyList<String>(), keys)
        } finally { JniUtils.sHaveGestureLib = original }
    }

    @Test fun nativeGlideSettingsAreRetainedWhenLibraryExists() {
        val original = JniUtils.sHaveGestureLib
        try {
            JniUtils.sHaveGestureLib = true
            val keys = createGestureTypingSettings(context).map { it.key }
            assertTrue(Settings.PREF_GESTURE_INPUT in keys)
            assertEquals(BuildConfig.AI_SWIPE_AVAILABLE, AI_SWIPE_ENABLED in keys)
            assertEquals(keys.size, keys.distinct().size)
        } finally { JniUtils.sHaveGestureLib = original }
    }
}
