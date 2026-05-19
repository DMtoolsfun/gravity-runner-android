# Aptoide Uploader API Notes

The dashboard manual upload remains the safest first submission path for Gravity Runner, especially while account eligibility and app ownership checks are being established.

The Aptoide Uploader API requires Aptoide API access and may require an active Aptoide Connect account subscription. Aptoide's documentation says uploads authenticate with an API key in the `Api-Key` header.

Store `APTOIDE_API_KEY` as an environment variable. Do not commit API keys, signing files, public keys, `.env` files, or local secret scripts.

A signed APK is required. The upload endpoint accepts APK files; `.aab`, `.zip`, and `.apks` files are not supported for app file upload.

The helper script looks for the newest `.apk` in:

```text
android/app/build/outputs/apk/release/
```

API field mapping must be verified against the official Aptoide docs before production use. As of the checked docs, the documented upload fields include `apk`, optional OBB/split files, `billingMethod`, `releaseMode`, `releaseTimestamp`, `requiresDeveloperApproval`, optional media assets, and optional locale metadata. App title, package name, privacy policy, support email, and website are not mapped by the helper unless Aptoide documents those request fields.

Current Gravity Runner metadata for manual review:

```text
Package: fun.dmtools.gravityrunne
Title: Gravity Runner
Privacy policy: https://dmtools.fun/gravity-runner-privacy.html
Support email: admin@dmtools.fun
Website: https://dmtools.fun
```

Dry run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/aptoide-upload.ps1 -WhatIf
```

Real upload:

```powershell
$env:APTOIDE_API_KEY = "..."
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/aptoide-upload.ps1
```

If the build uses the Aptoide billing public key, also set `APTOIDE_PUBLIC_KEY` before uploading. The helper only checks that it exists; it never prints the value or sends it to the upload API.
