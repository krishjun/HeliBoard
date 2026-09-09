# Verified AI swipe build

## Recorded result — 2026-09-09

Code commit: `1557ab789ebef750fc2faa0b7014f35346c1376c`

Workflow: https://github.com/krishjun/HeliBoard/actions/runs/34348393633

| Check | Offline | Cloud-enabled |
| --- | --- | --- |
| AI swipe contract/policy tests | 11 passed | 11 passed |
| AI swipe scheduler/cancellation tests | 7 passed | 7 passed |
| Editor acceptance/undo and consent tests | 4 passed | 4 passed |
| Total focused tests | 22 passed, 0 failed/skipped | 22 passed, 0 failed/skipped |
| debugNoMinify APK assembly | Passed | Passed |
| Merged manifest checks | Passed | Passed |
| Production Firebase/App Check Kotlin compilation | Not applicable | Passed |

The manifest checks verify package IDs, INTERNET permission only in the cloud build, absence of FirebaseInitProvider, package-specific file-provider authorities, minimum SDK 21/23, and cloud cleartext/data-collection defaults. Tests use real InputLogic/RichInputConnection code against a simulated editor and fake model providers for deterministic scheduling tests. No live model call is involved.

Use **JDK 21** for the Robolectric Android 36 sandbox. App source/bytecode compatibility remains Java 17. The build uses the checked-in wrapper, Android SDK 37 and NDK 28.0.13004108.

Cloud debug APK SHA-256:

```
c3d62e32fdb72bf460215d9a5e8e49f743768fc801841279073ee4e17c042d41
```

This APK has **no Firebase project configuration** and cannot make live predictions. Register/configure your Firebase app and App Check, fill the ignored properties file, and rebuild as described in AI_SWIPE.md.

## Not validated by this run

The entire pre-existing upstream test suite was not run. A minified/signed production release APK was not assembled. No emulator/physical-device interaction, live Firebase inference, actual App Check attestation, model-quality evaluation, latency measurement or novice typing-speed trial was performed. Passing focused tests and compilation is not evidence of faster real-world typing or release readiness.

Workflow artifacts include build logs, JUnit XML, reports, merged manifests, APKs and the exact tested source archive. Retention is seven days from the run. Subsequent documentation-only commits do not change the tested application code. Complete the device/privacy/latency/novice evaluation in AI_SWIPE.md before releasing the cloud feature.
