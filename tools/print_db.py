#!/usr/bin/env python2.5

import cgi
import codecs
import os
import pprint
import re
import shutil
import sys
import sqlite3

SCREENS = 0
COLUMNS = 4
ROWS = 4
HOTSEAT_SIZE = 4
CELL_SIZE = 110

CONTAINER_DESKTOP = -100
CONTAINER_HOTSEAT = -101

DIR = "db_files"
AUTO_FILE = DIR + "/launcher.db"
INDEX_FILE = DIR + "/index.html"

def usage():
  print "usage: print_db.py launcher.db <4x4|5x5|5x6|...> -- prints a launcher.db with"
  print "       the specified grid size (rows x cols)"
  print "usage: print_db.py <4x4|5x5|5x6|...> -- adb pulls a launcher.db from a device"
  print "       and prints it with the specified grid size (rows x cols)"
  print
  print "The dump will be created in a directory called db_files in cwd."
  print "This script will delete any db_files directory you have now"


def make_dir():
  shutil.rmtree(DIR, True)
  os.makedirs(DIR)

def adb_root_remount():
  os.system("adb root")
  os.system("adb remount")

def pull_file(fn):
  print "pull_file: " + fn
  rv = os.system("adb pull"
    + " /data/data/com.android.launcher3/databases/launcher.db"
    + " " + fn);
  if rv != 0:
    print "adb pull failed"
    sys.exit(1)

def get_favorites(conn):
  c = conn.cursor()
  c.execute("SELECT * FROM favorites")
  columns = [d[0] for d in c.description]
  rows = []
  for row in c:
    rows.append(row)
  return columns,rows

def get_screens(conn):
  c = conn.cursor()
  c.execute("SELECT * FROM workspaceScreens")
  columns = [d[0] for d in c.description]
  rows = []
  for row in c:
    rows.append(row)
  return columns,rows

def print_intent(out, id, i, cell):
  if cell:
    out.write("""<span class="intent" title="%s">shortcut</span>""" % (
        cgi.escape(cell, True)
      ))


def print_icon(out, id, i, cell):
  if cell:
    icon_fn = "icon_%d.png" % id
    out.write("""<img style="width: 3em; height: 3em;" src="%s">""" % ( icon_fn ))
    f = file(DIR + "/" + icon_fn, "w")
    f.write(cell)
    f.close()

def print_icon_type(out, id, i, cell):
  if cell == 0:
    out.write("Application (%d)" % cell)
  elif cell == 1:
    out.write("Shortcut (%d)" % cell)
  elif cell == 2:
    out.write("Folder (%d)" % cell)
  elif cell == 4:
    out.write("Widget (%d)" % cell)
  elif cell:
    out.write("%d" % cell)

def print_cell(out, id, i, cell):
  if not cell is None:
    out.write(cgi.escape(unicode(cell)))

FUNCTIONS = {
  "intent": print_intent,
  "icon": print_icon,
  "iconType": print_icon_type
}

def render_cell_info(out, cell, occupied):
  if cell is None:
    out.write("    <td width=%d height=%d></td>\n" %
        (CELL_SIZE, CELL_SIZE))
  elif cell == occupied:
    pass
  else:
    cellX = cell["cellX"]
    cellY = cell["cellY"]
    spanX = cell["spanX"]
    spanY = cell["spanY"]
    intent = cell["intent"]
    if intent:
      title = "title=\"%s\"" % cgi.escape(cell["intent"], True)
    else:
      title = ""
    out.write(("    <td colspan=%d rowspan=%d width=%d height=%d"
        + " bgcolor=#dddddd align=center valign=middle %s>") % (
          spanX, spanY,
          (CELL_SIZE*spanX), (CELL_SIZE*spanY),
          title))
    itemType = cell["itemType"]
    if itemType == 0:
      out.write("""<img style="width: 4em; height: 4em;" src="icon_%d.png">\n""" % ( cell["_id"] ))
      out.write("<br/>\n")
      out.write(cgi.escape(cell["title"]) + " <br/><i>(app)</i>")
    elif itemType == 1:
      out.write("""<img style="width: 4em; height: 4em;" src="icon_%d.png">\n""" % ( cell["_id"] ))
      out.write("<br/>\n")
      out.write(cgi.escape(cell["title"]) + " <br/><i>(shortcut)</i>")
    elif itemType == 2:
      out.write("""<i>folder</i>""")
    elif itemType == 4:
      out.write("<i>widget %d</i><br/>\n" % cell["appWidgetId"])
    else:
      out.write("<b>unknown type: %d</b>" % itemType)
    out.write("</td>\n")

def render_screen_info(out, screen):
  out.write("<tr>")
  out.write("<td>%s</td>" % (screen["_id"]))
  out.write("<td>%s</td>" % (screen["screenRank"]))
  out.write("</tr>")

