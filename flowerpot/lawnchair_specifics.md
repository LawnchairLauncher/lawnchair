## Categories

```python
{
    "HEALTH_AND_FITNESS": ["HEALTH_AND_FITNESS", "MEDICAL"],
    "KNOWLEDGE_AND_REFERENCE": ["EDUCATION", "BOOKS_AND_REFERENCE", "WEATHER"],
    "NEWS": ["NEWS_AND_MAGAZINES"],
    "ENTERTAINMENT": ["ENTERTAINMENT", "MUSIC_AND_AUDIO", "VIDEO_PLAYERS"],
    "MUSIC": ["MUSIC_AND_AUDIO"],
    "LIFESTYLE": ["LIFESTYLE", "BEAUTY"],
    "PHOTOGRAPHY": ["PHOTOGRAPHY"],
    "BUSINESS_AND_PRODUCTIVITY": ["PRODUCTIVITY", "BUSINESS"],
    "TOOLS": ["TOOLS"],
    "FINANCE": ["FINANCE"],
    "COMMUNICATION": ["COMMUNCICATION"],
    "SOCIAL": ["SOCIAL"],
    "TRAVEL_AND_NAVIGATION": ["MAPS_AND_NAVIGATION", "TRAVEL_AND_LOCAL"],
    "GAME": ["GAME"],  # Actually identified by isGame manifest tag
    "FOOD_AND_DRINK": ["FOOD_AND_DRINK"],
    "PERSONALIZATION": ["PERSONALIZATION"],  # Additionally identify icon packs by manifest
    "SHOPPING": ["SHOPPING"]
}
```

The base package name lists are built by scraping the Play Store using `playstore.py`, these lists are then merged into our categories using `merge.py`, together with static template files for base rules.

## Manually adding a rule

If you want to manually add a rule to one of Lawnchair's rulesets you can simply add it to one of the static templates in the `templates/` directory. Create one with a categories name if none exists yet.
