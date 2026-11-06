# Capacities Quick Note Pro

🎉 Now globally available in the play store! https://play.google.com/store/apps/details?id=com.dnnypck.capacitiesquicknotepro&hl=en_US

A lightweight Android app for quickly capturing notes and sending them directly to your [Capacities.io](https://capacities.io) daily note. Built with Jetpack Compose and Material 3.

Testers Google Group: https://groups.google.com/g/capacities-quick-note-testers

Android App (Pre-release): https://play.google.com/apps/testing/com.dnnypck.capacitiesquicknotepro

## Features

- **Quick Note Capture**: Simple, distraction-free text input for rapid note-taking
- **Share Target Integration**: Share text from any app directly to your Capacities daily note
- **Multiple Space Support**: Sign in once per space (Capacities OAuth is space-scoped) and choose a target when posting
- **Auto-detect Web Links**: If you save a URL only, your note gets saved as a Capacities Weblink automatically
- **Direct API Integration**: Sends notes through the Capacities API v1
- **OAuth sign-in**: Authorization Code + PKCE; tokens stored in EncryptedSharedPreferences
- **Modern Material 3 UI**: Clean, intuitive interface following Material Design 3 guidelines
- **Edge-to-Edge Design**: Modern Android UI with immersive edge-to-edge display
- **Error Handling**: Detailed error messages to help troubleshoot API connectivity issues
- **Backup/Restore**: Back up spaces and nicknames (not Capacities sign-in) and restore them later or on another device

## Requirements

- Android 7.0 (API 24) or higher
- A [Capacities.io](https://capacities.io) Pro account

## Setup

1. Install the app (Play Store, or a debug/release APK from source).
2. Open Settings and tap **Connect to Capacities**.
3. Sign in on Capacities and choose the space this app may write to.
4. Repeat **Connect another space** if you want more than one target.

You're ready to start capturing quick notes.

Existing installs that used a pasted API token will be asked to sign in with OAuth. The old beta API (and user-level tokens) are no longer used.

## OAuth

Sign-in uses Capacities OAuth 2.0 (authorization code + PKCE). No manual client ID setup is needed.

| Setting | Value |
| --- | --- |
| Client ID | `capacities_api_quick_note_pro` (public client, no secret) |
| Redirect URI | `com.dnnypck.capacitiesquicknotepro://oauth/callback` |
| Scopes | `api:read`, `api:write`, `offline_access` |

That client ID is the Gradle default. You can override it with `CAPACITIES_CLIENT_ID` in `local.properties`, `gradle.properties`, or `-P` if you ever need a different registration. Blank values are ignored so they do not wipe the default.

Optional redirect overrides (must match the registered URI and `AndroidManifest.xml`):

```
CAPACITIES_REDIRECT_SCHEME=com.dnnypck.capacitiesquicknotepro
CAPACITIES_REDIRECT_HOST=oauth
CAPACITIES_REDIRECT_PATH=/callback
```

## Usage

### Standalone Mode

1. Open the Capacities Quick Note app
2. Select your target space from the dropdown (if you have multiple spaces connected)
3. Type your note in the text field
4. Tap "Send" to save to Capacities
5. Your note will be appended to today's daily note in Capacities

### Share Target Mode

1. Select text or a URL in any app (browser, messaging app, etc.)
2. Tap "Share" and choose "Capacities Quick Note"
3. The app opens with the shared text pre-filled
4. Review and tap "Send"

### Markdown Formatting

The app supports all Capacities markdown shortcuts. Tap "Formatting your notes" to see the full reference:

- `#`, `##`, `###`, and `####` for headings
- `-` for bullets
- `1.` for numbered lists
- `>` for quotes
- `**text**` for bold text
- `*text*` for italic text
- `()` for tasks
- `#tags` for tags
- `[links](https://example.com)` for links

### URL Detection

When you enter only a URL (e.g., `https://example.com`), the app automatically saves it as a Capacities weblink with preview and metadata. If you include text with a URL, it's saved as a markdown note.

## Building from Source

### Prerequisites

- Android Studio Ladybug or newer
- JDK 11 or higher
- Android SDK with API 36

### Build Steps

```bash
git clone https://github.com/yourusername/CapacitiesQuickNote.git
cd CapacitiesQuickNote

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew assembleDebug
```

The debug APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

For release builds, you'll need a signing keystore:

```bash
export KEYSTORE_PASSWORD="your_keystore_password"
export KEY_PASSWORD="your_key_password"

./gradlew assembleRelease
```

Or use the provided script:

```bash
./build-release.sh
```

The release APK will be at: `app/build/outputs/apk/release/app-release.apk`

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **HTTP Client**: Ktor
- **Auth**: OAuth 2.0 Authorization Code + PKCE (Custom Tabs)
- **Secure storage**: EncryptedSharedPreferences (Android Keystore)
- **Navigation**: Jetpack Navigation Compose
- **Serialization**: kotlinx.serialization
- **Build System**: Gradle (Kotlin DSL)
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 14+)

## Project Structure

```
app/src/main/java/com/dnnypck/capacitiesquicknote/
├── MainActivity.kt                    # Main activity, handles shared text intents
├── data/
│   ├── oauth/                        # PKCE, token store, redirect activity
│   ├── network/
│   │   ├── HttpClient.kt             # Ktor HTTP client configuration
│   │   └── CapacitiesApi.kt          # Capacities API v1 calls
│   └── repository/
│       └── CapacitiesRepository.kt   # Send notes with token refresh
├── ui/
│   ├── main/
│   │   ├── MainScreen.kt            # Main note input screen
│   │   ├── MainViewModel.kt         # Main screen business logic
│   │   └── components/
│   ├── settings/
│   │   ├── SettingsScreen.kt       # OAuth connect / disconnect
│   │   └── SettingsViewModel.kt
│   ├── navigation/
│   └── theme/
└── util/
    ├── PreferencesManager.kt       # Spaces + EncryptedSharedPreferences tokens
    └── ViewModelFactory.kt
```

## Development

### Running Tests

```bash
./gradlew test              # Unit tests
./gradlew connectedAndroidTest  # Instrumented tests
```

### Code Style

This project follows standard Kotlin coding conventions and Android best practices.

## API Integration

The app uses Capacities API v1 (`X-Capacities-Api-Version: 1.0.0`). Access tokens are space-scoped, so requests no longer send `spaceId`.

### For URL-only content

- **Endpoint**: `POST https://api.capacities.io/object/url`
- **Authentication**: Bearer OAuth access token
- **Body**: `{ "url": "https://example.com" }`

### For text/markdown content

- **Endpoint**: `POST https://api.capacities.io/blocks/daily-note/append`
- **Authentication**: Bearer OAuth access token
- **Body**: `{ "markdown": "note text" }`

After OAuth, the app calls `GET /space` to store the connected space's id and title.

Access tokens expire after one hour. The app refreshes them with `offline_access` (refresh tokens rotate on every use). Sign-out calls `POST /oauth/revoke`.

## Privacy & Security

- OAuth tokens are stored on-device with EncryptedSharedPreferences (Android Keystore). Auto-backup excludes that file.
- Manual Backup/Restore saves spaces and nicknames only. It never writes OAuth tokens or API keys. After restore on a new device, connect to Capacities again. Exporting a backup does not sign you out.
- All communication with Capacities uses HTTPS.
- No data is collected, stored, or shared by this app beyond what you explicitly send to Capacities.
- The app requires only internet permission to communicate with the Capacities API.

## Troubleshooting

### "Failed to send to Capacities" / 401

- Open Settings and tap **Connect to Capacities** (or reconnect that space)
- Check your internet connection
- Confirm the Capacities consent screen granted `api:write`
- Review the detailed error message displayed in the app

### Restored a backup and notes won't send

- Manual backups do not include Capacities sign-in. Open Settings and tap **Connect to Capacities** for each space.
- Exporting a backup does not sign you out of this device.

### Share Target Not Appearing

- Ensure the app is installed properly
- Try restarting your device
- Check that you're sharing plain text (not images or other media)

## Version History

- **v1.3.0** (Build 10) - Capacities API v1 + OAuth (PKCE)
- **v1.0.3** (Build 4) - Earlier public release

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built for the [Capacities.io](https://capacities.io) ecosystem
- Uses the official Capacities API

## Support

For issues, questions, or suggestions, please [open an issue](../../issues) on GitHub.

---

**Note**: This is an unofficial third-party app and is not affiliated with or endorsed by Capacities.io.
