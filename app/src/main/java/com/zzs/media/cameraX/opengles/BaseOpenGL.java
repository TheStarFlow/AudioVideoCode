package com.zzs.media.cameraX.opengles;

import static android.opengl.GLES20.GL_FLOAT;

import android.content.Context;
import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * @author zzs
 * @Date 2021/6/17
 * @describe
 */
public class BaseOpenGL {

    private Context mContext;
    protected int program;
    //句柄
    protected int vPosition;
    protected int vCoord;
    protected int vTexture;
    protected int vMatrix;


    FloatBuffer textureBuffer; // 纹理坐标
    FloatBuffer vertexBuffer; //顶点坐标缓存区

    float[] VERTEX = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };

    float[] TEXTURE = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f

//            0.0f, 1.0f,//左上角
//            1.0f, 1.0f,//右上角
//            0.0f, 0.0f,//左下角
//            1.0f, 0.0f,//右下角
    };

    private int mWidth;
    private int mHeight;
    protected float[] mMatrix;


    public BaseOpenGL(Context context, int vertexShaderId, int fragmentShaderId) {
        this.mContext = context.getApplicationContext();
        vertexBuffer = ByteBuffer.allocateDirect(4 * 4 * 2).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.clear();
        vertexBuffer.put(VERTEX);

        textureBuffer = ByteBuffer.allocateDirect(4 * 4 * 2).order(ByteOrder.nativeOrder()).asFloatBuffer();
        textureBuffer.clear();
        textureBuffer.put(TEXTURE);

        String fragmentShader = OpenGLKit.readRawTextFile(mContext, fragmentShaderId);
        String vertexShader = OpenGLKit.readRawTextFile(mContext, vertexShaderId);
        program = OpenGLKit.loadProgram(fragmentShader, vertexShader);

        vPosition = GLES20.glGetAttribLocation(program, "vPosition");
        vCoord = GLES20.glGetAttribLocation(program, "vCoord");//1
        vTexture = GLES20.glGetUniformLocation(program, "vTexture");
        vMatrix = GLES20.glGetUniformLocation(program, "vMatrix");

    }


    public void setMatrix(float[] mMatrix) {
        this.mMatrix = mMatrix;
    }

    public void release() {
        GLES20.glDeleteProgram(program);
    }


    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    /**
     * 画纹理数据
     */
    public int onDraw(int texture) {
        GLES20.glViewport(0, 0, mWidth, mHeight);
        GLES20.glUseProgram(program);
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(vPosition, 2, GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(vPosition);

        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(vCoord, 2, GL_FLOAT, false, 0, textureBuffer);
        GLES20.glEnableVertexAttribArray(vCoord);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(vTexture, 0);
        beforeDraw();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return texture;
    }

    protected void beforeDraw() {
    }
}
