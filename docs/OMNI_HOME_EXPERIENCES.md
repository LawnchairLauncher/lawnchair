# Omni Home experiences

The Home experiences branch adds features inside `lawnchair/`, retaining Launcher3's model,
icon cache and gesture engine. There is no new Root or Shizuku implementation. Existing upstream
Root features are not changed by this work.

## Open the features

Open Launcher settings → **Omni Hub**. A configurable **Omni Shelf** gesture and action-search
results also open the Hub. English and Arabic resources are included; the UI follows the
existing Material theme and start/end layout direction.

### Search

- App-only and on-device search share a bounded prepared-name index. Matching normalizes Unicode,
  accents, Arabic hamza/diacritics and decimal digits, supports available ICU script transliteration,
  token prefixes, initials and optional fuzzy matches. Exact matches precede fuzzy ones.
- Add comma-separated names under Hub → Search aliases, in any script. Changing a label or alias
  changes its cache key; at most 1,024 prepared entries are retained. Matching different languages
  is based on labels, transliteration and aliases, **not universal semantic translation**.
- On-device search delivers app results before shortcuts, files, contacts or network work. Other
  providers emit progressively. Web suggestions have a short debounce. Android ContentResolver
  queries use CancellationSignal; custom HTTP calls are cancelled when their search is cancelled.
- Result filters separate apps, people, files and actions. App-only mode offers relevant filters.
- Search finds published app shortcuts, built-in local actions and saved scene names. Only a tap
  or keyboard selection executes an action. Supported built-ins open Shelf/new note/scenes,
  Wi-Fi settings, Bluetooth settings or the camera. App-provided shortcuts depend on each app.
- Shortcut results follow currently visible component/profile identities, including after hiding
  an app. LauncherApps limits accessible users and shortcuts; inaccessible/locked profiles are
  not bypassed.

### Shelf

Shelf stores up to 40 text/document cards. **New note** creates a local text card. Android's
Sharesheet can send text from Omni Note+ or another app to Shelf; the share remains a draft until
Save. Pin file uses Android's document picker and a persistent read grant; opening a card grants
read access only to its chosen viewer. Shelf does not inspect other apps' private databases.

The current scene's selected apps and installed apps whose labels contain Omni are quick launch
entries. Active media controls use the existing notification-listener access, if already enabled;
listeners exist only while Shelf is subscribed. Apps exposing a MediaSession, including a compatible
Omni DJ build, can appear. Private Note+/Memoria content is not automatically readable: share/pin
it, or have Workspace publish an approved bounded text card through OmniLink.

### Scenes

Save the current Home/search appearance, then edit a scene's name, theme, up to 12 Shelf apps,
time window and optional charging/headphone conditions. Snapshots include supported status/search
visibility preferences, icon shape/pack/sizes and selected members of existing widget stacks.
They do not replace the launcher database or move/delete existing Home icons.

The automatic switch is off by default. Automatic scenes are checked while Home is visible, on
resume and time/power/headphone events. Overnight windows are supported. Equal start/end means
all day; the first matching enabled scene wins. With no match, the last applied scene remains.
No polling/background service, location permission or exact-alarm permission is added.

Apply saves the previous appearance and stack selections for **Undo last scene change**. Scene
changes reuse the existing preference store and recreate Home to apply the supported settings.
Automatic events catch optional-feature failures instead of crashing Home.

### Rule folders

Create a rule folder under Hub. It also appears in the existing App Drawer folder UI. Rules can
match app-name text, package prefix and the Android game category; filled conditions combine with
AND. Explicit includes add apps independently of conditions; explicit exclusions take precedence.
Existing manual members remain unless excluded. Hidden apps remain excluded.

Folders recompute from one shared event-driven inventory when packages, rules or hidden-app
settings change. The loader no longer enumerates all installed apps once for every folder member.
Deleting the folder removes its rule. The existing folder editor remains available for manual
membership and renaming.

### Widget stacks

Choose Hub → Widget stacks → Add stack to Home, or select **Widget stacks** in the normal widget
picker. Configure the stack by adding real widgets with Android's binding/configuration flows.
Use the arrows or swipe the toolbar to select a member; the widget content retains its own touch
handling. Editing can remove individual members. Recursive Omni stacks are rejected.

Only the selected child widget view is inflated. The nested host clears view references when
switching and stops listening while Home is not visible. LOW tier offers up to four members;
other tiers allow eight. No blur is added. Removing an outer stack deletes its child host IDs.
Widget grants may need rebinding after reinstall/restore, as with other Android widget hosts.

## OmniLink and OEM integration

Install both Launcher PR #16 and Workspace PR #194 with matching signing keys, and enable the
local control switch in Hub → Phone integration. Merely selecting Omni search does not enable
remote control. The existing public draft handoff still works separately.

| New capability | Inputs | Scope |
| --- | --- | --- |
| launcher.list_actions | optional query | Built-in actions; no execution |
| launcher.open_action | id | Known action, resumed Home required |
| launcher.list_scenes | empty | Names and IDs |
| launcher.save_scene | name | Capture current supported appearance |
| launcher.apply_scene | id | Apply existing scene, retain undo |
| launcher.list_folders | empty | Existing folders and visible counts |
| launcher.set_folder_rule | rule object | Existing folder, validated component keys |
| launcher.set_alias | componentKey, aliases | Visible app only |
| launcher.shelf.list | empty | Up to ten titles and short previews |
| launcher.shelf.publish | id, title, text | Authenticated-source text card, no URI grants |
| launcher.widget_stacks.list | empty | Existing stack counts and selection |
| launcher.widget_stacks.select | outerId, index | Already-bound widget only |
| launcher.oem.status | empty | Actual system/owner/Recents state |
| launcher.oem.suspend_app | package, suspended | Provisioned device/profile owner required |

New reads and open_action are available to NORM; PRO adds experience edits. OEM/ADMIN also
advertise the enterprise suspension request. LITE retains the receiver's existing health-only
ceiling. Unknown packages/capabilities are denied. Mutations require host approval, advertise
dry-run, and recheck local consent/deadlines. Same signing and an OEM flavor are **not** system
privileges. A manufacturer/administrator must provision device/profile ownership before enterprise
suspension can succeed. SystemUI Recents is not replaced by installing this APK.

`launcher.list_apps` includes componentKey/profileSerial for accessible profiles. Supply the
returned optional profileSerial to open_app; omitting it preserves current-profile behavior.

## Validation and limits

- `python3 scripts/test_omni_experience.py` executes actual JVM tests for Unicode/ranking,
  progressive provider behavior/cancellation, schedules, rule overrides and receiver ceilings.
  It does not depend on the known Android JVM NO-SOURCE source-set wiring.
- The existing CI builds GitHub Debug, GitHub Release and Play Debug and checks formatting and
  actual Kotlin source isolation. Serializer/Compose/Android linkage is covered by those builds.
- Baseline profile journeys now include drawer scrolling and mixed-language typing. Macrobenchmarks
  include drawer/search frames and return from Settings. The device must use Omni as default Home.
- Physical-device animation, nested widget gestures, media sessions, OEM provisioning and end-to-end
  Workspace approvals require device validation. Source or CI success is not an FPS, RAM, battery,
  size-reduction or device-compatibility measurement.

See [OMNI_PERFORMANCE_VALIDATION.md](OMNI_PERFORMANCE_VALIDATION.md) for reproducible Infinix checks.
