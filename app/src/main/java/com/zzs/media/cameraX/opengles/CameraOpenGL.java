package com.zzs.media.cameraX.opengles;

import android.content.Context;
import android.opengl.GLES20;

import com.zzs.media.R;


/**
 * @author zzs
 * @Date 2021/6/17
 * @describe 相机 opengl 渲染
 */
public class CameraOpenGL extends BaseOpenGL {


    public CameraOpenGL(Context context) {
        super(context, R.raw.camera_vert, R.raw.camera_frag);
    }

    @Override
    protected void beforeDraw() {
        super.beforeDraw();
        GLES20.glUniformMatrix4fv(vMatrix, 1, false, mMatrix, 0);
    }

}
