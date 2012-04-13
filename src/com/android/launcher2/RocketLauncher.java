/*);
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TODO:
// background stellar matter:
//  - add some slow horizontal parallax motion, or perhaps veeeeery gradual outward drift

package com.android.launcher2;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.support.v13.dreams.BasicDream;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher.R;

import java.util.HashMap;
import java.util.Random;

public class RocketLauncher extends BasicDream {
    public static final boolean ROCKET_LAUNCHER = true;

    public static class Board extends FrameLayout
    {
        public static final boolean FIXED_STARS = true;
        public static final boolean FLYING_STARS = true;
        public static final int NUM_ICONS = 20;

        public static final float MANEUVERING_THRUST_SCALE = 0.1f; // tenth speed
        private boolean mManeuveringThrusters = false;
        private float mSpeedScale = 1.0f;

        public static final int LAUNCH_ZOOM_TIME = 400; // ms

        HashMap<ComponentName, Bitmap> mIcons;
        ComponentName[] mComponentNames;

        static Random sRNG = new Random();

        static float lerp(float a, float b, float f) {
            return (b-a)*f + a;
        }

        static float randfrange(float a, float b) {
            return lerp(a, b, sRNG.nextFloat());
        }

        static int randsign() {
            return sRNG.nextBoolean() ? 1 : -1;
        }

        static <E> E pick(E[] array) {
            if (array.length == 0) return null;
            return array[sRNG.nextInt(array.length)];
        }

        public class FlyingIcon extends ImageView {
            public static final float VMAX = 1000.0f;
            public static final float VMIN = 100.0f;
            public static final float ANGULAR_VMAX = 45f;
            public static final float ANGULAR_VMIN = 0f;
            public static final float SCALE_MIN = 0.5f;
            public static final float SCALE_MAX = 4f;

            public float v, vr;

            public final float[] hsv = new float[3];

            public float angle, anglex, angley;
            public float fuse;
            public float dist;
            public float endscale;
            public float boardCenterX, boardCenterY;

            public ComponentName component;

            public FlyingIcon(Context context, AttributeSet as) {
                super(context, as);
                setLayerType(View.LAYER_TYPE_HARDWARE, null);

                setBackgroundResource(R.drawable.flying_icon_bg);
                //android.util.Log.d("RocketLauncher", "ctor: " + this);
                hsv[1] = 1f;
                hsv[2] = 1f;
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (!mManeuveringThrusters || component == null) {
                    return false;
                }
                if (getAlpha() < 0.5f) {
                    setPressed(false);
                    return false;
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        setPressed(true);
                        Board.this.resetWarpTimer();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        final Rect hit = new Rect();
                        final Point offset = new Point();
                        getGlobalVisibleRect(hit, offset);
                        final int globx = (int) event.getX() + offset.x;
                        final int globy = (int) event.getY() + offset.y;
                        setPressed(hit.contains(globx, globy));
                        Board.this.resetWarpTimer();
                        break;
                    case MotionEvent.ACTION_UP:
                        if (isPressed()) {
                            setPressed(false);
                            postDelayed(new Runnable() {
                                public void run() {
                                    try {
                                        getContext().startActivity(new Intent(Intent.ACTION_MAIN)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            .setComponent(component));
                                    } catch (android.content.ActivityNotFoundException e) {
                                    } catch (SecurityException e) {
                                    }
                                }
                            }, LAUNCH_ZOOM_TIME);
                            endscale = 0;
                            AnimatorSet s = new AnimatorSet();
                            s.playTogether(
                                ObjectAnimator.ofFloat(this, "scaleX", 15f),
                                ObjectAnimator.ofFloat(this, "scaleY", 15f),
                                ObjectAnimator.ofFloat(this, "alpha", 0f)
                            );

                            // make sure things are still moving until the very last instant the
                            // activity is visible
                            s.setDuration((int)(LAUNCH_ZOOM_TIME * 1.25));
                            s.setInterpolator(new android.view.animation.AccelerateInterpolator(3));
                            s.start();
                        }
                        break;
                }
                return true;
            }

            public String toString() {
                return String.format("<'%s' @ (%.1f, %.1f) v=%.1f a=%.1f dist/fuse=%.1f/%.1f>",
                        "icon", getX(), getY(), v, angle, dist, fuse);
            }

            public void randomizeIcon() {
                component = pick(mComponentNames);
                setImageBitmap(mIcons.get(component));
            }

            public void randomize() {
                v = randfrange(VMIN, VMAX);
                angle = randfrange(0, 360f);
                anglex = (float) Math.sin(angle / 180. * Math.PI);
                angley = (float) Math.cos(angle / 180. * Math.PI);
                vr = randfrange(ANGULAR_VMIN, ANGULAR_VMAX) * randsign();
                endscale = randfrange(SCALE_MIN, SCALE_MAX);

                randomizeIcon();
            }
            public void reset() {
                randomize();
                boardCenterX = (Board.this.getWidth() - getWidth()) / 2;
                boardCenterY = (Board.this.getHeight() - getHeight()) / 2;
                setX(boardCenterX);
                setY(boardCenterY);
                fuse = (float) Math.max(boardCenterX, boardCenterY);
                setRotation(180-angle);
                setScaleX(0f);
                setScaleY(0f);
                dist = 0;
                setAlpha(0f);
            }
            public void update(float dt) {
                dist += v * dt;
                setX(getX() + anglex * v * dt);
                setY(getY() + angley * v * dt);
                //setRotation(getRotation() + vr * dt);
                if (endscale > 0) {
                    float scale = lerp(0, endscale, (float) Math.sqrt(dist / fuse));
                        setScaleX(scale * lerp(1f, 0.75f, (float) Math.pow((v-VMIN)/(VMAX-VMIN),3)));
                        setScaleY(scale * lerp(1f, 1.5f, (float) Math.pow((v-VMIN)/(VMAX-VMIN),3)));
                    final float q1 = fuse*0.15f;
                    final float q4 = fuse*0.75f;
                    if (dist < q1) {
                        setAlpha((float) Math.sqrt(dist/q1));
                    } else if (dist > q4) {
                        setAlpha((dist >= fuse) ? 0f : (1f-(float)Math.pow((dist-q4)/(fuse-q4),2)));
                    } else {
                        setAlpha(1f);
                    }
                }
            }
        }

        public class FlyingStar extends FlyingIcon {
            public FlyingStar(Context context, AttributeSet as) {
                super(context, as);
            }
            public void randomizeIcon() {
                setImageResource(R.drawable.widget_resize_handle_bottom);
            }
            public void randomize() {
                super.randomize();
                v = randfrange(VMAX*0.75f, VMAX*2f); // fasticate
                endscale = randfrange(1f, 2f); // ensmallen
            }
        }

        TimeAnimator mAnim;

        public Board(Context context, AttributeSet as) {
            super(context, as);

            setBackgroundColor(0xFF000000);

            LauncherApplication app = (LauncherApplication)context.getApplicationContext();
            mIcons = app.getIconCache().getAllIcons();
            mComponentNames = new ComponentName[mIcons.size()];
            mComponentNames = mIcons.keySet().toArray(mComponentNames);
        }

        private void reset() {
            removeAllViews();

            final ViewGroup.LayoutParams wrap = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);

            if (FIXED_STARS) {
                for(int i=0; i<20; i++) {
                    ImageView fixedStar = new ImageView(getContext(), null);
                    fixedStar.setImageResource(R.drawable.widget_resize_handle_bottom);
                    final float s = randfrange(0.25f, 0.75f);
                    fixedStar.setScaleX(s);
                    fixedStar.setScaleY(s);
                    fixedStar.setAlpha(0.75f);
                    addView(fixedStar, wrap);
                    fixedStar.setX(randfrange(0, getWidth()));
                    fixedStar.setY(randfrange(0, getHeight()));
                }
            }

            for(int i=0; i<NUM_ICONS*2; i++) {
                FlyingIcon nv = (FLYING_STARS && (i < NUM_ICONS))
                    ? new FlyingStar(getContext(), null)
                    : new FlyingIcon(getContext(), null);
                addView(nv, wrap);
                nv.reset();
            }

            mAnim = new TimeAnimator();
            mAnim.setTimeListener(new TimeAnimator.TimeListener() {
                public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
                    // setRotation(totalTime * 0.01f); // not as cool as you would think

                    final int START_ZOOM_TIME = 3000;
                    if (totalTime < START_ZOOM_TIME) {
                        final float x = totalTime/(float)START_ZOOM_TIME;
                        final float s = 1f-(float)Math.pow(x-1, 4);
                        setScaleX(s); setScaleY(s);
                    } else {
                        setScaleX(1.0f); setScaleY(1.0f);
                    }

                    if (mManeuveringThrusters) {
                        if (mSpeedScale > MANEUVERING_THRUST_SCALE) {
                            mSpeedScale -= (2*deltaTime/1000f);
                        }
                        if (mSpeedScale < MANEUVERING_THRUST_SCALE) {
                            mSpeedScale = MANEUVERING_THRUST_SCALE;
                        }
                    } else {
                        if (mSpeedScale < 1.0f) {
                            mSpeedScale += (deltaTime/1000f);
                        }
                        if (mSpeedScale > 1.0f) {
                            mSpeedScale = 1.0f;
                        }
                    }

                    for (int i=0; i<getChildCount(); i++) {
                        View v = getChildAt(i);
                        if (!(v instanceof FlyingIcon)) continue;
                        FlyingIcon nv = (FlyingIcon) v;
                        nv.update(deltaTime / 1000f * mSpeedScale);
                        final float scaledWidth = nv.getWidth() * nv.getScaleX();
                        final float scaledHeight = nv.getHeight() * nv.getScaleY();
                        if (   nv.getX() + scaledWidth < 0
                            || nv.getX() - scaledWidth > getWidth()
                            || nv.getY() + scaledHeight < 0 
                            || nv.getY() - scaledHeight > getHeight())
                        {
                            nv.reset();
                        }
                    }
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);

            reset();
            mAnim.start();
        }

        protected void onSizeChanged (int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w,h,oldw,oldh);
            mAnim.cancel();
            reset();
            mAnim.start();
        }


        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            mAnim.cancel();
        }

        @Override
        public boolean isOpaque() {
            return true;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            // we want to eat touch events ourselves if we're in warp speed
            return (!(ROCKET_LAUNCHER && mManeuveringThrusters));
        }

        final Runnable mEngageWarp = new Runnable() {
            @Override
            public void run() {
                mManeuveringThrusters = false;
            }
        };
        public void resetWarpTimer() {
            final Handler h = getHandler();
            h.removeCallbacks(mEngageWarp);
            h.postDelayed(mEngageWarp, 5000);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!ROCKET_LAUNCHER) {
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!mManeuveringThrusters) {
                    mManeuveringThrusters = true;
                    resetWarpTimer();
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        final int longside = metrics.widthPixels > metrics.heightPixels 
            ? metrics.widthPixels : metrics.heightPixels;

        Board b = new Board(this, null);
        setContentView(b, new ViewGroup.LayoutParams(longside, longside));
        b.setX((metrics.widthPixels - longside) / 2);
        b.setY((metrics.heightPixels - longside) / 2);
    }

    @Override
    public void onUserInteraction() {
        if (!ROCKET_LAUNCHER) {
            finish();
        }
    }
}
