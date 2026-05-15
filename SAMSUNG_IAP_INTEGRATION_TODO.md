# Samsung IAP Integration TODO

## Completed Android integration

- [x] Official Samsung IAP AAR added at `android/app/libs/samsung-iap-6.5.0.aar`
- [x] Android Gradle dependency includes local `.aar` files from `android/app/libs`
- [x] Manifest includes Samsung billing permission:
  - `com.samsung.android.iap.permission.BILLING`
- [x] Manifest includes internet permission:
  - `android.permission.INTERNET`
- [x] Real Samsung IAP SDK APIs confirmed from the installed AAR:
  - `com.samsung.android.sdk.iap.lib.helper.IapHelper`
  - `startPayment(...)`
  - `getOwnedList(...)`
  - `acknowledgePurchases(...)`
  - `HelperDefine.OperationMode.OPERATION_MODE_TEST`
- [x] `purchaseRemoveAds()` starts Samsung IAP purchase flow for `remove_ads`
- [x] `restorePurchases()` queries Samsung-owned products
- [x] App startup safely queries Samsung ownership
- [x] `remove_ads` is not consumed
- [x] Ads are removed only after Samsung ownership is confirmed

## Remaining Samsung Seller Portal steps

- [ ] Confirm commercial seller status is active
- [ ] Upload the Galaxy Store binary
- [ ] Create the in-app item with product ID exactly `remove_ads`
- [ ] Add licensed testers
- [ ] Validate purchase, cancel, restore, and already-owned flows on a Samsung device with Galaxy Store/Samsung Checkout available

## Validation commands

```powershell
npx cap sync android
cd android
.\gradlew.bat assembleDebug
```
