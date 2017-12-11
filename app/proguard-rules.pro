-keep,allowshrinking,allowoptimization class ch.deletescape.lawnchair.** {
  *;
}

-keep class ch.deletescape.lawnchair.BaseRecyclerViewFastScrollBar {
  public void setThumbWidth(int);
  public int getThumbWidth();
  public void setTrackWidth(int);
  public int getTrackWidth();
}

-keep class ch.deletescape.lawnchair.BaseRecyclerViewFastScrollPopup {
  public void setAlpha(float);
  public float getAlpha();
}

-keep class ch.deletescape.lawnchair.ButtonDropTarget {
  public int getTextColor();
}

-keep class ch.deletescape.lawnchair.CellLayout {
  public float getBackgroundAlpha();
  public void setBackgroundAlpha(float);
}

-keep class ch.deletescape.lawnchair.CellLayout$LayoutParams {
  public void setWidth(int);
  public int getWidth();
  public void setHeight(int);
  public int getHeight();
  public void setX(int);
  public int getX();
  public void setY(int);
  public int getY();
}

-keep class ch.deletescape.lawnchair.dragndrop.DragLayer$LayoutParams {
  public void setWidth(int);
  public int getWidth();
  public void setHeight(int);
  public int getHeight();
  public void setX(int);
  public int getX();
  public void setY(int);
  public int getY();
}

-keep class ch.deletescape.lawnchair.FastBitmapDrawable {
  public void setDesaturation(float);
  public float getDesaturation();
  public void setBrightness(float);
  public float getBrightness();
}

-keep class ch.deletescape.lawnchair.PreloadIconDrawable {
  public float getAnimationProgress();
  public void setAnimationProgress(float);
}

-keep class ch.deletescape.lawnchair.pageindicators.CaretDrawable {
  public float getCaretProgress();
  public void setCaretProgress(float);
}

-keep class ch.deletescape.lawnchair.Workspace {
  public float getBackgroundAlpha();
  public void setBackgroundAlpha(float);
}

-keep class com.google.android.libraries.launcherclient.* {
  *;
}

-dontwarn javax.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

-keep class ch.deletescape.lawnchair.DeferredHandler {
 *;
}

# Proguard will strip new callbacks in LauncherApps.Callback from
# WrappedCallback if compiled against an older SDK. Don't let this happen.
-keep class ch.deletescape.lawnchair.compat.** {
  *;
}

-keep class ch.deletescape.lawnchair.preferences.HiddenAppsFragment {
  *;
}
