package com.github.yinhangfeng.simplecrop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Scroller;

public class SimpleCropImageView extends View {
    public static final String TAG = "SimpleCropImageView";

    static final boolean DEBUG = true;

    //图片Drawable对象
    private Drawable drawable;

    //当前缩放比例 setupCanvas时置为初始缩放比例
    private float scaleAdjust = 1f;
    //初始缩放比例 相对于原图
    private float startingScale = -1f;
    //最大缩放比例 相对于原图
    private float maxScale = 4f;
    //最小缩放比例 相对于原图
    private float minScale = 0.5f;

    //最后一次layout view宽高
    private int viewWidth, viewHeight;
    //view 中点坐标
    private float centerX, centerY;

    //drawable图片真实宽高
    private int imageWidth = 0, imageHeight = 0;
    //drawable在当前画布上图片宽高(因为有旋转不一定是真实宽高)
    private int imageWidthDisplay = 0, imageHeightDisplay = 0;

    //图片坐标
    private float x = 0, y = 0;
    //旋转角度 用于动画过程
    private float rotation = 0;
    //旋转中心 未缩放情况下 相对于drawable原点的坐标
    private float px = 0, py = 0;
    //当前旋转角度 动画结束后更新 90的整数倍
    private float degree = 0;

    //上下左右边界
    private float boundaryLeft = 0;
    private float boundaryRight = 0;
    private float boundaryTop = 0;
    private float boundaryBottom = 0;
    //xy方向是否可拖动
    private boolean canDragX = false, canDragY = false;
    //上下左右可视边界 当前图片必须填满该矩形
    private float rectLeft = 0;
    private float rectRight = 0;
    private float rectTop = 0;
    private float rectBottom = 0;
    //矩形中心xy
    private float rectCenterX = 0;
    private float rectCenterY = 0;

    // 头像编辑框现对于view短边的比例
    private float boxFactor = 0.8f;
    // 头像编辑框大小
    private float boxWidth = 0, boxHeight = 0;
    // 头像编辑框半宽高
    private float hBoxWidth = 0, hBoxHeight = 0;
    // 画头像编辑框的矩形和画笔
    private RectF boxRect;
    private RectF boxRect1;
    private RectF boxRect2;
    private RectF boxRect3;
    private RectF boxRect4;
    private Paint boxPaint;
    private Paint huiPaint;

    private FlingAnimation mFlingAnimation;
    private ZoomAnimation mZoomAnimation;
    private RotateAnimation mRotateAnimation;

    private Animation curAnimation;

    //是否处于旋转动画中
    private boolean isRotation;

    //手势listener
    private SimpleGestureDetector mSimpleGestureDetector;

    public SimpleCropImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public SimpleCropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SimpleCropImageView(Context context) {
        super(context);
        init(context);
    }

