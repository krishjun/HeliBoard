# Continuous sentence swipe validation

## Verified application source

Code commit: `722086423a39e37008195ebdf559871c6e1a8021`

Workflow: https://github.com/krishjun/HeliBoard/actions/runs/34383564553

This run validates the continuous sentence implementation and the settings fix that makes it available without the optional native word-glide library. The exact source archive records the code commit above. Later documentation-only changes do not alter the tested application code.

| Check | Offline | Cloud-enabled |
| --- | --- | --- |
| Contract and protected-field policy tests | 11 passed | 11 passed |
| Scheduling, cancellation and timeout tests | 8 passed | 8 passed |
| Editor insertion/undo and consent tests | 9 passed | 9 passed |
| Sentence trace/alternative validation tests | 9 passed | 9 passed |
| Continuous MotionEvent and cancellation tests | 9 passed | 9 passed |
| Settings registration with/without native library | 2 passed | 2 passed |
| Total focused tests | 48 passed, 0 failures/errors/skips | 48 passed, 0 failures/errors/skips |
| debugNoMinify APK assembly | Passed | Passed |
| Merged manifest privacy/package checks | Passed | Passed |
| Production Firebase/App Check Kotlin compilation | Not applicable | Passed |

Counts were checked against all six JUnit XML suites in each downloaded workflow artifact, not inferred from the workflow status. Both build logs report BUILD SUCCESSFUL.

The tests exercise a complete finger-down/move/up trace, final-UP-only submission, batched touch history, second-finger cancellation, permission loss, keyboard bounds, tap replay, duration limits, actual-layout path alignment, ambiguous word boundaries, malformed/duplicate/implausible alternatives, guarded sentence insertion and undo, old-consent rejection and configuration-independent settings registration. Fake model providers make scheduling tests deterministic. Synthetic paths test the decoder contract; they are not measured Gemini predictions.

The build uses JDK 21, the checked-in Gradle wrapper, Android SDK 37 and NDK 28.0.13004108. App source/bytecode compatibility remains Java 17. The manifest checks enforce offline/cloud network and package boundaries. The cloud build also compiles the production Firebase and Play Integrity source path.

## APK identity and configuration

Cloud debug APK SHA-256:

```
3e93293e60553db61ad7b5be606de800159507e90704cebbeb11da8a2a90e890
```

Offline debug APK SHA-256:

```
d0402a3ae416ccd2d41bee25074498af3cee5f3aa4ae53c1550a514af6893bc0
```

The cloud CI APK has **no Firebase project configuration** and cannot make live predictions. Configure your Firebase Android app and App Check, fill the ignored properties file, and rebuild with `-PaiSwipe=true` as described in [AI_SWIPE.md](AI_SWIPE.md). The sentence gesture interface and its limitations are documented in [AI_SENTENCE_SWIPE.md](AI_SENTENCE_SWIPE.md).

## Not established by these checks

No live Gemini inference, actual App Check attestation, physical-device/emulator interaction, recognition-quality measurement, latency benchmark or novice typing-speed study was performed. Robolectric MotionEvent/editor tests are simulated and do not establish application-specific IME behavior on a device. The entire pre-existing upstream suite was not run, and a minified/signed production APK was not assembled. Production Kotlin compilation alone is not a release build.

Continuous sentence recognition remains experimental. The geometric plausibility filter cannot prove a sentence is correct, and model output may be wrong or empty. Evaluate the complete interaction on real devices and representative consenting users before claiming a speed or accuracy improvement or distributing a production cloud keyboard.

Workflow artifacts retain source, APKs, build logs, JUnit XML, reports and merged manifests for seven days. The earlier [AI_SWIPE_VALIDATION.md](AI_SWIPE_VALIDATION.md) is the historical Word-only result, not the validation record for this update.
