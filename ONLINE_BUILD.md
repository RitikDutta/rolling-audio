# Build Rolling Audio with GitHub Actions

Android Studio and a local Android SDK are not required. The workflow runs on pushes and pull requests to `main`, and can also be started manually.

## Build and download with GitHub CLI

```bash
gh repo view RitikDutta/rolling-audio --web
gh workflow run build-apk.yml --repo RitikDutta/rolling-audio --ref main
gh run list --repo RitikDutta/rolling-audio --workflow build-apk.yml
# Replace RUN_ID with the ID of the desired successful run.
gh run watch RUN_ID --repo RitikDutta/rolling-audio --exit-status
gh run download RUN_ID --repo RitikDutta/rolling-audio \
  --name RollingAudio-debug-APK --dir apk
cd apk
sha256sum -c app-debug.apk.sha256
```

In the browser, open the repository's **Actions** tab, choose a successful **Build Rolling Audio APK** run, then download **RollingAudio-debug-APK** under **Artifacts**. Extract the ZIP to get `app-debug.apk` and its checksum. Private repository artifacts require signing into an account with repository access. APK artifacts expire after 30 days; rerun the workflow to generate a fresh one.

## What the workflow verifies

- Java 17, checksum-verified Gradle 8.13 wrapper, Android SDK 35 and Build Tools 35.0.0.
- Unit tests for rolling PCM capture and the WAV file format.
- Robolectric tests for the authenticated MacroDroid broadcast interface on Android API 29 and 35.
- Android lint, debug APK compilation and debug signing verification.
- The `RollingAudio-verification-reports` artifact contains test and lint results, including on failed builds.

## Install and test on the phone

Copy `app-debug.apk` to an Android 10+ phone and install it, allowing installs from the browser or file manager if prompted. Open Rolling Audio, grant the microphone permission, tap **Start rolling microphone**, wait at least 10 seconds, then tap **Test snapshot**. The resulting `Download/Sarvam/voice.wav` must be 320,044 bytes. Follow [README.md](README.md) to configure the token-authenticated MacroDroid intents and select the file in HTTP Shortcuts. The microphone must be started while the Activity is visible.

## Troubleshooting

- **Workflow not found:** ensure `.github/workflows/build-apk.yml` is committed on `main` and Actions is enabled.
- **Build fails:** inspect the failed step and download verification reports; `gh run view RUN_ID --repo RitikDutta/rolling-audio --log-failed` prints failure logs.
- **SDK not found locally:** configure `ANDROID_HOME` or `local.properties`. GitHub Actions installs the required SDK packages automatically.
- **Install conflict:** an existing installation may have a different debug signing key. Uninstalling removes app data and changes the MacroDroid token, so copy the new token afterward. For distribution use your own release signing key.

No API keys or recording tokens belong in the repository. Sarvam/Groq credentials remain on the phone.
