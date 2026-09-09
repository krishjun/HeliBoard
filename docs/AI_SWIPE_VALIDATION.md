# AI swipe validation environment

Use **JDK 21** to run the complete feature tests: the repository's Robolectric Android 36 sandbox requires it. Android app source/bytecode compatibility remains Java 17; the Java 17 reference in AI_SWIPE.md describes the app compilation target, not the test runner requirement.

The `AI swipe validation` workflow builds offline and cloud configurations independently. It runs the prediction contract/scheduler tests and real InputLogic/RichInputConnection editing tests against a simulated editor, builds a debugNoMinify APK, verifies merged manifest permission/startup/provider-authority boundaries, and compiles the production Firebase App Check source path. The editor simulation is not a real-device or live-cloud test.

Provider authorities use the final application ID, so the cloud APK and the existing offline keyboard can coexist. API-client configuration remains absent in CI. An APK compiled in CI without firebase-ai.properties cannot perform live predictions.

Validation logs, JUnit XML, merged manifests and APKs are retained as workflow artifacts for seven days. Check the actual workflow result for pass/fail status; this document does not assert that a run has passed. Before release, complete the device/privacy/latency/novice-speed evaluation in AI_SWIPE.md with your Firebase project and correctly registered App Check credentials.