    /**
     * 初始化
     */
    private void init(Context context) {
        mSimpleGestureDetector = new SimpleGestureDetector(context, mOnGestureListener);
        mFlingAnimation = new FlingAnimation();
        mZoomAnimation = new ZoomAnimation();
        mRotateAnimation = new RotateAnimation();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if(widthMode != MeasureSpec.EXACTLY || heightMode != MeasureSpec.EXACTLY) {
            //FragmentStackView必须固定宽高
            if(isInEditMode()) {
                //防止在界面编辑器中报错
                if(widthMode == MeasureSpec.AT_MOST) {
                    widthMode = MeasureSpec.EXACTLY;
                } else if(widthMode == MeasureSpec.UNSPECIFIED) {
                    widthMode = MeasureSpec.EXACTLY;
                    widthSize = 300;
                }
                if(heightMode == MeasureSpec.AT_MOST) {
                    heightMode = MeasureSpec.EXACTLY;
                } else if(heightMode == MeasureSpec.UNSPECIFIED) {
                    heightMode = MeasureSpec.EXACTLY;
                    heightSize = 300;
                }
            } else {
                throw new IllegalArgumentException("SimpleCropImageView must be measured with MeasureSpec.EXACTLY.");
            }
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setupCanvas();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(drawable != null) {
            // 保存矩阵
            canvas.save();

            // 平移
            canvas.translate(x, y);
            //旋转动画
            if(rotation != 0) {
                canvas.rotate(rotation, px, py);
            }
            //当前图片旋转
            if(degree != 0) {
                canvas.rotate(degree);
            }
            // 缩放
            if(scaleAdjust != 1.0f) {
                canvas.scale(scaleAdjust, scaleAdjust);
            }
            // 画图
            drawable.draw(canvas);
            // 回复矩阵
            canvas.restore();

            // 画头像编辑框
            drawBox(canvas);
        }
    }

    /**
     * 当前view从窗口移除时调用
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animationStop();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return isRotation || mSimpleGestureDetector.onTouchEvent(event);
    }

    /**
     * 初始化各项参数
     */
    protected void setupCanvas() {
        if(drawable != null) {
            //去除内边距后view宽高
            viewWidth = getWidth();
            viewHeight = getHeight();
            //view中点坐标
            centerX = viewWidth / 2f;
            centerY = viewHeight / 2f;

            //图片宽高
            imageWidth = getImageWidth();
            imageHeight = getImageHeight();
            //Log.d(TAG, "setupCanvas imageWidth="+imageWidth+" imageHeight="+imageHeight);
            //图片半宽高
            int hWidth = Math.round(((float) imageWidth / 2f));
            int hHeight = Math.round(((float) imageHeight / 2f));
            drawable.setBounds(-hWidth, -hHeight, hWidth, hHeight);

            initAvatarMode();

            rectCenterX = (rectLeft + rectRight) / 2f;
            rectCenterY = (rectTop + rectBottom) / 2f;

            reset();
        }
    }

    /**
     * 初始化头像编辑模式
     */
    private void initAvatarMode() {
        //初始化头像编辑框
        initBox();
        //计算缩放比例
        calcScaleAvatar();
        //初始化可视矩形边界
        rectLeft = centerX - hBoxWidth;
        rectRight = centerX + hBoxWidth;
        rectTop = centerY - hBoxHeight;
        rectBottom = centerY + hBoxHeight;
    }

    public void setImageBitmap(Bitmap image) {
        Log.d(TAG, "setImageBitmap");
        this.drawable = new BitmapDrawable(getResources(), image);
        requestLayout();
    }

    /**
     * 获得图片原始宽度
     */
    public int getImageWidth() {
        if(drawable != null) {
            return drawable.getIntrinsicWidth();
        }
        return 0;
    }

    /**
     * 获得图片原始高度
     */
    public int getImageHeight() {
        if(drawable != null) {
            return drawable.getIntrinsicHeight();
        }
        return 0;
    }

    /**
     * 设置最小缩放比例
     */
    public void setMinScale(float minScale) {
        this.minScale = minScale;
    }

    /**
     * 设置最大缩放比例
     */
    public void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }

    /**
     * 重置
     */
    public void reset() {
        //图片初始默认坐标
        x = centerX;
        y = centerY;
        //图片初始默认旋转角度
        rotation = 0;
        degree = 0;
        //图片初始默认旋转中心
        px = 0;
        py = 0;
        //图片初始默认缩放比例
        scaleAdjust = startingScale;
        //未旋转时默认显示宽高
        imageWidthDisplay = imageWidth;
        imageHeightDisplay = imageHeight;
        //初始化边界
        calcBoundaries();
        invalidate();
    }

    public void animationStop() {
        if(curAnimation != null) {
            curAnimation.stop();
            curAnimation = null;
        }
    }

    /**
     * 计算头像编辑框宽高并生成矩形 初始化画笔
     */
    private void initBox() {
        float minLen = viewWidth < viewHeight ? viewWidth : viewHeight;
        boxWidth = boxHeight = boxFactor * minLen;
        hBoxWidth = boxWidth / 2f;
        hBoxHeight = boxHeight / 2f;
        float left = centerX - hBoxWidth;
        float top = centerY - hBoxHeight;
        float right = centerX + hBoxWidth;
        float bottom = centerY + hBoxWidth;

        boxRect = new RectF(left, top, right, bottom);
        boxRect1 = new RectF(0, 0, viewWidth, top);
        boxRect2 = new RectF(0, top, left, bottom);
        boxRect3 = new RectF(right, top, viewWidth, bottom);
        boxRect4 = new RectF(0, bottom, viewWidth, viewHeight);
        if(boxPaint == null) {
            boxPaint = new Paint();
            boxPaint.setColor(Color.WHITE);
            boxPaint.setStyle(Style.STROKE);
        }
        if(huiPaint == null) {
            huiPaint = new Paint();
            huiPaint.setARGB(127, 0, 0, 0);
        }
    }

