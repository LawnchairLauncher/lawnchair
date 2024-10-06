/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.dynamicanimation.animation;

/**
 * <p>FloatValueHolder holds a float value. FloatValueHolder provides a setter and a getter (
 * i.e. {@link #setValue(float)} and {@link #getValue()}) to access this float value. Animations can
 * be performed on a FloatValueHolder instance. During each frame of the animation, the
 * FloatValueHolder will have its value updated via {@link #setValue(float)}. The caller can
 * obtain the up-to-date animation value via {@link FloatValueHolder#getValue()}.
 *
 * <p> Here is an example for creating a {@link FlingAnimation} with a FloatValueHolder:
 * <pre class="prettyprint">
 * // Create a fling animation with an initial velocity of 5000 (pixel/s) and an initial position
 * // of 20f.
 * FloatValueHolder floatValueHolder = new FloatValueHolder(20f);
 * FlingAnimation anim = new FlingAnimation(floatValueHolder).setStartVelocity(5000);
 * anim.start();
 * </pre>
 *
 * @see SpringAnimation#SpringAnimation(FloatValueHolder)
 * @see FlingAnimation#FlingAnimation(FloatValueHolder)
 */

public class FloatValueHolder {
    private float mValue = 0.0f;

    /**
     * Constructs a holder for a float value that is initialized to 0.
     */
    public FloatValueHolder() {
    }

    /**
     * Constructs a holder for a float value that is initialized to the input value.
     *
     * @param value the value to initialize the value held in the FloatValueHolder
     */
    public FloatValueHolder(float value) {
        setValue(value);
    }

    /**
     * Sets the value held in the FloatValueHolder instance.
     *
     * @param value float value held in the FloatValueHolder instance
     */
    public void setValue(float value) {
        mValue = value;
    }

    /**
     * Returns the float value held in the FloatValueHolder instance.
     *
     * @return float value held in the FloatValueHolder instance
     */
    public float getValue() {
        return mValue;
    }
}