def process_file(fn):
  global SCREENS, COLUMNS, ROWS, HOTSEAT_SIZE
  print "process_file: " + fn
  conn = sqlite3.connect(fn)
  columns,rows = get_favorites(conn)
  screenCols, screenRows = get_screens(conn)

  data = [dict(zip(columns,row)) for row in rows]
  screenData = [dict(zip(screenCols, screenRow)) for screenRow in screenRows]

  # Calculate the proper number of screens, columns, and rows in this db
  screensIdMap = []
  hotseatIdMap = []
  HOTSEAT_SIZE = 0
  for d in data:
    if d["spanX"] is None:
      d["spanX"] = 1
    if d["spanY"] is None:
      d["spanY"] = 1
    if d["container"] == CONTAINER_DESKTOP:
      if d["screen"] not in screensIdMap:
        screensIdMap.append(d["screen"])
      COLUMNS = max(COLUMNS, d["cellX"] + d["spanX"])
      ROWS = max(ROWS, d["cellX"] + d["spanX"])
    elif d["container"] == CONTAINER_HOTSEAT:
      hotseatIdMap.append(d["screen"])
      HOTSEAT_SIZE = max(HOTSEAT_SIZE, d["screen"] + 1)
  SCREENS = len(screensIdMap)

  out = codecs.open(INDEX_FILE, encoding="utf-8", mode="w")
  out.write("""<html>
<head>
<style type="text/css">
.intent {
  font-style: italic;
}
</style>
</head>
<body>
""")

  # Data table
  out.write("<b>Favorites table</b><br/>\n")
  out.write("""<html>
<table border=1 cellspacing=0 cellpadding=4>
<tr>
""")
  print_functions = []
  for col in columns:
    print_functions.append(FUNCTIONS.get(col, print_cell))
  for i in range(0,len(columns)):
    col = columns[i]
    out.write("""  <th>%s</th>
""" % ( col ))
  out.write("""
</tr>
""")

  for row in rows:
    out.write("""<tr>
""")
    for i in range(0,len(row)):
      cell = row[i]
      # row[0] is always _id
      out.write("""  <td>""")
      print_functions[i](out, row[0], row, cell)
      out.write("""</td>
""")
    out.write("""</tr>
""")
  out.write("""</table>
""")

  # Screens
  out.write("<br/><b>Screens</b><br/>\n")
  out.write("<table class=layout border=1 cellspacing=0 cellpadding=4>\n")
  out.write("<tr><td>Screen ID</td><td>Rank</td></tr>\n")
  for screen in screenData:
    render_screen_info(out, screen)
  out.write("</table>\n")

  # Hotseat
  hotseat = []
  for i in range(0, HOTSEAT_SIZE):
    hotseat.append(None)
  for row in data:
    if row["container"] != CONTAINER_HOTSEAT:
      continue
    screen = row["screen"]
    hotseat[screen] = row
  out.write("<br/><b>Hotseat</b><br/>\n")
  out.write("<table class=layout border=1 cellspacing=0 cellpadding=4>\n")
  for cell in hotseat:
    render_cell_info(out, cell, None)
  out.write("</table>\n")

  # Pages
  screens = []
  for i in range(0,SCREENS):
    screen = []
    for j in range(0,ROWS):
      m = []
      for k in range(0,COLUMNS):
        m.append(None)
      screen.append(m)
    screens.append(screen)
  occupied = "occupied"
  for row in data:
    # desktop
    if row["container"] != CONTAINER_DESKTOP:
      continue
    screen = screens[screensIdMap.index(row["screen"])]
    cellX = row["cellX"]
    cellY = row["cellY"]
    spanX = row["spanX"]
    spanY = row["spanY"]
    for j in range(cellY, cellY+spanY):
      for k in range(cellX, cellX+spanX):
        screen[j][k] = occupied
    screen[cellY][cellX] = row
  i=0
  for screen in screens:
    out.write("<br/><b>Screen %d</b><br/>\n" % i)
    out.write("<table class=layout border=1 cellspacing=0 cellpadding=4>\n")
    for m in screen:
      out.write("  <tr>\n")
      for cell in m:
        render_cell_info(out, cell, occupied)
      out.write("</tr>\n")
    out.write("</table>\n")
    i=i+1

  out.write("""
</body>
</html>
""")

  out.close()

def updateDeviceClassConstants(str):
  global SCREENS, COLUMNS, ROWS, HOTSEAT_SIZE
  match = re.search(r"(\d+)x(\d+)", str)
  if match:
    COLUMNS = int(match.group(1))
    ROWS = int(match.group(2))
    HOTSEAT_SIZE = 2 * int(COLUMNS / 2)
    return True
  return False

def main(argv):
  if len(argv) == 1 or (len(argv) == 2 and updateDeviceClassConstants(argv[1])):
    make_dir()
    adb_root_remount()
    pull_file(AUTO_FILE)
    process_file(AUTO_FILE)
  elif len(argv) == 2 or (len(argv) == 3 and updateDeviceClassConstants(argv[2])):
    make_dir()
    process_file(argv[1])
  else:
    usage()

if __name__=="__main__":
  main(sys.argv)
