# Demo Release Checklist

This build is private/share-only for direct tester distribution. Do not submit the demo APK to Samsung, Aptoide, APKPure, or any other store.

## Build command

From the repo root:

```powershell
npx cap sync android
cd android
.\gradlew.bat assembleRelease -PdistributionChannel=demo
```

## Expected demo behavior

- Version label shows `v1.0 Demo`.
- Gameplay remains fully playable with normal save/progress behavior.
- No banner, interstitial, rewarded, or placeholder ad surfaces are shown.
- Remove Ads and Restore controls are hidden.
- Purchase dialogs and store-specific billing flows are suppressed.
- Rewarded ad buttons are disabled/hidden; when retries are exhausted, the player starts over at Level 1.

## Output

Expected APK path:

```text
android/app/build/outputs/apk/release/app-release.apk
```
