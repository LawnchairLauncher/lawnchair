from apkutils import extract_strings
from bs4 import BeautifulSoup, NavigableString
from pathlib import Path
import shutil
import os
import argparse

my_parser = argparse.ArgumentParser(description='List the content of a folder')
my_parser.add_argument('path', type=str, help='Path of the APK to import from')
my_parser.add_argument('string_name', type=str, help='Name of the string to import')

args = my_parser.parse_args()

apk_path = args.path
string_name = args.string_name
strings = extract_strings(apk_path)

p = Path(__file__)
project_root = p.parent.parent.parent
res_root = project_root / 'lawnchair' / 'res'

def add_to_xml(locale, string_name, string):
  folder_name = 'values' if locale == '' else f'values-{locale}'
  print(f'{folder_name}: {string}')
  file_path = res_root / folder_name / 'strings.xml'
  if not file_path.exists():
    os.makedirs(file_path.parent, exist_ok=True)
    shutil.copy(p.parent / 'template.xml', file_path)
  with open(file_path, 'r') as f:
    data = f.read()
  bs = BeautifulSoup(data, "xml")
  existing = bs.find('string', {'name': string_name})
  if existing is not None:
    return
  new_tag = bs.new_tag('string')
  new_tag['name'] = string_name
  new_tag.insert(0, NavigableString(string))
  tag_string = str(new_tag)

  lines = data.split('\n')
  insert_at = lines.index('</resources>')
  lines.insert(insert_at, f'    {tag_string}')
  result = '\n'.join(lines)
  with open(file_path, 'w') as f:
    f.write(result)

locales = strings[string_name]
for locale, string in locales.items():
  add_to_xml(locale, string_name, string)
