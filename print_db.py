#!/usr/bin/env python2.5

import cgi
import os
import shutil
import sys
import sqlite3

SCREENS = 5
COLUMNS = 4
ROWS = 4
HOTSEAT_SIZE = 5
CELL_SIZE = 110

DIR = "db_files"
AUTO_FILE = DIR + "/launcher.db"
INDEX_FILE = DIR + "/index.html"

def usage():
  print "usage: print_db.py launcher.db -- prints a launcher.db"
  print "usage: print_db.py -- adb pulls a launcher.db from a device"
  print "       and prints it"
  print
  print "The dump will be created in a directory called db_files in cwd."
  print "This script will delete any db_files directory you have now"


def make_dir():
  shutil.rmtree(DIR, True)
  os.makedirs(DIR)

def pull_file(fn):
  print "pull_file: " + fn
  rv = os.system("adb pull"
    + " /data/data/com.android.launcher/databases/launcher.db"
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

def print_intent(out, id, i, cell):
  if cell:
    out.write("""<span class="intent" title="%s">shortcut</span>""" % (
        cgi.escape(cell, True)
      ))


def print_icon(out, id, i, cell):
  if cell:
    icon_fn = "icon_%d.png" % id
    out.write("""<img src="%s">""" % ( icon_fn ))
    f = file(DIR + "/" + icon_fn, "w")
    f.write(cell)
    f.close()

def print_cell(out, id, i, cell):
  if not cell is None:
    out.write(cgi.escape(str(cell)))

FUNCTIONS = {
  "intent": print_intent,
  "icon": print_icon
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
      out.write("""<img src="icon_%d.png">\n""" % ( cell["_id"] ))
      out.write("<br/>\n")
      out.write(cgi.escape(cell["title"]) + " <br/><i>(app)</i>")
    elif itemType == 1:
      out.write("""<img src="icon_%d.png">\n""" % ( cell["_id"] ))
      out.write("<br/>\n")
      out.write(cgi.escape(cell["title"]) + " <br/><i>(shortcut)</i>")
    elif itemType == 2:
      out.write("""<i>folder</i>""")
    elif itemType == 3:
      out.write("""<i>live folder</i>""")
    elif itemType == 4:
      out.write("<i>widget %d</i><br/>\n" % cell["appWidgetId"])
    elif itemType == 1000:
      out.write("""<i>clock</i>""")
    elif itemType == 1001:
      out.write("""<i>search</i>""")
    elif itemType == 1002:
      out.write("""<i>photo frame</i>""")
    else:
      out.write("<b>unknown type: %d</b>" % itemType)
    out.write("</td>\n")

def process_file(fn):
  print "process_file: " + fn
  conn = sqlite3.connect(fn)
  columns,rows = get_favorites(conn)
  data = [dict(zip(columns,row)) for row in rows]

  out = file(INDEX_FILE, "w")
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

  # Hotseat
  hotseat = []
  for i in range(0, HOTSEAT_SIZE):
    hotseat.append(None)
  for row in data:
    if row["container"] != -101:
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
    screen = screens[row["screen"]]
    # desktop
    if row["container"] != -100:
      continue
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

def main(argv):
  if len(argv) == 1:
    make_dir()
    pull_file(AUTO_FILE)
    process_file(AUTO_FILE)
  elif len(argv) == 2:
    make_dir()
    process_file(argv[1])
  else:
    usage()

if __name__=="__main__":
  main(sys.argv)
