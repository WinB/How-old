package com.demo.liuwenbin.how_old;

import android.app.Activity;
import android.view.View.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.facepp.error.FaceppParseException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity implements OnClickListener{

    private static final int PICK_CODE = 0X110;
    private ImageView mPhoto;
    private Button mGetImage;
    private Button mDetect;
    private TextView mTip;
    private View mWaitting;

    private String mCurrentPhotoSrc;

    private Bitmap mPhotoImg;
    private Paint mPaint;
    
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();

        initEvents();
        mPaint = new Paint();
    }

    private void initEvents() {
        mGetImage.setOnClickListener(this);
        mDetect.setOnClickListener(this);
    }

    private void initViews() {
        mPhoto = (ImageView) findViewById(R.id.id_photo);
        mGetImage = (Button) findViewById(R.id.id_getImange);
        mDetect = (Button) findViewById(R.id.id_detect);
        mTip = (TextView) findViewById(R.id.id_tip);
        mWaitting = findViewById(R.id.id_waiting);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        if (requestCode == PICK_CODE)
        {
            if (intent != null)
            {
                Uri uri = intent.getData();
                Cursor cursor = getContentResolver().query(uri,null,null,null, null);
                cursor.moveToFirst();

                int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                mCurrentPhotoSrc = cursor.getString(idx);
                cursor.close();

                resizePhoto();

                mPhoto.setImageBitmap(mPhotoImg);
                mTip.setText("Click Detect ==>");

            }

        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void resizePhoto() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(mCurrentPhotoSrc,options);
        double ratio = Math.max(options.outWidth*1.0d/1024f, options.outHeight*1.0d/1024f);
        options.inSampleSize = (int) Math.ceil(ratio);
        options.inJustDecodeBounds=false;
        mPhotoImg = BitmapFactory.decodeFile(mCurrentPhotoSrc,options);

    }

    private static final int MSG_SUCESS = 0x111;
    private static final int MSG_ERROR = 0x112;

    private Handler mHandler = new Handler(){
        public void handleMessage(Message msg)
        {
            switch (msg.what) {
                case MSG_SUCESS:
                    mWaitting.setVisibility(View.GONE);
                    JSONObject rs = (JSONObject) msg.obj;

                    prepareRsBitmap(rs);

                    mPhoto.setImageBitmap(mPhotoImg);
                    break;
                case MSG_ERROR:
                    mWaitting.setVisibility(View.GONE);
                    String errorMsg = (String) msg.obj;

                    if (TextUtils.isEmpty(errorMsg))
                    {
                        mTip.setText("Error.");
                    }else
                    {
                        mTip.setText(errorMsg);
                    }
                    break;

            }
            super.handleMessage(msg);
        }
    };

    private void prepareRsBitmap(JSONObject rs) {

        Bitmap bitmap= Bitmap.createBitmap(mPhotoImg.getWidth(),mPhotoImg.getHeight(),mPhotoImg.getConfig());
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(mPhotoImg, 0, 0, null);
        try
        {
            JSONArray faces = rs.getJSONArray("face");
            int faceCount = faces.length();
            mTip.setText("find"+faceCount);

            for (int i = 0; i <faceCount; i++)
            {
                //拿到单独的face对象
                JSONObject face = faces.getJSONObject(i);
                JSONObject posObj = face.getJSONObject("position");

                float x = (float) posObj.getJSONObject("center").getDouble("x");
                float y = (float) posObj.getJSONObject("center").getDouble("y");

                float w = (float) posObj.getDouble("width");
                float h = (float) posObj.getDouble("height");

                x = x/100 * bitmap.getWidth();
                y = y/100 * bitmap.getHeight();

                w = w/100 * bitmap.getWidth();
                h = h/100 * bitmap.getHeight();

                mPaint.setColor(0xffffffff);
                mPaint.setStrokeWidth(3);
                //画BOX

                //矩形框的左边
                canvas.drawLine(x-w/2, y-h/2, x-w/2, y+h/2,mPaint);
                //矩形框的底边
                canvas.drawLine(x-w/2, y-h/2, x+w/2, y-h/2,mPaint);
                //矩形框的上边
                canvas.drawLine(x+w/2, y-h/2, x+w/2, y+h/2,mPaint);
                //矩形框的右边
                canvas.drawLine(x-w/2, y+h/2, x+w/2, y+h/2,mPaint);

                //获取性别（gender）和年龄(age)
                int age = face.getJSONObject("attribute").getJSONObject("age").getInt("value");
                String gender = face.getJSONObject("attribute").getJSONObject("gender").getString("value");

                //绘制年龄显示矩形框
                Bitmap ageBitmap = buildAgeBitmap(age,"Male".equals(gender));

                //对气泡进行缩放，跟随图片头像大小变动
                int ageWidth = ageBitmap.getWidth();
                int ageHeight = ageBitmap.getHeight();

                if(bitmap.getWidth() < mPhoto.getWidth() && bitmap.getHeight() < mPhoto.getHeight())
                {
                    float ratio = Math.max(bitmap.getWidth() * 1.0f/mPhoto.getWidth(), bitmap.getHeight() * 1.0F/mPhoto.getHeight());
                    ageBitmap = Bitmap.createScaledBitmap(ageBitmap, (int)(ageWidth * ratio),
                            (int)(ageHeight * ratio), false);
                }
                canvas.drawBitmap(ageBitmap, x - ageBitmap.getWidth()/2, y - h/2 - ageBitmap.getHeight(), null);
                mPhotoImg = bitmap;

            }
        } catch (JSONException e)
        {
            e.printStackTrace();
        }
    }

    private Bitmap buildAgeBitmap(int age, boolean isMale) {
        TextView tv = (TextView)mWaitting.findViewById(R.id.id_age_and_gender);
        tv.setText(age+"");
        if(isMale)
        {
            tv.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.male), null, null, null);
        }else
        {
            tv.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.female), null, null, null);
        }
        tv.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(tv.getDrawingCache());
        tv.destroyDrawingCache();

        return bitmap;
    }

    public void onClick(View v){
        switch (v.getId()) {
            case R.id.id_getImange:

                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, PICK_CODE);
                break;

            case R.id.id_detect:
                mWaitting.setVisibility(View.VISIBLE);

                if (mCurrentPhotoSrc != null && !mCurrentPhotoSrc.trim().equals(""))
                {
                    resizePhoto();
                }else
                {
                    mPhotoImg = BitmapFactory.decodeResource(getResources(),R.drawable.t4);
                }


                FaceppDetect.detect(mPhotoImg, new FaceppDetect.CallBack() {

                    @Override
                    public void success(JSONObject result)
                    {
                        Message msg = Message.obtain();
                        msg.what = MSG_SUCESS;
                        msg.obj = result;
                        mHandler.sendMessage(msg);
                    }

                    @Override
                    public void error(FaceppParseException exception)
                    {
                        Message msg = Message.obtain();
                        msg.what = MSG_ERROR;
                        msg.obj = exception.getErrorMessage();
                        mHandler.sendMessage(msg);
                    }

                });
                break;

        }
    }
}
