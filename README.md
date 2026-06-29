<p align="center">
    <img src="./media/banner.png" width="100%">
</p>

<p align="center">
    <a href="https://github.com/YisusPineapple/Overture"><img src="https://img.shields.io/badge/commander-YisusPineapple-AD2A5A?style=for-the-badge"></a>
    <a href="#ai-engineered"><img src="https://img.shields.io/badge/🤖%20AI%20Engineered-Gemini/Claude-545DFF?style=for-the-badge"></a>
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

**Overture** is a hard-fork of the original Symphony project, born from a unique collaboration. **This project is 100% maintained, engineered, and designed by an AI (Claude/Gemini) in Pair Programming with YisusPineapple.**

Our mission is to push the boundaries of Android UI/UX using **Material 3 Expressive (M3E)** and **Liquid Glass** aesthetics, while maintaining a strict zero-telemetry policy and extreme performance optimizations for modest hardware.

## ✨ Features

- **Liquid Glass UI:** Hardware-accelerated ambient backgrounds that react to your music, with zero-cost fallbacks for older devices.
- **M3E Physics:** Organic, spring-based animations (60/120fps) that make the interface feel alive and tangible.
- **Dynamic Global Theming:** The app extracts the dominant palette from the current track's artwork and propagates it across every surface in real time — no hardcoded colors, no restart required.
- **Karaoke Lyrics Engine:** Native support for Enhanced LRC word-level `<time>` tags, highlighting each word as it is sung with M3E spring transitions. Falls back gracefully to line-synced and static lyrics.
- **Organic M3E Sliders:** Custom seek and volume controls built with spring physics — overshoot on press, settle on release — consistent with the tactile language of the rest of the UI.
- **Advanced Queue Management:** Drag-to-reorder, persistent queue state across sessions, and shuffle/repeat modes that operate on the queue without mutating the source library.
- **Absolute Privacy (SAF):** Overture uses the Android Storage Access Framework (SAF). We **do not** request broad `READ_EXTERNAL_STORAGE` permissions. You grant access only to the specific folders you choose.
- **Zero Telemetry:** No tracking, no analytics. The `INTERNET` permission is used **exclusively** for the optional in-app GitHub update checker (which can be completely disabled in Settings).
- **Extreme Optimization:** UI rendering isolated to the `Draw` phase to prevent battery drain and device heating.
- **Gapless Playback & Advanced Audio:** Powered by Media3 (ExoPlayer) and a custom C++ metadata decoder (Metaphony).

## 🛠️ Architecture & Performance

Overture is built to run flawlessly even on older hardware (minSdk 24). We achieve this by:
- Avoiding `Modifier.fillMaxWidth(ratio)` in favor of `Modifier.drawWithContent` for progress bars.
- Using `GraphicsLayer` for hardware-accelerated translations instead of triggering layout recompositions.
- Caching micro-bitmaps for ambient blur effects using strict WebP compression and `inSampleSize` memory management.
- Bypassing the main thread for heavy I/O and Bitmap processing.

## License

[AGPL-3.0](./LICENSE) - Inherited from the original Symphony project by Zyrouge.