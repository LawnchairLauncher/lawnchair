package ch.deletescape.wallpaperpicker.glrenderer;

import android.opengl.GLES20;

public class GLES20IdImpl implements GLId {
    private final int[] mTempIntArray = new int[1];

    @Override
    public int generateTexture() {
        GLES20.glGenTextures(1, mTempIntArray, 0);
        GLES20Canvas.checkError();
        return mTempIntArray[0];
    }

    @Override
    public void glGenBuffers(int n, int[] buffers, int offset) {
        GLES20.glGenBuffers(n, buffers, offset);
        GLES20Canvas.checkError();
    }

    @Override
    public void glDeleteTextures(int n, int[] textures, int offset) {
        GLES20.glDeleteTextures(n, textures, offset);
        GLES20Canvas.checkError();
    }


}
