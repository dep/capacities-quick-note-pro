# Design: Migrate to the new Capacities API

Date: 2026-08-06
Status: Approved by user (with instruction to implement through to a locally testable build)

## Background

The app currently uses the beta Capacities API (`POST /save-to-daily-note`, `POST /save-weblink`) with one global API key plus manually entered Space IDs. Capacities has released API v1.0.0 (https://developers.capacities.io/api/reference/). Verified against the published OpenAPI spec and the official TypeScript SDK:

- The old endpoints are replaced: daily-note saves are `POST /blocks/daily-note/append`; weblink saves are `POST /object/url`.
- There is no `spaceId` field anywhere in the new API. New `cap-api-…` tokens are scoped to a single space; `GET /space` (singular) returns the token's space. The token determines the target space.
- Auth remains `Authorization: Bearer <token>`.

## Design

### 1. Credentials & data model
- `Space` gains `apiToken: String? = null` (`null` = needs a token).
- Global API key removed from UI and from `hasRequiredSettings()`, which becomes "at least one space has a non-blank token". Sending requires the selected space to have a token.
- New space entries get a locally generated UUID as `id` (now only a local picker key; users never enter a Space ID).
- Demo mode unchanged.

### 2. Guided re-setup for existing users
- One-time migration in `PreferencesManager` on launch: clear the legacy global API key, keep existing space entries (ids + nicknames) token-less, and set a flag that triggers a one-time notice: Capacities has a new API, each space now needs its own API token, generated in Capacities under Settings → Capacities API. Token-less spaces are marked "token needed" in Settings.

### 3. Shared API layer
- New `data/network/CapacitiesApi.kt`:
  - `appendToDailyNote(token, markdown)` → `POST https://api.capacities.io/blocks/daily-note/append`, body `{"markdown": …}`.
  - `saveWeblink(token, url)` → `POST https://api.capacities.io/object/url`, body `{"url": …}`.
- Request bodies are `@Serializable` DTOs encoded with kotlinx.serialization (already a dependency), fixing the existing manual-escaping fragility. The old `origin` field does not exist in the new API and is dropped.
- `postToTarget` and `KtorHttpClient` remain unchanged underneath.

### 4. Call sites
- `MainViewModel`, `WidgetNoteActivity`, `WidgetService` replace inline URL/JSON with `CapacitiesApi` calls using the selected space's token; when the token is missing they show "This space needs a new API token — open Settings". URL-only detection unchanged.

### 5. Settings UI
- Global "API Key" field removed.
- Add/edit space dialog fields: **API Token** (required) + **Nickname** (optional), replacing Space ID.
- Token-less entries show a "token needed" indicator.

### 6. Backup/restore
- `BackupData` becomes `backupVersion = 2`: spaces carry tokens; top-level `apiKey` optional so v1 backups still decode.
- Restoring a v1 backup imports spaces token-less and tells the user to add new tokens.

### 7. Errors & limits
- Existing `Result`-based error display stays. On HTTP 401/403, add a hint that the token may be from the old API.
- Rate limits (30/min daily note, 10/min weblink) far exceed app usage; no handling added.

### 8. Docs
- README "API Integration" and "Setup" sections updated for per-space tokens and new endpoints.

## Out of scope
Auto-fetching space names via `GET /space`, `noTimeStamp`, other new endpoints, dual-mode fallback to the old API.
