# Nurva UI and user-flow redesign

This increment replaces the Wall You drawer-first navigation with a Nurva-first product structure while preserving the existing wallpaper engine and its source integrations.

## App structure

The app now has five persistent destinations:

1. **Home** — product dashboard, quick status, and shortcuts into groups, Explore, and Library.
2. **Groups** — email account creation/sign-in, group creation, invite joining, invitations, members, device roster, and device revocation.
3. **Explore** — the existing wallpaper providers, moved behind a horizontal source selector.
4. **Library** — Favorites and Recently applied, combined under one destination.
5. **Settings** — account/device management, appearance, wallpaper behavior, cache, backup/restore, and About.

The top bar uses Nurva branding and opens Groups from its account action. About remains a secondary destination from Settings.

## First-use flow

- Home is usable without an account. Wallpaper discovery and local favorites remain available.
- The first group action opens Groups. The user can create an account or sign in.
- If email confirmation is enabled in Supabase, signup shows a confirmation message and the user signs in after confirming their email.
- After sign-in, the user creates a named group. The current Android installation is registered as the owner device.
- The owner creates an Editor or Viewer invitation. The app can copy the `nurva://join?token=...` link or share it through Android's Sharesheet.
- On another Android device, opening the link launches Nurva. After sign-in, Join group registers that device under the invited role.
- The selected group's member roles and active device roster are shown. Owners can revoke any other device; admins can revoke editor/viewer devices but not owner/admin devices.

## Visual language

- Midnight blue/ink surfaces with a stable lavender primary and teal secondary accent.
- Rounded 18–30 dp cards and a compact, confident typography scale.
- Five persistent destinations — Home, Groups, Explore, Library, and Settings — instead of the legacy wallpaper-provider drawer.
- A Nurva-specific launcher mark, app theme resource, and light/dark color surfaces.
- Account and device credential preferences are excluded from Android cloud backup and device transfer because their encryption keys live in Android Keystore.
- Short, task-based screen labels: Home, Groups, Explore, Library, Settings.
- Wallpaper content remains recognizable but inherits Nurva's palette, shape system, and app navigation.

## Settings organization

- Account & devices: account, shared spaces, permissions.
- Appearance: system/light/dark theme and theme-aware image adjustment.
- Wallpaper behavior: applied wallpaper history and automated changer rules.
- Storage & cache: image cache limit.
- Backup & restore: local favorites import/export.
- About: version, links, and open-source notices.

## Setup dependencies

Cloud account and group calls require `NURVA_SUPABASE_URL` and `NURVA_SUPABASE_PUBLISHABLE_KEY` in `local.properties` (local Termux builds) or as GitHub Actions secrets (CI APKs). The two SQL migrations must be applied in order. A publishable key is designed for client applications; never put a Supabase secret/service-role key in Android code or an APK.

This increment adds the Android account/group/invitation/device-access screens and their authenticated RPC client. Live account operations require the Supabase project configuration and both migrations above, and still need a real two-device test. Wallpaper image upload/download orchestration, remote wallpaper application, WorkManager background sync, mosaic distribution, and schedules remain separate implementation milestones; the UI does not claim those functions are live yet.
