# Nurva feature status for this foundation increment

This bundle is a first implementation layer, not a feature-complete application.

| Required feature | Included in this bundle | Still needed before calling it working |
|---|---|---|
| Mosaic lock screens | Room assignments, deterministic grid crop planner, Bitmap tile renderer, SQL tile persistence RPC | Editor/preview UI, assign real devices and display layout, persist and download tile images, apply and test on three or more devices |
| Synchronized themes | Room theme/item/schedule data, SQL theme and schedule RPCs | Theme/collection UI, schedule calculation/execution, worker and notification integration, multi-device tests |
| Invite from anywhere | Hashed, expiring, use-limited invitations and group join RPC; secure credential store | Android sign-in, QR creation/scanner/deep links, invitation screens, client API integration, tests across separate networks |
| Always-current group state | Server revision counter/state snapshot RPC, local revision cache and outbox schema | Android Supabase client, reconciliation engine, upload/download orchestration, connectivity-aware WorkManager worker, conflict/restart tests |
| Per-device controls | Persisted target/apply mode and preference-update RPC | Settings UI, pending-approval model, wallpaper worker that respects target/mode, Android API tests |
| Private groups | Server-side role checks, private Storage bucket, device secrets, device/member/invite revocation RPCs | Android member/device management screens, security review, end-to-end authorization tests; client-side E2E encryption is not claimed |
| Real wallpaper application | Existing Wall You wallpaper foundation remains in place; mosaic bitmap renderer is included | Connect downloaded revisions to WallpaperManager, handle permissions/errors and manual approval, verify on real devices |

## Included code

- Room v3→v4 migration, sync entities and DAO for groups, members, devices, wallpaper revisions, mosaic tiles, themes, theme items, schedules and retryable outbox records.
- Android Keystore AES-GCM credential storage, with random per-installation secrets.
- Mosaic grid geometry that normalizes crops and center-crops panorama to the assembled grid ratio, plus an Android Bitmap renderer.
- Backoff policy for retry scheduling.
- Supabase PostgreSQL schema/RPCs for groups, secure invites, device authorization/revocation, state reads, per-device settings, verified wallpaper publication, themes, schedules, mosaic tile assignments, member removal and invite revocation.
- Supabase Edge Functions for signed upload/download URL issuance and server-side JPEG/PNG/WebP signature/header-dimension inspection and SHA-256 verification (not a full image decode).
- GitHub Actions jobs for Android build/tests, Deno function checks/image tests and PostgreSQL migration smoke tests.

## Security boundaries

- Never bundle the Supabase service-role key in Android.
- Raw invitation tokens are returned once; only hashes are stored.
- Installation secrets are encrypted using a key held by Android Keystore; do not log them.
- The wallpaper bucket is private. Access uses Edge Functions that verify signed-in account, current group membership and installation secret.
- Wallpaper publication is service-role-only and consumes a matching pending upload record, after the Edge Function checks image signature, dimensions and SHA-256.
- Revoking a device prevents new authorized downloads and state reads. A file already downloaded to a lost device cannot be remotely erased; this must be explained in product privacy copy.
- Transport encryption is provided by HTTPS. End-to-end encryption of wallpaper content is not implemented or claimed in this foundation.

## Required next implementation phase

1. Add Supabase Android auth/API dependencies and a repository/client that uses `DeviceCredentialStore`.
2. Add Create Group, invite QR/share, accept invite, member/device management and per-device settings UI.
3. Build persistent WorkManager sync workers: fetch authoritative state, download/verify assets, enqueue/apply according to device settings, acknowledge revisions, retry with exponential backoff.
4. Add theme/collection and schedule editor, schedule trigger logic, and manual-approval UI.
5. Add mosaic setup/preview, device placements, tile download/render/apply integration.
6. Add push notification provider integration as an optional wake-up signal; polling/reconnect reconciliation must still work without push.
7. Run Room migration tests, deploy a disposable Supabase project, and run two-device cross-network and three-device mosaic acceptance tests.
