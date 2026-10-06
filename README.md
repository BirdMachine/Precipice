# Precipice

Android voice and avatar shell for Birdie, with ChatGPT as the interim assistant while Brill is built. Package: `dev.birdmachine.precipice`. Brill remains a separate future identity.

Version 0.2 includes the recovered white/lilac catgirl maid as a layered paper doll: neutral bust, blink eyes, speaking mouth, breathing and listening/thinking tilt. The glass orb remains selectable on Android 13+. The asset manifest records canvas coordinates; this is a first expression pack, not a full Live2D rig.

Tap Talk to listen, tap Finish to send, or type a message. Stop interrupts speech or discards a pending reply. Phone speech recognition prefers Android's on-device service; the fallback is labelled as potentially using network. Speech output requires an installed offline English TTS voice. Settings includes expression previews and voice speed.

Controls and the scrollable settings panel stay in the upper two thirds for Kestrel's damaged display.

## ChatGPT connection

Settings → Continue with ChatGPT opens the system browser using OpenAI's local-project authorization contract: loopback callback, PKCE, state/nonce, signed identity validation, encrypted Android Keystore token storage, refresh and disconnect. Select a model returned for the connected plan, then talk. Manage usage opens ChatGPT settings.

The shell keeps only this session's turns in memory and accepts streamed replies only after completion. Existing ChatGPT chats, memories and connected apps are not imported. Sign-in and actual plan inference still need a test on Kestrel; no account credentials are included in builds.

## Build and signing

Android API 37, AGP 9.4, Gradle 9.6, JDK 17. Run:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

An installable build requires the persistent test key; otherwise compilation produces an unsigned APK. See [signing setup](docs/SIGNING.md). CI verifies the pinned public certificate before uploading an APK and refuses to publish when the private signing secret is absent. Never commit the private key.

The old September CI debug certificate differs. A phone with that old build may need one migration uninstall; APKs using the new persistent key can update in place afterward.
