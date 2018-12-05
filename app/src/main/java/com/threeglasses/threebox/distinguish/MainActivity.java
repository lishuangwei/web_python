package com.threeglasses.threebox.distinguish;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.opencv.imgproc.Imgproc.COLOR_BGR2HSV;
import static org.opencv.imgproc.Imgproc.calcHist;
import static org.opencv.imgproc.Imgproc.compareHist;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    ImageView mImg, mImg1, mImg2;
    TextView mTxt;
    MediaMetadataRetriever mMedia;
    Bitmap mBitmap, mBitmap1, mBitmap2;
    List<ImagePiece> mSelImg, mSelImg1, mSelImg2;
    int mScale = 1;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case BaseLoaderCallback.SUCCESS:
                    Log.i("shuang", "成功加载");
                    break;
                default:
                    super.onManagerConnected(status);
                    Log.i("shaung", "加载失败");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void init() {
        mImg = findViewById(R.id.img);
        mImg1 = findViewById(R.id.img1);
        mImg2 = findViewById(R.id.img2);
        mImg.setOnClickListener(this);
        mImg1.setOnClickListener(this);
        mTxt = findViewById(R.id.name);
        if (!OpenCVLoader.initDebug()) {
            Log.d("shuang", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d("shuang", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onClick(View view) {
        if (this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
        }
        switch (view.getId()) {
            case R.id.img:
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("video/*");
                startActivityForResult(Intent.createChooser(intent, "选择要导入的视频"), 1);
                break;
            case R.id.img1:
                Intent intent1 = new Intent();
                intent1.setType("image/*");
                intent1.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent1, 2);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            String path = getPath(this, data.getData());
            Log.d("shuang", "onActivityResult: " + path);
            long time = System.currentTimeMillis();
            mBitmap = getVideoBuff(path, 0.2f);
            mBitmap1 = getVideoBuff(path, 0.5f);
            mBitmap2 = getVideoBuff(path, 0.8f);
            mImg.setImageBitmap(mBitmap);
            mImg1.setImageBitmap(mBitmap1);
            mImg2.setImageBitmap(mBitmap2);

            mSelImg = split(mBitmap, 2, 2);
            mSelImg1 = split(mBitmap1, 2, 2);
            mSelImg2 = split(mBitmap2, 2, 2);
            if (mSelImg != null && mSelImg1 != null && mSelImg2 != null) {
                new GetTypeTask().execute();
            }
            long time1 = System.currentTimeMillis();
            Log.d("shuang", "time: " + (time1 - time));
            long ti = (time1 - time);
            getSupportActionBar().setTitle("解析时间" + ti + "毫秒");

        } else if (requestCode == 2 && resultCode == RESULT_OK) {
            long time = System.currentTimeMillis();
            Uri uri = data.getData();
            String img_url = uri.getPath();
            Log.d("shuang", "onActivityResult: img_url="+img_url);
            try {
                mBitmap = getBitmapFormUri(this, uri);
                mImg1.setImageBitmap(mBitmap);
                mSelImg = split(mBitmap, 2, 2);
                if (mSelImg != null) {
                    new GetBitmapTask().execute();
                }
                long time1 = System.currentTimeMillis();
                long ti = (time1 - time);
                getSupportActionBar().setTitle("解析时间" + ti + "毫秒");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //根据返回路径获取图片并缩放
    public Bitmap getBitmapFormUri(Activity ac, Uri uri) throws IOException {
        InputStream input = ac.getContentResolver().openInputStream(uri);
        BitmapFactory.Options onlyBoundsOptions = new BitmapFactory.Options();
        onlyBoundsOptions.inJustDecodeBounds = true;
        onlyBoundsOptions.inDither = true;
        onlyBoundsOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        BitmapFactory.decodeStream(input, null, onlyBoundsOptions);
        input.close();
        int originalWidth = onlyBoundsOptions.outWidth;
        int originalHeight = onlyBoundsOptions.outHeight;
        Log.d("shuang", "getBitmapFormUri: 宽："+originalWidth);
        Log.d("shuang", "getBitmapFormUri: 高："+originalHeight);
        if ((originalWidth == -1) || (originalHeight == -1))
            return null;
        float hh = 800f;
        float ww = 480f;
        if (originalWidth > originalHeight && originalWidth > ww) {
            mScale = (int) (originalWidth / ww);
        } else if (originalWidth < originalHeight && originalHeight > hh) {
            mScale = (int) (originalHeight / hh);
        }
        if (mScale <= 0)
            mScale = 1;
        Log.d("shuang", "getBitmapFormUri: 缩放比例＝" + mScale);
        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inSampleSize = mScale;
        bitmapOptions.inDither = true;
        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        input = ac.getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(input, null, bitmapOptions);
        input.close();
        return compressImage(bitmap);
    }

    //质量压缩图片
    public Bitmap compressImage(Bitmap image) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        int options = 100;
        while (baos.toByteArray().length / 1024 > 100) {
            baos.reset();
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);
            options -= 10;
            Log.d("shuang", "compressImage: option=" + options);
        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
        Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, null);
        return bitmap;
    }


    //获取视频帧
    private Bitmap getVideoBuff(String filepath, float ratio) {
        Log.d("shuang", "getVideoBuff: filepath=" + filepath);
        mMedia = new MediaMetadataRetriever();
        Bitmap bitmap = null;
        try {
            mMedia.setDataSource(filepath);
            long duration = Long.parseLong(mMedia.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            Log.d("shuang", "getVideoBuff: duration=" + duration);
            bitmap = mMedia.getFrameAtTime((long) (ratio * 1000 * duration), MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            Log.d("shuang", "getVideoBuff: getFrameAtTime bitmap=" + bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mMedia.release();
        }
        return bitmap;
    }

    //获取视频路径
    public String getPath(Context context, Uri uri) {
        Log.d("shuang", "getPath: uri=" + uri);
        boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                String type = split[0];
                Log.d("shuang", "getPath: type=" + type + " docId=" + docId);
                return "storage/" + type + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                String id = DocumentsContract.getDocumentId(uri);
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            } else if (isMediaDocument(uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                else if ("video".equals(type))
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                else if ("audio".equals(type))
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String selection = "_id=?";
                String[] selectionArgs = new String[]{split[1]};
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    //裁剪图片均分
    private List<ImagePiece> split(Bitmap bitmap, int xPiece, int yPiece) {
        Log.d("shuang", "split: bitmap =" + bitmap);
        List<ImagePiece> pieces = new ArrayList<ImagePiece>(xPiece * yPiece);
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Log.d("shuang", "split: width =" + width + " height =" + height);
        int pieceWidth = width / xPiece;
        int pieceHeight = height / yPiece;
        for (int i = 0; i < yPiece; i++) {
            for (int j = 0; j < xPiece; j++) {
                ImagePiece piece = new ImagePiece();
                piece.setIndex(j + i * xPiece);
                int xValue = j * pieceWidth;
                int yValue = i * pieceHeight;
                piece.setBitmap(Bitmap.createBitmap(bitmap, xValue, yValue, pieceWidth, pieceHeight));
                pieces.add(piece);
            }
        }
        return pieces;
    }

    //opencv直方图比较
    public double similar(Bitmap bt1, Bitmap bt2) {
        List<Mat> listImage1 = new ArrayList<>();
        List<Mat> listImage2 = new ArrayList<>();
        Mat img1 = new Mat();
        Mat img2 = new Mat();
        Utils.bitmapToMat(bt1, img1);
        Utils.bitmapToMat(bt2, img2);
        Mat hsv_img1 = new Mat();
        Mat hsv_img2 = new Mat();
        Imgproc.cvtColor(img1, hsv_img1, COLOR_BGR2HSV);
        Imgproc.cvtColor(img2, hsv_img2, COLOR_BGR2HSV);
        listImage1.add(hsv_img1);
        listImage2.add(hsv_img2);
        Mat hist_img1 = new Mat();
        Mat hist_img2 = new Mat();
        MatOfFloat ranges = new MatOfFloat(0, 255);
        MatOfInt histSize = new MatOfInt(50);
        MatOfInt channels = new MatOfInt(0);
        calcHist(listImage1, channels, new Mat(), hist_img1, histSize, ranges);
        calcHist(listImage2, channels, new Mat(), hist_img2, histSize, ranges);
        Core.normalize(hist_img1, hist_img1, 0, 1, Core.NORM_MINMAX, -1, new Mat());
        Core.normalize(hist_img2, hist_img2, 0, 1, Core.NORM_MINMAX, -1, new Mat());
        Double result = compareHist(hist_img1, hist_img2, Imgproc.CV_COMP_CORREL);
        return result;
    }

    //比较视频
    private String getType(List<ImagePiece> list, List<ImagePiece> list1, List<ImagePiece> list2) {
        double hor = similar(list.get(0).getBitmap(), list.get(1).getBitmap());
        double hor1 = similar(list.get(2).getBitmap(), list.get(3).getBitmap());
        double ver = similar(list.get(0).getBitmap(), list.get(2).getBitmap());
        double ver1 = similar(list.get(1).getBitmap(), list.get(3).getBitmap());
        Log.d("shuang", "getType: hor=" + hor + "--ver=" + ver);
        Log.d("shuang", "getType: hor1=" + hor1 + "--ver1=" + ver1);

        double hor2 = similar(list1.get(0).getBitmap(), list1.get(1).getBitmap());
        double hor3 = similar(list1.get(2).getBitmap(), list1.get(3).getBitmap());
        double ver2 = similar(list1.get(0).getBitmap(), list1.get(2).getBitmap());
        double ver3 = similar(list1.get(1).getBitmap(), list1.get(3).getBitmap());
        Log.d("shuang", "getType: hor2=" + hor2 + "--ver2=" + ver2);
        Log.d("shuang", "getType: hor3=" + hor3 + "--ver3=" + ver3);

        double hor4 = similar(list2.get(0).getBitmap(), list2.get(1).getBitmap());
        double hor5 = similar(list2.get(2).getBitmap(), list2.get(3).getBitmap());
        double ver4 = similar(list2.get(0).getBitmap(), list2.get(2).getBitmap());
        double ver5 = similar(list2.get(1).getBitmap(), list2.get(3).getBitmap());
        Log.d("shuang", "getType: hor4=" + hor4 + "--ver4=" + ver4);
        Log.d("shuang", "getType: hor5=" + hor5 + "--ver5=" + ver5);
        String type = "";
        if (hor > ver && hor > 0.968 && hor1 > ver1 && hor1 > 0.968
                && hor2 > ver2 && hor2 > 0.968 && hor3 > ver3 && hor3 > 0.968
                && hor4 > ver4 && hor4 > 0.968 && hor5 > ver5 && hor5 > 0.968) {
            float bi = (float) (mBitmap.getWidth() / 2) / (mBitmap.getHeight());
            Log.d("shuang", "getType: bi1=" + bi);
            if (bi >= 2 && bi < 3.5) {
                type = "360lr";
            } else {
                type = "3dlr";
            }
        } else if (ver > hor && ver > 0.968 && ver1 > hor1 && ver1 > 0.968
                && ver2 > hor2 && ver2 > 0.968 && ver3 > hor3 && ver3 > 0.968
                && ver4 > hor4 && ver4 > 0.968 && ver5 > hor5 && ver5 > 0.968) {
            float bi = (float) mBitmap.getWidth() / (mBitmap.getHeight() / 2);
            Log.d("shuang", "getType: bi2=" + bi);
            if (bi >= 2 && bi < 3.5) {
                type = "360ud";
            } else {
                type = "3dud";
            }
        } else {
            float bi = (float) mBitmap.getWidth() / (mBitmap.getHeight());
            Log.d("shuang", "getType: bi3=" + bi);
            if (bi >= 2 || (mBitmap.getWidth() >= 2000 && mBitmap.getHeight() > 1080)) {
                type = "360";
            } else {
                type = "2d";
            }
        }
        Log.d("shuang", "getType: type before＝" + type);
        type = getHanming(type, list);
        Log.d("shuang", "getType: type after＝" + type);
        return type;
    }

    //比较图片
    private String getType(List<ImagePiece> list) {
        double hor = similar(list.get(0).getBitmap(), list.get(1).getBitmap());
        double hor1 = similar(list.get(2).getBitmap(), list.get(3).getBitmap());
        double ver = similar(list.get(0).getBitmap(), list.get(2).getBitmap());
        double ver1 = similar(list.get(1).getBitmap(), list.get(3).getBitmap());
        Log.d("shuang", "getType: hor=" + hor + "--ver=" + ver);
        Log.d("shuang", "getType: hor1=" + hor1 + "--ver1=" + ver1);
        String type = "";
        if (hor > ver && hor > 0.968 && hor1 > ver1 && hor1 > 0.968) {
            float bi = (float) (mBitmap.getWidth() / 2) / (mBitmap.getHeight());
            Log.d("shuang", "getType: bi1=" + bi);
            if (bi >= 2 && bi < 3.5) {
                type = "360lr";
            } else {
                type = "3dlr";
            }
        } else if (ver > hor && ver > 0.968 && ver1 > hor1 && ver1 > 0.968) {
            float bi = (float) mBitmap.getWidth() / (mBitmap.getHeight() / 2);
            Log.d("shuang", "getType: bi2=" + bi);
            if (bi >= 2 && bi < 3.5) {
                type = "360ud";
            } else {
                type = "3dud";
            }
        } else {
            float bi = (float) mBitmap.getWidth() / (mBitmap.getHeight());
            Log.d("shuang", "getType: bi3=" + bi);
            if (bi >= 2 || (mBitmap.getWidth() * mScale >= 2000 && mBitmap.getHeight() * mScale > 1080)) {
                type = "360";
            } else {
                type = "2d";
            }
        }
        Log.d("shuang", "getType: type before＝" + type);
        type = getHanming(type, list);
        Log.d("shuang", "getType: type after＝" + type);
        return type;
    }

    //再根据汉明距离比较
    private String getHanming(String cvtype, List<ImagePiece> imagePieces) {
        String type = cvtype;
        String large = SimilarPhoto.find(imagePieces, 45);
        String small = SimilarPhoto.find(imagePieces, 10);
        Log.d("shuang", "getHanming: large=" + large);
        Log.d("shuang", "getHanming: small=" + small);
        if (cvtype.equals("360lr") || cvtype.equals("3dlr")) {
            if (!large.equals(SimilarPhoto.TYPE_LR)) {
                float bi = (float) mBitmap.getWidth() / (mBitmap.getHeight());
                if (bi >= 2 && bi < 3.5) {
                    type = "360";
                } else {
                    type = "2d";
                }
            }
        } else if (cvtype.equals("360")) {
            if (small.equals(SimilarPhoto.TYPE_LR)) {
                type = "360lr";
            } else if (small.equals(SimilarPhoto.TYPE_UD)) {
                type = "360ud";
            }
        } else if (cvtype.equals("2d")) {
            if (small.equals(SimilarPhoto.TYPE_LR)) {
                type = "3dlr";
            } else if (small.equals(SimilarPhoto.TYPE_UD)) {
                type = "3dud";
            }
        }
        return type;
    }

    class GetTypeTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            return getType(mSelImg, mSelImg1, mSelImg2);
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            mTxt.setText(s);
        }
    }

    class GetBitmapTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            return getType(mSelImg);
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            mTxt.setText(s);
        }
    }

}