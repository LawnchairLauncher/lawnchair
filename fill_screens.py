#!/usr/bin/env python2.5

import cgi
import os
import shutil
import sys
import sqlite3

SCREENS = 5
COLUMNS = 4
ROWS = 4
CELL_SIZE = 110

DIR = "db_files"
AUTO_FILE = "launcher.db"

APPLICATION_COMPONENTS = [
  "com.android.calculator2/com.android.calculator2.Calculator",
  "com.android.providers.downloads.ui/com.android.providers.downloads.ui.DownloadList",
  "com.android.settings/com.android.settings.Settings",
  "com.android.mms/com.android.mms.ui.ConversationList",
  "com.android.contacts/com.android.contacts.activities.PeopleActivity",
  "com.android.contacts/com.android.contacts.activities.DialtactsActivity"
]

def usage():
  print "usage: fill_screens.py -- fills up the launcher db"


def make_dir():
  shutil.rmtree(DIR, True)
  os.makedirs(DIR)

def pull_file(fn):
  print "pull_file: " + fn
  rv = os.system("adb pull"
    + " /data/data/com.cyanogenmod.trebuchet/databases/launcher.db"
    + " " + fn);
  if rv != 0:
    print "adb pull failed"
    sys.exit(1)

def push_file(fn):
  print "push_file: " + fn
  rv = os.system("adb push"
    + " " + fn
    + " /data/data/com.cyanogenmod.trebuchet/databases/launcher.db")
  if rv != 0:
    print "adb push failed"
    sys.exit(1)

def process_file(fn):
  print "process_file: " + fn
  conn = sqlite3.connect(fn)
  c = conn.cursor()
  c.execute("DELETE FROM favorites")

  intentFormat = "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10200000;component=%s;end"

  id = 0;
  for s in range(SCREENS):
    for x in range(ROWS):
      for y in range(COLUMNS):
        id += 1
        insert = "INSERT into favorites (_id, title, intent, container, screen, cellX, cellY, spanX, spanY, itemType, appWidgetId, iconType) VALUES (%d, '%s', '%s', %d, %d, %d, %d, %d, %d, %d, %d, %d)"
        insert = insert % (id, "title", "", -100, s, x, y, 1, 1, 2, -1, 0)
        c.execute(insert)
        folder_id = id

        for z in range(15):
          id += 1
          intent = intentFormat % (APPLICATION_COMPONENTS[id % len(APPLICATION_COMPONENTS)])
          insert = "INSERT into favorites (_id, title, intent, container, screen, cellX, cellY, spanX, spanY, itemType, appWidgetId, iconType) VALUES (%d, '%s', '%s', %d, %d, %d, %d, %d, %d, %d, %d, %d)"
          insert = insert % (id, "title", intent, folder_id, 0, 0, 0, 1, 1, 0, -1, 0)
          c.execute(insert)

  conn.commit()
  c.close()

def main(argv):
  if len(argv) == 1:
    make_dir()
    pull_file(AUTO_FILE)
    process_file(AUTO_FILE)
    push_file(AUTO_FILE)
  else:
    usage()

if __name__=="__main__":
  main(sys.argv)
