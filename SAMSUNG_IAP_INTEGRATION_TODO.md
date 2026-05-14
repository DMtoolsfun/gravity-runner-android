# Samsung IAP Integration TODO

## Current status

Samsung In-App Purchase is not installed in this Android project yet.

The inspection found:

- No Samsung IAP helper module in `android/settings.gradle`
- No Samsung IAP dependency in `android/app/build.gradle`
- No Samsung IAP `.jar` or `.aar` in `android/app/libs`
- No Samsung IAP package imports or existing IAP implementation classes under `android/app/src/main/java`
- Existing web hooks are present in `www/index.html`:
  - `window.GravityRunnerNative.purchaseRemoveAds()`
  - `window.GravityRunnerNative.restorePurchases()`
  - `window.GravityRunnerRewards.setAdsRemoved(true)`

Because the SDK is missing, the app must not fake a successful Remove Ads purchase. The native bridge currently fails safely and logs/shows:

```text
Samsung IAP SDK not installed yet
```

## Required SDK work

Add the official Samsung In-App Purchase SDK for Galaxy Store builds before enabling real purchase behavior.

Expected Android integration points:

1. Add the official Samsung IAP SDK artifact or helper module to the Android project.
   - If Samsung provides an `.aar` or `.jar`, place it under `android/app/libs` and add the matching Gradle dependency.
   - If Samsung provides a helper module/sample module, include it from `android/settings.gradle` and add the module dependency in `android/app/build.gradle`.
   - Do not use Google Play Billing for this Galaxy Store purchase.

2. Confirm the SDK package names and APIs from the installed Samsung SDK documentation/sample code.
   - Do not invent SDK classes or method names.
   - Use Samsung IAP test/development mode while validating.

3. Implement real logic in `android/app/src/main/java/fun/dmtools/gravityrunner/MainActivity.java`.
   - Product ID: `remove_ads`
   - `purchaseRemoveAds()` should start the Samsung purchase flow for `remove_ads`.
   - `restorePurchases()` should query owned purchases.
   - Startup should query ownership safely.
   - If and only if Samsung confirms ownership, call:

```javascript
window.GravityRunnerRewards.setAdsRemoved(true)
```

4. Persist local Remove Ads state only after confirmed Samsung ownership.
   - Do not consume `remove_ads`.
   - Do not allow repurchase after ownership is confirmed.
   - Acknowledge/confirm the purchase only if the installed Samsung SDK requires it for one-time non-consumable items.

5. Keep all failure paths safe.
   - Galaxy Store/Samsung Checkout unavailable: keep ads enabled.
   - User cancels: keep ads enabled.
   - Query fails: keep ads enabled.
   - SDK unavailable: keep ads enabled.

## Validation after SDK installation

Run:

```powershell
npx cap sync android
cd android
.\gradlew.bat assembleDebug
```
