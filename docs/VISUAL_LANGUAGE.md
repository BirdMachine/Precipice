# Precipice visual language — v0

The interface begins as a lens, not a character.

## Core object

A mostly transparent, refractive orb floating over a slow ambient field. The orb should not look like a rainbow-painted bubble; color should appear because the object bends the world behind it.

On API 33+ the first renderer uses AGSL glass with:

- refraction: `0.24`
- chromatic dispersion: `0.19`
- edge light: `0.30`
- very light frost/blur so the RGB fringe stays legible

These are starting points, not sacred constants.

## Motion vocabulary

- **idle:** slow breathing, tiny enough to feel alive rather than animated
- **touch:** gel-like squash/stretch and spring return
- **listening:** future input amplitude can soften/deform the lens
- **speaking:** future output amplitude can increase refraction and RGB separation
- **thinking:** future internal motion can move behind/through the lens rather than becoming a spinner
- **controls:** future controls may bud from the central glass body as droplets and be reabsorbed

## Architecture rule

The rest of Precipice should depend on Precipice-owned glass components rather than directly on a third-party liquid-glass library. Rendering libraries are replaceable organs.

## Kestrel rule

Primary interaction must remain comfortably above the damaged lower third of the screen. Bottom-edge UI may exist later, but it must never be required for basic voice interaction.
