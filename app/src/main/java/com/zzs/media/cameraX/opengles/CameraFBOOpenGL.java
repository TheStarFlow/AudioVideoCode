package com.zzs.media.cameraX.opengles;

import android.content.Context;
import android.opengl.GLES20;

import com.zzs.media.R;


/**
 * @author zzs
 * @Date 2021/6/17
 * @describe
 */
public class CameraFBOOpenGL extends BaseFBOOpenGL {

    public CameraFBOOpenGL(Context context) {
        super(context, R.raw.camera_vert, R.raw.camera_frag);
    }

    @Override
    protected void beforeDraw() {
        super.beforeDraw();
        GLES20.glUniformMatrix4fv(vMatrix, 1, false, mMatrix, 0);
    }
}
