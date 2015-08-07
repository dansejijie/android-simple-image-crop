package com.github.yinhangfeng.simplecrop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.io.File;


public class SimpleCropImageActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "SimpleCropImageActivity";

    public static final String OUT_PATH = "OUT_PATH";
    public static final String SOURCE_TYPE = "SOURCE_TYPE";
    public static final String IMAGE_PATH = "IMAGE_PATH";
    public static final int CAMERA = 1;
    public static final int PICTURE = 2;

    private static final String AVATAR_WIDTH = "AVATAR_WIDTH";
    private static final String AVATAR_HEIGHT = "AVATAR_HEIGHT";

    private static final String TEMP_IMAGE_NAME = "simple_image_crop_temp.jpg";

    public static void open(Activity activity, int requestCode, String outPath, int sourceType, int avatarWidth, int avatarHeight) {
        Intent intent = new Intent(activity, SimpleCropImageActivity.class);
        intent.putExtra(OUT_PATH, outPath);
        intent.putExtra(SOURCE_TYPE, sourceType);
        intent.putExtra(AVATAR_WIDTH, avatarWidth);
        intent.putExtra(AVATAR_HEIGHT, avatarHeight);
        activity.startActivityForResult(intent, requestCode);
    }

    private File srcFile;//相机图片或选择的图片file对象
    private File outFile;//编辑好的图片存放文件
    private int avatarWidth;
    private int avatarHeight;

    private SimpleCropImageView simpleCropImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_crop_image);

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        setTitle("编辑头像");

        simpleCropImageView = (SimpleCropImageView) findViewById(R.id.corp_view);

        findViewById(R.id.rotate90).setOnClickListener(this);
        findViewById(R.id.rotate_90).setOnClickListener(this);

        Intent intent = getIntent();
        String outPath = intent.getStringExtra(OUT_PATH);
        int sourceType = intent.getIntExtra(SOURCE_TYPE, PICTURE);
        if(sourceType != PICTURE && sourceType != CAMERA) {
            sourceType = PICTURE;
        }
        avatarWidth = intent.getIntExtra(AVATAR_WIDTH, 0);
        avatarHeight = intent.getIntExtra(AVATAR_HEIGHT, 0);
        if(outPath == null || avatarWidth == 0 || avatarHeight == 0) {
            Log.e(TAG, "onCreate outPath == null || avatarWidth == 0 || avatarHeight == 0");
            finish();
            return;
        }

        outFile = new File(outPath);
        File outParent = outFile.getParentFile();
        if(!outParent.exists()) {
            outParent.mkdirs();
        }

        if(savedInstanceState == null) {
            getImage(sourceType);
        }
    }

    private void getImage(int type) {
        Intent intent;
        switch (type) {
        case CAMERA:
            srcFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), TEMP_IMAGE_NAME);
            Uri uri = Uri.fromFile(srcFile);
            intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);// 启动自带的照相功能
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            startActivityForResult(intent, CAMERA);
            break;
        case PICTURE:
            intent = new Intent();
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, PICTURE);
            break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_simple_crop_image, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.edit_complete) {
            completeEdit();
            return true;
        } else if(id == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean setImage(File file) {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if(bitmap == null) {
            Toast.makeText(this, "获取图片失败", Toast.LENGTH_SHORT).show();
            return false;
        }
        simpleCropImageView.setImageBitmap(bitmap);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult requestCode=" + requestCode
                    + " resultCode=" + resultCode + " data=" + data);
        if (resultCode == RESULT_OK) {
            if (requestCode == PICTURE) {
                srcFile = FileUtils.getUriFile(this, data.getData());
            }
            Log.i(TAG, "onActivityResult srcFile=" + srcFile);
            if (srcFile == null || !srcFile.exists()) {
                Toast.makeText(this, "图片不存在", Toast.LENGTH_SHORT).show();
            } else if(setImage(srcFile)) {
                return;
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private void completeEdit() {
        Bitmap bitmap = simpleCropImageView.getCropBitmap(avatarWidth, avatarHeight);

        if(ImageUtils.compressImage(bitmap, outFile)) {
            Intent intent = new Intent();
            intent.putExtra(IMAGE_PATH, outFile.getAbsolutePath());
            setResult(RESULT_OK, intent);
            finish();
            return;
        }
        Toast.makeText(this, "裁剪失败", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(id == R.id.rotate90) {
           simpleCropImageView.rotate(1);
        } else if(id == R.id.rotate_90) {
            simpleCropImageView.rotate(-1);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if (srcFile != null) {
            savedInstanceState.putString("srcFile", srcFile.getPath());
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String imgFilePath = savedInstanceState.getString("srcFile");
        if(imgFilePath != null) {
            srcFile = new File(imgFilePath);
        }
    }
}
