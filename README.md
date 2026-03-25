# Capacities Quick Note Pro

A lightweight Android app for quickly capturing notes and sending them directly to your [Capacities.io](https://capacities.io) daily note. Built with Jetpack Compose and Material 3.

Testers Google Group: https://groups.google.com/g/capacities-quick-note-testers

Android App (Pre-release): https://play.google.com/apps/testing/com.dnnypck.capacitiesquicknotepro

## Features

- **Quick Note Capture**: Simple, distraction-free text input for rapid note-taking
- **Share Target Integration**: Share text from any app directly to your Capacities daily note
- **Multiple Space Support**: Settings allow you to configure multiple spaces and choose one upon posting
- **Auto-detect Web Links**: If you save a URL only, your note gets saved as a Capacities Weblink automatically
- **Direct API Integration**: Seamlessly sends notes to Capacities via their official API
- **Secure Credential Storage**: API keys stored securely using Android's SharedPreferences
- **Modern Material 3 UI**: Clean, intuitive interface following Material Design 3 guidelines
- **Edge-to-Edge Design**: Modern Android UI with immersive edge-to-edge display
- **Error Handling**: Detailed error messages to help troubleshoot API connectivity issues
- **Backup/Restore**: Back up your settings to your phone, Google Drive, etc, and restore them later or on another device

## Requirements

- Android 7.0 (API 24) or higher
- A [Capacities.io](https://capacities.io) Pro account
- Capacities API key and Space ID

## Setup

### Getting Your Capacities Credentials

1. **API Key**:
   - Open Capacities app
   - Go to Settings → Capacities API
   - Create a new API key
   - Copy the generated key

2. **Space ID**:
   - Open Capacities app
   - Go to Settings → Space settings
   - Find and copy your Space ID

### Installing the App

1. Download the latest APK from the [Releases](../../releases) page
2. Install on your Android device
3. Open the app and tap the settings icon (⚙️)
4. Enter your API Key and Space ID
5. Tap "Save Settings"

You're ready to start capturing quick notes!

## Usage

### Standalone Mode

1. Open the Capacities Quick Note app
2. Select your target space from the dropdown (if you have multiple spaces configured)
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
│   └── network/
│       ├── HttpClient.kt             # Ktor HTTP client configuration
│       └── PostRequest.kt            # API request logic
├── ui/
│   ├── main/
│   │   ├── MainScreen.kt            # Main note input screen
│   │   ├── MainViewModel.kt         # Main screen business logic
│   │   └── components/
│   │       ├── ContentTextField.kt  # Custom text input component
│   │       └── SendButton.kt        # Custom send button component
│   ├── settings/
│   │   ├── SettingsScreen.kt       # Settings configuration screen
│   │   └── SettingsViewModel.kt    # Settings business logic
│   ├── navigation/
│   │   └── Navigation.kt           # Navigation graph setup
│   └── theme/
│       ├── Color.kt                # Material 3 color scheme
│       ├── Theme.kt                # App theme configuration
│       └── Type.kt                 # Typography definitions
└── util/
    ├── PreferencesManager.kt       # SharedPreferences wrapper
    └── ViewModelFactory.kt         # ViewModel instantiation

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

The app intelligently uses two Capacities API endpoints:

### For URL-only content:
- **Endpoint**: `https://api.capacities.io/save-weblink`
- **Method**: POST
- **Authentication**: Bearer token (API key)
- **Required Fields**:
  - `spaceId`: Your Capacities space identifier
  - `url`: The URL to save as a weblink

### For text/markdown content:
- **Endpoint**: `https://api.capacities.io/save-to-daily-note`
- **Method**: POST
- **Authentication**: Bearer token (API key)
- **Required Fields**:
  - `spaceId`: Your Capacities space identifier
  - `mdText`: Markdown-formatted note content
  - `origin`: Set to "commandPalette"

The app automatically detects if your input is only a URL and uses the appropriate endpoint.

## Privacy & Security

- API credentials are stored locally on your device using Android's SharedPreferences
- All communication with Capacities API uses HTTPS
- No data is collected, stored, or shared by this app beyond what you explicitly send to Capacities
- The app requires only internet permission to communicate with the Capacities API

## Troubleshooting

### "Failed to send to Capacities" Error

- Verify your API key and Space ID are correct in Settings
- Check your internet connection
- Ensure your Capacities API key has the necessary permissions
- Review the detailed error message displayed in the app

### Share Target Not Appearing

- Ensure the app is installed properly
- Try restarting your device
- Check that you're sharing plain text (not images or other media)

## Version History

- **v1.0.3** (Build 4) - Current release
- Initial public release with core functionality

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