    /**
     * 计算头像模式下最大最小缩放比例与初始比例 暂时当头像编辑框为正方形
     */
    private void calcScaleAvatar() {
        float scaleHorizontal = boxWidth / imageWidth;
        float scaleVertical = boxHeight / imageHeight;
        startingScale = minScale = Math.max(scaleHorizontal, scaleVertical);
    }

    /**
     * 规范x坐标
     */
    private float boundX(float x) {
        if(x < boundaryLeft) {
            x = boundaryLeft;
        } else if(x > boundaryRight) {
            x = boundaryRight;
        }
        return x;
    }

    /**
     * 规范y坐标
     */
    private float boundY(float y) {
        if(y < boundaryTop) {
            y = boundaryTop;
        } else if(y > boundaryBottom) {
            y = boundaryBottom;
        }
        return y;
    }

    /**
     * 计算画布可拖动边界和xy方向是否可拖动
     */
    private void calcBoundaries() {
        //计算图片缩放后半宽高
        float hScaledWidth = ((float) imageWidthDisplay * scaleAdjust) / 2f;
        float hScaledHeight = ((float) imageHeightDisplay * scaleAdjust) / 2f;

        //上下左右边界 及xy方向是否可拖动
        float left, right, top, bottom;
        left = rectRight - hScaledWidth;
        right = rectLeft + hScaledWidth;
        top = rectBottom - hScaledHeight;
        bottom = rectTop + hScaledHeight;
        if(left < right) {
            canDragX = true;
        } else {
            left = right = rectCenterX;
            canDragX = false;
        }
        if(top < bottom) {
            canDragY = true;
        } else {
            top = bottom = rectCenterY;
            canDragY = false;
        }

        boundaryLeft = left;
        boundaryRight = right;
        boundaryTop = top;
        boundaryBottom = bottom;
        //Log.d(TAG, "calcBoundaries boundaryLeft="+boundaryLeft+" boundaryRight="+boundaryRight+" boundaryTop="+boundaryTop+" boundaryBottom="+boundaryBottom);
    }

    /**
     * 画头像编辑框
     */
    private void drawBox(Canvas canvas) {
        //Log.d(TAG, "drawBox");
        canvas.save();
        canvas.drawRect(boxRect1, huiPaint);
        canvas.drawRect(boxRect2, huiPaint);
        canvas.drawRect(boxRect3, huiPaint);
        canvas.drawRect(boxRect4, huiPaint);
        canvas.drawRect(boxRect, boxPaint);
        canvas.restore();
    }

    /**
     * 获得双击时缩放比例
     */
    private float getMaxMinZoom() {
        //Log.d(TAG, "getZoom maxScale="+maxScale+" minScale="+minScale+" scaleAdjust="+scaleAdjust);
        if(maxScale == minScale) {
            return 1f;
        }
        if(scaleAdjust < (maxScale + minScale) / 2f) {
            return maxScale / scaleAdjust;
        } else {
            return minScale / scaleAdjust;
        }
    }

    /**
     * 处理拖动
     * @return 该拖动是否处理
     */
    public boolean handleDrag(float dx, float dy) {
        //Log.d(TAG, "handleDrag");
        if(!canDragX && !canDragY) {
            return false;
        }
        if(dx == 0 && dy == 0) {
            return false;
        }
        float newX = boundX(x + dx);
        float newY = boundY(y + dy);
        if(newX == x && newY == y) {
            return false;
        }
        x = newX;
        y = newY;
        invalidate();
        return true;
    }

