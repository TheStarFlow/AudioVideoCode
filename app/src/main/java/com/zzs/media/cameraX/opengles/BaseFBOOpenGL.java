package com.zzs.media.cameraX.opengles;

import android.content.Context;
import android.opengl.GLES20;

/**
 * @author zzs
 * @Date 2021/6/17
 * @describe 扩展缓存渲染
 */
public class BaseFBOOpenGL extends BaseOpenGL {

    int[] frameExt;
    int[] frameData;

    public BaseFBOOpenGL(Context context, int vertexShaderId, int fragmentShaderId) {
        super(context, vertexShaderId, fragmentShaderId);
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        releaseBuffer();
        initExt(width, height);
    }

    private void initExt(int width, int height) {
        frameExt = new int[1];
        GLES20.glGenFramebuffers(1, frameExt, 0);

        frameData = new int[1];
        GLES20.glGenTextures(1, frameData, 0);

        //do bind
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameData[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        //do operation
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameData[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        //do bind frame buffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameExt[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, frameData[0], 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);


    }

    private void releaseBuffer() {
        if (frameData != null) {
            GLES20.glDeleteTextures(1, frameData, 0);
            frameData = null;
        }
        if (frameExt != null) {
            GLES20.glDeleteFramebuffers(1, frameExt, 0);
        }
    }

    @Override
    public int onDraw(int texture) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameExt[0]);
        super.onDraw(texture);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);  //
        return frameData[0];
    }
}
