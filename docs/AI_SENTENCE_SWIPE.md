# Continuous sentence swipe (experimental)

This mode decodes one continuous finger-down gesture as a phrase or sentence. It does not stitch together separate lifted word swipes, and it is not restricted to the native decoder's word candidates. It uses the existing Firebase AI Logic configuration and Gemini Flash-Lite provider described in [AI_SWIPE.md](AI_SWIPE.md).

## Use

Enable **Settings > Glide typing > AI swipe assistant** in a configured cloud build. Existing users must accept the updated disclosure because sentence requests include gesture evidence in addition to draft text. Choose **Sentence swipe** in the keyboard's AI row. Without the optional word-glide library, this is the initial mode. The button then reads **Word swipe**, which switches back.

Place the cursor at the end of an ordinary text field, without selecting text. Touch the first letter, glide across the letters of all the words without lifting, and lift only after the last word. A pass over the space bar is an optional word-boundary hint; it is not a required delimiter. Pauses and direction changes provide evidence but are not mandatory separators.

The keyboard shows the trace without committing a guessed word. Only the final finger-up submits the completed gesture. Up to three distinct interpretations appear as horizontally scrollable cards. Tap one to see its complete, vertically scrollable text, then tap **Insert**. **Other versions** returns to the alternatives. Nothing is automatically sent. **Undo AI insertion** removes only the accepted insertion (including any added boundary space), while editor/cursor/text still match. Typing, moving the cursor, or starting another gesture discards the pending alternatives or undo.

A gesture with letters corresponding to `nowhere` can support `Nowhere.` or `Now here.`. The word-boundary test demonstrates both options can be retained. This is a synthetic test, not a live Gemini accuracy result.

Ordinary taps are replayed through native typing. Non-letter keys remain native. Sentence mode is not used during touch exploration. Use Word swipe mode for native long-press alternatives, gesture shortcuts, and normal word-glide behavior.

## Decoder and safeguards

`AiSwipeSentenceTouch` owns the whole gesture before the native word decoder. It captures actual key centers/sizes, normalized path coordinates, relative timing and batched MotionEvent history. A second finger, cancellation, keyboard change, permission loss or out-of-bounds movement cancels the whole stroke; its remaining events cannot leak into native word commits.

`AiSwipeTrace` reduces the complete path on a worker dispatcher, retaining endpoints and significant bends/pauses. The Firebase request includes this evidence, noisy nearest-key visits, keyboard language and up to 256 UTF-16 units of preceding draft. The model is instructed to infer word boundaries jointly and transcribe the traced sentence, not answer the draft, paraphrase it or invent a continuation. Word mode retains its separate optional continuation switch.

Output is structured JSON: `{"alternatives":["...","..."]}`. The client validates type, length, characters, uniqueness and chronological letter/path plausibility against the actual layout. It may display fewer than three choices or none; it never pads the list. This geometric filter is a heuristic, not proof that an interpretation is correct. Review before insertion.

The snapshot includes the connection, session, revision, cursor, preceding text and keyboard identity. It is checked before dispatch, display and acceptance. Undo has its own post-edit snapshot. No text is rewritten during capture/preview. A fixed-height AI row prevents the keyboard moving under the finger as status text changes.

## Bounds and privacy

This implementation supports alphabetic layouts whose letters are represented directly by single UTF-16 keys. QWERTY and synthetic AZERTY are covered by deterministic tests. It does not claim universal language support, transliteration, complex-script composition, emoji/digit gestures, or device-calibrated recognition. Sentence output is limited to 320 UTF-16 units and 48 whitespace-separated words, with basic punctuation.

A stroke is bounded by 60 seconds and 4096 captured samples; exceeding either cancels it with an explanation, rather than truncating it into a different message. Requests retain at most 384 path points and 80 keys. The word and sentence paths share request throttling (30 starts/minute per engine, at least one second apart, 250 ms debounce). Sentence inference times out after eight seconds; word inference after three. Failure cooldown is ten seconds. These are client safeguards, not billing caps or latency promises.

The existing protected-field, incognito, no-learning, lock-screen, consent and App Check boundaries remain. The v2 consent marker is not restored from backups. No gesture, prompt or result is logged or persistently cached by this implementation. A gesture can encode sensitive material even in an ordinary field; neither the field checks nor the draft filter can detect every secret. Cloud processing remains an explicit choice. Revocation stops new work but cannot retract a request already received by Google.

No proprietary glide library is required for sentence capture. Native Word swipe still requires the existing library and dictionary. Default offline builds have neither Firebase dependencies nor INTERNET permission.

## Release evaluation

Run the focused `*AiSwipe*` tests in both build configurations. The fixtures exercise continuous MotionEvents, cancellation, historical samples, multiple fingers, actual-layout alignment, ambiguous boundaries, response rejection, editor insertion/undo, consent and scheduling. They do not establish live model quality.

Before release, use a configured Firebase project and actual devices to test slow/fast inaccurate sentences, loops/repeated letters, explicit/implicit boundaries, layout changes, short taps/long presses, rotation, app-specific composition and delayed-response edits. Include keyboard geometries such as one-handed, split and floating layouts. Compare corrected words/minute, final character error rate, correction effort, abstention/rejection rates and median/p95 suggestion latency against ordinary word glide. No live inference, physical-device recognition accuracy or novice speed improvement is claimed by the implementation alone.
