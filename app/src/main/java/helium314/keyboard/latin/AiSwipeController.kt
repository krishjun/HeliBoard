// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.view.View
import android.view.accessibility.AccessibilityManager
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.MainKeyboardView
import android.view.inputmethod.InputConnection
import helium314.keyboard.latin.aiswipe.*
import helium314.keyboard.latin.inputlogic.aiSwipeWord
import helium314.keyboard.latin.inputlogic.isAiSwipeBatch
import helium314.keyboard.latin.inputlogic.isAiSwipeComposing
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
    private var sentenceMode = false
    private var sentenceTouch: AiSwipeSentenceTouch? = null
    private var sentence: Sentence? = null
    private var decoding: Job? = null
    private class Sentence(val stamp: AiSwipeEditStamp, val connection: InputConnection, val keyboard: Keyboard)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> invalidate() }

    private class Pending(val stamp: AiSwipeEditStamp, val connection: InputConnection,
                          val word: String, val words: SuggestedWords, val request: AiSwipeRequest)
    private class Undo(val stamp: AiSwipeEditStamp, val connection: InputConnection,
                       val inserted: String, val original: String, val words: SuggestedWords)

    // LatinIME constructs this object before attaching a Context; do not initialize prefs earlier.
    fun attach(view: View) {
        if (!BuildConfig.AI_SWIPE_AVAILABLE) return
        sentenceTouch?.close()
        bar = view.findViewById(R.id.ai_swipe_bar)
        val keyboardView = view.findViewById<MainKeyboardView>(R.id.keyboard_view)
        sentenceTouch = keyboardView?.let { keyboard ->
            AiSwipeSentenceTouch(keyboard, { sentenceMode && eligible() && !touchExploration() },
                ::beginSentence, ::endSentence) { tooLong ->
                invalidate()
                if (eligible()) bar?.showStatus(if (tooLong) R.string.ai_sentence_too_long else R.string.ai_sentence_cancelled)
            }
        }
        bar?.setMode(sentenceMode, ::toggleMode)
        if (preferences == null) {
            preferences = ime.prefs().also { it.registerOnSharedPreferenceChangeListener(preferenceListener) }
        }
        invalidate()
    }

    fun startSession() {
        session++
        sentenceMode = false
        active = false
        invalidate()
    }

    fun startView() {
        active = true
        // Sentence capture does not depend on the optional proprietary word-glide library.
        if (!JniUtils.sHaveGestureLib) sentenceMode = true
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
        sentenceTouch?.close()
        sentenceTouch = null
        preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferences = null
        bar = null
    }

    fun invalidate() {
        revision++
        engine?.invalidate()
        decoding?.cancel(); decoding = null
        sentenceTouch?.cancel()
        pending = null
        sentence = null
        undo = null
        bar?.setMode(sentenceMode, ::toggleMode)
        if (eligible()) bar?.showStatus(if (sentenceMode) R.string.ai_sentence_hint else R.string.ai_swipe_hint)
        else bar?.hide()
    }

    fun selectionChanged(start: Int, end: Int) {
        val cursor = sentence?.stamp?.cursor ?: pending?.stamp?.cursor ?: undo?.stamp?.cursor ?: return
        if (start != cursor || end != cursor) invalidate()
    }

    private fun eligible(): Boolean {
        if (!BuildConfig.AI_SWIPE_AVAILABLE || !active || !ime.isInputViewShown ||
            !AiSwipeProviderFactory.isConfigured()) return false
        val sv = ime.mSettings.current
        val editor = ime.currentInputEditorInfo ?: return false
        val locked = sv.mIsLocked ||
            (ime.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true ||
            (Build.VERSION.SDK_INT >= 24 &&
                (ime.getSystemService(Context.USER_SERVICE) as? UserManager)?.isUserUnlocked != true)
        // Field gate precedes consent disk checks and, most importantly, ALL editor reads.
        if (!AiSwipePolicy.allowsField(editor.inputType, editor.imeOptions, true,
                sv.mIncognitoModeEnabled, locked)) return false
        return !ime.isEmojiSearch && AiSwipeConsent.isGranted(ime)
    }

    fun onGestureResult(words: SuggestedWords) {
        invalidate()
        if (sentenceMode || !eligible() || !JniUtils.sHaveGestureLib ||
            !ime.mSettings.current.mGestureInputEnabled || words.isEmpty || !ime.mInputLogic.isAiSwipeBatch) return
        val original = ime.mInputLogic.aiSwipeWord
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
        val before = if (stamp.before.isEmpty()) "" else
            connection.getTextBeforeCursor(stamp.before.length, 0)?.toString() ?: return false
        val after = connection.getTextAfterCursor(1, 0)?.toString() ?: return false
        return stamp.matches(session, revision, rich.expectedSelectionStart, rich.expectedSelectionEnd, before, after)
    }

    private fun valid(value: Pending): Boolean = pending === value && matches(value.stamp, value.connection) &&
        ime.mInputLogic.isAiSwipeBatch && ime.mInputLogic.aiSwipeWord == value.word

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
            ime.mInputLogic.isAiSwipeComposing) { invalidate(); return }
        undo = null
        revision++
        if (if (value.original.isEmpty()) ime.mInputLogic.undoAiSwipeSentence(value.inserted)
            else ime.mInputLogic.undoAiSwipeSuggestion(value.inserted, value.original, ime.mSettings.current)) {
            ime.setSuggestions(value.words)
            ime.mKeyboardSwitcher.updateShiftState(ime.currentAutoCapsState, ime.currentRecapitalizeState)
        }
        invalidate()
    }

    private fun touchExploration() = (ime.getSystemService(Context.ACCESSIBILITY_SERVICE)
        as? AccessibilityManager)?.isTouchExplorationEnabled == true

    private fun toggleMode() {
        sentenceMode = !sentenceMode
        invalidate()
    }

    private fun beginSentence(keyboard: Keyboard): Boolean {
        invalidate()
        if (!sentenceMode || !eligible()) return false
        val rich = ime.mInputLogic.mConnection
        val connection = ime.currentInputConnection ?: return false
        if (!rich.isCursorPositionKnown || rich.hasSelection() ||
            connection.getTextAfterCursor(1, 0)?.isEmpty() != true) {
            bar?.showStatus(R.string.ai_sentence_end_only)
            return false
        }
        val before = connection.getTextBeforeCursor(AiSwipeRequest.MAX_CONTEXT, 0)?.toString() ?: return false
        if (!AiSwipePolicy.allowsContext(before)) {
            bar?.showStatus(R.string.ai_sentence_private)
            return false
        }
        sentence = Sentence(AiSwipeEditStamp(session, revision, rich.expectedSelectionStart, before), connection, keyboard)
        bar?.showStatus(R.string.ai_sentence_drawing)
        // No composer changes, native decoding, or network requests while the finger is down.
        return true
    }

    private fun validSentence(value: Sentence): Boolean = sentence === value && sentenceMode &&
        ime.mKeyboardSwitcher.keyboard === value.keyboard && matches(value.stamp, value.connection)

    private fun endSentence(keys: List<AiSwipeTraceKey>, points: List<AiSwipeTracePoint>) {
        val snapshot = sentence ?: return
        if (!validSentence(snapshot)) { invalidate(); return }
        bar?.showStatus(R.string.ai_sentence_working)
        decoding = scope.launch {
            val trace = withContext(Dispatchers.Default) { AiSwipeTrace(keys, AiSwipeTrace.simplify(points)) }
            if (!validSentence(snapshot)) return@launch
            val request = AiSwipeRequest(AiSwipeRequest.contextTail(snapshot.stamp.before), emptyList(),
                ime.mSettings.current.mLocale.toLanguageTag().take(64), false, trace)
            val predictor = engine ?: AiSwipeProviderFactory.create(ime)?.let {
                AiSwipeEngine(scope, it, SystemClock::elapsedRealtime).also { created -> engine = created }
            } ?: return@launch
            predictor.submit(request, { validSentence(snapshot) }, { status ->
                bar?.showStatus(when (status) {
                    AiSwipeStatus.WORKING -> R.string.ai_sentence_working
                    AiSwipeStatus.UNAVAILABLE -> R.string.ai_sentence_unavailable
                    AiSwipeStatus.RATE_LIMITED -> R.string.ai_sentence_paused
                    AiSwipeStatus.READY -> R.string.ai_sentence_hint
                })
            }) { result ->
                if (result.alternatives.isEmpty()) bar?.showStatus(R.string.ai_sentence_no_match)
                else bar?.showSentences(result.alternatives) { text -> acceptSentence(snapshot, text, request.locale) }
            }
        }
    }

    private fun acceptSentence(value: Sentence, text: String, locale: String) {
        if (!validSentence(value) || !AiSwipeTrace.validSentence(text)) { invalidate(); return }
        val inserted = AiSwipeTrace.insertion(value.stamp.before, text, locale)
        sentence = null
        engine?.invalidate()
        revision++
        if (!ime.mInputLogic.applyAiSwipeSentence(value.stamp.before, inserted, ime.mSettings.current)) {
            invalidate(); return
        }
        ime.setSuggestions(SuggestedWords.getEmptyInstance())
        // Updating shift may change the keyboard object; the undo snapshot is text/editor-based.
        ime.mKeyboardSwitcher.updateShiftState(ime.currentAutoCapsState, ime.currentRecapitalizeState)
        val stamp = AiSwipeEditStamp(session, revision, value.stamp.cursor + inserted.length, value.stamp.before + inserted)
        if (!matches(stamp, value.connection)) { invalidate(); return }
        val accepted = Undo(stamp, value.connection, inserted, "", SuggestedWords.getEmptyInstance())
        undo = accepted
        bar?.showUndo { undo(accepted) }
    }

}
