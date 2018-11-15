package com.example.jinhui.picoptimize;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;

/**
 * 资源文件方霞mipmap还是drawable文件夹下？
 * setHasMipMap开启
 *
 * google官方建议将启动图片放在mipmap下，其他图片放在drawable文件夹下
 *
 * 分辨率和dpi
 * 240-320dpi xhdpi
 * 320-480dpi xxhdpi
 * 480-640dpi xxxhdpi
 *
 * 方案一：为每种dpi都出一套图片资源
 * 方案二：提供一套你需要支持的最大dpi的图片(android会自动渲染)
 *
 * 图片匹配问题
 * 对于480dpi分辨率的手机，如果一个图放在xhdpi下，系统会怎么处理？
 * android图标匹配规则：
 * xxhdpi->xxxhdpi->nodpi->xhdpi->hdpi
 *
 * 同一张图片，放在不同目录下，会生成不同大小的bitmap，建议放在xxhdpi文件夹下
 *
 * 图片加载优化
 * 异步请求 图片放在后台请求
 * 图片缓存 对于列表中的图片进行缓存
 * 网络请求 使用okhttp进行图片请求
 * 懒加载 当图片呈现可视化区域在进行加载
 *
 * 照片墙
 *
 * 超大图片加载方案
 * 用bitmap 进行图片压缩来加载超大图片，会看不清图片细节
 * 建议采用BitmapRegionDecoder来解决
 *
 *
 * 图片加载框架(Universal ImageLoader、Picasso、Glide、Fresco)
 * Universal ImageLoader(UIL)
 * 多线程，支持下载监听
 * bitmap裁剪
 * listview暂停加载
 *
 * Picasso
 * 缓存图片原图到本地
 * 使用的时ARGB-8888占用内存较大
 *
 * Glide
 * 与activity/fragment生命周期一致
 * 改变图片大小再加载到内存
 *
 * Fresco
 * 性能好 首次加载图片速度非常快，用户体验好
 * 内存表现出色 有效的对内存块的图片进行了管理
 * 渐进式预览 大致展示图片轮廓，然后逐渐展示清晰图片
 * 多图请求 封装了先加载低分辨率图片，然后再显示高分辨率图片
 * 图片呈现效果 自定义占位符、圆角图
 * Gif、WebP格式
 *
 *
 * ALPHA_8、ARGB_4444、ARGB_8888和RGB_565 (https://blog.csdn.net/fence2012/article/details/44928871)
 *
 * 在Android的Bitmap.Config中有四个枚举类型：ALPHA_8、ARGB_4444、ARGB_8888和RGB_565
 *
 * 下面是这四种类型的详细解释：
 *
 * ALPHA_8：每个像素都需要1（8位）个字节的内存，只存储位图的透明度，没有颜色信息
 *
 * ARGB_4444：A(Alpha)占4位的精度，R(Red)占4位的精度，G(Green)占4位的精度，B（Blue）占4位的精度，加起来一共是16位的精度，折合是2个字节，也就是一个像素占两个字节的内存，同时存储位图的透明度和颜色信息。不过由于该精度的位图质量较差，官方不推荐使用
 *
 * ARGB_8888：这个类型的跟ARGB_4444的原理是一样的，只是A,R,G,B各占8个位的精度，所以一个像素占4个字节的内存。由于该类型的位图质量较好，官方特别推荐使用。但是，如果一个480*800的位图设置了此类型，那个它占用的内存空间是：480*800*4/(1024*1024)=1.5M
 *
 * RGB_565：同理，R占5位精度，G占6位精度，B占5位精度，一共是16位精度，折合两个字节。这里注意的时，这个类型存储的只是颜色信息，没有透明度信息
 * ---------------------
 * 作者：若城风
 * 来源：CSDN
 * 原文：https://blog.csdn.net/fence2012/article/details/44928871
 * 版权声明：本文为博主原创文章，转载请附上博文链接！
 *
 * https://blog.csdn.net/pbm863521/article/details/74391787
 *
 * 三种Android图片压缩方法 压缩到指定大小
 *
 *
 * 简书: https://www.jianshu.com/p/4b0ba08bfb58
 * github: https://github.com/searchdingding/PhotoCompreeDemo
 * 2018-11-15 功课做了一半，有时间会继续更新！
 *
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Bitmap mCurrentBitmap;

    private static final String TAG = "MainActivity";

    Button btnTakePhoto, btnPhotoAlbum;
    ImageView imageView;
    Bitmap photoBitmap;
    File file;
    /**
     * 7.0获取的图片地址，与7.0之前方式不一样
     */

    // 图片拍照的标识,1拍照0相册
    private static int TAKEPAHTO = 1;
    //三个常量全局标识
    //图库
    private static final int PHOTO_PHOTOALBUM = 0;
    //拍照
    private static final int PHOTO_TAKEPHOTO = 1;
    //裁剪

    /**
     * 7.0系统手机设置的图片Uri地址
     */
    private Uri takePhotoSaveAdr;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main1);

        // 举例
        test();

