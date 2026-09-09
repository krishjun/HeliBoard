// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.latin.aiswipe.*
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowInputMethodManager

/** Exercises real InputLogic/RichInputConnection edits against the project's simulated editor. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodService::class, ShadowLocaleManagerCompat::class,
    AiSwipeInputMethodManager::class, ShadowKeyboardSwitcher::class, ShadowFacilitator2::class])
class AiSwipeEditorTest {
    private val ime = Robolectric.setupService(LatinIME::class.java)
    private val logic get() = ime.mInputLogic
    private val connection get() = logic.mConnection
    private val composerReader = InputLogic::class.java.getDeclaredField("mWordComposer").apply { isAccessible = true }
    private val composer get() = composerReader.get(logic) as WordComposer
    private val settings get() = Settings.getValues()

    @Before fun reset() {
        ShadowInputMethodService.reset()
        ime.prefs().edit().clear().commit()
        AiSwipeConsent.revoke(ime)
        ShadowFacilitator2.lastAddedWord = ""
        composer.reset()
        assertTrue(connection.resetCachesUponCursorMoveAndReturnSuccess(0, 0, true))
    }

    private fun swipe(word: String) {
        connection.beginBatchEdit()
        try {
            connection.commitText("I will see you ", 1)
            composer.setBatchInputWord(word)
            connection.setComposingText(word, 1)
        } finally { connection.endBatchEdit() }
    }

    @Test fun acceptanceAndUndoPreservePrefixAndComposingState() {
        swipe("son")
        assertTrue(logic.applyAiSwipeSuggestion("son", "soon at the usual place.", settings))
        assertEquals("I will see you soon at the usual place.", ShadowInputMethodService.text)
        assertFalse(composer.isComposingWord)
        assertEquals(ShadowInputMethodService.text.length, connection.expectedSelectionStart)
        assertEquals("", ShadowFacilitator2.lastAddedWord)
        assertTrue(logic.undoAiSwipeSuggestion("soon at the usual place.", "son", settings))
        assertEquals("I will see you son", ShadowInputMethodService.text)
        assertEquals("son", composer.typedWord)
        assertTrue(composer.isBatchMode)
        assertEquals("son", ShadowInputMethodService.composingText)
        assertEquals(0, ShadowInputMethodService.batchEdit)
    }

    @Test fun wrongOrNoLongerComposingWordCannotBeReplaced() {
        swipe("son")
        assertFalse(logic.applyAiSwipeSuggestion("soon", "other", settings))
        composer.reset()
        assertFalse(logic.applyAiSwipeSuggestion("son", "other", settings))
        assertEquals("I will see you son", ShadowInputMethodService.text)
    }

    @Test fun subsequentEditMakesOldUndoInapplicable() {
        swipe("son")
        assertTrue(logic.applyAiSwipeSuggestion("son", "soon", settings))
        connection.commitText("!", 1)
        assertFalse(logic.undoAiSwipeSuggestion("soon", "son", settings))
        assertEquals("I will see you soon!", ShadowInputMethodService.text)
    }

    @Test fun restoredPreferenceDoesNotGrantCloudConsent() {
        ime.prefs().edit().putBoolean(AI_SWIPE_ENABLED, true).commit()
        assertFalse(AiSwipeConsent.isGranted(ime))
        assertTrue(AiSwipeConsent.grant(ime))
        assertTrue(AiSwipeConsent.isGranted(ime))
        AiSwipeConsent.revoke(ime)
        assertFalse(AiSwipeConsent.isGranted(ime))
    }
}

/** Both separately installable package IDs must resolve to the actual test application. */
@Implements(InputMethodManager::class)
class AiSwipeInputMethodManager : ShadowInputMethodManager() {
    @Implementation override fun getInputMethodList() = listOf(
        InputMethodInfo(BuildConfig.APPLICATION_ID, "LatinIME", "AI swipe test keyboard", null))
    @Implementation fun getShortcutInputMethodsAndSubtypes() = emptyMap<InputMethodInfo, List<InputMethodSubtype>>()
    @Implementation override fun getCurrentInputMethodSubtype() = InputMethodSubtype.InputMethodSubtypeBuilder()
        .setSubtypeLocale("en").build()
}
