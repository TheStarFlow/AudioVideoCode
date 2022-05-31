package com.zzs.media.cameraX.opengles;

import android.content.Context;
import android.opengl.GLES20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author zzs
 * @Date 2021/6/17
 * @describe
 */
public class OpenGLKit {

    public static String readRawTextFile(Context context, int rawId) {
        InputStream is = context.getResources().openRawResource(rawId);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder sb = new StringBuilder();
        try {
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public static int loadProgram(String fragmentShader, String vertexShader) {

        int vShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vShader,vertexShader);
        GLES20.glCompileShader(vShader);
        int[] state = new int[1];
        GLES20.glGetShaderiv(vShader, GLES20.GL_COMPILE_STATUS,state,0);
        if (state[0]!= GLES20.GL_TRUE)
        //加载顶点着色器失败
            throw new IllegalStateException("init vertex Shader failed"+ GLES20.glGetShaderInfoLog
                    (vShader));

        int fShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fShader,fragmentShader);
        GLES20.glCompileShader(fShader);
        GLES20.glGetShaderiv(fShader, GLES20.GL_COMPILE_STATUS,state,0);
        if (state[0]!= GLES20.GL_TRUE)
            //加载顶点着色器失败
            throw new IllegalStateException("init fragment Shader failed "+ GLES20.glGetShaderInfoLog
                    (fShader));

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program,vShader);
        GLES20.glAttachShader(program,fShader);
        GLES20.glLinkProgram(program);

        //获得状态
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, state, 0);
        if (state[0] != GLES20.GL_TRUE) {
            throw new IllegalStateException("link program:" + GLES20.glGetProgramInfoLog(program));
        }
        GLES20.glDeleteShader(vShader);
        GLES20.glDeleteShader(fShader);
        return program;
    }
}
