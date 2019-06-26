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

from pprint import pprint
from requests_html import HTMLSession
from bs4 import BeautifulSoup
import json
import re
from urllib.parse import unquote

# A script to scrape the play store for relevant apps from all categories
# Note: This script is currently a huge mess and has just been hacked together until it worked as desired.
# Feel free to clean it up or improve it!

BASE_URL = 'https://play.google.com/store/apps/'
CATEGORY_URL = f'{BASE_URL}category/'
TOP_URL = f'{BASE_URL}top/category/'
NEW_URL = f'{BASE_URL}new/category/'
DETAIL_URL = f'{BASE_URL}details?id='
CATEGORIES = [
    'PERSONALIZATION',
    'BOOKS_AND_REFERENCE',
    'SOCIAL',
    'COMMUNICATION',
    'TOOLS',
    'ENTERTAINMENT',
    'EDUCATION',
    'FINANCE',
    'BUSINESS',
    'LIFESTYLE',
    'MEDICAL',
    'MUSIC_AND_AUDIO',
    'PHOTOGRAPHY',
    'VIDEO_PLAYERS',
    'HEALTH_AND_FITNESS',
    'NEWS_AND_MAGAZINES',
    'BUSINESS',
    'FOOD_AND_DRINK',
    'MAPS_AND_NAVIGATION',
    'TRAVEL_AND_LOCAL',
    'SHOPPING'
]
CATEGORIES_ = [
    'ART_AND_DESIGN',
    'AUTO_AND_VEHICLES',
    'BEAUTY',
    'BOOKS_AND_REFERENCE',
    'BUSINESS',
    'COMICS',
    'COMMUNICATION',
    'DATING',
    'EDUCATION',
    'ENTERTAINMENT',
    'EVENTS',
    'FINANCE',
    'FOOD_AND_DRINK',
    'HEALTH_AND_FITNESS',
    'HOUSE_AND_HOME',
    'LIBRARIES_AND_DEMO',
    'LIFESTYLE',
    'MAPS_AND_NAVIGATION',
    'MEDICAL',
    'MUSIC_AND_AUDIO',
    'NEWS_AND_MAGAZINES',
    'PARENTING',
    'PERSONALIZATION',
    'PHOTOGRAPHY',
    'PRODUCTIVITY',
    'SHOPPING',
    'SOCIAL',
    'SPORTS',
    'TOOLS',
    'TRAVEL_AND_LOCAL',
    'VIDEO_PLAYERS',
    'ANDROID_WEAR',
    'WEATHER',
    'GAME'
]
PACKAGE_BLACKLIST = [
    r"\.cmcm\.",
    r"\.gomo\.",
    r"cheetah",
    r"sweetlovloc",
    r"hdwallpaper",
    r"ikeyboard",
    r"com\.gau",
    r"hdtheme",
    r"com\.amber\.",
    r"com\.soko",
    r"com\.andromo\.", # App creator, not all apps with this are bad, but most of them are
    r"com\.jrj",
    r"live\.?wallpaper\.free",
    r"\.leafgreen\.",
    r"cleanmaster",
    r"emoji",
    r"cleaner\.booster",
    r"com\.toolapp",
    r"com\.jb\.",
    r"cootek",
    r"snowlife01",
    r"com\.visu",
    r"style_7",
    r"com\.triciaapps\.",
    r"bestfree",
    r"bestwall",
    r"bestliv",
    r"com\.motion",
    r"videodownloaderfor",
    r"lovesticker",
    r"com\.narvii\.amino\.x",
    r"kokowallpapers",
    r"girly",
    r"com\.jham\.",
    r"net\.pierrox\.lightning_launcher\.lp\.",
    r"ginlemon\.",
    r"s10",
    r"faceapp\.",
    r"facescan",
    r"lovetest",
    r"statussaver",
    r"battle\.?royale",
    r"webcreation\.",
    r"com\.wonderfulgames\.",
    r"com\.nexttechgamesstudio",
    r"com\.funpop\.",
    r"beautifulwall",
    r"com\.wpl\.",
    r"com\.blogspot\.euapps\.",
    r"com\.wsinc\.",
    r"cloudtv\.",
    r"\.cute\.",
    r"com\.american\.",
    r"com\.clear\.",
    r"\.cool\.",
    r"com\.free\.",
    r"com\.hd\.",
    r"amoledhd",
    r"com\.keyboard\.",
    r"com\.launcher\.wallpaper",
    r"com\.messenger\.sms\.",
    r"niceringtone",
    r"com\.pikasapps\.",
    r"com\.redraw\.",
    r"com\.ss\.",
    r"com\.thalia\.",
    r"com\.wallpaper",
    r"com\.warrior",
    r"glitter\.",
    r"hdwall",
    r"keyboard\.theme",
    r"lovequote",
    r"mobi\.infolife\.",
    r"\.horoscop",
    r"com\.bbg\.",
    r"channelpromoter",
    r"\.boost(er)?\.?cleaner",
    r"com\.ape\.",
    r"iphone",
    r"apus",
    r"boost(er)?\.?master",
    r"\.cooler\.",
    r"com\.booster\.",
    r"master\.booster",
    r"forinstagram",
    r"followers",
    r"galaxys",
    r"lionmobi",
    r"\.tohsoft\.",
    r"\.toolapp\.",
    r"com\.tool\.",
    r"free\.vpn",
    r"com\.vinwap\.",
    r"for\.whatsapp",
    r"forwhatsapp",
    r"forfacebook",
    r"frontdoor\.",
    r"free\.mp3",
    r"$theme",
    r"battery\.?save"
]
# Just making sure we get everything we want
ADDITIONAL_URLS = [
    BASE_URL,
    f"{BASE_URL}editors_choice",
    f"{BASE_URL}top",
    f"{DETAIL_URL}ch.deletescape.lawnchair.plah",
    f"{DETAIL_URL}amirz.rootless.nexuslauncher",
    f"{DETAIL_URL}com.edzondm.linebit",
    f"{DETAIL_URL}com.jndapp.line.x.iconpack",
    f"{BASE_URL}dev?id=7714575631540799503"
]
ID_MATCHER = r'\?id=(.*)'
CATEGORY_MATCHER = f'/category/(.*)'

