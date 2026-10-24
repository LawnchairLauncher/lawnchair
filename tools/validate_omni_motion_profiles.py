#!/usr/bin/env python3
"""Validate OmniMotionProfile cubic-Bezier curves without Android dependencies.

Run from the repository root:
    python3 tools/validate_omni_motion_profiles.py

The reported velocity/acceleration/jerk values are normalized curve-shape metrics,
not device frame-time or physical-motion measurements.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Dict, List, Tuple

NUMBER = r"([+-]?(?:\d+(?:\.\d*)?|\.\d+))f?"
CURVE_RE = re.compile(
    r"private\s+val\s+(\w+)\s*=\s*PathInterpolator\(\s*"
    + NUMBER + r"\s*,\s*" + NUMBER + r"\s*,\s*"
    + NUMBER + r"\s*,\s*" + NUMBER + r"\s*\)"
)
EXPECTED_CONTEXTS = ("AppLaunch", "DrawerSettle", "FolderClose")
EXPECTED_STYLES = ("fusion", "pixel", "ios", "samsung")
DURATION_FACTORS = {"fusion": 1.0}
DURATION_PROBES_MS = (1, 60, 100, 120, 180, 300, 500, 1000)


def cubic(p0: float, p1: float, p2: float, p3: float, t: float) -> float:
    one_minus_t = 1.0 - t
    return (
        one_minus_t ** 3 * p0
        + 3.0 * one_minus_t ** 2 * t * p1
        + 3.0 * one_minus_t * t ** 2 * p2
        + t ** 3 * p3
    )


def parameter_for_x(x: float, x1: float, x2: float) -> float:
    """Invert a monotonic cubic Bezier's x component by bounded bisection."""
    low, high = 0.0, 1.0
    for _ in range(40):
        mid = (low + high) / 2.0
        value = cubic(0.0, x1, x2, 1.0, mid)
        if value < x:
            low = mid
        else:
            high = mid
    return (low + high) / 2.0


def sample_curve(points: Tuple[float, float, float, float], samples: int) -> List[float]:
    x1, y1, x2, y2 = points
    values = []
    for i in range(samples):
        x = i / (samples - 1)
        t = parameter_for_x(x, x1, x2)
        values.append(cubic(0.0, y1, y2, 1.0, t))
    return values


def curve_metrics(values: List[float]) -> Tuple[float, float, float]:
    step = 1.0 / (len(values) - 1)
    velocity = [(b - a) / step for a, b in zip(values, values[1:])]
    acceleration = [(b - a) / step for a, b in zip(velocity, velocity[1:])]
    jerk = [(b - a) / step for a, b in zip(acceleration, acceleration[1:])]
    return (
        max((abs(v) for v in velocity), default=0.0),
        max((abs(v) for v in acceleration), default=0.0),
        max((abs(v) for v in jerk), default=0.0),
    )


def parse_curves(source: str) -> Dict[str, Tuple[float, float, float, float]]:
    curves = {}
    for match in CURVE_RE.finditer(source):
        name = match.group(1)
        curves[name] = tuple(float(match.group(i)) for i in range(2, 6))
    return curves


def parse_duration_factors(source: str) -> Dict[str, float]:
    marker = "fun scaleFolderDuration"
    if marker not in source:
        raise ValueError("scaleFolderDuration() was not found")
    block = source.split(marker, 1)[1].split("fun getRadialScaleInterpolator", 1)[0]
    factors = {"fusion": 1.0}
    for style, value in re.findall(
        r"STYLE_(PIXEL|IOS|SAMSUNG)\s*->\s*([0-9]+(?:\.[0-9]+)?)f", block
    ):
        factors[style.lower()] = float(value)
    if set(factors) != set(EXPECTED_STYLES):
        raise ValueError("Could not find all four folder duration factors")
    if "coerceAtLeast(120)" not in block:
        raise ValueError("Folder duration no longer enforces the 120 ms minimum")
    return factors


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "profile_file",
        nargs="?",
        type=Path,
        default=repo_root / "lawnchair/src/app/lawnchair/animation/OmniMotionProfile.kt",
        help="path to OmniMotionProfile.kt (default: repository source path)",
    )
    parser.add_argument("--samples", type=int, default=1001, help="samples per curve (minimum 101)")
    args = parser.parse_args()
    if args.samples < 101:
        parser.error("--samples must be at least 101")
    if not args.profile_file.is_file():
        print("ERROR: profile file not found: {}".format(args.profile_file), file=sys.stderr)
        return 2

    source = args.profile_file.read_text(encoding="utf-8")
    curves = parse_curves(source)
    expected = {
        style + context
        for style in EXPECTED_STYLES
        for context in EXPECTED_CONTEXTS
    }
    missing = sorted(expected - set(curves))
    if missing:
        print("ERROR: missing profile curves: " + ", ".join(missing), file=sys.stderr)
        return 2

    failures = []
    print("Omni motion curve validation ({} samples each)".format(args.samples))
    print("Metrics are normalized curve derivatives; not device performance measurements.")
    print("{:<23} {:>10} {:>12} {:>12} {:>12}".format("CURVE", "MAX_V", "MAX_A", "MAX_J", "END_ERROR"))
    for name in sorted(expected):
        x1, y1, x2, y2 = curves[name]
        x_values = [
            cubic(0.0, x1, x2, 1.0, i / (args.samples - 1))
            for i in range(args.samples)
        ]
        if any(b + 1e-7 < a for a, b in zip(x_values, x_values[1:])):
            failures.append(name + ": x Bezier is not monotonic")
        if any(v < -1e-5 or v > 1.00001 for v in (x1, y1, x2, y2)):
            failures.append(name + ": control point is outside [0, 1]")
        values = sample_curve((x1, y1, x2, y2), args.samples)
        end_error = max(abs(values[0]), abs(values[-1] - 1.0))
        if end_error > 1e-4:
            failures.append(name + ": endpoint error exceeds tolerance")
        if any(v < -1e-4 or v > 1.0001 for v in values):
            failures.append(name + ": interpolated value overshoots [0, 1]")
        if any(b + 1e-4 < a for a, b in zip(values, values[1:])):
            failures.append(name + ": interpolated value reverses direction")
        max_v, max_a, max_j = curve_metrics(values)
        print("{:<23} {:>10.3f} {:>12.3f} {:>12.3f} {:>12.2e}".format(
            name, max_v, max_a, max_j, end_error
        ))

    try:
        factors = parse_duration_factors(source)
    except ValueError as error:
        failures.append(str(error))
        factors = {}

    if factors:
        print("\nFolder duration floor check (milliseconds; formula mirrors Kotlin implementation)")
        print("{:<10} {}".format(
            "STYLE", " ".join("{:>8}ms".format(base) for base in DURATION_PROBES_MS)
        ))
        for style in EXPECTED_STYLES:
            durations = [max(int(base * factors[style]), 120) for base in DURATION_PROBES_MS]
            if any(duration < 120 for duration in durations):
                failures.append(style + ": duration fell below 120 ms")
            print("{:<10} {}".format(
                style, " ".join("{:>8}".format(value) for value in durations)
            ))

    if failures:
        print("\nFAIL: {} issue(s)".format(len(failures)), file=sys.stderr)
        for failure in failures:
            print(" - " + failure, file=sys.stderr)
        return 1
    print("\nPASS: all expected curves are bounded, monotonic, and reach both endpoints.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
