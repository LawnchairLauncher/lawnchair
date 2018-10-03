# Rootless Pixel Launcher
By Amir Zaidi

## Links

Play Store release: https://play.google.com/store/apps/details?id=amirz.rootless.nexuslauncher

Photos and videos: https://photos.app.goo.gl/qdcAcLOdiu8Kl1Bh1 

APK Downloads: https://github.com/amirzaidi/launcher3/releases

Rootless Pixel Bridge: https://github.com/amirzaidi/AIDLBridge/releases

Magisk version (only for Pixel users): https://github.com/amirzaidi/launcher3magisk/releases 

Alphas: https://t.me/appforks

Contact: https://t.me/mirai

## Vision

My launcher is a close to AOSP launcher that only changes the necessary code to allow for small extensions and backporting to older Android versions. If you want a more feature packed launcher use Nova, Action or Lawnchair.
It is focused on simplicity and rock solid stability. Bug reports go above all else, and almost every feature request will be denied.

## Features
### Lollipop
- The new Google Now feed is available to the left of the home screen, without needing root access.
- The theme changes based on the wallpaper in use. Dark wallpapers will trigger the dark theme, otherwise it uses the default theme.
- At the top of the first home screen page there is a Smartspace widget with events, weather and the date, all in the Google Sans font.
- The new search bar is at the bottom of the home screen and the top of the app drawer.
Google Calendar’s icon changes with the date.
- The exact same screen size profiles from the Pixel 2 Launcher are included. The same grid sizes and icon sizes.
- Google Wallpapers and Voice Search are hidden from the app drawer.
- The default home screen dock setup reflects the real Pixel Launcher's setup: Phone, Messages, Gmail, Store, Browser, Camera.

**Custom**

- Probably the most requested feature in the history of mankind’s development, icon packs are included in the settings app. Just a simple selection list where you can select from the list of packs installed on your phone.
- Long pressing an app reveals the Edit shortcut. Press on it to hide an app in the app drawer or disable the icon pack for that app.
- There are app suggestions based on the amount of drawer clicks at the top of the app drawer. These are not the same as the real Pixel Launcher. I will explain why in the FAQ.
- Round app icons from Android 7.1 are used when available.
- Calendar icons change with the date if the icon pack you use supports it.
- All icon packs are automatically hidden from the app drawer when an icon pack is being used.
- Google Now Launcher is hidden from the app drawer.
- There are custom screen size profile with a 4x4, 5x4 or 6x6 grid when the DPI is low or high. The 6x6 profile automatically kicks in on large phones. If you want to enable this, your DPI needs to be 370 or less (on a 1920x1080 device). This equals a 467dp smallest width, for those on Nougat.
- Big tablet screen support is added with bigger icons and a search bar, using the Pixel C profile.
- You can open and close notifications from anywhere on the home screen by swiping up and down.
- New releases can be found in the settings app when clicking About -> Version.
- Long press app popups are centered on the screen with a nice animation, when positioning it on the left or right would make it go out of the screen.
- When the popup still goes out of the screen in landscape, the popup arrow is hidden.
- When pressing the search bar without the Google App available on your phone, the browser opens google.com. This makes it useful for MicroG or Pico GApps users on custom ROMs, instead of being a waste of space.
- Themes are hardcoded to look like the Pixel, for compatibility across OEMs.
- For devices without software navigation bar, there is padding under the search widget on the home screen.
- Added an ellipsis (...) to At A Glance date when the text goes out of bounds.
- The At A Glance setting has been replaced with an enable/disable toggle. You can still access the customization settings by long pressing on the widget.
- Users can override the theme by selecting their preferred theme in the settings.
- The added "Transparent" dark theme makes the app drawer as transparent as the launcher code allows.
- When no Google App is installed but Google Go is found, its search will be launched when clicking the search bar
- Accents are stripped from searches, so it is easier to search for apps in the app drawer.
- Widgets can be resized in any direction where their size is bigger than 1 on the grid.
- On Lollipop, the app drawer status bar gets a slightly darker background to make it easier to see the white icons.
- The keyboard immediately closes when sliding down the app drawer search.
- Long pressing the arrow for direct search has been re-added.

### Marshmallow
- There are notification dots in the colour of the app’s icon.
- You can view and interact with notifications in the long press popup on apps.
- The settings menu uses the Oreo style and colours.
- When long pressing an app there will be static shortcuts in the popup.

### Nougat
- A light theme is applied when the wallpaper is light enough. This has a dark status bar and text.

### Nougat 7.1
- When long pressing an app there will be static and dynamic shortcuts in the popup.

### Oreo
- There are settings for adding icons to home screen when installing, and changing the icon shape for adaptive icons.
- Google Clock shows the current time and changes in real time.
- The Google Now feed uses the same theme as the launcher.
- Adaptive icon shape options include Squircle, Square, Rounded Square, Circle and Teardrop.
- The light theme used when the wallpaper is light will also has dark navigation bar.

**Custom**