session = HTMLSession()

category_to_apps = {}
all_apps = []

for category in CATEGORIES:
    category_to_apps[category] = []
    r = session.get(f'{TOP_URL}{category}')
    clusters = list(dict.fromkeys([f'{CATEGORY_URL}{category}'] + list(filter(lambda l: "/cluster" in l, r.html.links))))
    apps_ = []
    for cluster in clusters:
        r = session.get(cluster)
        if not 'cluster' in cluster:
            try:
                r.html.render()
            except Exception as e:
                pass
        html = BeautifulSoup(r.html.html, 'html.parser')
        apps_ += list(filter(lambda l: "/apps/details?" in l, r.html.links))
    apps = []
    for app in apps_:
        m = re.search(ID_MATCHER, app)
        if m:
            id = m.group(1)
            if len(id) < 45 and not any(re.search(filter, id.lower()) for filter in PACKAGE_BLACKLIST):
                apps.append(id)
            else:
                print(f'catched {id}')
    apps = list(dict.fromkeys(apps))
    all_apps += apps
    all_apps = list(dict.fromkeys(all_apps))
    category_to_apps[category] = apps
    with open(f'playstore/{category}', 'w') as out:
        out.write('\n'.join(apps))
        out.write('\n')

for url in ADDITIONAL_URLS:
    r = session.get(url)
    clusters = list(filter(lambda l: "/cluster" in l, r.html.links))
    ids_ = []
    for cluster in clusters:
        r = session.get(cluster)
        ids_ += list(filter(lambda l: "/apps/details?" in l, r.html.links))
    ids = []
    for id in ids_:
        m = re.search(ID_MATCHER, id)
        if m:
            id = m.group(1)
            if len(id) < 50 and not any(re.search(filter, id.lower()) for filter in PACKAGE_BLACKLIST):
                ids.append(m.group(1))
            else:
                print(f'catched {id}')
    ids = list(dict.fromkeys(ids))[:12]
    for id in ids:
        r = session.get(f'{DETAIL_URL}{id}')
        genre = r.html.find('[itemprop=genre]', first=True)
        ratings = r.html.find('span[aria-label~=ratings]', first=True)
        if not ratings or len(ratings.text) < 5:
            if ratings:
                print(f'Only: {ratings.text} ratings')
            else:
                print(f'App appears to have no ratings')
            continue
        if (not genre):
            print(f'Error at app: {id}')
            continue
        m = re.search(CATEGORY_MATCHER, genre.attrs['href'])
        if m:
            category = m.group(1)
            if category.startswith('GAME_'):
                category = 'GAME'
            #all_apps.append(id)
            if category not in category_to_apps:
                category_to_apps[category] = []
            if id not in category_to_apps[category]:
                category_to_apps[category].append(id)
                with open(f'playstore/{category}', 'a+') as out:
                    out.write(f'{id}\n')
            print(category)

for app in all_apps:
    r = session.get(f'{DETAIL_URL}{app}')
    clusters = list(filter(lambda l: "/cluster" in l, r.html.links))
    ids_ = []
    for cluster in clusters:
        r = session.get(cluster)
        ids_ += list(filter(lambda l: "/apps/details?" in l, r.html.links))
    ids = []
    for id in ids_:
        m = re.search(ID_MATCHER, id)
        if m:
            id = m.group(1)
            if len(id) < 45 and not any(re.search(filter, id.lower()) for filter in PACKAGE_BLACKLIST):
                ids.append(m.group(1))
            else:
                print(f'catched {id}')
    ids = list(dict.fromkeys(ids))[:12]
    for id in ids:
        r = session.get(f'{DETAIL_URL}{id}')
        genre = r.html.find('[itemprop=genre]', first=True)
        ratings = r.html.find('span[aria-label~=ratings]', first=True)
        if not ratings or len(ratings.text) < 5:
            if ratings:
                print(f'Only: {ratings.text} ratings')
            else:
                print(f'App appears to have no ratings')
            continue
        if (not genre):
            print(f'Error at app: {id}')
            continue
        m = re.search(CATEGORY_MATCHER, genre.attrs['href'])
        if m:
            category = m.group(1)
            if category.startswith('GAME_'):
                category = 'GAME'
            #all_apps.append(id)
            if category not in category_to_apps:
                category_to_apps[category] = []
            if id not in category_to_apps[category]:
                category_to_apps[category].append(id)
                with open(f'playstore/{category}', 'a+') as out:
                    out.write(f'{id}\n')
            print(category)

# for category in CATEGORIES:
#     apps = category_to_apps[category]
#     with open(f'playstore/{category}', 'w') as out:
#         out.write('\n'.join(apps))
#     pprint(apps)