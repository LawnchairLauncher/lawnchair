/*
 * Copyright (C) 2012 The Android Open Source Project
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
package ch.deletescape.wallpaperpicker.glrenderer;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.microedition.khronos.opengles.GL11;

public class GLES20Canvas implements GLCanvas {
    // ************** Constants **********************
    private static final String TAG = "GLES20Canvas";
    private static final int FLOAT_SIZE = Float.SIZE / Byte.SIZE;

    private static final int COORDS_PER_VERTEX = 2;
    private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * FLOAT_SIZE;

    private static final int COUNT_FILL_VERTEX = 4;
    private static final int OFFSET_FILL_RECT = 0;

    private static final int GL_TARGET = GL11.GL_TEXTURE_2D;

    private static final float[] BOX_COORDINATES = {
            0, 0, // Fill rectangle
            1, 0,
            0, 1,
            1, 1,
            0, 0, // Draw line
            1, 1,
            0, 0, // Draw rectangle outline
            0, 1,
            1, 1,
            1, 0,
    };

    private static final String POSITION_ATTRIBUTE = "aPosition";
    private static final String MATRIX_UNIFORM = "uMatrix";
    private static final String TEXTURE_MATRIX_UNIFORM = "uTextureMatrix";
    private static final String TEXTURE_SAMPLER_UNIFORM = "uTextureSampler";
    private static final String ALPHA_UNIFORM = "uAlpha";

    private static final String TEXTURE_VERTEX_SHADER = "uniform mat4 " + MATRIX_UNIFORM + ";\n"
            + "uniform mat4 " + TEXTURE_MATRIX_UNIFORM + ";\n"
            + "attribute vec2 " + POSITION_ATTRIBUTE + ";\n"
            + "varying vec2 vTextureCoord;\n"
            + "void main() {\n"
            + "  vec4 pos = vec4(" + POSITION_ATTRIBUTE + ", 0.0, 1.0);\n"
            + "  gl_Position = " + MATRIX_UNIFORM + " * pos;\n"
            + "  vTextureCoord = (" + TEXTURE_MATRIX_UNIFORM + " * pos).xy;\n"
            + "}\n";

    private static final String TEXTURE_FRAGMENT_SHADER = "precision mediump float;\n"
            + "varying vec2 vTextureCoord;\n"
            + "uniform float " + ALPHA_UNIFORM + ";\n"
            + "uniform sampler2D " + TEXTURE_SAMPLER_UNIFORM + ";\n"
            + "void main() {\n"
            + "  gl_FragColor = texture2D(" + TEXTURE_SAMPLER_UNIFORM + ", vTextureCoord);\n"
            + "  gl_FragColor *= " + ALPHA_UNIFORM + ";\n"
            + "}\n";

    private static final int INITIAL_RESTORE_STATE_SIZE = 8;
    private static final int MATRIX_SIZE = 16;

    // Keep track of restore state
    private float[] mMatrices = new float[INITIAL_RESTORE_STATE_SIZE * MATRIX_SIZE];
    private IntArray mSaveFlags = new IntArray();

    private int mCurrentMatrixIndex;

    // Projection matrix
    private float[] mProjectionMatrix = new float[MATRIX_SIZE];

    // GL programs
    private int mTextureProgram;

    // GL buffer containing BOX_COORDINATES
    private int mBoxCoordinates;

    // Handle indices -- common
    private static final int INDEX_POSITION = 0;
    private static final int INDEX_MATRIX = 1;

    // Handle indices -- texture
    private static final int INDEX_TEXTURE_MATRIX = 2;
    private static final int INDEX_TEXTURE_SAMPLER = 3;
    private static final int INDEX_ALPHA = 4;

    private abstract static class ShaderParameter {
        public int handle;
        protected final String mName;

        public ShaderParameter(String name) {
            mName = name;
        }

        public abstract void loadHandle(int program);
    }

    private static class UniformShaderParameter extends ShaderParameter {
        public UniformShaderParameter(String name) {
            super(name);
        }

        @Override
        public void loadHandle(int program) {
            handle = GLES20.glGetUniformLocation(program, mName);
            checkError();
        }
    }

    private static class AttributeShaderParameter extends ShaderParameter {
        public AttributeShaderParameter(String name) {
            super(name);
        }

        @Override
        public void loadHandle(int program) {
            handle = GLES20.glGetAttribLocation(program, mName);
            checkError();
        }
    }

    private ShaderParameter[] mTextureParameters = {
            new AttributeShaderParameter(POSITION_ATTRIBUTE), // INDEX_POSITION
            new UniformShaderParameter(MATRIX_UNIFORM), // INDEX_MATRIX
            new UniformShaderParameter(TEXTURE_MATRIX_UNIFORM), // INDEX_TEXTURE_MATRIX
            new UniformShaderParameter(TEXTURE_SAMPLER_UNIFORM), // INDEX_TEXTURE_SAMPLER
            new UniformShaderParameter(ALPHA_UNIFORM), // INDEX_ALPHA
    };

    private final IntArray mUnboundTextures = new IntArray();

    // Temporary variables used within calculations
    private final float[] mTempMatrix = new float[32];
    private final RectF mTempSourceRect = new RectF();
    private final RectF mTempTargetRect = new RectF();
    private final float[] mTempTextureMatrix = new float[MATRIX_SIZE];
    private final int[] mTempIntArray = new int[1];

    private static final GLId mGLId = new GLES20IdImpl();

    public GLES20Canvas() {
        Matrix.setIdentityM(mTempTextureMatrix, 0);
        Matrix.setIdentityM(mMatrices, mCurrentMatrixIndex);

        FloatBuffer boxBuffer = createBuffer(BOX_COORDINATES);
        mBoxCoordinates = uploadBuffer(boxBuffer);

        int textureVertexShader = loadShader(GLES20.GL_VERTEX_SHADER, TEXTURE_VERTEX_SHADER);
        int textureFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, TEXTURE_FRAGMENT_SHADER);

        mTextureProgram = assembleProgram(textureVertexShader, textureFragmentShader,
                mTextureParameters);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        checkError();
    }

    private static FloatBuffer createBuffer(float[] values) {
        // First create an nio buffer, then create a VBO from it.
        int size = values.length * FLOAT_SIZE;
        FloatBuffer buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(values, 0, values.length).position(0);
        return buffer;
    }

    private int assembleProgram(int vertexShader, int fragmentShader, ShaderParameter[] params) {
        int program = GLES20.glCreateProgram();
        checkError();
        if (program == 0) {
            throw new RuntimeException("Cannot create GL program: " + GLES20.glGetError());
        }
        GLES20.glAttachShader(program, vertexShader);
        checkError();
        GLES20.glAttachShader(program, fragmentShader);
        checkError();
        GLES20.glLinkProgram(program);
        checkError();
        int[] mLinkStatus = mTempIntArray;
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, mLinkStatus, 0);
        if (mLinkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        for (ShaderParameter param : params) {
            param.loadHandle(program);
        }
        return program;
    }

    private static int loadShader(int type, String shaderCode) {
        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        checkError();
        GLES20.glCompileShader(shader);
        checkError();

        return shader;
    }

    @Override
    public void setSize(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        checkError();
        Matrix.setIdentityM(mMatrices, mCurrentMatrixIndex);
        Matrix.orthoM(mProjectionMatrix, 0, 0, width, 0, height, -1, 1);
        Matrix.translateM(mMatrices, mCurrentMatrixIndex, 0, height, 0);
        Matrix.scaleM(mMatrices, mCurrentMatrixIndex, 1, -1, 1);
    }

    @Override
    public void clearBuffer() {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        checkError();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        checkError();
    }

    // This is a faster version of translate(x, y, z) because
    // (1) we knows z = 0, (2) we inline the Matrix.translateM call,
    // (3) we unroll the loop
    @Override
    public void translate(float x, float y) {
        int index = mCurrentMatrixIndex;
        float[] m = mMatrices;
        m[index + 12] += m[index + 0] * x + m[index + 4] * y;
        m[index + 13] += m[index + 1] * x + m[index + 5] * y;
        m[index + 14] += m[index + 2] * x + m[index + 6] * y;
        m[index + 15] += m[index + 3] * x + m[index + 7] * y;
    }

    @Override
    public void rotate(float angle, float x, float y, float z) {
        if (angle == 0f) {
            return;
        }
        float[] temp = mTempMatrix;
        Matrix.setRotateM(temp, 0, angle, x, y, z);
        float[] matrix = mMatrices;
        int index = mCurrentMatrixIndex;
        Matrix.multiplyMM(temp, MATRIX_SIZE, matrix, index, temp, 0);
        System.arraycopy(temp, MATRIX_SIZE, matrix, index, MATRIX_SIZE);
    }

    @Override
    public void save(int saveFlags) {
        boolean saveMatrix = (saveFlags & SAVE_FLAG_MATRIX) == SAVE_FLAG_MATRIX;
        if (saveMatrix) {
            int currentIndex = mCurrentMatrixIndex;
            mCurrentMatrixIndex += MATRIX_SIZE;
            if (mMatrices.length <= mCurrentMatrixIndex) {
                mMatrices = Arrays.copyOf(mMatrices, mMatrices.length * 2);
            }
            System.arraycopy(mMatrices, currentIndex, mMatrices, mCurrentMatrixIndex, MATRIX_SIZE);
        }
        mSaveFlags.add(saveFlags);
    }

    @Override
    public void restore() {
        int restoreFlags = mSaveFlags.removeLast();
        boolean restoreMatrix = (restoreFlags & SAVE_FLAG_MATRIX) == SAVE_FLAG_MATRIX;
        if (restoreMatrix) {
            mCurrentMatrixIndex -= MATRIX_SIZE;
        }
    }

    private void setPosition(ShaderParameter[] params, int offset) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mBoxCoordinates);
        checkError();
        GLES20.glVertexAttribPointer(params[INDEX_POSITION].handle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false, VERTEX_STRIDE, offset * VERTEX_STRIDE);
        checkError();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        checkError();
    }

    private void draw(ShaderParameter[] params, int type, int count, float x, float y, float width,
                      float height) {
        setMatrix(params, x, y, width, height);
        int positionHandle = params[INDEX_POSITION].handle;
        GLES20.glEnableVertexAttribArray(positionHandle);
        checkError();
        GLES20.glDrawArrays(type, 0, count);
        checkError();
        GLES20.glDisableVertexAttribArray(positionHandle);
        checkError();
    }

    private void setMatrix(ShaderParameter[] params, float x, float y, float width, float height) {
        Matrix.translateM(mTempMatrix, 0, mMatrices, mCurrentMatrixIndex, x, y, 0f);
        Matrix.scaleM(mTempMatrix, 0, width, height, 1f);
        Matrix.multiplyMM(mTempMatrix, MATRIX_SIZE, mProjectionMatrix, 0, mTempMatrix, 0);
        GLES20.glUniformMatrix4fv(params[INDEX_MATRIX].handle, 1, false, mTempMatrix, MATRIX_SIZE);
        checkError();
    }

    @Override
    public void drawTexture(BasicTexture texture, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        copyTextureCoordinates(texture, mTempSourceRect);
        mTempTargetRect.set(x, y, x + width, y + height);
        convertCoordinate(mTempSourceRect, mTempTargetRect, texture);
        drawTextureRect(texture, mTempSourceRect, mTempTargetRect);
    }

    private static void copyTextureCoordinates(BasicTexture texture, RectF outRect) {
        outRect.set(0, 0, texture.getWidth(), texture.getHeight());
    }

    @Override
    public void drawTexture(BasicTexture texture, RectF source, RectF target) {
        if (target.width() <= 0 || target.height() <= 0) {
            return;
        }
        mTempSourceRect.set(source);
        mTempTargetRect.set(target);

        convertCoordinate(mTempSourceRect, mTempTargetRect, texture);
        drawTextureRect(texture, mTempSourceRect, mTempTargetRect);
    }

    private void drawTextureRect(BasicTexture texture, RectF source, RectF target) {
        setTextureMatrix(source);
        drawTextureRect(texture, mTempTextureMatrix, target);
    }

    private void setTextureMatrix(RectF source) {
        mTempTextureMatrix[0] = source.width();
        mTempTextureMatrix[5] = source.height();
        mTempTextureMatrix[12] = source.left;
        mTempTextureMatrix[13] = source.top;
    }

    // This function changes the source coordinate to the texture coordinates.
    // It also clips the source and target coordinates if it is beyond the
    // bound of the texture.
    private static void convertCoordinate(RectF source, RectF target, BasicTexture texture) {
        int width = texture.getWidth();
        int height = texture.getHeight();
        int texWidth = texture.getTextureWidth();
        int texHeight = texture.getTextureHeight();
        // Convert to texture coordinates
        source.left /= texWidth;
        source.right /= texWidth;
        source.top /= texHeight;
        source.bottom /= texHeight;

        // Clip if the rendering range is beyond the bound of the texture.
        float xBound = (float) width / texWidth;
        if (source.right > xBound) {
            target.right = target.left + target.width() * (xBound - source.left) / source.width();
            source.right = xBound;
        }
        float yBound = (float) height / texHeight;
        if (source.bottom > yBound) {
            target.bottom = target.top + target.height() * (yBound - source.top) / source.height();
            source.bottom = yBound;
        }
    }

    private void drawTextureRect(BasicTexture texture, float[] textureMatrix, RectF target) {
        ShaderParameter[] params = prepareTexture(texture);
        setPosition(params, OFFSET_FILL_RECT);
        GLES20.glUniformMatrix4fv(params[INDEX_TEXTURE_MATRIX].handle, 1, false, textureMatrix, 0);
        checkError();
        draw(params, GLES20.GL_TRIANGLE_STRIP, COUNT_FILL_VERTEX, target.left, target.top,
                target.width(), target.height());
    }

    private ShaderParameter[] prepareTexture(BasicTexture texture) {
        ShaderParameter[] params;
        int program;
        params = mTextureParameters;
        program = mTextureProgram;
        prepareTexture(texture, program, params);
        return params;
    }

    private void prepareTexture(BasicTexture texture, int program, ShaderParameter[] params) {
        deleteRecycledResources();
        GLES20.glUseProgram(program);
        checkError();
        GLES20.glDisable(GLES20.GL_BLEND);
        checkError();
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        checkError();
        texture.onBind(this);
        GLES20.glBindTexture(GL_TARGET, texture.getId());
        checkError();
        GLES20.glUniform1i(params[INDEX_TEXTURE_SAMPLER].handle, 0);
        checkError();
        GLES20.glUniform1f(params[INDEX_ALPHA].handle, 1);
        checkError();
    }

    @Override
    public boolean unloadTexture(BasicTexture texture) {
        boolean unload = texture.isLoaded();
        if (unload) {
            synchronized (mUnboundTextures) {
                mUnboundTextures.add(texture.getId());
            }
        }
        return unload;
    }

    @Override
    public void deleteRecycledResources() {
        synchronized (mUnboundTextures) {
            IntArray ids = mUnboundTextures;
            if (mUnboundTextures.size() > 0) {
                mGLId.glDeleteTextures(ids.size(), ids.getInternalArray(), 0);
                ids.clear();
            }
        }
    }

    @Override
    public void setTextureParameters(BasicTexture texture) {
        GLES20.glBindTexture(GL_TARGET, texture.getId());
        checkError();
        GLES20.glTexParameteri(GL_TARGET, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TARGET, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GL_TARGET, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GL_TARGET, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    }

    @Override
    public void initializeTextureSize(BasicTexture texture, int format, int type) {
        GLES20.glBindTexture(GL_TARGET, texture.getId());
        checkError();
        int width = texture.getTextureWidth();
        int height = texture.getTextureHeight();
        GLES20.glTexImage2D(GL_TARGET, 0, format, width, height, 0, format, type, null);
    }

    @Override
    public void initializeTexture(BasicTexture texture, Bitmap bitmap) {
        GLES20.glBindTexture(GL_TARGET, texture.getId());
        checkError();
        GLUtils.texImage2D(GL_TARGET, 0, bitmap, 0);
    }

    @Override
    public void texSubImage2D(BasicTexture texture, int xOffset, int yOffset, Bitmap bitmap,
                              int format, int type) {
        GLES20.glBindTexture(GL_TARGET, texture.getId());
        checkError();
        GLUtils.texSubImage2D(GL_TARGET, 0, xOffset, yOffset, bitmap, format, type);
    }

    @Override
    public int uploadBuffer(FloatBuffer buf) {
        return uploadBuffer(buf, FLOAT_SIZE);
    }

    private int uploadBuffer(Buffer buffer, int elementSize) {
        mGLId.glGenBuffers(1, mTempIntArray, 0);
        checkError();
        int bufferId = mTempIntArray[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufferId);
        checkError();
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buffer.capacity() * elementSize, buffer,
                GLES20.GL_STATIC_DRAW);
        checkError();
        return bufferId;
    }

    public static void checkError() {
        int error = GLES20.glGetError();
        if (error != 0) {
            Throwable t = new Throwable();
            Log.e(TAG, "GL error: " + error, t);
        }
    }

    @Override
    public GLId getGLId() {
        return mGLId;
    }
}
