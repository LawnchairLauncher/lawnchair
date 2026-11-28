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


def entries_of(body):
    """(label, description, is_heading) found in one screen's source, in order."""
    found = []
    lines = body.split("\n")
    for i, line in enumerate(lines):
        m = re.search(r"\b(label|heading)\s*=\s*(?:\{\s*)?" + STRING, line)
        if not m:
            continue
        description = None
        for nxt in lines[i + 1:i + 4]:
            d = re.search(r"\bdescription\s*=\s*" + STRING, nxt)
            if d:
                description = d.group(1)
                break
        found.append((m.group(2), description, m.group(1) == "heading"))
    return found


def expanded_body(fname, bodies, screen_fns, depth=2):
    """A screen's source plus the composables it calls (sections like AutoBackupPreferences),
    but not other screens, which are indexed on their own."""
    body = bodies[fname]
    if depth == 0:
        return body
    parts = [body]
    for called in sorted(set(re.findall(r"\b([A-Z]\w*)\(", body))):
        if called != fname and called in bodies and called not in screen_fns:
            parts.append(expanded_body(called, bodies, screen_fns, depth - 1))
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
        body = expanded_body(fname, bodies, set(screens))
        title = screen_title(bodies[fname])
        if title:
            titles[route] = title
        for label, description, is_heading in entries_of(body):
            if label == title or (label, route) in seen:
                continue
            seen.add((label, route))
            entries.append((label, description, route, is_heading))

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
    ] + [f"import app.lawnchair.ui.preferences.navigation.{r}" for r in sorted(set(routes) | {"PreferenceRoute"})] + [
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
