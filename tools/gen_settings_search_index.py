#!/usr/bin/env python3
"""Builds the settings search index from the settings screens' source.

Reads which composable each settings route shows (PreferenceNavigation.kt), then collects the
labels, descriptions and group headings that each of those composables shows, and writes
SettingsSearchIndex.kt. String resources are stored by name and looked up at runtime, so a
removed string does not break the build; it is just skipped. Run it after adding, renaming or
removing settings:

    python3 tools/gen_settings_search_index.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PREFS = os.path.join(ROOT, "lawnchair/src/app/lawnchair/ui/preferences")
NAV = os.path.join(PREFS, "navigation/PreferenceNavigation.kt")
ROUTES = os.path.join(PREFS, "navigation/PreferenceRoutes.kt")
OUT = os.path.join(PREFS, "search/SettingsSearchIndex.kt")

# Screens that are not useful as search targets.
SKIP_ROUTES = {
    "Root", "Dummy", "DebugMenu", "FeatureFlags", "SettingsSearch", "GesturesPickApp", "GesturesPickShortcut",
}
STRING = r"stringResource\((?:id\s*=\s*)?R\.string\.(\w+)"

# Screens with tabs: the composable shown on each tab, the key its entries get and the route that
# opens that tab.
TABS = {
    "Search": [
        ("DockSearchPreference", "SearchDock", "Search(SearchRoute.DOCK_SEARCH)"),
        ("DrawerSearchPreference", "SearchDrawer", "Search(SearchRoute.DRAWER_SEARCH)"),
    ],
}
EXTRA_IMPORTS = {
    "SearchDock": "app.lawnchair.ui.preferences.destinations.SearchRoute",
    "SearchDrawer": "app.lawnchair.ui.preferences.destinations.SearchRoute",
}


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def route_expressions():
    """Route name -> Kotlin expression, for routes that need no arguments."""
    exprs = {}
    for m in re.finditer(r"data (object|class) (\w+)(\(([^)]*)\))?", read(ROUTES)):
        kind, name, args = m.group(1), m.group(2), m.group(4)
        if kind == "object":
            exprs[name] = name
        elif args is not None and all("=" in a for a in args.split(",") if a.strip()):
            exprs[name] = name + "()"
    return exprs


def screen_functions():
    """Composable function name -> route name, from the navigation graph."""
    result = {}
    nav = read(NAV)
    for m in re.finditer(r"composable<(\w+)>", nav):
        body = nav[m.end():m.end() + 400]
        call = re.search(r"\{[^{}]*?\b([A-Z]\w*)\(", body)
        if call and call.group(1) not in result:
            result[call.group(1)] = m.group(1)
    return result


def function_bodies():
    """Top-level composable function name -> its source, across the preference sources."""
    bodies = {}
    for base, _, files in os.walk(PREFS):
        for name in files:
            if not name.endswith(".kt"):
                continue
            src = read(os.path.join(base, name))
            starts = [(m.start(), m.group(1)) for m in re.finditer(r"^fun (\w+)\(", src, re.M)]
            for i, (start, fname) in enumerate(starts):
                end = starts[i + 1][0] if i + 1 < len(starts) else len(src)
                bodies.setdefault(fname, src[start:end])
    return bodies


def indent(line):
    return len(line) - len(line.lstrip())


def entries_of(body):
    """(label, description, is_heading) found in one screen's source, in order."""
    found = []
    lines = body.split("\n")
    for i, line in enumerate(lines):
        m = re.search(r"\b(label|heading)\s*=\s*(?:\{\s*)?" + STRING, line)
        if not m:
            continue
        # The description must be another argument of the same call: on a following line with
        # the same indentation, before the call ends (a line indented less). Deeper lines belong
        # to nested calls and are skipped.
        description = None
        for nxt in lines[i + 1:i + 30]:
            if not nxt.strip():
                continue
            if indent(nxt) < indent(line):
                break
            if indent(nxt) > indent(line):
                continue
            d = re.search(r"\bdescription\s*=\s*" + STRING, nxt)
            if d:
                description = d.group(1)
                break
        found.append((m.group(2), description, m.group(1) == "heading"))
    return found


