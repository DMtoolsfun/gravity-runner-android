# Gravity Runner — Galaxy Store Launch Prep

Status: Prepared while Samsung Commercial Seller request is blocked/pending support  
Project: Gravity Runner  
Company: DMtools.fun LLC  
Branch: Mobile Games  
Build target: v0.1 Galaxy Preview

## Current Project Status

### Completed

- Gameplay build frozen and backed up.
- Galaxy monetized build branch created.
- AdMob test ads integrated.
- Rewarded retry ads working.
- Banner placement fixed above Android navigation buttons.
- Samsung IAP SDK v6.5.0 integrated.
- `remove_ads` purchase does not fake success.
- Remove Ads fails safely until Seller Portal product setup is complete.
- Samsung support request submitted for greyed-out/blank Commercial Seller form field.

### Waiting On

- Samsung support response for Commercial Seller request form issue.
- Commercial Seller approval.
- Ability to upload Gravity Runner binary and create the `remove_ads` in-app item.

## Galaxy Store Listing Draft

### App Name

Gravity Runner

### Short Description

Tap, hold, and drift through moving gates in a retro arcade gravity runner.

### Full Description

Gravity Runner is a simple retro arcade game built for quick, replayable mobile sessions.

Hold the screen to rise, release to fall, and drift through moving maze gates. Clear levels, collect power orbs, manage your lives, and choose your speed mode. The rules are easy to understand, but each level pushes your timing, control, and reaction speed.

Features:

- Retro arcade-style visuals
- Simple one-touch controls
- Beginner, Intermediate, and Expert speed modes
- Level-based progression
- Three-life runs
- Reward orbs for points, slow maze, wall breaking, and extra lives
- Gate-edge bounce mechanic
- Optional rewarded ads for extra retry opportunities
- Optional Remove Ads purchase

Gravity Runner is designed to be quick to learn, fun to retry, and challenging to master.

## Store Category

Recommended category path:

- Games
- Arcade
- Casual

## Keywords

- gravity runner
- arcade game
- retro game
- runner game
- maze game
- one touch game
- casual game
- tap game
- pixel game
- high score game
- mobile arcade
- reflex game

## Monetization Disclosure

Gravity Runner is free to play and may show ads.

The game includes:

- Banner ads on menu/game-over style screens
- Occasional interstitial ads outside active gameplay
- Optional rewarded ads for extra retry opportunities
- Optional one-time Remove Ads purchase

Recommended store wording:

Contains ads and optional in-app purchases.

## Remove Ads Product Draft

Product ID: `remove_ads`  
Product Title: Remove Ads  
Product Type: One-time item / non-repurchasable unlock behavior  
Suggested Price: $0.99

Product Description:

Remove banner and pop-up ads from Gravity Runner. Optional rewarded ads may still be available for extra retry rewards.

Remove Ads should remove:

- Banner ads
- Interstitial ads
- Any forced/non-optional ads

Remove Ads does not need to remove:

- Optional rewarded ads, because those are player-chosen and tied to bonuses

## Screenshot Plan

1. Start Screen — Choose your speed and start your run.
2. Gameplay Gates — Hold to rise, release to fall, and drift through the gates.
3. Reward Orbs — Catch power orbs for points, slow time, extra lives, and wall breaking.
4. Level Complete — Clear levels and chase your best run.
5. Game Over / Retry — Run out of tries? Retry the level or use an optional reward.
6. Pause Screen — Pause anytime and come back to the run.

## Release Build Checklist

### Code/Build

- Confirm working branch: `galaxy-monetized-build`
- Confirm gameplay has not changed from approved version
- Confirm app opens on phone
- Confirm pause works
- Confirm difficulty buttons work
- Confirm banner appears only on menu/game-over style screens
- Confirm banner hides during active gameplay
- Confirm rewarded retry ads work
- Confirm Remove Ads fails safely until Seller Portal product exists
- Confirm Restore fails safely until Seller Portal product exists
- Confirm `remove_ads` product ID matches code and Seller Portal exactly

### Samsung Seller Portal

- Commercial Seller status request submitted
- Commercial Seller status approved
- Gravity Runner app registered
- Binary uploaded
- `remove_ads` in-app item created
- Licensed tester account added
- IAP test purchase completed
- Restore purchase tested after reinstall/clear data
- Cancel purchase tested
- Ads remain enabled if purchase fails/cancels

### Assets

- Background image: `gravity-runner-background-1440x1200.jpg`
- Profile image: `gravity-runner-profile-240x240.jpg`
- Banner image: `gravity-runner-banner-656x324.jpg`
- App icon prepared
- Screenshots captured

### Policy

- Privacy Policy page published
- Support page/contact available
- Ads disclosed
- In-app purchases disclosed
- Data safety/content forms completed

## Next Steps While Waiting on Samsung Support

1. Publish privacy policy/support page somewhere accessible.
2. Prepare app icon.
3. Capture app screenshots from phone.
4. Confirm current code is committed and backed up.
5. Prepare signed release build instructions.
6. When Samsung unlocks Commercial Seller status, upload the app and create `remove_ads`.

## Record Plan

### #022 — Samsung Commercial Seller Request Blocked/Pending Support

Status: In Progress / Waiting

### #023 — Galaxy Store Listing and Launch Prep

Status: Ready to Record After Review
