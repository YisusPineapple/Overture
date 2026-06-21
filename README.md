<p align="center">
    <img src="./media/banner.png" width="100%">
</p>

<p align="center">
    <a href="https://github.com/YisusPineapple/Overture"><img src="https://img.shields.io/badge/commander-YisusPineapple-AD2A5A?style=for-the-badge"></a>
    <a href="#ai-engineered"><img src="https://img.shields.io/badge/🤖%20AI%20Engineered-Gemini-545DFF?style=for-the-badge"></a>
    <a href="https://github.com/zyrouge/symphony"><img src="https://img.shields.io/badge/forked%20from-Symphony-d946ef?style=for-the-badge"></a>
</p>

<h1 align="center">Overture</h1>

<p align="center">🎵 Offline music playback, elevated to an art form. Engineered for absolute performance and uncompromising privacy.</p>

<p align="center">
    <a href="https://github.com/YisusPineapple/Overture/releases/latest">Download (latest)</a> |
    <a href="https://github.com/YisusPineapple/Overture/releases">View all releases</a>
</p>

<p align="center">
    <a href=""><img src="https://img.shields.io/badge/stage-evolution-545DFF?style=flat-square"></a>
    <a href=""><img src="https://img.shields.io/badge/supports-Android%207.0+-AD2A5A?style=flat-square"></a>
    <a href="./LICENSE"><img src="https://img.shields.io/github/license/YisusPineapple/Overture?style=flat-square"></a>
</p>

<br>

## 🤖 The AI-Engineered Era

**Overture** is a hard-fork of the original Symphony project, born from a unique collaboration. **This project is 100% maintained, engineered, and designed by an AI (Claude / Gemini) in Pair Programming with YisusPineapple.**

Our mission is to push the boundaries of Android UI/UX using **Material 3 Expressive (M3E)** and **Liquid Glass** aesthetics, while maintaining a strict zero-telemetry policy and extreme performance optimizations for modest hardware.

## ✨ Features

- **Liquid Glass UI:** Hardware-accelerated ambient backgrounds that react to your music, with zero-cost fallbacks for older devices.
- **M3E Physics:** Organic, spring-based animations (60/120fps) that make the interface feel alive and tangible.
- **Absolute Privacy:** No internet permissions required for playback. No tracking. No telemetry.
- **Extreme Optimization:** UI rendering isolated to the `Draw` phase to prevent battery drain and device heating.
- **Gapless Playback & Advanced Audio:** Powered by Media3 and a custom C++ metadata decoder (Metaphony).
- **Heuristic Recommendations:** A local, on-device engine that analyzes your listening habits and favorites to suggest albums and artists.

## 🛠️ Architecture & Performance

Overture is built to run flawlessly even on older hardware (minSdk 24). We achieve this by:
- Avoiding `Modifier.fillMaxWidth(ratio)` in favor of `Modifier.drawWithContent` for progress bars.
- Using `GraphicsLayer` for hardware-accelerated translations instead of triggering layout recompositions.
- Caching micro-bitmaps for ambient blur effects.
- Bypassing the main thread for heavy I/O and Bitmap processing.

## License

[AGPL-3.0](./LICENSE) - Inherited from the original Symphony project by Zyrouge.
