<div align="center">
  <img src="assets/banner.svg" alt="TinyChess — a complete offline chess engine in 37 KB" width="100%">

  <br>

  **A complete, offline chess game and engine for Android — in 37 KB.**

  <sub>Zero permissions &nbsp;·&nbsp; zero dependencies &nbsp;·&nbsp; zero native libraries &nbsp;·&nbsp; zero telemetry</sub>

  <br><br>

  [![Download APK](https://img.shields.io/badge/Download_TinyChess_v1.0.0-D48837?style=for-the-badge&logo=android&logoColor=white)](https://github.com/R1y3n/tinychess/releases/latest)
  [![Stars](https://img.shields.io/github/stars/R1y3n/tinychess?style=for-the-badge&color=2E6B4E&logo=github)](https://github.com/R1y3n/tinychess/stargazers)
  [![Release](https://img.shields.io/github/v/release/R1y3n/tinychess?style=for-the-badge&color=3F628A)](https://github.com/R1y3n/tinychess/releases)

  <br>

  ![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
  ![APK size](https://img.shields.io/badge/APK-37_KB-D48837?style=flat-square)
  ![Permissions](https://img.shields.io/badge/Permissions-0-2E6B4E?style=flat-square)
  ![Dependencies](https://img.shields.io/badge/Dependencies-0-2E6B4E?style=flat-square)
  ![Java](https://img.shields.io/badge/Java-pure_Android_API-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
  ![Offline](https://img.shields.io/badge/Offline-100%25-2E6B4E?style=flat-square)
</div>

---

TinyChess is a full chess app and a from-scratch engine written in a single Java source file. It has no Gradle project, no AndroidX, no game assets, no network access and no ads. The entire APK is smaller than most app icons.

It was built around one question: **how much chess can you fit into the smallest possible Android package?**

<br>

<table>
<tr>
<td width="50%" valign="top">

### By the numbers

| | |
|:--|:--|
| **APK** | 37 KB |
| **DEX** | ~42 KB uncompressed |
| **Source** | 1 activity, 1 custom view |
| **Permissions** | none |
| **Third-party code** | none |
| **Native libraries** | none |
| **Min Android** | 8.0 (API 26) |
| **Target Android** | 14 (API 34) |

</td>
<td width="50%" valign="top">

### What you get

- Complete FIDE ruleset
- Four difficulty levels
- Lossless undo
- Crash-safe auto-save
- PGN sharing
- Procedural wood audio
- Vector-rendered pieces
- A hidden easter egg

</td>
</tr>
</table>

<br>

## Features

### Complete rules, no shortcuts

Castling, en passant, pawn promotion, checks, checkmate, stalemate, the 50-move rule, threefold repetition and insufficient material are all implemented. Move generation uses a 0x88 board and every move is validated against king safety before it reaches the board.

### Four genuinely different bots

| Level | Search depth | Think budget | Character |
|:--|:--:|:--:|:--|
| **Easy** | 2 ply | 140 ms | Makes human-like mistakes |
| **Medium** | 4 ply | 320 ms | Casual club player |
| **Hard** | 6 ply | 680 ms | Punishes loose pieces |
| **Expert** | 16 ply | 1450 ms | Full-strength tactical search |

Easy and Medium intentionally add controlled imprecision at the root so the lower tiers feel different rather than simply slower.

### Quality-of-life built in

- **Lossless undo** — snapshot history restores the exact board, castling rights, en-passant square, clocks and hash; undoing also takes back the bot's reply.
- **Crash-safe resume** — the session is written synchronously to `SharedPreferences` after every move and on every lifecycle exit. Force-stop the app mid-game and continue where you left off.
- **PGN export** — share the complete game, with proper headers and SAN notation, through Android's share sheet.
- **Captured trays and material score** — see what has been traded and who is ahead.
- **Smooth movement** — pieces glide, lift and swap with castling animation; captured pieces fade under the mover.
- **Procedural audio** — move and capture sounds are synthesized at runtime with `AudioTrack`. There are no WAV, MP3 or OGG files in the APK.

### Made to feel native

The board, buttons, coordinates, captured pieces and every chess piece are drawn on a single `Canvas`. The pieces are vector paths, not bitmaps, so the UI stays sharp at any density. The launcher icon is an adaptive vector too.

### One small secret

Tap the **TinyChess** title on the start screen ten times. That is all we will say.

<br>

## Install

1. Download the latest APK from **[Releases](https://github.com/R1y3n/tinychess/releases/latest)**.
2. Open it on an Android 8.0+ device and allow installation from your browser or file manager.
3. Or install over USB:

```bash
adb install -r TinyChess-v1.0.0.apk
```

The published APK is signed with a local debug key. It is intended for sideloading and personal use, not for Play Store distribution.

<br>

## Nightly build (size experiment)

<table>
<tr>
<td width="116" valign="top"><img src="assets/icon-nightly.svg" width="96" alt="TinyChess Nightly purple icon"></td>
<td valign="top">

The nightly APK installs **alongside** stable as `com.tiny.chess.nightly`, so it never replaces or updates the release build. It has its own purple icon.

This variant tests a much smaller **installed** footprint by telling ART not to AOT-compile the app:

- `android:vmSafeMode="true"` disables ART AOT compilation.
- `android:useEmbeddedDex="true"` runs the DEX directly from the APK.
- `classes.dex` is stored uncompressed and 4-byte aligned for direct loading.

On the test device, Android Settings reports **6.4 MB** of storage for stable and **149 KB** for nightly — roughly **43x smaller**. The nightly code directory is 61 KB with no `oat/` artifacts. The APK itself is slightly larger (**53 KB**) because the DEX is not compressed.

**Trade-off:** without AOT, startup and engine search are slower. This is a test build, not a replacement for stable.

**[Download the nightly prerelease](https://github.com/R1y3n/tinychess/releases/tag/nightly)**

</td>
</tr>
</table>

<br>

## Build from source

### Requirements

- JDK 8 or newer (`javac`, `keytool`)
- Android SDK with `build-tools` and at least one `platforms/android-*`
- `zip` available on your `PATH`

### Build

```bash
git clone https://github.com/R1y3n/tinychess.git
cd tinychess
export ANDROID_HOME="$HOME/Android/Sdk"

./build.sh stable    # -> tinychess.apk
./build.sh nightly   # -> tinychess-nightly.apk
```

The script compiles the vector resources with `aapt2`, compiles Java directly against `android.jar`, converts the classes with `d8`, packs the DEX, aligns the archive with `zipalign` and signs it with `apksigner`. Running without an argument builds the stable variant.

If `debug.keystore` is missing, `build.sh` generates one automatically. Never reuse a debug key for a production release.

<br>

## Project layout

```text
tinychess/
├── AndroidManifest.xml          # one activity, portrait, no permissions
├── build.sh                     # aapt2 + javac + d8 + zipalign + apksigner
├── res/
│   ├── drawable/                # adaptive icon vectors
│   └── mipmap-anydpi-v26/
├── nightly/
│   ├── AndroidManifest.xml      # com.tiny.chess.nightly + no-AOT flags
│   └── res/                     # purple adaptive icon
├── src/com/tiny/chess/
│   └── MainActivity.java        # UI, engine, persistence, audio
├── assets/
│   ├── banner.svg               # README art
│   └── icon-nightly.svg         # nightly icon art
└── README.md
```

<br>

## Under the hood

TinyChess deliberately avoids every abstraction that would add bytes.

| Layer | Implementation |
|:--|:--|
| **Board** | 128-square 0x88 array, compact integer pieces |
| **Search** | Iterative-deepening alpha-beta with principal-variation search |
| **Pruning** | Transposition table (65,536 entries), null-move pruning, late-move reductions, killer moves |
| **Quiescence** | Capture and promotion search to avoid horizon effects |
| **Evaluation** | Tapered midgame/endgame score with piece-square tables and bishop-pair bonus |
| **Hashing** | Zobrist keys for position, side, castling and en passant |
| **Threading** | UI board and search board are fully separated; the engine runs off the UI thread |
| **Persistence** | Synchronous `SharedPreferences` with move history and SAN list |
| **Rendering** | One `View`, one `Canvas`, vector paths for all six piece types |
| **Audio** | Real-time PCM synthesis at 22,050 Hz |

No engine library is linked, downloaded or bundled. The search, evaluation, move generation and rules are all part of the same 2,300-line source file.

<br>

## Privacy

TinyChess requests **no permissions**. It cannot access the internet because it has no `INTERNET` permission. There are no accounts, analytics, ads, trackers or third-party SDKs. Everything stays on the device.

<br>

## Controls

| Action | How |
|:--|:--|
| Select a piece | Tap it — green dots show legal moves |
| Capture | Tap a gold-framed destination |
| Promote | Choose from the panel that appears |
| Undo | Toolbar **Undo** |
| Share game | Toolbar **Share PGN** |
| New game | Toolbar **New / Menu** |
| Continue | **Continue** on the start card |

<br>

---

<div align="center">

### Built by R1y3n

<a href="https://github.com/R1y3n">
  <img src="https://github.com/R1y3n.png" width="84" alt="R1y3n">
</a>

**R1y3n** · System automation

[GitHub](https://github.com/R1y3n) &nbsp;·&nbsp; [Discord](https://discord.gg/gbVnRTn)

<sub>Small by design. Fast by necessity. No frameworks were harmed in the making of this APK.</sub>

</div>
