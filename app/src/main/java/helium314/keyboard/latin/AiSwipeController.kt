// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.view.View
import android.view.inputmethod.InputConnection
import helium314.keyboard.latin.aiswipe.*
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.*

/** Lives with one IME. All editor access and state changes happen on its main thread. */
class AiSwipeController(private val ime: LatinIME) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: AiSwipeEngine? = null
    private var bar: AiSwipeSuggestionBar? = null
    private var preferences: SharedPreferences? = null
    private var session = 0L
    private var revision = 0L
    private var active = false
    private var pending: Pending? = null
    private var undo: Undo? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> invalidate() }

    private class Pending(val stamp: AiSwipeEditStamp, val connection: InputConnection,
                          val word: String, val words: SuggestedWords, val request: AiSwipeRequest)
    private class Undo(val stamp: AiSwipeEditStamp, val connection: InputConnection,
                       val inserted: String, val original: String, val words: SuggestedWords)

    // LatinIME constructs this object before attaching a Context; do not initialize prefs earlier.
    fun attach(view: View) {
        if (!BuildConfig.AI_SWIPE_AVAILABLE) return
        bar = view.findViewById(R.id.ai_swipe_bar)
        if (preferences == null) {
            preferences = ime.prefs().also { it.registerOnSharedPreferenceChangeListener(preferenceListener) }
        }
        invalidate()
    }

    fun startSession() {
        session++
        active = false
        invalidate()
    }

    fun startView() {
        active = true
        invalidate()
    }

    fun finish() {
        active = false
        session++
        invalidate()
    }

    fun close() {
        finish()
        scope.cancel()
        preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferences = null
        bar = null
    }

    fun invalidate() {
        revision++
        engine?.invalidate()
        pending = null
        undo = null
        if (eligible()) bar?.showStatus(R.string.ai_swipe_hint) else bar?.hide()
    }

    fun selectionChanged(start: Int, end: Int) {
        val cursor = pending?.stamp?.cursor ?: undo?.stamp?.cursor ?: return
        if (start != cursor || end != cursor) invalidate()
    }

    private fun eligible(): Boolean {
        if (!BuildConfig.AI_SWIPE_AVAILABLE || !active || !ime.isInputViewShown ||
            !AiSwipeProviderFactory.isConfigured() || !JniUtils.sHaveGestureLib) return false
        val sv = ime.mSettings.current
        val editor = ime.currentInputEditorInfo ?: return false
        val locked = sv.mIsLocked ||
            (ime.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true ||
            (Build.VERSION.SDK_INT >= 24 &&
                (ime.getSystemService(Context.USER_SERVICE) as? UserManager)?.isUserUnlocked != true)
        // Field gate precedes consent disk checks and, most importantly, ALL editor reads.
        if (!AiSwipePolicy.allowsField(editor.inputType, editor.imeOptions, true,
                sv.mIncognitoModeEnabled, locked)) return false
        return sv.mGestureInputEnabled && !ime.isEmojiSearch && AiSwipeConsent.isGranted(ime)
    }

    fun onGestureResult(words: SuggestedWords) {
        invalidate()
        if (!eligible() || words.isEmpty || !ime.mInputLogic.mWordComposer.isBatchMode) return
        val composer = ime.mInputLogic.mWordComposer
        val original = composer.typedWord
        if (!AiSwipeRequest.isSwipeWord(original)) return
        val rich = ime.mInputLogic.mConnection
        if (!rich.isCursorPositionKnown || rich.hasSelection()) return
        val connection = ime.currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(AiSwipeRequest.MAX_CONTEXT + AiSwipeRequest.MAX_WORD, 0)
            ?.toString() ?: return
        if (!before.endsWith(original) || connection.getTextAfterCursor(1, 0)?.isEmpty() != true) return
        if (!AiSwipePolicy.allowsContext(before)) return
        val candidates = (listOf(original) + (0 until words.size()).map { words.getWord(it) })
            .filter(AiSwipeRequest::isSwipeWord).distinct().take(AiSwipeRequest.MAX_CANDIDATES)
        val request = AiSwipeRequest(AiSwipeRequest.contextTail(before.dropLast(original.length)),
            candidates, ime.mSettings.current.mLocale.toLanguageTag().take(64),
            preferences?.getBoolean(AI_SWIPE_SENTENCES, true) == true)
        val snapshot = Pending(AiSwipeEditStamp(session, revision, rich.expectedSelectionStart, before),
            connection, original, words, request)
        pending = snapshot
        val predictor = engine ?: AiSwipeProviderFactory.create(ime)?.let {
            AiSwipeEngine(scope, it, SystemClock::elapsedRealtime).also { created -> engine = created }
        } ?: return
        predictor.submit(request, { valid(snapshot) }, { status ->
            bar?.showStatus(when (status) {
                AiSwipeStatus.WORKING -> R.string.ai_swipe_working
                AiSwipeStatus.UNAVAILABLE -> R.string.ai_swipe_unavailable
                AiSwipeStatus.RATE_LIMITED -> R.string.ai_swipe_paused
                AiSwipeStatus.READY -> R.string.ai_swipe_hint
            })
        }) { result ->
            if (result.word == original && result.continuation.isEmpty()) {
                bar?.showStatus(R.string.ai_swipe_no_completion)
            } else {
                val phrase = result.joined(request.locale)
                bar?.showSuggestions(result.word, phrase.takeIf { result.continuation.isNotEmpty() },
                    { accept(snapshot, result.word) }, { accept(snapshot, phrase) })
            }
        }
    }

    private fun matches(stamp: AiSwipeEditStamp, connection: InputConnection): Boolean {
        if (!eligible() || ime.currentInputConnection !== connection) return false
        val rich = ime.mInputLogic.mConnection
        val before = connection.getTextBeforeCursor(stamp.before.length, 0)?.toString() ?: return false
        val after = connection.getTextAfterCursor(1, 0)?.toString() ?: return false
        return stamp.matches(session, revision, rich.expectedSelectionStart, rich.expectedSelectionEnd, before, after)
    }

    private fun valid(value: Pending): Boolean = pending === value && matches(value.stamp, value.connection) &&
        ime.mInputLogic.mWordComposer.isBatchMode && ime.mInputLogic.mWordComposer.typedWord == value.word

    private fun accept(value: Pending, replacement: String) {
        if (!valid(value)) { invalidate(); return }
        // Every edit is an explicit UI action. No IME action or send key is invoked.
        pending = null
        engine?.invalidate()
        revision++
        if (!ime.mInputLogic.applyAiSwipeSuggestion(value.word, replacement, ime.mSettings.current)) {
            invalidate()
            return
        }
        ime.setSuggestions(SuggestedWords.getEmptyInstance())
        ime.mKeyboardSwitcher.updateShiftState(ime.currentAutoCapsState, ime.currentRecapitalizeState)
        val before = value.stamp.before.dropLast(value.word.length) + replacement
        val stamp = AiSwipeEditStamp(session, revision,
            value.stamp.cursor - value.word.length + replacement.length, before)
        // An editor may reject a commit. Do not offer destructive undo unless the actual edit is visible.
        if (!matches(stamp, value.connection)) { invalidate(); return }
        val accepted = Undo(stamp, value.connection, replacement, value.word, value.words)
        undo = accepted
        bar?.showUndo { undo(accepted) }
    }

    private fun undo(value: Undo) {
        if (undo !== value || !matches(value.stamp, value.connection) ||
            ime.mInputLogic.mWordComposer.isComposingWord) { invalidate(); return }
        undo = null
        revision++
        if (ime.mInputLogic.undoAiSwipeSuggestion(value.inserted, value.original, ime.mSettings.current)) {
            ime.setSuggestions(value.words)
            ime.mKeyboardSwitcher.updateShiftState(ime.currentAutoCapsState, ime.currentRecapitalizeState)
        }
        invalidate()
    }
}
