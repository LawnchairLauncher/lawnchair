# Lawnchair 12.1

[![Build debug APK](https://github.com/LawnchairLauncher/lawnchair/actions/workflows/build_debug_apk.yml/badge.svg)](https://github.com/LawnchairLauncher/lawnchair/actions/workflows/build_debug_apk.yml)
[![Build release APK](https://github.com/LawnchairLauncher/lawnchair/actions/workflows/build_release_apk.yml/badge.svg)](https://github.com/LawnchairLauncher/lawnchair/actions/workflows/build_release_apk.yml)
[![Crowdin](https://badges.crowdin.net/e/188ba69d884418987f0b7f1dd55e3a4e/localized.svg)](https://lawnchair.crowdin.com/lawnchair)
[![Telegram](https://img.shields.io/endpoint?url=https%3A%2F%2Ftg.sumanjay.workers.dev%2Flccommunity)](https://t.me/lccommunity)
[![Discord](https://img.shields.io/discord/803299970169700402?label=server&logo=discord)](https://discord.gg/3x8qNWxgGZ)

Lawnchair is a free, open-source home app for Android. Taking Launcher3 — Android’s default home app — as a starting point, it ports Pixel Launcher features and introduces rich options for customization. This branch houses the codebase of Lawnchair 12.1, currently in alpha and based on Launcher3 from Android 12.1. For Lawnchair 9, 10, 11, and 12, see the branches with the `9-`, `10-`, `11-`, and `12-` prefixes, respectively.

## Contribute code

Whether you’ve fixed a bug or introduced a new feature, we welcome pull requests! (If you’d like to make a larger change and check with us first, you can do so via [Lawnchair’s Telegram group chat](https://t.me/lawnchairci).) To help translate Lawnchair 12.1 instead, please see “[Translate](#translate).”

You can use Git to clone this repository:

```
git clone --recursive https://github.com/LawnchairLauncher/lawnchair.git
```

To build the app, select the `lawnWithQuickstepDebug` build type. Should you face errors relating to the `iconloaderlib` and `searchuilib` projects, run `git submodule update --init --recursive`.

Here are a few contribution tips:

- [The `lawnchair` package](https://github.com/LawnchairLauncher/lawnchair/tree/12.1-dev/lawnchair) houses Lawnchair’s own code, whereas [the `src` package](https://github.com/LawnchairLauncher/lawnchair/tree/12.1-dev/src) includes a clone of the Launcher3 codebase with modifications. Generally, place new files in the former, keeping changes to the latter to a minimum.

- You can use either Java or, preferably, Kotlin.

- Make sure your code is logical and well formatted. If using Kotlin, see [“Coding conventions” in the Kotlin documentation](https://kotlinlang.org/docs/coding-conventions.html).

- Set `12.1-dev` as the base branch for pull requests.

## Translate

You can help translate Lawnchair 12.1 [on Crowdin](https://lawnchair.crowdin.com/lawnchair). Here are a few tips:

- When using quotation marks, insert the symbols specific to the target language, as listed in [this table](https://en.wikipedia.org/wiki/Quotation_mark#Summary_table).

- Lawnchair uses title case for some English UI text. Title case isn’t used in other languages; opt for sentence case instead.

- Some English terminology may have no commonly used equivalents in other languages. In such cases, use short descriptive phrases—for example, the equivalent of _bottom row_ for _dock_.

## Quick links

- [News](https://t.me/lawnchairci)
- [Lawnchair on Twitter](https://twitter.com/lawnchairapp)
- [Website](https://lawnchair.app)
- [_XDA_ thread](https://forum.xda-developers.com/t/lawnchair-customizable-pixel-launcher.3627137/)

You can view all our links [in the Lawnchair Wiki](https://github.com/LawnchairLauncher/lawnchair/wiki).
