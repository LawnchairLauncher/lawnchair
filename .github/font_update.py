import json
import os
import sys

import requests

if not ((3, 10) <= sys.version_info[:2] <= (3, 14)):
    print("🤨 This script effectiveness is guaranteed from Python 3.10 to Python 3.14")


# https://developers.google.com/fonts/docs/developer_api#sorting
# Depending on what you choose here, Lawnchair font list may be sorted differently(?).
class SortingMethod:
    SORT_UNDEFINED = "sort_undefined"
    ALPHA = "alpha"
    DATE = "date"
    POPULARITY = "popularity"
    STYLE = "style"
    TRENDING = "trending"


class FontCapability:
    CAPABILITY_UNSPECIFIED = "capability_unspecified"

    VF = "vf"
    """
    Capability: Variable Fonts

    Only show fonts that have variable axes instead of static.
    """

    WOFF2 = "woff2"
    """
    Capability: WOFF2

    Only show fonts that optimised with WOFF2 format.
    """

    FAMILY_TAGS = "family_tags"


def fetch_font_data(key: str, sorting: str, capability: str):
    url = "https://www.googleapis.com/webfonts/v1/webfonts"

    params = dict()
    params["key"] = key
    params["sort"] = sorting
    params["capability"] = capability

    response = requests.get(url, params=params)

    if response.status_code != 200:
        raise Exception(
            f"💥 Failed to fetch font data: {response.status_code} {response.text}"
        )

    return response.json()


def get_local_diff(font_data: dict):
    remote_fonts = {font["family"]: font for font in font_data.get("items", [])}

    local_fonts = dict()
    with open(LOCAL_FONT, "r", encoding="utf-8") as f:
        try:
            local_data = json.load(f)
            local_fonts = {font["family"]: font for font in local_data.get("items", [])}
        except json.JSONDecodeError:
            # Could not parse the local font file; proceeding as if there are no local fonts.
            print(f"⚠️  Warning: Failed to parse {LOCAL_FONT} as JSON. Assuming no local fonts.")

    added, removed, updated = list(), list(), list()

    for family, font in remote_fonts.items():
        if family not in local_fonts:
            added.append(font)
        else:
            local_font = local_fonts[family]
            if local_font["lastModified"] != font["lastModified"]:
                updated.append((local_font, font))

    for family in local_fonts:
        if family not in remote_fonts:
            removed.append(local_fonts[family])

    if not (added or updated or removed):
        return "🥞 Done, No changes detected"

    markdown_output = ["| ✏️ Font | Status | Version |", "| :--- | :--- | :--- |"]

    for font in added:
        markdown_output.append(f"| {font['family']} | Added | `{font['version']}` |")

    for font in removed:
        markdown_output.append(f"| {font['family']} | Removed | `{font['version']}` |")

    for old, new in updated:
        markdown_output.append(
            f"| {new['family']} | Updated from `{old['version']}` to | `{new['version']}` |"
        )

    return "\n".join(markdown_output)


LOCAL_TEST = False
if LOCAL_TEST:
    LOCAL_FONT = os.path.join("google_fonts.json")
    API_KEY = ""
else:
    LOCAL_FONT = os.getenv(
        "LAWNCHAIR_FONTS_LOCATION",
        os.path.join("lawnchair", "assets", "fonts", "google_fonts.json"),
    )
    API_KEY = os.environ["GCLOUD_FONTS_CREDENTIALS"]
SORTING = os.getenv("LAWNCHAIR_FONTS_SORTING", SortingMethod.ALPHA)
CAPABILITY = os.getenv(
    "LAWNCHAIR_FONTS_CAPABILITY", FontCapability.CAPABILITY_UNSPECIFIED
)


font_data = fetch_font_data(API_KEY, SORTING, CAPABILITY)


if os.path.exists(LOCAL_FONT):
    diff = get_local_diff(font_data)
    print(diff)
else:
    print("📝 No local font data found, skipping diff generation.")

with open(LOCAL_FONT, "w", encoding="utf-8") as f:
    json.dump(
        font_data, f, indent=None, separators=(",", ":")
    )  # Trim whitespace because those can add 2/4/6/8 additional megabytes to the file