    /**
     * 处理缩放	普通缩放或缩放动画
     *
     * @param midX   midY 缩放中点坐标
     * @param rScale 相对当前缩放比例
     * @return 缩放是否被处理
     */
    protected boolean handleScale(float midX, float midY, float rScale) {
        //Log.d(TAG, "handleScale");
        //获得绝对缩放比例
        float newScale = scaleAdjust * rScale;

        //规范化缩放比例
        if(newScale > maxScale) {
            newScale = maxScale;
            rScale = newScale / scaleAdjust;
        } else if(newScale < minScale) {
            newScale = minScale;
            rScale = newScale / scaleAdjust;
        }
        if(newScale == scaleAdjust) {
            return false;
        }
        scaleAdjust = newScale;

        //计算缩放后边界
        calcBoundaries();
        //计算缩放后新坐标
        x = boundX(calcScaledCoordinate(midX, x, rScale));
        y = boundY(calcScaledCoordinate(midY, y, rScale));
        //Log.d(TAG, "handleScale x="+x+"y="+y);
        invalidate();
        return true;
    }

    /**
     * 处理旋转
     *
     * @param dAngle 相对旋转角度
     * @param rpx    rpx 旋转中心
     */
    private void handleRotate(float dAngle, float rpx, float rpy) {
        //Log.d(TAG, "handleRotate 旋转中心 px="+rpx+" py="+rpy);
        rotation += dAngle;
        px = rpx;
        py = rpy;
        invalidate();
    }

    /**
     * 计算相对于某点缩放后的xy
     *
     * @param mid    缩放中点坐标
     * @param cur    当前坐标
     * @param rScale 相对当前缩放比例
     */
    private float calcScaledCoordinate(float mid, float cur, float rScale) {
        return (cur - mid) * rScale + mid;
    }

    /**
     * 顺时针或逆时针旋转90度
     * 相对于当前画布中心
     * @param direction >= 0顺时针 否则逆时针
     */
    public void rotate(int direction) {
        if(isRotation) {
            return;
        }
        mRotateAnimation.start(centerX - x, centerY - y, direction >= 0 ? 90f : -90f);
    }

    /**
     * 旋转动画结束后更新图片状态
     */
    private void updateStatusAfterRotate() {
        //规范旋转角度
        normRotate();
        //计算旋转后图片状态
        calcStatusAfterRotate();
        //计算旋转后图片宽高
        calcImageDisplay();
        //计算新边界
        calcBoundaries();
        //防止出界
        x = boundX(x);
        y = boundY(y);
        //重绘
        invalidate();
    }

    /**
     * 将旋转角度规范到90度的整数倍
     */
    private void normRotate() {
        rotation %= 360f;
        float remainder = rotation % 90f;
        if(remainder != 0) {
            if(remainder < 45f) {
                rotation -= remainder;
            } else {
                rotation = rotation - remainder + 90f;
            }
        }
    }

    /**
     * 计算按px py为中心旋转后图片的新坐标
     */
    private void calcStatusAfterRotate() {
        //		Log.d(TAG, "calcStatusAfterRotate 原 px="+px+" py="+py);
        //		Log.d(TAG, "calcStatusAfterRotate 原 rotation="+rotation);
        //		Log.d(TAG, "calcStatusAfterRotate 原 x="+x+" y="+y);

        if(rotation == 0) {
            rotation = px = py = 0;
            return;
        }
        // 计算旋转后当前图片中点相对于未旋转图片中点的偏移
        float rx = 0, ry = 0;
        if(rotation == 90f) {
            rx = px + py;
            ry = py - px;
        } else if(rotation == 180f) {
            rx = px + px;
            ry = py + py;
        } else if(rotation == 270f) {
            rx = px - py;
            ry = py + px;
        }

        degree = (degree + rotation) % 360f;
        rotation = px = py = 0;
        x += rx;
        y += ry;

        //Log.d(TAG, "calcNewCoordinateAfterRotate 新 x="+x+" y="+y+ " degree="+degree);
    }

    /**
     * 计算在旋转后当前画布上图片宽高
     */
    private void calcImageDisplay() {
        if(degree == 90f || degree == 270f) {
            imageWidthDisplay = imageHeight;
            imageHeightDisplay = imageWidth;
        } else {
            imageWidthDisplay = imageWidth;
            imageHeightDisplay = imageHeight;
        }
    }

