package com.android.quickstep.hints;

import android.content.Context;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.widget.FrameLayout;

public class ProactiveHintsContainer extends FrameLayout {

  public static final FloatProperty<ProactiveHintsContainer> HINT_VISIBILITY =
      new FloatProperty<ProactiveHintsContainer>("hint_visibility") {
        @Override
        public void setValue(ProactiveHintsContainer proactiveHintsContainer, float v) {
          proactiveHintsContainer.setHintVisibility(v);
        }

        @Override
        public Float get(ProactiveHintsContainer proactiveHintsContainer) {
          return proactiveHintsContainer.mHintVisibility;
        }
      };

  private float mHintVisibility;

  public ProactiveHintsContainer(Context context) {
    super(context);
  }

  public ProactiveHintsContainer(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ProactiveHintsContainer(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public ProactiveHintsContainer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  public void setView(View v) {
    removeAllViews();
    addView(v);
  }

  public void setHintVisibility(float v) {
    if (v == 1) {
      setVisibility(VISIBLE);
    } else {
      setVisibility(GONE);
    }
    mHintVisibility = v;
  }
}
