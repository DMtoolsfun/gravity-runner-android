# Aptoide Release Notes

Recordable task: #027

## Billing and Ads

- The Aptoide build is free with ads.
- Rewarded retry ads remain enabled.
- Remove Ads products:
  - `remove_ads_30_days` - $3.99
  - `remove_ads_lifetime` - $9.99
- Remove Ads removes banner and interstitial ads.
- Optional rewarded ads may still be available for retry/continue flows.
- Aptoide products must be configured in Aptoide Connect before real purchase testing.
- Aptoide billing must confirm purchase ownership before ads are removed.
- No fake purchase success is allowed.

## Build Setup

- Aptoide builds use `distributionChannel=aptoide`.
- Samsung Galaxy Store builds can continue to use the existing Samsung IAP path with `distributionChannel=samsung`.
- Aptoide Billing requires the Aptoide Connect public key at build time:
  - `-PaptoidePublicKey=<Aptoide Connect public key>`
- If the Aptoide SDK, Wallet, public key, or products are unavailable, purchases fail safely and ads remain enabled.

## Aptoide Connect Setup Required

Create these in-app products in Aptoide Connect before real purchase testing:

- `remove_ads_30_days`
- `remove_ads_lifetime`

The 30-day product is delivered as a 30-day local entitlement after Aptoide purchase confirmation. The lifetime product is delivered only after Aptoide purchase ownership is returned by the SDK.