- Adaptive icons are disabled for pre-Oreo apps, so you don’t get boxes in boxes.
- Available icon shape options include Cylinder from HMD/Nokia Android phones.

## Google Icons
This launcher supports adaptive icon packs. I made an example pack called Google Icons, which replaces OEM icons with the Google variant:
https://github.com/amirzaidi/GoogleIcons/releases

## Magisk
There is also a module for Pixel users if they grew bored of the default launcher and want to try out my launcher.

Disable this launcher and switch to different launcher before enabling or disabling the module. Not doing so could result in a “0.0dip has stopped working" bug. If you do encounter this bug, try installing the real Pixel Launcher from the app store.

## Credits
Before I talk about the things I used for the creation, I want to put a disclaimer that I did not simply copy and paste what the developers did. I looked at their code to spare myself time in researching the places where changes are necessary for a feature, and then made my own implementation. Still, I want to credit these developers for the hard work they have done upon which others like me could build.

### Till Kottmann
Till is the founder of Lawnchair, the Pixel Launcher with many customization features. Initially I did not want to implement icon packs because I feared it would break too many things, but then I took a look at his old implementation to see how much work it really was. And to my surprise it could be done without changing any AOSP Java code. Instead, I could specify a custom icon loader through XML and then focus on writing that icon loader code. The icon loader could load an icon pack app’s icon list and save that list in memory.

### Paphonb
https://forum.xda-developers.com/android/apps-games/app-rootless-pixel-2-launcher-google-t3688393 

Samsung users were facing stylistic issues, saying they were seeing blue folders and a blue app drawer. They also had this problem in the real Pixel Launcher, but Paphonb had fixed it according to them. So I decompiled Paphonb’s Pixel Launcher and compared it with the real APK to see what he changed. From his I figured out what was necessary to override Samsung’s changes and incorporated them in my styles.

### Luke Klinker
https://github.com/klinker24/launcher3/commit/305438ddac487b3f5febfca3c9950f780307cec0

This commit, used by Flick Launcher, was what gave me the idea of a simple most-clicked counter for app predictions. I extended the code with the idea of “decay” where new apps can eventually catch up with the older apps and the old apps aren’t stuck forever at a high click count.

### Anas
https://github.com/AOSPA/android_packages_apps_Launcher3/commit/43294ca8bf124c58c9f99d2d587e4ce3c835e891 

This member of Paranoid Android, who is also known as TheCrazySkull, was the first person that got the Google Now feed to work and release sources for it on GitHub. Thanks to him, the entire chain of “Rootless” Google Now feeds has been set in motion. I first tried implementing it on the Custom ROM called VertexOS, and wanted to find a way to debug what I was doing. I managed to get the launcher to compile in Android Studio, and to my surprise it worked after simply installing it as an apk! I hadn’t seen anyone else release a launcher that supported this (except for Google, obviously) so I continued working on it until it became the first version of “Rootless Pixel Launcher” (after a lot of decompilation with trial and error).

### Kevin Barry
The Nova Launcher developer was kind enough to e-mail me the details of WHY the Google Now feed was working on my launcher. He explained that the Google App used to have two ways of showing the Google Now feed: either the Google app was installed as a debug variant app, or the launcher was installed as a system app (which requires root). A few months ago Google changed this condition, so it also shows when the Launcher is installed as a debug app. This is why my debug variant launcher APKs are working, and the apps on the Play Store (which requires a release variant app) need a “companion” that runs as a debug app to get the Now feed. I think I still haven’t replied to this e-mail, so if you are reading this Kevin, I’m sorry..

### Harsh Shandilya
This Substratum developer taught me how to make a Magisk module this week. I wanted to release it on his name, but he said it’s so simple I didn’t have to do it. So instead I will credit him here.

### OnePlus
I kanged the translations for the terms “Icon pack” and “Applying” from their launcher. Sorry (not really).

### Google
For making the real Pixel Launcher in the first place. I also got some translations using Google Translate.

### Alpha testers
Working on this launcher took a long time, and I would like to thank all the testers across different Android versions and OEMs for making sure the launcher has rock solid stability. You reported bugs to me at an incredible pace which is necessary for a project of this scope. Without you I wouldn’t have been able to come this far!

### Tools
Enjarify, Procyon and Jadx do 75% of the decompilation work, but you have to manually fix up the decompiled sources before you can even think about compiling in Android Studio. I want to thank the creators of these decompilation tools for the impressive work they have done.

## FAQ
### Can you add x?
If it requires an additional settings entry, no. I stand for simplicity and perfection out of the box, and having a lot of customization does not fit in that vision. Use Nova, Action or Lawnchair if you want features, I probably won’t add it.

### Can you backport y?
I tried my best to backport as much as possible all the way to Lollipop. Some features like app shortcuts are simply too difficult for me to do properly and without bugs at this moment. The light theme could work on Marshmallow, but is broken on some OEM Stock Marshmallow ROMs so I decided to just disable it for Marshmallow.

