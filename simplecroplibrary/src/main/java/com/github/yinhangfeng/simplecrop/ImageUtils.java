package com.github.yinhangfeng.simplecrop;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by yhf on 2015/8/7.
 */
public class ImageUtils {
    private static final String TAG = "ImageUtils";

    public static boolean compressImage(Bitmap bitmap, File outFile) {
        if (bitmap == null) {
            return false;
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95,
                    out);
            return true;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "compressImage FileNotFoundException", e);
        } finally {
            if(out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
        return false;
    }

    /**
     * 裁剪图片
     * @param bitmap 原始图片
     * @param x 起点x
     * @param y 起点y
     * @param cropWidth 裁剪区域width
     * @param cropHeight 裁剪区域height
     * @param degree 旋转角度
     * @param outWidth 输出宽度
     * @param outHeight 输出高度
     */
    public static Bitmap cropBitmap(Bitmap bitmap, int x, int y, int cropWidth, int cropHeight, float degree, int outWidth, int outHeight) {
        //规范旋转角度
        degree %= 360f;
        if(degree < 0) {
            degree += 360f;
        }
        if (degree >= 315f || degree < 45f) {
            degree = 0;
        } else if (degree >= 45f && degree < 135f) {
            degree = 90f;
        } else if (degree >= 135f && degree < 225f) {
            degree = 180f;
        } else if (degree >= 225f && degree < 315f) {
            degree = 270f;
        }
        //防止裁剪区域超出图片
        int bmWidth = bitmap.getWidth();
        int bmHeight = bitmap.getHeight();
        cropWidth = Math.min(cropWidth, bmWidth);
        cropHeight = Math.min(cropHeight, bmHeight);
        int xBound = bmWidth - cropWidth;
        int yBound = bmHeight - cropHeight;
        if(x < 0) {
            x = 0;
        } else if(x > xBound) {
            x = xBound;
        }
        if(y < 0) {
            y = 0;
        } else if(y > yBound) {
            y = yBound;
        }
        //裁剪缩放图片
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        matrix.postScale((float) outWidth / cropWidth, (float)outHeight / cropHeight);
        return Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight, matrix, true);
    }
}
