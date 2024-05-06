# Lawnchair Launcher Contributing Guidelines

![](https://raw.githubusercontent.com/validcube/lawnchair/pave-path/.github/lawnchair_round.png)

First and foremost, Welcome to the **Lawnchair Launcher** Contributing Guidelines! This document will contains everything you'll need to contribute to the Lawnchair Launcher project.

## Before you start!

We would like for you to be familiar with the tool we are using, the tool we are using will depend on which types of contribution you are going to make.

### Translation

For translation, you would have to know only one tool: [Crowdin](https://lawnchair.crowdin.com) which allows us to collaborate with multiple translators & proofreaders.

Here are few tips for starter:

- When using quotation marks, insert the symbols specific to the target language, as listed in this [this summary table](https://en.wikipedia.org/wiki/Quotation_mark#Summary_table);
  
- Lawnchair uses title case for some English UI text, Title case isn't used in other languages; opt for sentence case instead;
  
- Some English terminology may have no commonly used equivalents in other languages. In such cases, use short descriptive phrases—for example, the equivalent of *bottom row* for *dock*;
  
- Some languages *(e.g. French)* might have gender-specific word for masculine and feminine; recommend to opt for neutral-specific instead.
  

### Code

For code, It's highly recommend that you use an Integrated Development Enviroment (IDE), know Java or preferably Kotlin and Git, which is a powerful Source Management Control for collaborating with multiple programmers.

Before cloning the repository, you must clone the repository and it submodule using the recursive argument:

```
git clone --recursive https://github.com/LawnchairLauncher/lawnchair.git
```

To build the Lawnchair Launcher, select the `lawnWithQuickstepDebug` build type. Should you face errors relating to the `iconloaderlib` and `searchuilib` projects, run `git submodule update --init --recursive`.

Here are some contribution tips to help you get started:

- Always make sure that you're up-to-date with Lawnchair Launcher by setting your branch to `14-dev` to newer;
  
- Make sure your code is logical and well-formatted. If using Kotlin, see [“Coding conventions” in the Kotlin documentation](https://kotlinlang.org/docs/coding-conventions.html);
  
- [The `lawnchair` package](https://github.com/LawnchairLauncher/lawnchair/tree/14-dev/lawnchair) houses Lawnchair’s own code, whereas [the `src` package](https://github.com/LawnchairLauncher/lawnchair/tree/14-dev/src) includes a clone of the Launcher3 codebase with modifications. Generally, place new files in the former, keeping changes to the latter to a minimum
  

#### Version

As of [#4361](https://github.com/LawnchairLauncher/lawnchair/pull/4361), the Lawnchair Launcher version code is separated by four sectors:

1. Android's Major version
  
2. Android's Minor version
  
3. Lawnchair Launcher's development status
  
4. Lawnchair Launcher's development version
  

##### Android's Major & Minor version

These makes up the first two sectors of the version code, Android 11 will be `11_00_XX_XX` while Android 12.1 will be `12_01_XX_XX`.

##### Development Status & Version

Depending on which status the Lawnchair Launcher is on, it will make an impact on the 3rd and 4th sectors of the version code. Lawnchair Launcher **Alpha 4** will be `XX_XX_01_04`

| Status | Fields 3 |
| --- | --- |
| Alpha | 01  |
| Beta | 02  |
| Release Candidates | 03  |
| Release | 04  |

## Quick links

- [News](https://t.me/lawnchairci)
- [Lawnchair on Twitter](https://twitter.com/lawnchairapp)
- [Website](https://lawnchair.app)
- [_XDA_ thread](https://forum.xda-developers.com/t/lawnchair-customizable-pixel-launcher.3627137/)

You can view all our links [in the Lawnchair Wiki](https://github.com/LawnchairLauncher/lawnchair/wiki).