### How do I prevent my Smartspace widget from being cut off?
Disable the weather card in Google Now, or use a lower DPI. Changing your DPI can be done from Android 6 onwards, and it can be done through the settings app from Android 7. It’s called “smallest width” in the developer options.

### How do I change my grid size?
This is based on your DPI/smallest width. For Marshmallow you will have to Google how to change it with ADB. On Nougat and Oreo you can change it using the “smallest width” setting in developer options.

### How do I get the new Google Now Feed?
Update the Google App to the latest version, set my launcher as the default launcher, then reboot the phone. If you are on a tablet, I’m afraid the Google App doesn’t support it.

### Why is the Google Now Feed not exactly like yours?
Google likes giving everyone a different experience with 10+ A/B testing flags. This is beyond my control and all handled by the Google App.

### The Smartspace weather is not working
This is dependent on your Google App and can break for many reasons. Make sure your location is on the High Accuracy setting, Google Now shows a card for your weather, you have an up to date Google App, and the new feed is working.

### The Smartspace events are not working
These only work with Google Calendar. Like the weather, everything is handled by the Google App, so it is hard for me to debug.

### Notification dots are not working
These only work on Marshmallow on newer, so Lollipop will never show them. If you are at least on Marshmallow make sure the app has permissions to read notifications. You can check this in the settings. If they still don’t work, reboot your device.

### Why custom app suggestions, and not the Google ones?
Google uses a massive library for prediction based on the time of day and location. I have tried decompiling this in the past, but getting everything to work flawlessly is an extremely difficult task. Therefore I decided to leave it out starting with version 2.0, and continue that trend with this 3.0 release.

### Do you support custom ROMs?
Only official LineageOS and official Paranoid Android. Anything else can have unexpected bugs that I cannot account for. Make sure you are on either of those ROMs or your device’s stock ROM if you want to do a bug report.

### How do I get the coloured Google icon on the search bar?
Use the “Calming Coastline” Live Wallpaper. You can find a port of the new Pixel 2 Live Wallpapers here: https://forum.xda-developers.com/showpost.php?p=74142755&postcount=608

### How is this different from Paphonb’s Pixel 2 Launcher?
They are two completely different projects. I started with AOSP Launcher3 in Android Studio, he started with the real Pixel Launcher APK where everything was already working. I focused on implementing all the functionality in Java, he modified the APK to work better on older Android versions. The disadvantage to my method is that some features like Google’s App Suggestions are too hard to add. The advantage to my method is that I could add anything I wanted at any time, like Icon Pack support. So, it is an initial effort vs down the line feature implementation effort trade-off.

### Why do Pixel users need to use the Magisk module?
Ironically, the “Rootless Pixel Launcher” requires root for Pixel users. The reason for this is that it uses the same name as the real Pixel Launcher. The real one cannot be removed or overwritten without root. Changing the name would break the Smartspace features, because Google hardcoded the Google App to only provide the features to the “real” Pixel Launcher.

### What is Launcher3?
Launcher3 is the name that the default AOSP launcher uses. If you compile AOSP from sources directly, that is what you will get. My GitHub project is called Launcher3 because I forked from AOSP and did not change the name. Changing the GitHub project name now is possible, but unnecessary.

### I can't install it because of a package is corrupt error
First use another launcher like Nova as your default. Then delete any other Pixel Launcher you have on your phone. If you can't do this because you are on a Pixel or custom ROM, use the Magisk module (needs Magisk).

### How do I change between 4x4, 5x5 and 6x6 grids?
Change your DPI, like with the Smartspace getting cut off FAQ entry.

### I can't swipe away cards on the new Now Feed?
Blame Google, they decided this was better.

### How do I activate dark mode?
Set a very dark wallpaper. The threshold is an average brightness of 25% and there shouldn't be many bright spots.

### App shortcuts are not showing
Set my launcher as the default launcher first. They only work on Android 7.1 and higher.

### I can't add some widget
Remove it from any other launcher you have on your phone first

### How do I uninstall this?
Select another launcher as your default, then you can uninstall it from your phone's settings app.

### How do I install this?
Go to the APK Downloads link, and click the top most "Launcher3-aosp-debug.apk". After it is downloaded, click the notification and press install.

### Navigation bar is not transparent.
Custom ROM users have to disable the "RR dynamic navbar" feature. Samsung and LG users might have to enable the fullscreen mode for this app in their device's settings.

### Navigation buttons are grey on Galaxy S8/9
Unfortunately this is a Samsung specific problem and would require a lot of hacking around to fix. If you are desperate to make them white, consider using a substratum theme.

### Feed has weird sizes that go out of the screen
Seems to be a Google App problem, every launcher with the new feed is suffering from it, including the real Pixel Launcher. Make sure you are not using a Google App Beta.

### Huawei/Honor crash
For a reason I can’t diagnose because I don’t have a Huawei device, recent Huawei phones seem to crash on launch when the user hasn’t given the launcher storage permissions. If this happens to you go into your system settings and grant the storage permission manually.
