#!/usr/bin/env python3
"""Count physical lines in tracked files, including upstream code, excluding submodule contents.

Run from the repository root: python3 scripts/repo_metrics.py
Counts include comments/blank lines. They describe this fork, not code authored by its maintainer.
"""
from collections import Counter
from pathlib import Path
import subprocess

SOURCE = {'.kt', '.java', '.aidl', '.c', '.cpp', '.h', '.hpp'}
TEST_PARTS = {'test', 'tests', 'androidTest', 'multivalentTests', 'testsForDevice'}
counts = Counter()
for raw in subprocess.check_output(['git', 'ls-files', '-z']).split(b'\0'):
    if not raw:
        continue
    path = Path(raw.decode('utf-8', 'surrogateescape'))
    if not path.is_file():
        continue
    counts['tracked_files'] += 1
    suffix = path.suffix.lower()
    if suffix in SOURCE:
        role = 'test' if TEST_PARTS.intersection(path.parts) else 'production'
        counts[f'{role}_source_files'] += 1
        counts[f'{role}_source_lines'] += len(path.read_bytes().splitlines())
    if suffix == '.kt':
        counts['kotlin_files'] += 1
        counts['kotlin_lines'] += len(path.read_bytes().splitlines())
    if suffix == '.md':
        counts['markdown_files'] += 1
for key in ('tracked_files', 'production_source_files', 'production_source_lines',
            'test_source_files', 'test_source_lines', 'kotlin_files', 'kotlin_lines', 'markdown_files'):
    print(f'{key}: {counts[key]:,}')