    /**
     * 将图片放到最大或最小
     */
    public void setMaxOrMin(boolean max) {
        if(max) {
            scaleAdjust = maxScale;
        } else {
            scaleAdjust = minScale;
        }
        invalidate();
    }

//    /**
//     * 获得当前头像编辑数据
//     *
//     * @param originalImageWidth originalImageHeight 原始图片宽高
//     */
//    public Bundle getAvatarEditData(int originalImageWidth, int originalImageHeight) {
//        //Log.d(TAG, "getAvatarEditData originalImageWidth="+originalImageWidth+"originalImageHeight="+originalImageHeight);
//        //原始图片比显示图片比例
//        float originalWidthScale = originalImageWidth / imageWidth;
//        float originalHeightScale = originalImageHeight / imageHeight;
//        //未旋转时编辑框相对于drawable图片的宽高
//        float rw = boxWidth / scaleAdjust;
//        float rh = boxHeight / scaleAdjust;
//        //未旋转时编辑框中点相对于drawable图片中点坐标
//        float rx1 = (rectCenterX - x) / scaleAdjust;
//        float ry1 = (rectCenterY - y) / scaleAdjust;
//        //Log.d(TAG, "getAvatarEditData rx1="+rx1+" ry1="+ry1);
//        //旋转后编辑框中点相对于图片中点的坐标与宽高
//        float width = rw, height = rh;
//        float rx = rx1, ry = ry1;
//        if(degree == 90f) {
//            width = rh;
//            height = rw;
//            rx = ry1;
//            ry = -rx1;
//        } else if(degree == 180f) {
//            rx = -rx1;
//            ry = -ry1;
//        } else if(degree == 270f) {
//            width = rh;
//            height = rw;
//            rx = -ry1;
//            ry = rx1;
//        }
//        //Log.d(TAG, "getAvatarEditData rx="+rx+" ry="+ry);
//        //旋转后裁剪起始点坐标
//        float sx = rx + (imageWidth - width) / 2f;
//        float sy = ry + (imageHeight - height) / 2f;
//        //Log.d(TAG, "getAvatarEditData sx="+sx+" sy="+sy+" degree="+degree+" width="+width+" height="+height);
//        //相对于原始图片参数
//        sx /= originalWidthScale;
//        sy /= originalHeightScale;
//        width /= originalWidthScale;
//        height /= originalHeightScale;
//
//        Bundle bundle = new Bundle();
//        bundle.putFloat("x", sx);
//        bundle.putFloat("y", sy);
//        bundle.putFloat("width", width);
//        bundle.putFloat("height", height);
//        bundle.putFloat("degrees", degree);
//        return bundle;
//    }

    private SimpleGestureDetector.OnGestureListener mOnGestureListener = new SimpleGestureDetector.OnGestureListener() {

        @Override
        public void onDoubleTap(float x, float y) {
            float zoom = getMaxMinZoom();
            //Log.d(TAG, "onDoubleTap zoom="+zoom);
            if(zoom == 1f) {
                return;
            }
            animationStop();
            mZoomAnimation.start(x, y, zoom);
        }

        @Override
        public void onSingleTap() {
        }

        @Override
        public void onFling(float vx, float vy) {
            animationStop();
            mFlingAnimation.start(vx, vy);
        }

        @Override
        public void onActionUp() {
        }

        @Override
        public void onActionDown() {
            animationStop();
        }

        @Override
        public void onZoom(float midX, float midY, float rScale) {
            handleScale(midX, midY, rScale);
        }

        @Override
        public void onDrag(float dx, float dy) {
            handleDrag(dx, dy);
        }
    };

    private abstract class Animation implements Runnable {

        protected final void postOnAnimation() {
            ViewCompat.postOnAnimation(SimpleCropImageView.this, this);
        }

        protected void animationStart() {
            curAnimation = this;
        }

        protected void animationFinish() {
            curAnimation = null;
        }

        public void stop() {
            removeCallbacks(this);
            animationFinish();
        }

        public abstract boolean isFinished();
    }

    private class FlingAnimation extends Animation {

