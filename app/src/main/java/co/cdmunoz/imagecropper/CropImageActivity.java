package co.cdmunoz.imagecropper;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import co.cdmunoz.imagecropper.cropper.CropperView;
import co.cdmunoz.imagecropper.cropper.CropperViewAttacher;
import co.cdmunoz.imagecropper.cropper.overlay.CropOverlayView;
import co.cdmunoz.imagecropper.cropper.overlay.Edge;
import co.cdmunoz.imagecropper.cropper.utils.ConstantsImageCrop;
import co.cdmunoz.imagecropper.cropper.utils.ImageViewUtil;
import co.cdmunoz.imagecropper.cropper.utils.InternalStorageContentProvider;
import co.cdmunoz.imagecropper.cropper.utils.Utils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CropImageActivity extends AppCompatActivity
    implements CropImageView, View.OnClickListener {

  private static final String LOG = CropImageActivity.class.getName();

  private static final int REQUEST_CAMERA = 0;
  private static final int REQUEST_STORAGE = 1;
  private static final int REQUEST_CODE_PICK_GALLERY = 0x1;
  private static final int REQUEST_CODE_TAKE_PICTURE = 0x2;
  private final int IMAGE_MAX_SIZE = 1024;
  public static final String TEMP_PHOTO_FILE_NAME = "temp_photo.jpg";

  @BindView(R.id.cropImageCancel) ImageView cropImageCancel;
  @BindView(R.id.cropImageDone) ImageView cropImageDone;
  @BindView(R.id.relativeImage) RelativeLayout relativeImage;
  @BindView(R.id.btnTakePicture) Button btnTakePicture;
  @BindView(R.id.btnChooseGallery) Button btnChooseGallery;
  @BindView(R.id.cropperView) CropperView cropperView;
  @BindView(R.id.imgImage) ImageView imgImage;
  @BindView(R.id.cropOverlayView) CropOverlayView cropOverlayView;

  private ContentResolver mContentResolver;
  private String currentDateandTime = "";
  private String mImagePath = null;
  private Uri mSaveUri = null;
  private Uri mImageUri = null;
  private File mFileTemp;
  private ProgressDialog dialog;
  private float minScale = 1f;

  @Override protected void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_image_cropper);
    ButterKnife.bind(this);

    createDirIfNoExist();
    initViews();

    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      requestExternalStoragePermission();
    }
  }

  private void requestExternalStoragePermission() {
    if (ActivityCompat.shouldShowRequestPermissionRationale(this,
        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

      ActivityCompat.requestPermissions(CropImageActivity.this,
          new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_STORAGE);
    } else {

      ActivityCompat.requestPermissions(this,
          new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_STORAGE);
    }
  }

  static boolean createDirIfNoExist() {
    boolean ret = true;
    File file = new File(Environment.getExternalStorageDirectory(), "Cropper");
    if (!file.exists()) {
      if (!file.mkdirs()) {
        ret = false;
      }
    }
    return ret;
  }

  @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    if (requestCode == REQUEST_STORAGE) {
      if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(this, "Storage permission was NOT granted.", Toast.LENGTH_SHORT).show();
      }
    } else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  @Override public void initViews() {
    mContentResolver = getContentResolver();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
    currentDateandTime = sdf.format(new Date());
    makeLayoutSquare();
    initClickListener();
    cropperView.addListener(new CropperViewAttacher.IGetImageBounds() {
      @Override public Rect getImageBounds() {
        return new Rect((int) Edge.LEFT.getCoordinate(), (int) Edge.TOP.getCoordinate(),
            (int) Edge.RIGHT.getCoordinate(), (int) Edge.BOTTOM.getCoordinate());
      }
    });
  }

  @Override public void makeLayoutSquare() {
    Display display = getWindowManager().getDefaultDisplay();
    Point size = new Point();
    display.getSize(size);
    int width = size.x;
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, width);
    relativeImage.setLayoutParams(params);
  }

  @Override public void hideCropping() {
    imgImage.setVisibility(View.VISIBLE);
    cropperView.setVisibility(View.GONE);
    cropOverlayView.setVisibility(View.GONE);
    findViewById(R.id.test).setVisibility(View.GONE);
  }

  @Override public void showCropping() {
    imgImage.setVisibility(View.GONE);
    cropperView.setVisibility(View.VISIBLE);
    cropOverlayView.setVisibility(View.VISIBLE);
    findViewById(R.id.test).setVisibility(View.VISIBLE);
  }

  @Override public void onGetImages(String action) {
    createTempFile();
    if (null != action) {
      switch (action) {
        case ConstantsImageCrop.IntentExtras.ACTION_CAMERA:
          getIntent().removeExtra("ACTION");
          takePic();
          return;
        case ConstantsImageCrop.IntentExtras.ACTION_GALLERY:
          getIntent().removeExtra("ACTION");
          pickImage();
          return;
      }
    }
  }

  @Override public void createTempFile() {
    String state = Environment.getExternalStorageState();
    String path;
    if (Environment.MEDIA_MOUNTED.equals(state)) {
      path = Environment.getExternalStorageDirectory()+"/Cropper";
      mFileTemp = new File(path, currentDateandTime + TEMP_PHOTO_FILE_NAME);
    } else {
      mFileTemp = new File(getFilesDir() + "/Cropper", currentDateandTime + TEMP_PHOTO_FILE_NAME);
    }
  }

  @Override public void takePic() {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      requestCameraPermission();
    } else {
      showCameraPreview();
    }
  }

  private void requestCameraPermission() {
    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
      ActivityCompat.requestPermissions(CropImageActivity.this,
          new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA);
    } else {
      ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA },
          REQUEST_CAMERA);
    }
  }

  private void showCameraPreview() {
    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    try {
      Uri mImageCaptureUri;
      String state = Environment.getExternalStorageState();
      if (Environment.MEDIA_MOUNTED.equals(state)) {
        //mImageCaptureUri = Uri.fromFile(mFileTemp);
        mImageCaptureUri = FileProvider.getUriForFile(CropImageActivity.this,
            BuildConfig.APPLICATION_ID + ".provider", mFileTemp);
      } else {
        mImageCaptureUri = InternalStorageContentProvider.CONTENT_URI;
      }
      takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageCaptureUri);
      takePictureIntent.putExtra("return-data", true);
      startActivityForResult(takePictureIntent, REQUEST_CODE_TAKE_PICTURE);
    } catch (ActivityNotFoundException nfe) {
      Log.e(LOG, "++++++++++++ showCameraPreview: " + nfe.getMessage());
    }
  }

  @Override public void pickImage() {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
    try {
      startActivityForResult(intent, REQUEST_CODE_PICK_GALLERY);
    } catch (ActivityNotFoundException nfe) {
      Log.e(LOG, "++++++++++++ pickImage: " + nfe.getMessage());
    }
  }

  @Override public void initClickListener() {
    btnTakePicture.setOnClickListener(this);
    btnChooseGallery.setOnClickListener(this);
    cropImageDone.setOnClickListener(this);
    cropImageCancel.setOnClickListener(this);
  }

  @Override public void onClick(View view) {
    int id = view.getId();
    switch (id) {
      case R.id.btnChooseGallery:
        mImagePath = null;//do again
        onGetImages(ConstantsImageCrop.IntentExtras.ACTION_GALLERY);
        break;
      case R.id.btnTakePicture:
        mImagePath = null;
        onGetImages(ConstantsImageCrop.IntentExtras.ACTION_CAMERA);
        break;
      case R.id.cropImageDone:
        if (mImagePath != null) saveAndUploadImage();
        break;
      case R.id.cropImageCancel:
        finishBack();
        break;
    }
  }

  void finishBack() {
    Intent returnIntent = new Intent();
    setResult(CropImageActivity.RESULT_CANCELED, returnIntent);
    finish();
  }

  private void saveAndUploadImage() {
    configDialog();
    boolean saved = saveOutput();
    imgImage.setImageBitmap(getBitmap(mImageUri));
    if (saved) {
      Toast.makeText(this, "Upload Image", Toast.LENGTH_SHORT).show();
      hideCropping();
    }
  }

  void configDialog() {
    dialog = new ProgressDialog(this);
    dialog.setMessage("Uploading..");
  }

  private boolean saveOutput() {
    Bitmap croppedImage = getCroppedImage();

    if (mSaveUri != null) {
      OutputStream outputStream = null;
      try {
        outputStream = mContentResolver.openOutputStream(mSaveUri);
        if (outputStream != null) {
          croppedImage.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
        }
      } catch (IOException ex) {
        ex.printStackTrace();
        return false;
      } finally {
        if (outputStream != null) {
          try {
            outputStream.close();
          } catch (Throwable t) {
            Log.e(LOG, "++++++++ saveOutput: " + t.getMessage());
          }
        }
      }
    } else {
      return false;
    }
    croppedImage.recycle();
    return true;
  }

  private Bitmap getBitmap(Uri uri) {
    InputStream in;
    Bitmap returnedBitmap;
    try {
      in = mContentResolver.openInputStream(uri);
      //Decode image size
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(in, null, o);
      in.close();
      int scale = 1;
      if (o.outHeight > IMAGE_MAX_SIZE || o.outWidth > IMAGE_MAX_SIZE) {
        scale = (int) Math.pow(2, (int) Math.round(
            Math.log(IMAGE_MAX_SIZE / (double) Math.max(o.outHeight, o.outWidth)) / Math.log(0.5)));
      }

      BitmapFactory.Options o2 = new BitmapFactory.Options();
      o2.inSampleSize = scale;
      in = mContentResolver.openInputStream(uri);
      Bitmap bitmap = BitmapFactory.decodeStream(in, null, o2);
      in.close();
      returnedBitmap = fixOrientationBugOfProcessedBitmap(bitmap);
      return returnedBitmap;
    } catch (FileNotFoundException nfe) {
      Log.e(LOG, "+++++++++ getBitmap: nfe " + nfe.getMessage());
    } catch (IOException ioe) {
      Log.e(LOG, "+++++++++ getBitmap: ioe " + ioe.getMessage());
    }
    return null;
  }

  private Bitmap fixOrientationBugOfProcessedBitmap(Bitmap bitmap) {
    try {
      if (getCameraPhotoOrientation(this, Uri.parse(mFileTemp.getPath())) == 0) {
        return bitmap;
      } else {
        Matrix matrix = new Matrix();
        matrix.postRotate(getCameraPhotoOrientation(this, Uri.parse(mFileTemp.getPath())));
        // recreate the new Bitmap and set it back
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix,
            true);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      return null;
    }
  }

  public static int getCameraPhotoOrientation(@NonNull Context context, Uri imageUri) {
    int rotate = 0;
    try {
      context.getContentResolver().notifyChange(imageUri, null);
      ExifInterface exif = new ExifInterface(imageUri.getPath());
      int orientation =
          exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
      switch (orientation) {
        case ExifInterface.ORIENTATION_ROTATE_270:
          rotate = 270;
          break;
        case ExifInterface.ORIENTATION_ROTATE_180:
          rotate = 180;
          break;
        case ExifInterface.ORIENTATION_ROTATE_90:
          rotate = 90;
          break;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return rotate;
  }

  public Bitmap getCroppedImage() {
    Bitmap mCurrentDisplayedBitmap = getCurrentDisplayedImage();
    Rect displayedImageRect =
        ImageViewUtil.getBitmapRectCenterInside(mCurrentDisplayedBitmap, cropperView);

    // Get the scale factor between the actual Bitmap dimensions and the
    // displayed dimensions for width.
    float actualImageWidth = mCurrentDisplayedBitmap.getWidth();
    float displayedImageWidth = displayedImageRect.width();
    float scaleFactorWidth = actualImageWidth / displayedImageWidth;

    // Get the scale factor between the actual Bitmap dimensions and the
    // displayed dimensions for height.
    float actualImageHeight = mCurrentDisplayedBitmap.getHeight();
    float displayedImageHeight = displayedImageRect.height();
    float scaleFactorHeight = actualImageHeight / displayedImageHeight;

    // Get crop window position relative to the displayed image.
    float cropWindowX = Edge.LEFT.getCoordinate() - displayedImageRect.left;
    float cropWindowY = Edge.TOP.getCoordinate() - displayedImageRect.top;
    float cropWindowWidth = Edge.getWidth();
    float cropWindowHeight = Edge.getHeight();

    // Scale the crop window position to the actual size of the Bitmap.
    float actualCropX = cropWindowX * scaleFactorWidth;
    float actualCropY = cropWindowY * scaleFactorHeight;
    float actualCropWidth = cropWindowWidth * scaleFactorWidth;
    float actualCropHeight = cropWindowHeight * scaleFactorHeight;

    // Crop the subset from the original Bitmap.
    return Bitmap.createBitmap(mCurrentDisplayedBitmap, (int) actualCropX, (int) actualCropY,
        (int) actualCropWidth, (int) actualCropHeight);
  }

  private Bitmap getCurrentDisplayedImage() {
    Bitmap result =
        Bitmap.createBitmap(cropperView.getWidth(), cropperView.getHeight(), Bitmap.Config.RGB_565);
    Canvas c = new Canvas(result);
    cropperView.draw(c);
    return result;
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    createTempFile();
    showCropping();
    mImageUri = data.getData();
    if (requestCode == REQUEST_CODE_TAKE_PICTURE && resultCode == RESULT_OK) {
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {
        hideCropping();
        imgImage.setVisibility(View.VISIBLE);
        imgImage.setImageResource(R.mipmap.ic_launcher);
        return;
      }

      mImagePath = mFileTemp.getPath();
      mSaveUri = Utils.getImageUri(mImagePath, CropImageActivity.this);
      if(null == mImageUri){
        mImageUri = Utils.getImageUri(mImagePath, CropImageActivity.this);

      }
      loadIntoCropperView(null);
    } else if (requestCode == REQUEST_CODE_PICK_GALLERY && resultCode == RESULT_OK) {
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {
        hideCropping();
        imgImage.setVisibility(View.VISIBLE);
        imgImage.setImageResource(R.mipmap.ic_launcher);
        Toast.makeText(this, "NO permission on Storage", Toast.LENGTH_SHORT).show();
        //code for default image
        return;
      }

      try {
        InputStream inputStream = getContentResolver().openInputStream(mImageUri);
        FileOutputStream fileOutputStream =
            openFileOutput(mFileTemp.getName(), Context.MODE_PRIVATE);
        copyStream(inputStream, fileOutputStream);
        inputStream.close();
        inputStream = null;
        fileOutputStream.flush();
        fileOutputStream.close();
        fileOutputStream = null;
        mImagePath = mFileTemp.getPath();
        mSaveUri = Utils.getImageUri(mImagePath, CropImageActivity.this);
        //mImageUri = Utils.getImageUri(mImagePath, CropImageActivity.this);
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), mImageUri);
        loadIntoCropperView(bitmap);
      } catch (Exception e) {
        Log.e(LOG, "+++++++++ onActivityResult: e " + e.getMessage());
      }
    } else {
      hideCropping();
      imgImage.setVisibility(View.VISIBLE);
    }
  }

  private static void copyStream(InputStream input, OutputStream output) throws IOException {
    byte[] buffer = new byte[512];
    int bytesRead;
    while ((bytesRead = input.read(buffer)) != -1) {
      output.write(buffer, 0, bytesRead);
    }
  }

  private void loadIntoCropperView(Bitmap b) {
    showCropping();
    if(null == b) {
      b = getBitmap(mImageUri);
    }
    Drawable bitmap = new BitmapDrawable(getResources(), b);
    int h = bitmap.getIntrinsicHeight();
    int w = bitmap.getIntrinsicWidth();
    final float cropWindowWidth = Edge.getWidth();
    final float cropWindowHeight = Edge.getHeight();
    if (h <= w) {
      minScale = (cropWindowHeight + 1f) / h;
    } else if (w < h) {
      minScale = (cropWindowWidth + 1f) / w;
    }

    cropperView.setMaximumScale(minScale * 9);
    cropperView.setMediumScale(minScale * 6);
    cropperView.setMinimumScale(minScale);
    cropperView.setImageDrawable(bitmap);
    cropperView.setScale(minScale);
  }
}