def expanded_body(fname, bodies, screen_fns, depth=2, skip=frozenset()):
    """A screen's source plus the composables it calls (sections like AutoBackupPreferences),
    but not other screens, which are indexed on their own, nor the composables in [skip]."""
    body = bodies[fname]
    if depth == 0:
        return body
    parts = [body]
    for called in sorted(set(re.findall(r"\b([A-Z]\w*)\(", body))):
        if called != fname and called in bodies and called not in screen_fns and called not in skip:
            parts.append(expanded_body(called, bodies, screen_fns, depth - 1, skip))
    return "\n".join(parts)


def screen_title(body):
    layout = re.search(r"Preference(?:Layout(?:LazyColumn)?|SearchScaffold|Scaffold)\(", body)
    if not layout:
        return None
    m = re.search(r"\blabel\s*=\s*" + STRING, body[layout.end():layout.end() + 400])
    return m.group(1) if m else None


def main():
    exprs = route_expressions()
    screens = screen_functions()
    bodies = function_bodies()
    entries = []
    seen = set()
    titles = {}
    for fname, route in screens.items():
        if route in SKIP_ROUTES or route not in exprs or fname not in bodies:
            continue
        tabs = TABS.get(route, [])
        title = screen_title(bodies[fname])
        sections = [(route, expanded_body(fname, bodies, set(screens), skip={t[0] for t in tabs}))]
        for tab_fn, key, expr in tabs:
            if tab_fn in bodies:
                exprs[key] = expr
                sections.append((key, expanded_body(tab_fn, bodies, set(screens))))
        for key, body in sections:
            if title:
                titles[key] = title
            for label, description, is_heading in entries_of(body):
                if label == title or (label, key) in seen:
                    continue
                seen.add((label, key))
                entries.append((label, description, key, is_heading))

    # Dashboard categories lead to whole screens.
    dashboard = bodies.get("PreferencesDashboard", "")
    for m in re.finditer(r"PreferenceCategory\((.*?)onNavigate\s*=\s*\{\s*onNavigate\((\w+)", dashboard, re.S):
        label = re.search(r"label\s*=\s*" + STRING, m.group(1))
        route = m.group(2)
        if label and route in exprs and (label.group(1), route) not in seen:
            seen.add((label.group(1), route))
            entries.append((label.group(1), None, route, True))
            titles.setdefault(route, label.group(1))

    routes = sorted({e[2] for e in entries})
    out = [
        "package app.lawnchair.ui.preferences.search",
        "",
    ] + sorted(
        {f"import app.lawnchair.ui.preferences.navigation.{r}" for r in set(routes) | {"PreferenceRoute"} if r not in EXTRA_IMPORTS}
        | {f"import app.lawnchair.ui.preferences.navigation.{exprs[r].split('(')[0]}" for r in routes if r in EXTRA_IMPORTS}
        | {f"import {EXTRA_IMPORTS[r]}" for r in routes if r in EXTRA_IMPORTS}
    ) + [
        "",
        "// Generated by tools/gen_settings_search_index.py, do not edit by hand.",
        "",
        "internal val settingsSearchEntries = listOf(",
    ]
    for label, description, route, is_heading in entries:
        desc = f'"{description}"' if description else "null"
        out.append(
            f'    SettingsSearchEntry("{label}", {desc}, "{route}", "{titles.get(route, label)}", '
            f"{'true' if is_heading else 'false'}),"
        )
    out += [")", "", "internal fun settingsSearchRoute(screen: String): PreferenceRoute? = when (screen) {"]
    for route in routes:
        out.append(f'    "{route}" -> {exprs[route]}')
    out += ["    else -> null", "}", ""]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print(f"{len(entries)} entries in {len(routes)} screens -> {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    sys.exit(main())
