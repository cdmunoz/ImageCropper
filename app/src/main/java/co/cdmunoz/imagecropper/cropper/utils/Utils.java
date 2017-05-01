package co.cdmunoz.imagecropper.cropper.utils;

import android.content.Context;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import co.cdmunoz.imagecropper.BuildConfig;
import java.io.File;

public class Utils {

  public static Uri getImageUri(String path, Context context) {
    //return Uri.fromFile(new File(path));
    return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider",
        new File(path));
  }
}
