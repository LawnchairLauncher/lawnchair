 #     This file is part of Lawnchair Launcher.
 #
 #     Lawnchair Launcher is free software: you can redistribute it and/or modify
 #     it under the terms of the GNU General Public License as published by
 #     the Free Software Foundation, either version 3 of the License, or
 #     (at your option) any later version.
 #
 #     Lawnchair Launcher is distributed in the hope that it will be useful,
 #     but WITHOUT ANY WARRANTY; without even the implied warranty of
 #     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 #     GNU General Public License for more details.
 #
 #     You should have received a copy of the GNU General Public License
 #     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.

from pathlib import Path
import shutil
import time
import sys

CATEGORY_MAP = {
    "HEALTH_AND_FITNESS": ["HEALTH_AND_FITNESS", "MEDICAL"],
    "KNOWLEDGE_AND_REFERENCE": ["EDUCATION", "BOOKS_AND_REFERENCE", "WEATHER"],
    "NEWS": ["NEWS_AND_MAGAZINES"],
    "ENTERTAINMENT": ["ENTERTAINMENT", "MUSIC_AND_AUDIO", "VIDEO_PLAYERS", "MUSIC_AND_AUDIO"],
    "MUSIC": ["MUSIC_AND_AUDIO"],
    "LIFESTYLE": ["LIFESTYLE", "BEAUTY", "SHOPPING"],
    "PHOTOGRAPHY": ["PHOTOGRAPHY"],
    "BUSINESS_AND_PRODUCTIVITY": ["PRODUCTIVITY", "BUSINESS", "FINANCE"],
    "TOOLS": ["TOOLS", "VIDEO_PLAYERS"],
    "COMMUNICATION": ["COMMUNICATION", "SOCIAL"],
    "TRAVEL_AND_NAVIGATION": ["MAPS_AND_NAVIGATION", "TRAVEL_AND_LOCAL"],
    "GAME": ["GAME"],  # Actually identified by isGame manifest tag which not all apps have :(
    "FOOD_AND_DRINK": ["FOOD_AND_DRINK"],
    "PERSONALIZATION": ["PERSONALIZATION"]  # Additionally identify icon packs by manifest
}

IN_PATH = "playstore"
OUT_PATH = "../assets/flowerpot/"
TEMPLATE_PATH = "templates"
FORMAT_VERSION = "1"
FORMAT_VERSION_HUMAN = "azalea"

out_p = Path(OUT_PATH)
if out_p.exists():
    shutil.rmtree(out_p)

try:  
    out_p.mkdir()
except OSError:  
    print ("Creation of the directory %s failed" % OUT_PATH)

for category in CATEGORY_MAP.keys():
    with open("%s/%s" % (OUT_PATH, category), "a+") as out:
        out.write("# generated at %s using %s\n" % (time.ctime(), sys.argv[0]))
        out.write("# format: flowerpot-%s (%s)\n" % (FORMAT_VERSION, FORMAT_VERSION_HUMAN))
        out.write("# specs: https://del.dog/flowerpot\n")
        out.write("$%s\n" % FORMAT_VERSION)
        template = Path("%s/%s" % (TEMPLATE_PATH, category))
        if template.exists():
            out.write("# STATIC TEMPLATE\n")
            out.write(template.read_text())
        for origin in CATEGORY_MAP[category]:
            input = Path("%s/%s" % (IN_PATH, origin))
            if input.exists():
                out.write("# %s\n" % origin)
                out.write(input.read_text())
            else:
                print("No input file found for %s" % origin)
