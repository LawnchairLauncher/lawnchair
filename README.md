# Lawnchair - [![Crowdin](https://d322cqt584bo4o.cloudfront.net/lawnchairandroid/localized.svg)](https://translate.lawnchair.app)

![Lawnchair](banner.png)

Lawnchair is the customizable Pixel Launcher alternative. This Repository contains our full rewrite from scratch (simply known as V2), 
if you were looking for our older V1 sources you can find the archive [here](https://github.com/LawnchairLauncher/Lawnchair-V1).

## How to Contribute?

If you know Java/Android and have some Kotlin knowledge you should quickly be able to start contributing to Lawnchair.
Make sure to generally add any new classes/resources to the `lawnchair/` subdirectory and try to make as little changes as necessary directly inside Launcher3 code.
Kotlin is preferred but we'll accept great contributions in Java as well. We'll happily accept all bug fixes and will most likely also accept your well thought out feature.

### Adding a search provider

A great entry point to start to understand how we work is trying to add your own search provider. You can find existing providers to reference in the `ch.deletescape.lawnchair.globalsearch.providers` package. 
It is pretty self explanatory, all you really need to do is find an Intent to directly start the search in the app of your liking, and then add that to a new provider. 
After registering it in `ch.deletescape.lawnchair.globalsearch.SearchProviderController` your new provider should appear in the app and be ready for testing.

### Building

Alright, I admit it, our build variants are a bit of a mess right now. Make sure to choose one of the variants prefixed `quickstepLawnchairDev*`
from the "Build Variants" menu in Android Studio / IntelliJ. Our team is usually always on the latest stable version of Android Studio.

If you're redistributing Lawnchair, please avoid using the package name `ch.deletescape.lawnchair.plah` and `ch.deletescape.lawnchair.ci`
as that will prevents users from updating to the official builds.

-- Note to self --
I spent a lot of time trying to figure out how to compile Lawnchair apk, and following are some points to keep in mind when trying to compile the source.
 * Try to stay away from Android Studio if possible.
 * Make sure java platform is set to java8. (Extra stress on this point)
 * Try cleaning the gradle directory before compiling if possible.
 * Command to compile apk is - `./gradlew assembleQuickstepLawnchairDevOptimized`
 * Build might take like 10-20 mins

## Translations

Wanna help us by translating Lawnchair to your own language? Just take a look at [our Crowdin page](https://translate.lawnchair.app/).

## Source releases

To make our problems with rip-offs a bit easier we will work in private development cycles but release our newest sources with every production release (beta, stable).
This also means that any contributions will be cherry-picked to our tree internally, we'll notify you whenever we do this. We're going to keep releasing public alpha builds in-between
cycles in the future.

## Licensing

All of Lawnchairs additions to Launcher3 are licensed under GPLv3 and anyone wanting to use it (even in parts) has to adhere to this license,
make sure to inform yourself before creating your own project based on Lawnchair. Here some guidelines to how we actually handle licensing in the case of Lawnchair
(I am not a Lawyer, don't take this as legally binding advice):

 * We will ALWAYS use all our legal options if you simply rename our app, strip it of all credit and upload it anywhere as your own, be this with or without monetization.
 * We usually do not enforce the GPL clause which would force you to release the source of your entire project, but do however ask you to release any direct modifications to Lawnchair code.
 * You are required to give us full credits for any work you may be using from us.
 * If you're simply using Lawnchair as a reference for minor implementation details or utility functions (not entire features of our app) you can treat that as if it were licensed under Apache2 and properly credit us.
 * We kindly ask you to use common sense and don't profit entirely off the work of others.
 * If you have any questions regarding licensing or derivative works feel free to contact licensing@deletescape.ch

 ## Graphics

 The official Lawnchair graphics were designed by [Josh Baldassarre](https://www.uplabs.com/jshbldssrr) & [Lumiq Creative](https://lumiqcreative.com). Thanks a lot to those awesome designers!

## Useful links

[Website](https://lawnchair.app) - [Twitter](https://twitter.com/lawnchairapp) - [Telegram](https://t.me/lawnchair) - 
[XDA Thread](https://forum.xda-developers.com/android/apps-games/lawnchair-customizable-pixel-launcher-t3627137) - [APK Mirror](https://www.apkmirror.com/apk/deletescape/lawnchair/)
