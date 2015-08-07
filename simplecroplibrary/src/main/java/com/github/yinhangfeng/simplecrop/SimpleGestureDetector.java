package com.github.yinhangfeng.simplecrop;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.GestureDetector.SimpleOnGestureListener;

public class SimpleGestureDetector {
    public static final String TAG = "GestureTouchListener";

    private static final boolean DEBUG = SimpleCropImageView.DEBUG;

    public interface OnGestureListener {

        /**
         * 双击事件
         *
         * @param x x坐标
         * @param y y坐标
         */
        void onDoubleTap(float x, float y);

        /**
         * fling事件
         *
         * @param vx x方向速度
         * @param vy y方向速度
         */
        void onFling(float vx, float vy);

        /**
         * 手离开事件
         */
        void onActionUp();

        /**
         * 手放下事件
         */
        void onActionDown();

        /**
         * 缩放事件
         *
         * @param midX   缩放中点x坐标
         * @param midY   缩放中y点坐标
         * @param rScale 相对上次缩放比例
         */
        void onZoom(float midX, float midY, float rScale);

        /**
         * 拖动事件
         *
         * @param dx x方向相对拖动距离
         * @param dy y方向相对拖动距离
         */
        void onDrag(float dx, float dy);

    }

    // 缩放手势时两点间上一次的距离的平方
    private float lastZoomDistance = 0;
    // 缩放手势时两点的中点
    private float zoomMidX;
    private float zoomMidY;

    //拖动操作中上次触摸点坐标
    private float lastMotionX;
    private float lastMotionY;

    // 双击 fling 手势事件检测类
    private GestureDetector mGestureDetector;

    private OnGestureListener mOnGestureListener;

    public SimpleGestureDetector(Context context, OnGestureListener onGestureListener) {
        mOnGestureListener = onGestureListener;
        // 处理双击
        mGestureDetector = new GestureDetector(context, new SimpleOnGestureListener() {

            @Override
            public boolean onDoubleTap(MotionEvent e) {// 双击
                //if(DEBUG) Log.d(TAG, "mGestureDetector onDoubleTap");
                mOnGestureListener.onDoubleTap(e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if(DEBUG) Log.d(TAG, "flingDetector onFling vx=" + vx + " vy=" + vy);
                mOnGestureListener.onFling(vx, vy);
                return true;
            }
        });
    }

    public boolean onTouchEvent(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);
        if(action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            lastZoomDistance = 0;
            mOnGestureListener.onActionUp();
        }
        if(mGestureDetector.onTouchEvent(event)) {
            //if(DEBUG) Log.i(TAG, "onTouchEvent mGestureDetector handled");
            return true;
        }

        float x = event.getX();
        float y = event.getY();

        switch(action) {
        case MotionEvent.ACTION_DOWN:
            lastMotionX = x;
            lastMotionY = y;
            mOnGestureListener.onActionDown();
            break;
        case MotionEvent.ACTION_MOVE:
            if(MotionEventCompat.getPointerCount(event) > 1) {
                float dx = x - MotionEventCompat.getX(event, 1);
                float dy = y - MotionEventCompat.getY(event, 1);
                float distance = dx * dx + dy * dy;
                if(lastZoomDistance > 0) {
                    if(lastZoomDistance != distance) {
                        float relativeScale = (float) Math.sqrt(distance / lastZoomDistance);
                        mOnGestureListener.onZoom(zoomMidX, zoomMidY, relativeScale);
                    }
                } else {
                    zoomMidX = (x + event.getX(1)) / 2f;
                    zoomMidY = (y + event.getY(1)) / 2f;
                }
                lastZoomDistance = distance;
            } else {
                mOnGestureListener.onDrag(x - lastMotionX, y - lastMotionY);
            }
            lastMotionX = x;
            lastMotionY = y;
            break;
        case MotionEventCompat.ACTION_POINTER_DOWN:
            lastZoomDistance = 0;
            break;
        case MotionEventCompat.ACTION_POINTER_UP:
            if(MotionEventCompat.getPointerCount(event) == 2) {
                int upIndex = MotionEventCompat.getActionIndex(event);
                int activeIndex = upIndex == 0 ? 1 : 0;
                lastMotionX = MotionEventCompat.getX(event, activeIndex);
                lastMotionY = MotionEventCompat.getY(event, activeIndex);
            }
            lastZoomDistance = 0;
            break;
        }
        return true;
    }
}
