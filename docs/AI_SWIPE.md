# AI swipe assistant

This fork has two opt-in Firebase AI Logic modes. **Word swipe** keeps local word decoding immediate, then offers an AI correction/short continuation. **Sentence swipe** interprets one continuous finger-down gesture as a whole phrase or sentence and offers multiple versions before insertion. Both are experimental: implementation/tests alone are not evidence that novices type faster.

For the continuous interaction, algorithm, bounds and evaluation checklist see [AI_SENTENCE_SWIPE.md](AI_SENTENCE_SWIPE.md). The sentence mode does not require the optional native word-glide library. Word swipe still requires that library and a suitable dictionary, as described in the main README.

## Interaction

After enabling the assistant, the AI row provides a **Sentence swipe / Word swipe** mode switch. In Sentence mode, glide across the letters of every word without lifting; lift after the final word. Space-bar passes are optional boundary hints. Review up to three interpretations, tap one for its full scrollable preview, then **Insert**. The keyboard does not insert a guessed native word while the gesture is underway.

Word mode retains the existing behavior: the native word decoder updates the editor immediately, and AI reranks up to eight native candidates using the recent draft. **AI word** replaces only the latest swiped word; **AI message** adds a short continuation. Long-press a word-mode choice to read it. The separate **Suggest message continuations** switch only controls those speculative tails; it does not disable whole-sentence transcription.

Neither mode automatically sends a message or rewrites earlier draft text. **Undo AI insertion** restores the original word in Word mode or removes the appended sentence in Sentence mode. It is available only while the editor, cursor and text remain unchanged. Typing, changing selection, switching fields/languages or hiding the keyboard invalidates old results. Predictions are currently restricted to a collapsed cursor at the end of an ordinary text field.

## Build modes

The default build remains offline: no Firebase dependencies or INTERNET permission. It retains the existing package IDs and Android 21 support. Explicit cloud builds use separate package IDs and require Android 23 or newer.

Use JDK 21 for the Robolectric Android 36 test sandbox; app source/bytecode remains Java 17. The project uses its checked-in wrapper, Android SDK 37 and NDK 28.0.13004108.

```sh
# Offline
./gradlew :app:assembleDebugNoMinify
# Cloud-enabled, but still needs Firebase configuration and consent
./gradlew :app:assembleDebugNoMinify -PaiSwipe=true
# Focused feature tests, independently in both configurations
./gradlew :app:testDebugUnitTest --tests '*AiSwipe*'
./gradlew :app:testDebugUnitTest --tests '*AiSwipe*' -PaiSwipe=true
```

## Firebase setup before live use

1. Enable Firebase AI Logic in your Firebase project with the Gemini Developer API backend. Choose a project/billing/data-processing configuration appropriate for unsent keyboard drafts; review the provider's applicable terms before distribution.
2. Register `helium314.keyboard.ai.debug` for debug/debugNoMinify or `helium314.keyboard.ai` for release. Configure the actual signing certificate for API restrictions and App Check.
3. Copy `firebase-ai.properties.example` to the ignored root file `firebase-ai.properties`. Set `projectId` from `project_info.project_id`, `applicationId` from the matching Android client's `client_info.mobilesdk_app_id`, and `apiKey` from that client's `api_key.current_key` in the Firebase Android configuration. Here `applicationId` is the Firebase app ID, often `1:...:android:...`, NOT the Android package name.
4. Configure App Check. Debug builds use the debug provider: register the token generated on your device in your own Firebase console and keep it private. Production builds use Play Integrity; configure attestation/enforcement for the actual signing/distribution arrangement. Never ship the debug provider or disable enforcement to work around a release error.
5. Build with `-PaiSwipe=true`, install and enable the keyboard, then enable **Settings > Glide typing > AI swipe assistant** and accept the disclosure. Install/enable the native glide library only for Word swipe; Sentence swipe captures gestures directly. Existing Word-only consent must be renewed for gesture uploads.

`firebase-ai.properties` and `google-services.json` are gitignored. Firebase client configuration is not an authorization boundary. Do not put a raw Gemini API key, service-account credential or App Check debug token into source or this properties file. No Google Services Gradle plugin is needed; the client lazily initializes a named Firebase app after consent and an eligible request.

The configured default remains `gemini-3.5-flash-lite`, with Firebase BoM `34.18.0`, minimal thinking and structured JSON. Word output has a 256-token budget; sentence output 1024. Changing the configured model requires checking its thinking/schema support and testing it; there is no silent fallback to a differently priced model.

An unconfigured CI APK cannot make live predictions. This code does not provision a Firebase project, billing, App Check registration or provider permissions, and does not fabricate credentials.

## Privacy and failure behavior

Field eligibility is checked before editor reads. Password, visible/web-password, email, URI, number, phone, person-name and other specialized fields are excluded, along with incognito, no-suggestions/no-personalized-learning, screen lock and locked direct-boot storage. A defensive draft filter suppresses obvious URLs, email addresses, long tokens and sequences of four or more digits. It cannot identify every secret in ordinary prose or in a gesture.

Word requests contain at most 256 UTF-16 units of preceding draft, candidate words and locale. Sentence requests additionally contain normalized gesture points, relative timing and the actual keyboard layout; they have no native word-candidate restriction. No conversation scraping, notification access, clipboard reading, persistent prompt/gesture/result cache, or private-content/SDK-exception logging is added. This describes client behavior, not Google's retention/training policy.

Consent requires a preference AND a version-2 marker in no-backup storage. An old version-1 marker or restored settings backup cannot authorize gesture uploads. Firebase's automatic initialization provider is removed, default data collection disabled, and automatic App Check token refresh disabled. Revocation cancels pending work and prevents new requests, but cannot retract an already-received request.

Both modes check editor/session/revision/cursor/text snapshots before dispatch, display, acceptance and undo. Sentence requests also check keyboard identity. JSON fields, output sizes, allowed characters and duplicates are validated; sentence alternatives undergo a heuristic path-alignment check. Draft content is treated as untrusted data, never instructions. The filter is not proof of correct interpretation.

Native typing does not wait for cloud results. The shared client debounces for 250 ms, spaces starts by at least one second, allows at most 30 starts per rolling minute per engine, and cools down ten seconds after failure. Word timeout is three seconds; sentence timeout eight seconds. No automatic retries. Failures leave draft text intact. Client limits are not billing caps or an abuse-prevention boundary: configure provider quotas/monitoring separately.

## Validation and release

[AI_SWIPE_VALIDATION.md](AI_SWIPE_VALIDATION.md) records the earlier Word-only build. [AI_SENTENCE_SWIPE.md](AI_SENTENCE_SWIPE.md) describes the new test/evaluation scope. CI checks both build configurations, feature tests, APK assembly, merged-manifest boundaries and production Firebase/App Check Kotlin compilation. Live-device inference, recognition quality, latency and novice-speed trials remain separate release gates; do not infer them from green builds.

## Official references

- https://firebase.google.com/docs/ai-logic/get-started
- https://firebase.google.com/docs/ai-logic/models
- https://firebase.google.com/docs/ai-logic/generate-structured-output
- https://firebase.google.com/docs/ai-logic/thinking
- https://firebase.google.com/docs/ai-logic/app-check
