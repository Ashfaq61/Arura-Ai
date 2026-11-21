# AURA AI — Autonomous Android Assistant

AURA AI is a modular, multi-agent, self-evolving Android assistant built with **Kotlin**, **Jetpack Compose (Material 3)**, **Firebase AI Logic (Gemini + App Check)**, **Room**, **DataStore**, **WorkManager**, **CameraX**, and native Android **Accessibility / Notification / Telecom** integrations.

## Architecture Modules (`com.example.aura.*`)
- `ai/` — `AuraOrchestrator`, `MultiProviderRouter`, `ModelRoutingEngine` (Local-Only / Hybrid / Cloud-Only routing, Free-Only budget enforcement, Gemma 4 edge compatibility checks), `MultilingualDetector` (English, Urdu, Roman Urdu), `AppCheckHelper`.
- `agents/` — `AgentNetworkManager` coordinating 24 specialist agents with an A2A-style message bus and MCP tool registry.
- `automation/` — `AuraAccessibilityService`, `AccessibilityBridge` (numbered UI nodes, API 30+ `takeScreenshot`, stale-coordinate `screenHash` freshness checks, popup/loading detection, target confidence check), `AuraNotificationListenerService`, `DeviceControlEngine`.
- `voice/` — `VoiceEngine` (`SpeechRecognizer` EN/UR with silence/turn-end configuration, continuous turn-taking toggle, response de-duplication, `TextToSpeech` presets Kore/Aoede/Zephyr/Leda/Callirrhoe/Autonoe, barge-in stop).
- `research/` — `GitHubAndHfResearchClient` + evaluated targets (`OpenPhoneUse`, `Google ARTEMIS`, `GAgent`, `OpenGUI`, `Google ADK`, `Microsoft Agent Framework`, `Gemma 4`).
- `evolution/` — `LongHorizonAndSkillEngine` (10–100 step milestone planner, loop detection & replanning, Skill Memory with version drift repair, 13-stage SHA-256 signed self-modification pipeline), `SelfHealingEngine` (error recovery + strict idempotency guard against duplicate payments/messages/calls/deletions), `ToolFactoryAndEvolutionEngine`, `ScheduledWorkflowWorker` & `SelfEvolutionWorker` (WorkManager).
- `security/` — `AuraKeystoreVault` (Android Keystore AES-256-GCM encryption), `PermissionGuard` (4-level safety policies, Emergency STOP), `PromptInjectionDefender` (`<UNTRUSTED_EXTERNAL_DATA>` sandboxing & tamper blocking), `PrivacyDataRouter` (OTP/password/token/card redaction & screen minimization).
- `testing/` — `BenchmarkLabEngine` (12-case ARTEMIS / AndroidWorld-style verification suite with baseline comparison & regression gating).
- `calls/` — `PhoneCallAndMessagingManager` (call state monitoring, dialer role check, confirmed calling, WhatsApp/Share drafts).
- `files/` — `FileDocumentManager` (SAF + Photo Picker + `PdfRenderer` + Gemini OCR/summarization).
- `memory/` — `AuraDatabase` (Room v2 with `MIGRATION_1_2`), `MemoryRepository`, `PreferencesRepository` (DataStore).
- `ui/` — Cyber-Obsidian dark interface with animated Voice Orb, adaptive `NavigationBar`/`NavigationRail`, Live System Observability & JSON diagnostic export.

## Setup & API Configuration
1. **Google Gemini (Keyless)**: Powered by **Firebase AI Logic** + **App Check** (`app/google-services.json` + `configureAppCheck(this, intent)`). No Gemini API key is required.
2. **Optional Third-Party Providers**: Add Groq, OpenRouter, Hugging Face, or custom OpenAI-compatible keys in **System → API, Cost & Voices**; keys are encrypted at rest via Android Keystore AES-256-GCM.
3. **Device Opt-In Permissions**:
   - `RECORD_AUDIO` for voice input, `CAMERA` for CameraX Vision, `READ_PHONE_STATE`/`CALL_PHONE` for Call Agent, `POST_NOTIFICATIONS` for scheduled briefings.
   - Enable **AURA AI Screen & Automation** in Android Accessibility Settings for live numbered node reading, screenshots, and gesture dispatch.
   - Enable **AURA AI Notification Reader** in Notification Access Settings for notification/WhatsApp summarization.

## Automated Builds & Release Signing (GitHub Actions)
See `.github/workflows/android-ci.yml`. Every push, PR, and manual dispatch runs `./gradlew testDebugUnitTest --stacktrace` and `./gradlew lintDebug --stacktrace` as mandatory gates.

### Required GitHub Repository Secrets for Signed Release APKs
Add these under **GitHub Repository → Settings → Secrets and variables → Actions → New repository secret**:

1. **`KEYSTORE_BASE64`**: Base64-encoded contents of your **existing production** release keystore (`.jks` / `.keystore`) containing key alias `upload`.
   - **Important**: **Never generate a replacement keystore** if an existing production APK is already installed or published, or in-place updates will fail and overwrite compatibility will break.
   - Encode your existing keystore without line breaks:
     - **Linux**: `base64 -w 0 my-upload-key.jks`
     - **macOS**: `base64 -i my-upload-key.jks | tr -d '\n'`
     - **PowerShell**: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks"))`
2. **`STORE_PASSWORD`** *(or `KEYSTORE_PASSWORD`)*: Password for the release keystore (`app/build.gradle.kts` reads `STORE_PASSWORD`; CI also accepts `KEYSTORE_PASSWORD`).
3. **`KEY_PASSWORD`**: Private key password for the release key alias (for PKCS12 keystores where key and store share one password, enter that same password).
4. **`KEY_ALIAS`**: Set to `upload` (matches `keyAlias = "upload"` in `app/build.gradle.kts`; verify your keystore's alias with `keytool -list -v -keystore my-upload-key.jks`).
- *Optional (Recommended)* **`EXPECTED_SIGNING_CERT_SHA256`**: 64-character SHA-256 fingerprint of your production signing certificate so CI enforces exact signing identity pinning before publishing.

### Release Verification Gate
Before uploading artifacts or creating a GitHub Release (`push` to `main` or `workflow_dispatch`), CI validates all signing secrets without printing values, rejects debug certificates, runs `assembleRelease`, verifies `apksigner` integrity, checks `package='com.aistudio.auraai.xkmqzp'` and monotonic `versionCode`, and verifies the published SHA-256 checksum.