//        ImageView firstImg = findViewById(R.id.first_img);
//        ImageView secondImg = findViewById(R.id.second_img);
//
//        loadOriginalSize(firstImg);
//        testInBitmap(secondImg);
    }

    private void test() {
        //获取读取和相机权限
        getRootPermissions();
        initView();
        initData();
    }

    private void initData() {
        btnTakePhoto.setOnClickListener(this);
        btnPhotoAlbum.setOnClickListener(this);
    }

    private void initView() {
        btnTakePhoto = findViewById(R.id.btn_takephoto);
        btnPhotoAlbum = findViewById(R.id.btn_photoalbum);
        imageView = findViewById(R.id.imageView);
    }

    /**
     * 获取6.0读取文件的权限
     */
    @SuppressLint("CheckResult")
    public void getRootPermissions() {
        //2.0版本去掉Manifest.permission.ACCESS_COARSE_LOCATION,
        RxPermissions rxPermissions = new RxPermissions(this); // where this is an Activity instance
        rxPermissions.request(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA
        )
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean granted) {
                        if (granted) { // 在android 6.0之前会默认返回true
                            // 已经获取权限
                            Log.e(TAG, "已经获取权限");
                        } else {
                            // 未获取权限
                            Log.e(TAG, "您没有授权该权限，请在设置中打开授权");
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {

                    }
                }, new Action() {
                    @Override
                    public void run() {
                        Log.e(TAG, "{run}");
                    }
                });
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_takephoto:
                TAKEPAHTO = 1;
                // 启动系统相机
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                // 判断7.0android系统
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    //临时添加一个拍照权限
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    //通过FileProvider获取uri
                    takePhotoSaveAdr = FileProvider.getUriForFile(MainActivity.this,
                            "com.example.jinhui.picoptimize", new File(Environment.getExternalStorageDirectory(), "savephoto" +
                                    ".jpg"));
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, takePhotoSaveAdr);
                } else {
                    takePhotoSaveAdr = Uri.fromFile(new File(Environment.getExternalStorageDirectory(), "savephoto" +
                            ".jpg"));
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, takePhotoSaveAdr);
                }
                startActivityForResult(intent, PHOTO_TAKEPHOTO);
                break;
            case R.id.btn_photoalbum:
                TAKEPAHTO = 0;
                Intent intentAlbum = new Intent(Intent.ACTION_PICK, null);
                //其中External为sdcard下的多媒体文件,Internal为system下的多媒体文件。
                //使用INTERNAL_CONTENT_URI只能显示存储在内部的照片
                intentAlbum.setDataAndType(
                        MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*");
                //返回结果和标识
                startActivityForResult(intentAlbum, PHOTO_PHOTOALBUM);
                break;

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {//避免选图时取消操作
            switch (requestCode) {
                case PHOTO_TAKEPHOTO:
                    Uri clipUri;
                    //判断如果是7.0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        clipUri = takePhotoSaveAdr;
                    } else {
                        clipUri = Uri.fromFile(new File(Environment.getExternalStorageDirectory() + "/savephoto.jpg"));
                    }
                    bitmapCompress(clipUri);
                    break;
                case PHOTO_PHOTOALBUM:
                    Uri photoAlbumUri = data.getData();
                    bitmapFactory(photoAlbumUri);
                    break;
            }
        }
    }

    /**
     * 压缩图片使用,采用BitmapFactory.decodeFile。这里是尺寸压缩
     */
    public void bitmapFactory(Uri imageUri) {
        String[] filePathColumns = {MediaStore.Images.Media.DATA};
        Cursor c = getContentResolver().query(imageUri, filePathColumns, null, null, null);
        assert c != null;
        c.moveToFirst();
        int columnIndex = c.getColumnIndex(filePathColumns[0]);
        String imagePath = c.getString(columnIndex);
        Log.e(TAG, "压缩前大小: " + FileSizeUtil.getFileOrFilesSize(imagePath));
        c.close();

        // 配置压缩的参数
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; //获取当前图片的边界大小，而不是将整张图片载入在内存中，避免内存溢出
        BitmapFactory.decodeFile(imagePath, options);

        // 开始图片压缩
        options.inJustDecodeBounds = false;
        // inSampleSize的作用就是可以把图片的长短缩小inSampleSize倍，所占内存缩小inSampleSize的平方
        options.inSampleSize = calculateSampleSize(options, 500, 500);
        // RGB_565图片像素占用一个字节， ARGB_8888图片像素占用2个字节
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        Bitmap bm = BitmapFactory.decodeFile(imagePath, options); // 解码文件
        Log.e(TAG, "压缩后大小: " + FileSizeUtil.getFileOrFilesSize(imagePath));
        imageView.setImageBitmap(bm);
    }

    /**
     * 计算出所需要压缩的大小
     *
     * @param options
     * @param reqWidth  我们期望的图片的宽，单位px
     * @param reqHeight 我们期望的图片的高，单位px
     * @return
     */
    private int calculateSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int sampleSize = 1;
        int picWidth = options.outWidth;
        int picHeight = options.outHeight;
        if (picWidth > reqWidth || picHeight > reqHeight) {
            int halfPicWidth = picWidth / 2;
            int halfPicHeight = picHeight / 2;
            while (halfPicWidth / sampleSize > reqWidth || halfPicHeight / sampleSize > reqHeight) {
                sampleSize *= 2;
            }
        }
        return sampleSize;
    }


    /**
     * 这里我们生成了一个Pic文件夹，在下面放了我们质量压缩后的图片，用于和原图对比
     * 压缩图片使用Bitmap.compress()，这里是质量压缩
     */
    public void bitmapCompress(Uri uriClipUri) {
        try {
            //裁剪后的图像转成BitMap
            //photoBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uriClipUri));
            photoBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uriClipUri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //创建路径
        String path = Environment.getExternalStorageDirectory()
                .getPath() + "/Pic";
        //获取外部储存目录
        file = new File(path);
        //创建新目录, 创建此抽象路径名指定的目录，包括创建必需但不存在的父目录。
        file.mkdirs();
        //以当前时间重新命名文件
        long i = System.currentTimeMillis();
        //生成新的文件
        file = new File(file.toString() + "/" + i + ".png");
        Log.e("fileNew", file.getPath());
        //创建输出流
        OutputStream out = null;
        try {
            out = new FileOutputStream(file.getPath());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //压缩文件，返回结果，参数分别是压缩的格式，压缩质量的百分比，输出流
        boolean bCompress = photoBitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);

        try {
            photoBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(file));
        } catch (IOException e) {
            e.printStackTrace();
        }
        imageView.setImageBitmap(photoBitmap);
    }


    /**
     * 直接加载sdcard里的图片原始大小
     * @param firstImg
     */
    private void loadOriginalSize(ImageView firstImg) {
        String sdCard = Environment.getExternalStorageDirectory().getAbsolutePath();
        String filePath = sdCard + "/11.jpg";

        mCurrentBitmap = BitmapFactory.decodeFile(filePath);
        firstImg.setImageBitmap(mCurrentBitmap);
    }

    /**
     * 压缩图片
     * @param img
     */
    public void testPicOptimize(ImageView img){
        String sdCard = Environment.getExternalStorageDirectory().getAbsolutePath();
        String filePath = sdCard + "/11.jpg";
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        int width = options.outWidth;
        options.inSampleSize = width / 200;
        // RGB_565图片像素占用2个字节， ARGB_8888图片一个像素占用4个字节
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
        img.setImageBitmap(bitmap);
    }

    /**
     * inBitmap的使用
     * @param img
     */
    public void testInBitmap(ImageView img){
        String sdCard = Environment.getExternalStorageDirectory().getAbsolutePath();
        String filePath = sdCard + "/11.jpg";

        BitmapFactory.Options options = new BitmapFactory.Options();
        // 这里第二张图片就复用了第一张图片的内存
        Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
        img.setImageBitmap(bitmap);
    }
}
