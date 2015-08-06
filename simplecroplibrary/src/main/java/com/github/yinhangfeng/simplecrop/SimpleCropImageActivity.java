package com.github.yinhangfeng.simplecrop;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class SimpleCropImageActivity extends AppCompatActivity {

    private SimpleCropImageView simpleCropImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_crop_image);

        simpleCropImageView = (SimpleCropImageView) findViewById(R.id.corp_view);
        simpleCropImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.test));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_simple_crop_image, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if(id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void rotate_90(View v) {
        simpleCropImageView.rotate(-1);
    }

    public void rotate90(View v) {
        simpleCropImageView.rotate(1);
    }

    public void reset(View v) {
        simpleCropImageView.reset();
    }
}
