# AI swipe assistant

This fork adds an explicitly opt-in Firebase AI Logic assistant on top of HeliBoard's existing glide decoder. It is an implementation for evaluation, not a measured claim that novices already type faster.

## Interaction

Swipe a word normally. The local decoder updates the editor immediately. After a short pause, a separate AI row can offer a better local candidate and a short continuation. Tap **AI word** to replace just the latest swiped word, or **AI message** to replace that word with the proposed phrase. Long-press a candidate to read its full text. Continue swiping to ignore it. Nothing is automatically rewritten or sent. **Undo AI insertion** restores the original swipe word while the editor, cursor and text remain unchanged; the next input dismisses undo.

For example, after a rough swipe in `I will see you ...`, the native decoder might offer `son` and `soon`. AI can prefer `soon`, and optionally suggest a short continuation. This is illustrative, not a recorded model result.

This version reranks up to eight native candidates. It does not introduce a new raw-path recognizer, recover a word absent from those candidates, rewrite earlier sentences, or read the other person's chat history. Suggestions are deliberately limited to a collapsed cursor at the end of an ordinary text field. Native glide still requires HeliBoard's separately installed gesture library and a suitable local dictionary; that library is not bundled or downloaded by this feature. See the main README's glide-typing instructions.

## Build modes

The default build stays offline: no Firebase dependencies and no INTERNET permission are added. Existing package IDs and Android 21 support are retained. Cloud builds are explicit, use a separate package ID to install alongside the offline keyboard, and target Android 23 or newer.

```sh
# Offline keyboard
./gradlew :app:assembleDebugNoMinify

# Cloud-enabled keyboard (AI remains unavailable until configured)
./gradlew :app:assembleDebugNoMinify -PaiSwipe=true

# Focused contract, privacy and scheduling tests
./gradlew :app:testDebugUnitTest --tests '*AiSwipe*'
./gradlew :app:testDebugUnitTest --tests '*AiSwipe*' -PaiSwipe=true
```

The checked-in Gradle wrapper, JDK 17, Android SDK 37 and NDK 28.0.13004108 are used. The former `testRunTestsUnitTest` command is not used by this feature's workflow.

## Firebase setup required before live use

1. In your Firebase project, enable Firebase AI Logic with the Gemini Developer API backend. Use a project/billing/data-processing configuration appropriate for keyboard drafts. Review Google's applicable data-use terms before distributing a cloud keyboard.
2. Register the Android package that you will build: `helium314.keyboard.ai.debug` for debug/debugNoMinify, or `helium314.keyboard.ai` for release. Register the correct signing certificate for App Check and API restrictions.
3. Copy `firebase-ai.properties.example` to `firebase-ai.properties` at the repository root. Fill `projectId` from `project_info.project_id`, `applicationId` from the matching Android client's `client_info.mobilesdk_app_id`, and `apiKey` from that client's `api_key.current_key` in your Firebase Android configuration. Here `applicationId` means the Firebase app ID (often starting `1:...:android:...`), **not** the Android package name.
4. Configure Firebase App Check. Debug builds install the debug App Check provider; register the development token generated on your device in your own Firebase console. Keep that token private. Production builds use Play Integrity; configure attestation and enforcement for your actual signing/distribution arrangement. Do not ship the debug provider or disable enforcement to solve a release configuration problem.
5. Build with `-PaiSwipe=true`, install, enable the keyboard, install/enable the glide library, then open **Settings > Glide typing > AI swipe assistant** and accept the explicit data disclosure. The separate **Suggest message continuations** switch controls whether AI may offer more than a word correction.

`firebase-ai.properties` and `google-services.json` are gitignored. Firebase client configuration is not a secret security boundary. Never put a raw Gemini API key, service-account credential or App Check debug token in these files or source code. This implementation does not require the Google Services Gradle plugin: it lazily creates a named Firebase app using the supplied client configuration after consent and an eligible swipe.

