# APKPure Release Checklist

APKPure is extra distribution for Gravity Runner. Samsung remains the main store and monetization target.

## Build Command

Use the explicit APKPure channel so store-specific purchase UI is disabled:

```powershell
npx cap sync android
cd android
.\gradlew.bat assembleRelease -PdistributionChannel=apkpure
```

Release APK output:

```text
android/app/build/outputs/apk/release/app-release.apk
```

## APKPure UI Behavior

- Free-with-ads build.
- Remove Ads button is hidden.
- Restore button is hidden.
- Remove Ads offer dialog is not shown automatically.
- Samsung purchase/restore wording is not shown.
- Aptoide purchase/restore wording is not shown.
- Version label shows `v1.0 APKPure`.
- Banner, interstitial, and rewarded retry ads remain enabled.
- Rewarded retry flow remains available when the ad SDK can serve a rewarded ad.
- Interstitial pacing remains unchanged.

## Privacy And Support

- Privacy policy: `https://dmtools.fun/gravity-runner-privacy.html`
- Support page: `https://dmtools.fun/gravity-runner-support.html`
- Support email: `admin@dmtools.fun`

## Unchanged Store Paths

- Default builds still use `distributionChannel=samsung`.
- Samsung IAP code and Samsung release-candidate behavior remain in the project.
- Aptoide billing code remains in the project and still requires `-PdistributionChannel=aptoide`.
- AdMob remains enabled for APKPure release builds.
