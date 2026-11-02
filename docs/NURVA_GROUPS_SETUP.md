# Nurva accounts and groups setup

The Android group flow uses Supabase Auth (email + password) and the existing Nurva database RPC functions. The Android client only contains the Supabase project URL and a publishable client key. Never put a `sb_secret_...` key or legacy `service_role` key in `local.properties`, Gradle, GitHub Actions build arguments, or the APK.

## 1. Configure the Supabase project

1. Create or open your Supabase project.
2. In **Authentication → Providers**, enable Email/password sign-in.
3. Keep email confirmation enabled for real accounts, and configure email delivery for production. With confirmation enabled, signup returns no active session; the app tells the user to confirm the email and sign in.
4. Apply the migrations in order:
   - `backend/supabase/migrations/202611010001_nurva_sync.sql`
   - `backend/supabase/migrations/202611010002_nurva_device_roster_permissions.sql`
5. In **Project Settings → API Keys** (or the project's Connect dialog), copy the Project URL and the **Publishable key**. Do not copy a Secret key.

The first migration creates the group, membership, device, invite, and RPC schema. The second migration adds each device's user ID and effective group role to the sync-state response used by the Android device roster.

## 2. Configure a local Termux build

From `~/apps/Nurva`, create or edit `local.properties` (the repository's `.gitignore` already excludes it):

```properties
NURVA_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
NURVA_SUPABASE_PUBLISHABLE_KEY=sb_publishable_REPLACE_ME
```

Keep any existing `sdk.dir` line in that file. Replace the two Nurva values with your project's URL and publishable key. Never commit `local.properties`.

Then build:

```bash
./gradlew testDebugUnitTest assembleDebug
```

## 3. Configure GitHub Actions APKs

For CI-built APKs to connect to your Supabase project too, add these repository Actions secrets:

- `NURVA_SUPABASE_URL`
- `NURVA_SUPABASE_PUBLISHABLE_KEY`

These are client-side values by design. Do not add a Supabase secret/service-role key. Builds still compile without the two secrets, but the app displays a cloud-configuration message and cannot sign in until configured.

## 4. Test the first group flow

1. Install the configured APK on the first Android device.
2. Create an account and confirm the email if the project requires confirmation.
3. Sign in, create a group, and verify the current device appears as `owner`.
4. Create an `editor` or `viewer` invite and share it.
5. Install the same configured app on another device, create/sign into a different account, and open the `nurva://join?...` link.
6. Join the group and check that the new device appears with the invited role.
7. From an owner/admin device, revoke the other device and refresh the roster.

## Security and current scope

- Passwords are sent directly to Supabase Auth over HTTPS and are not stored by Nurva.
- Access and refresh tokens are encrypted with AES-GCM using an Android Keystore key.
- Per-group device secrets continue to be generated and stored by `DeviceCredentialStore`.
- Group creation, invite issuance, joining, sync-state reads, and device revocation call authenticated RPCs. PostgreSQL checks the current user, device secret, membership, and role.
- Invite links contain a single token. The database stores only its SHA-256 hash; the token is shown once to the inviter. Current invites expire after seven days and allow up to ten uses.
- This increment does not yet publish or synchronize wallpaper image files. It establishes accounts, groups, invitations, membership roles, and device access management as the next working layer.
