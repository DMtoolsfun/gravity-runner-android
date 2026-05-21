# Galaxy Release Candidate Checklist

Status: Release candidate prep while Samsung Commercial Distribution Request is in progress.

## Build Path

Use the Android Capacitor project as the source of truth.

```powershell
npx cap sync android
cd android
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Default builds use `distributionChannel=samsung`. Aptoide builds must opt in explicitly with `-PdistributionChannel=aptoide` and the required Aptoide public key.

Release APK output:

```text
android/app/build/outputs/apk/release/app-release.apk
```

## Samsung Monetization Path

- Samsung Galaxy Store builds use Samsung IAP SDK v6.5.0.
- Product ID: `remove_ads`
- Product type: one-time/non-consumable item.
- Debug builds initialize Samsung IAP in test mode.
- Release builds initialize Samsung IAP in production mode.
- Purchase and restore grant ad removal only after Samsung ownership is confirmed.
- Remove Ads hides banner and interstitial ads.
- Rewarded retry ads remain optional and available.

## Privacy And Support

- Privacy policy: `https://dmtools.fun/gravity-runner-privacy.html`
- Support page: `https://dmtools.fun/gravity-runner-support.html`
- Support email: `admin@dmtools.fun`

## Reviewer Test Notes

- App name/package: Gravity Runner / `fun.dmtools.gravityrunner`
- Version: `1.0` (`versionCode` 1)
- The game is free with ads and an optional Remove Ads purchase.
- Interstitials are paced and only requested outside active gameplay.
- Rewarded ads are player-initiated for continue/retry rewards.
- Restore uses Samsung owned-product lookup for `remove_ads`.
- If the Samsung IAP product is not available yet, purchase/restore fail safely and ads remain enabled.

## Remaining Samsung-Dependent Blocker

- Samsung Commercial Distribution approval must complete before the app can be submitted with paid IAP.
- After approval, create the Samsung Seller Portal in-app item with product ID exactly `remove_ads`, add licensed testers, then validate purchase, cancel, already-owned, and restore flows on a Samsung device.