The default model is `gemini-3.5-flash-lite`, verified against Firebase's model documentation on 2026-09-09. Firebase BoM is pinned to `34.18.0`. The request uses minimal thinking, a small output budget and structured JSON. Changing `model` requires reviewing that model's support for the configured thinking level and structured-output API, then testing it; there is no silent fallback to a differently priced model.

A CI APK built without your Firebase configuration is useful for build verification only: it cannot make live predictions. Configuration is not fabricated, and no project, billing account, App Check registration or provider permission is provisioned by this code.

## Privacy and correctness boundaries

Field eligibility is checked before reading editor context. Password, visible/web-password, email, URI, number, phone, person-name and other specialized fields are excluded, along with incognito mode, no-suggestions/no-personalized-learning fields, the lock screen and locked direct-boot storage. A defensive text filter also suppresses obvious email addresses, URLs, long tokens and sequences of four or more digits. This filter cannot detect every secret placed in ordinary prose; the cloud consent disclosure says so.

Only the recent draft prefix (at most 256 UTF-16 units), the candidate words and the keyboard locale enter the request. No conversation scraping, notification access, clipboard reading or persistent prompt/result cache is added. The implementation does not log drafts, predictions or SDK exceptions. This describes the client, not Google's server-side retention or training policy.

Consent needs both a preference and a marker in no-backup storage. Restoring a settings backup cannot independently enable uploads. Firebase's automatic initialization provider is removed from the cloud manifest, data collection defaults are disabled, and App Check automatic token refresh is disabled. Turning the feature off cancels pending work and prevents new prediction requests; it cannot retract a request already received by the provider.

Predictions carry an editor/session/revision/cursor/text snapshot. The snapshot is rechecked before network dispatch, display, acceptance and undo. Starting a new gesture, touching a key, manual candidate selection, editing/pasting, changing selection, switching fields/languages/preferences or hiding the keyboard invalidates the old result. Responses may only select an actual candidate. Missing fields, non-string JSON, oversize output, control characters and bidirectional override characters are rejected. Draft content is untrusted data in the prompt, not instructions.

The local decoder never waits for cloud work. The client debounces for 250 ms, spaces request starts by at least one second, permits at most 30 requests per rolling minute per IME engine, times out after three seconds, and pauses for ten seconds after a failure. There is no automatic retry loop. Network, quota, attestation or parse failures leave local input intact. These client-side limits are not a billing guarantee or abuse-prevention boundary: also configure provider quotas and monitoring. Budget alerts alone are not a hard spending cap.

## Validation before a release

CI separately compiles/tests offline and cloud builds and checks the production App Check source path. Automated core tests cover JSON/candidate validation, punctuation/language joining, context clipping, protected-field policy, edit stamps, debouncing, cancellation, stale results, single-flight behavior, timeout, backoff and the request budget. They do not replace live-device IME and cloud testing.

On real devices test: slow/fast inaccurate swipes; taps and paste during a delayed response; cursor movement and selection; app/field/locale changes; backspace immediately after AI acceptance; undo; composing spans in different messaging/browser apps; screen lock, incognito and no-learning flags; loss of connectivity; invalid Firebase/App Check configuration; malformed/blocked output; rotation; floating/one-handed keyboard modes; and TalkBack. Inspect the final merged manifest: offline must lack INTERNET, cloud must lack FirebaseInitProvider.

Evaluate novice speed with the same randomized phrase set, local glide versus AI-assisted glide, counterbalanced order and a short practice period. Record corrected words per minute, final character error rate, undo/rejection rate, and median/p95 time from swipe release to usable suggestion. Use synthetic or consented phrases, not private messages. Release only after the latency and error results justify the feature; no benchmark improvement has been measured by this implementation work.

## Official references

- Firebase setup and Android dependencies: https://firebase.google.com/docs/ai-logic/get-started
- Available models: https://firebase.google.com/docs/ai-logic/models
- Structured JSON output: https://firebase.google.com/docs/ai-logic/generate-structured-output
- Thinking configuration: https://firebase.google.com/docs/ai-logic/thinking
- App Check: https://firebase.google.com/docs/ai-logic/app-check
