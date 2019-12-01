<img src="icon.png" alt="Icon" width="148px"/>

# Lawnchair — ![1.52M Downloads](https://img.shields.io/badge/downloads-1.52M-brightgreen) ![Rating: 4.4/5](https://img.shields.io/badge/rating-4.4%2F5-brightgreen) [![93% Localised](https://d322cqt584bo4o.cloudfront.net/lawnchairandroid/localized.svg)](https://translate.lawnchair.app) 

Lawnchair is a customisable home app for Android. Taking AOSP’s *Launcher3* as a starting point, it introduces rich personalisation settings while maintaining high standards in performance and accessibility.

## Contributing to Lawnchair
The `/lawnchair` subdirectory houses custom classes and resources.

### Adding Search Providers
The `ch.deletescape.lawnchair.globalsearch.provider` package lists search providers included in Lawnchair. To add a new provider, register the relevant Intent in `ch.deletescape.lawnchair.globalsearch.SearchProviderController`.

### Building the App
In your IDE’s *Build Variants* panel, pick any variant whose name starts with `quickstepLawnchairDev`. When redistributing Lawnchair, please refrain from using the `ch.deletescape.lawnchair.plah` and `ch.deletescape.lawnchair.ci` package names.

### Localising Copy
Visit our [*Crowdin* page](https://translate.lawnchair.app) to help translate Lawnchair.

## License
Lawnchair is distributed under the [*GPLv3* license](https://www.gnu.org/licenses/gpl-3.0.en.html). Consequently, you’re required to credit us in derivative products.

## Links
[![Join r/lawnchairlauncher](https://img.shields.io/reddit/subreddit-subscribers/lawnchairlauncher?label=Join%20r%2Flawnchairlauncher&style=social)](https://www.reddit.com/r/lawnchairlauncher)


[![Follow @lawnchairapp on Twitter](https://img.shields.io/twitter/follow/lawnchairapp?style=social)](https://twitter.com/intent/follow?screen_name=lawnchairapp)
