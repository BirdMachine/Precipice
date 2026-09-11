# Precipice

> The side of the seam.

Precipice is the first Android vessel for Brill: a deliberately small, provider-swappable voice shell built to exist before it grows up.

## v0 visual direction

The first living object is a mostly-transparent refractive orb. It samples a moving color field behind itself, bends that world through glass, and uses chromatic dispersion for delicious RGB edge fringing. The glass layer is intentionally wrapped behind Precipice-owned composables so we can swap rendering libraries later without rewriting the app.

Current experiment:

- Jetpack Compose, no Material UI components
- `BuildItCode/LiquidGlass` for live refraction / RGB dispersion on API 33+
- custom `PrecipiceGlassOrb` abstraction
- animated ambient backdrop
- touch squish + breathing/speaking deformation hooks
- controls kept above the lower screen danger-zone on Kestrel

## Build

Current toolchain targets Android API 37 with AGP 9.4 / Gradle 9.6 and JDK 17.

Open in Android Studio, or run with a local Gradle 9.6 install:

```bash
gradle :app:assembleDebug
```

GitHub Actions also builds a debug APK on every push.

## Principle

First make the weird little blob exist. Then teach it to plorp. 🫧🌈
