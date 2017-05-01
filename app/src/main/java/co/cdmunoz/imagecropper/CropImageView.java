package co.cdmunoz.imagecropper;

public interface CropImageView {
  void initViews();

  void makeLayoutSquare();

  void hideCropping();

  void showCropping();

  void onGetImages(String action);

  void createTempFile();

  void takePic();

  void pickImage();

  void initClickListener();
}