        private Scroller mScroller = new Scroller(getContext());
        private int lastFlingX;
        private int lastFlingY;

        @Override
        public void run() {
            if(mScroller.computeScrollOffset()) {
                int x = mScroller.getCurrX();
                int y = mScroller.getCurrY();
                int dx = x - lastFlingX;
                int dy = y - lastFlingY;
                lastFlingX = x;
                lastFlingY = y;
                if(!handleDrag(dx, dy)) {
                    stop();
                }
            }
            if(!mScroller.isFinished()) {
                postOnAnimation();
            } else {
                animationFinish();
            }
        }

        public void start(float vx, float vy) {
            lastFlingX = lastFlingY = 0;
            mScroller.fling(0, 0, (int) vx, (int) vy, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
            postOnAnimation();
            animationStart();
        }

        public void stop() {
            super.stop();
            mScroller.abortAnimation();
        }

        @Override
        public boolean isFinished() {
            return mScroller.isFinished();
        }
    }

    private abstract class AnimationImpl extends Animation {

        private long mStartTime;
        private int mDuration;
        private float rDuration;
        private boolean isFinished;

        @Override
        public final void run() {
            if(isFinished) {
                return;
            }
            int timePassed = (int) (AnimationUtils.currentAnimationTimeMillis() - mStartTime);
            if(timePassed < mDuration) {
                if(onUpdate(timePassed * rDuration)) {
                    postOnAnimation();
                }
            } else {
                onUpdate(1);
                isFinished = true;
                animationFinish();
            }
        }

        protected abstract boolean onUpdate(float fraction);

        protected void start(int duration) {
            isFinished = false;
            mStartTime = AnimationUtils.currentAnimationTimeMillis();
            mDuration = duration;
            rDuration = 1f / duration;
            postOnAnimation();
            animationStart();
        }

        public void stop() {
            super.stop();
            isFinished = true;
        }

        @Override
        public final boolean isFinished() {
            return isFinished;
        }
    }

    private class ZoomAnimation extends AnimationImpl {

        private float centerX;
        private float centerY;
        private float lastZoom;
        //目标缩放比例
        private float finalZoom;

        @Override
        protected boolean onUpdate(float fraction) {
            float zoom = finalZoom * fraction;
            float rZoom = zoom / lastZoom;
            lastZoom = zoom;
            if(fraction == 1) {
                //设置最终缩放
                setMaxOrMin(finalZoom > 1);
            } else {
                if(handleScale(centerX, centerY, rZoom)) {
                    return true;
                } else {
                    stop();
                }
            }
            return false;
        }

        /**
         * @param centerX 缩放中心x
         * @param centerY 缩放中心y
         * @param finalZoom 目标相对于当前的缩放比例
         */
        public void start(float centerX, float centerY, float finalZoom) {
            this.centerX = centerX;
            this.centerY = centerY;
            lastZoom = 1;
            this.finalZoom = finalZoom;
            super.start(200);
        }
    }

    private class RotateAnimation extends AnimationImpl {

        private float centerX;
        private float centerY;
        private float lastAngle;
        //目标旋转角度
        private float finalAngle;

        @Override
        protected boolean onUpdate(float fraction) {
            float angle = finalAngle * fraction;
            float dAngle = angle - lastAngle;
            lastAngle = angle;
            if(fraction == 1) {
                handleRotate(dAngle, centerX, centerY);
                rotateStop();
                return false;
            } else {
                handleRotate(dAngle, centerX, centerY);
            }
            return true;
        }

        /**
         * @param centerX 旋转中心x
         * @param centerY 旋转中心y
         * @param finalAngle 需要旋转的相对角度
         */
        protected void start(float centerX, float centerY, float finalAngle) {
            isRotation = true;
            this.centerX = centerX;
            this.centerY = centerY;
            this.lastAngle = 0;
            this.finalAngle = finalAngle;
            super.start(200);
        }

        @Override
        public void stop() {
            boolean isFinished = isFinished();
            super.stop();
            if(!isFinished) {
                rotateStop();
            }
        }

        //旋转动画停止时需要的处理
        private void rotateStop() {
            updateStatusAfterRotate();
            isRotation = false;
        }
    }
}
