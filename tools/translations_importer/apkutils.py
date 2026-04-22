from bs4 import BeautifulSoup
import os
import subprocess
import re

string_def_pattern = '^    resource 0x[0123456789abcdef]{8} string\/(.+)$'
locale_def_pattern = '^      \\(((?:[a-z]|[A-Z]|-|\\+)*)\\)'

def find_aapt2():
  android_home = os.environ['ANDROID_HOME']
  build_tools = f'{android_home}/build-tools'
  latest = sorted(os.listdir(build_tools))[-1]
  if latest is None:
    return
  aapt2 = f'{build_tools}/{latest}/aapt2'
  return aapt2

aapt2_path = find_aapt2()

def execute_aapt2(args):
  output = subprocess.run([aapt2_path] + args, stdout=subprocess.PIPE, text=True)
  return output.stdout.split('\n')

def extract_strings(apk_path):
  lines = execute_aapt2(['dump', 'resources', apk_path])
  
  all_map = {}

  current_string = None
  current_map = {}
  for line in lines:
    if not line.startswith('      ('):
      if current_string is not None:
        if len(current_map) > 0:
          all_map[current_string] = current_map
        current_string = None
        current_map = {}
      if line.startswith('    resource'):
        matches = re.findall(string_def_pattern, line)
        if len(matches) == 0:
          continue
        string_name = matches[0]
        current_string = string_name
        current_map = {}
      continue
    if current_string is not None:
      locale = re.findall(locale_def_pattern, line)[0]
      line = line[len(locale)+9:]
      if not line.startswith('"'):
        continue
      string = line[1:-1]
      current_map[locale] = string
  
  if current_string is not None:
    all_map[current_string] = current_map
    current_string = None
    current_map = {}

  return all_map
