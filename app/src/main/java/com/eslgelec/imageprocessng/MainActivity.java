package com.eslgelec.imageprocessng;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.SIFT;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import org.opencv.core.MatOfDMatch;
import org.opencv.core.DMatch;
import org.opencv.features2d.DescriptorMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.drew.imaging.ImageProcessingException;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.lang.GeoLocation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 {
    private static final String TAG = "OCVSample::Activity";
    private CameraBridgeViewBase mOpenCvCameraView;

    private static final int PICK_IMAGES_REQUEST_CODE = 100;
    private static final int READ_STORAGE_PERMISSION_REQUEST_CODE = 101;

    private Button selectImagesButton;
    private Button readmeButton;

    private List<Uri> selectedImageUris;
    private final List<GpsCoordinates> imageGpsCoordinates = Collections.synchronizedList(new ArrayList<>());
    private final List<ImageFeatureData> allImageFeatures = Collections.synchronizedList(new ArrayList<>());

    private static class GpsCoordinates {
        double latitude;
        double longitude;
        Uri imageUri;

        public GpsCoordinates(double lat, double lon, Uri uri) {
            this.latitude = lat;
            this.longitude = lon;
            this.imageUri = uri;
        }

        @Override
        public String toString() {
            return "Image: " + imageUri.getLastPathSegment() + " -> Lat: " + latitude + ", Lon: " + longitude;
        }
    }

    private static class ImageFeatureData {
        Uri imageUri;
        GpsCoordinates gpsCoordinates;
        MatOfKeyPoint keyPoints;
        Mat descriptors;

        public ImageFeatureData(Uri uri, GpsCoordinates gps, MatOfKeyPoint kp, Mat desc) {
            this.imageUri = uri;
            this.gpsCoordinates = gps;
            this.keyPoints = kp;
            this.descriptors = desc;
        }

        public void releaseMats() {
            if (keyPoints != null) keyPoints.release();
            if (descriptors != null) descriptors.release();
        }

        @Override
        public String toString() {
            String gpsStr = (gpsCoordinates != null) ? "Lat: " + gpsCoordinates.latitude + ", Lon: " + gpsCoordinates.longitude : "No GPS";
            int kpCount = (keyPoints != null && !keyPoints.empty()) ? (int) keyPoints.total() : 0;
            int descCount = (descriptors != null && !descriptors.empty()) ? descriptors.rows() : 0;
            return "Image: " + imageUri.getLastPathSegment() + ", " + gpsStr +
                    ", KeyPoints: " + kpCount + ", Descriptors: " + descCount;
        }
    }

    public MainActivity() {
        super();
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "called onCreate");
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        mOpenCvCameraView = findViewById(R.id.tutorial1_activity_java_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        selectedImageUris = new ArrayList<>();
        selectImagesButton = findViewById(R.id.select_images_button);
        selectImagesButton.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) {
                openGalleryToSelectImages();
            }
        });

        readmeButton = findViewById(R.id.readme_button);
        readmeButton.setOnClickListener(v -> showReadmeDialog());
    }

    private void showReadmeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("How This App Works")
                .setMessage("This application estimates the GPS coordinates of a target image using SIFT features from a set of reference images.\n\n"
                        + "1. Select Images: Tap the \"Select Photos from Gallery\" button to choose 2 to 30 images from your device. At least one of these images should have known GPS data in its EXIF metadata, and one will be chosen as the target image whose GPS is to be predicted.\n\n"
                        + "2. Feature Extraction: The app uses the SIFT (Scale-Invariant Feature Transform) algorithm to detect keypoints and compute descriptors for each selected image. This process runs in the background. Images are resized for performance if they are too large.\n\n"
                        + "3. GPS Data Reading: GPS coordinates (latitude and longitude) are read from the EXIF metadata of each selected image using the 'metadata-extractor' library.\n\n"
                        + "4. Target Selection: After processing, a dialog will appear listing images that contain GPS data. Select one image from this list as the \"target image.\" The GPS of this image will be predicted (even if its original GPS is known, for comparison).\n\n"
                        + "5. GPS Prediction: The SIFT descriptors of the target image are matched against the SIFT descriptors of all other selected images (reference images) that have GPS data. Lowe's ratio test is used to find good matches.\n\n"
                        + "6. Weighted Average: The GPS coordinates of the target image are predicted by calculating a weighted average of the GPS coordinates of the reference images. The weight for each reference image is determined by the number of good SIFT matches it has with the target image.\n\n"
                        + "7. Display Result: The original GPS (if available) and the predicted GPS of the target image are displayed in an alert dialog.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private boolean checkAndRequestPermissions() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission}, READ_STORAGE_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == READ_STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGalleryToSelectImages();
            } else {
                Toast.makeText(this, "Storage permission is required to select images.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openGalleryToSelectImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(Intent.createChooser(intent, "Select Pictures"), PICK_IMAGES_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGES_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            selectedImageUris.clear();
            imageGpsCoordinates.clear();
            for (ImageFeatureData featureData : allImageFeatures) {
                featureData.releaseMats();
            }
            allImageFeatures.clear();

            ClipData clipData = data.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri imageUri = clipData.getItemAt(i).getUri();
                    selectedImageUris.add(imageUri);
                    extractGpsFromUri(imageUri);
                }
            } else if (data.getData() != null) {
                Uri imageUri = data.getData();
                selectedImageUris.add(imageUri);
                extractGpsFromUri(imageUri);
            }

            if (selectedImageUris.size() < 2 || selectedImageUris.size() > 30) {
                Toast.makeText(this,
                        "Please select between 2 and 30 photos. Selected: " + selectedImageUris.size(),
                        Toast.LENGTH_LONG).show();
                selectedImageUris.clear();
                for (ImageFeatureData featureData : allImageFeatures) { 
                    featureData.releaseMats();
                }
                allImageFeatures.clear(); 
                imageGpsCoordinates.clear(); 
                return; 
            }

            if (!selectedImageUris.isEmpty()) {
                Toast.makeText(this,
                        "Selected " + selectedImageUris.size() + " images. Processing...",
                        Toast.LENGTH_LONG).show();
                new SiftExtractionTask().execute(new ArrayList<>(selectedImageUris));
            }
        }
    }

    private class SiftExtractionTask extends AsyncTask<List<Uri>, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Toast.makeText(MainActivity.this, "SIFT feature extraction starting...", Toast.LENGTH_SHORT).show();
            selectImagesButton.setEnabled(false);
        }

        @Override
        protected String doInBackground(List<Uri>... params) {
            List<Uri> urisToProcess = params[0];
            if (urisToProcess == null || urisToProcess.isEmpty()) {
                return "No images to process.";
            }

            SIFT sift;
            try {
                sift = SIFT.create();
            } catch (UnsatisfiedLinkError e) {
                return "SIFT algorithm could not be initialized. Check OpenCV setup.";
            }
            if (sift == null) {
                return "SIFT not available. Check OpenCV build.";
            }

            int processedImageCount = 0;
            for (Uri imageUri : urisToProcess) {
                if (isCancelled()) break;
                File tempFile = createTempFileFromUri(imageUri);
                if (tempFile == null) continue;

                Mat imageMat = Imgcodecs.imread(tempFile.getAbsolutePath());
                tempFile.delete();
                if (imageMat.empty()) continue;

                Mat resizedMat = new Mat();
                final int MAX_IMAGE_DIMENSION = 1024;
                int w = imageMat.width(), h = imageMat.height();
                double ratio = (double) w / h;
                int newW = w > h ? MAX_IMAGE_DIMENSION : (int) (MAX_IMAGE_DIMENSION * ratio);
                int newH = w > h ? (int) (MAX_IMAGE_DIMENSION / ratio) : MAX_IMAGE_DIMENSION;
                if (w > MAX_IMAGE_DIMENSION || h > MAX_IMAGE_DIMENSION) {
                    Imgproc.resize(imageMat, resizedMat,
                            new org.opencv.core.Size(newW, newH), 0, 0, Imgproc.INTER_AREA);
                    imageMat.release();
                } else {
                    resizedMat = imageMat.clone();
                }

                Mat grayMat = new Mat();
                if (resizedMat.channels() > 1) {
                    Imgproc.cvtColor(resizedMat, grayMat, Imgproc.COLOR_BGR2GRAY);
                } else {
                    grayMat = resizedMat.clone();
                }
                resizedMat.release();

                MatOfKeyPoint keyPoints = new MatOfKeyPoint();
                Mat descriptors = new Mat();
                sift.detectAndCompute(grayMat, new Mat(), keyPoints, descriptors);
                grayMat.release();

                GpsCoordinates currentGps = null;
                synchronized (imageGpsCoordinates) {
                    for (GpsCoordinates gps : imageGpsCoordinates) {
                        if (gps.imageUri.equals(imageUri)) {
                            currentGps = gps;
                            break;
                        }
                    }
                }

                synchronized (allImageFeatures) {
                    allImageFeatures.add(new ImageFeatureData(imageUri, currentGps, keyPoints, descriptors));
                }
                processedImageCount++;
            }
            return "SIFT feature extraction finished for " + processedImageCount + " of " + urisToProcess.size() + " images.";
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
            selectImagesButton.setEnabled(true);

            final List<GpsCoordinates> gpsAvailableImages = new ArrayList<>();
            final List<String> gpsAvailableImageNames = new ArrayList<>();

            synchronized (imageGpsCoordinates) {
                if (imageGpsCoordinates.isEmpty()) {
                    Toast.makeText(MainActivity.this, "No GPS data found in any of the photos.", Toast.LENGTH_LONG).show();
                    return;
                }
                for (ImageFeatureData featureData : allImageFeatures) {
                    if (featureData.gpsCoordinates != null) {
                        gpsAvailableImages.add(featureData.gpsCoordinates);
                        gpsAvailableImageNames.add(featureData.imageUri.getLastPathSegment() != null ?
                                featureData.imageUri.getLastPathSegment() : featureData.imageUri.toString());
                    }
                }
            }

            if (gpsAvailableImages.isEmpty()) {
                Toast.makeText(MainActivity.this, "No photos with GPS data found among those for which SIFT features were extracted.", Toast.LENGTH_LONG).show();
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Select Target Photo (For GPS Prediction)");
            builder.setItems(gpsAvailableImageNames.toArray(new String[0]), (dialog, which) -> {
                GpsCoordinates selectedTargetGps = gpsAvailableImages.get(which);
                ImageFeatureData targetImageFeature = null;
                List<ImageFeatureData> referenceImageFeatures = new ArrayList<>();

                synchronized (allImageFeatures) {
                    for (ImageFeatureData featureData : allImageFeatures) {
                        if (featureData.imageUri.equals(selectedTargetGps.imageUri)) {
                            targetImageFeature = featureData;
                        } else {
                            if (featureData.keyPoints != null && !featureData.keyPoints.empty()) {
                                referenceImageFeatures.add(featureData);
                            }
                        }
                    }
                }

                if (targetImageFeature != null) {
                    if (referenceImageFeatures.isEmpty()) {
                        Toast.makeText(MainActivity.this, "No other photos with SIFT features available for reference.", Toast.LENGTH_LONG).show();
                    } else {
                        startGpsPrediction(targetImageFeature, referenceImageFeatures);
                    }
                } else {
                    Toast.makeText(MainActivity.this, "SIFT data not found for the selected target photo.", Toast.LENGTH_LONG).show();
                }
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
            AlertDialog dialog = builder.create();
            dialog.show();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            Toast.makeText(MainActivity.this, "SIFT extraction cancelled.", Toast.LENGTH_SHORT).show();
            selectImagesButton.setEnabled(true);
        }
    }

    private void startGpsPrediction(ImageFeatureData targetImage, List<ImageFeatureData> referenceImages) {
        Log.i(TAG, "startGpsPrediction called.");
        Log.i(TAG, "Target image: " + targetImage.toString());
        Log.i(TAG, "Number of reference images: " + referenceImages.size());

        if (targetImage.descriptors == null || targetImage.descriptors.empty()) {
            Toast.makeText(this, "SIFT descriptors not found for the target photo.", Toast.LENGTH_LONG).show();
            return;
        }

        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);

        List<Integer> matchCounts = new ArrayList<>();
        List<ImageFeatureData> validReferenceImages = new ArrayList<>();

        for (ImageFeatureData refImage : referenceImages) {
            Log.d(TAG, "Processing reference: " + refImage.toString());
            if (refImage.descriptors == null || refImage.descriptors.empty()) {
                Log.w(TAG, "Reference image " + refImage.imageUri.getLastPathSegment() + " has no SIFT descriptors. Skipping.");
                matchCounts.add(0); 
                continue;
            }
            if (refImage.gpsCoordinates == null) {
                Log.w(TAG, "Reference image " + refImage.imageUri.getLastPathSegment() + " has no GPS data. Skipping for weighted average.");
            }

            List<MatOfDMatch> knnMatches = new ArrayList<>();
            if (targetImage.descriptors.type() != refImage.descriptors.type()) {
                Log.e(TAG, "Descriptor type mismatch: Target is " + targetImage.descriptors.type() + ", Ref is " + refImage.descriptors.type());
                matchCounts.add(0);
                continue;
            }
            if (targetImage.descriptors.cols() != refImage.descriptors.cols()) {
                Log.e(TAG, "Descriptor size mismatch: Target is " + targetImage.descriptors.cols() + ", Ref is " + refImage.descriptors.cols());
                matchCounts.add(0);
                continue;
            }

            matcher.knnMatch(targetImage.descriptors, refImage.descriptors, knnMatches, 2); 

            float ratioThresh = 0.75f; 
            int goodMatchesCount = 0;
            for (MatOfDMatch knnMatch : knnMatches) {
                DMatch[] matchesArray = knnMatch.toArray();
                if (matchesArray.length == 2) { 
                    if (matchesArray[0].distance < ratioThresh * matchesArray[1].distance) {
                        goodMatchesCount++;
                    }
                } else if (matchesArray.length == 1) { 
                     goodMatchesCount++; 
                }
            }
            Log.i(TAG, "Found " + goodMatchesCount + " good SIFT matches between target and " + refImage.imageUri.getLastPathSegment());
            matchCounts.add(goodMatchesCount);
            if (refImage.gpsCoordinates != null) { 
                validReferenceImages.add(refImage);
            } 

            for (MatOfDMatch m : knnMatches) {
                m.release();
            }
        }

        List<Integer> validMatchCounts = new ArrayList<>();
        int k=0;
        for(ImageFeatureData ref : referenceImages) { 
            if(validReferenceImages.contains(ref)){
                if (k < matchCounts.size()) {
                    validMatchCounts.add(matchCounts.get(k));
                } else {
                    Log.e(TAG, "Index k out of bounds for matchCounts. This should not happen.");
                }
            }
            k++;
        }

        double totalWeight = 0;
        double weightedLatSum = 0;
        double weightedLonSum = 0;
        int contributingReferences = 0;

        for (int i = 0; i < validReferenceImages.size(); i++) {
            ImageFeatureData ref = validReferenceImages.get(i);
            if (i < validMatchCounts.size()) {
                int currentMatches = validMatchCounts.get(i);
                if (currentMatches > 0 && ref.gpsCoordinates != null) {
                    weightedLatSum += ref.gpsCoordinates.latitude * currentMatches;
                    weightedLonSum += ref.gpsCoordinates.longitude * currentMatches;
                    totalWeight += currentMatches;
                    contributingReferences++;
                }
            } else {
                 Log.e(TAG, "Index i out of bounds for validMatchCounts. This should not happen.");
            }
        }

        String predictedGpsMessage;
        if (totalWeight > 0 && contributingReferences > 0) {
            double predictedLat = weightedLatSum / totalWeight;
            double predictedLon = weightedLonSum / totalWeight;
            predictedGpsMessage = String.format(Locale.US, "Predicted GPS: Lat %.6f, Lon %.6f", predictedLat, predictedLon);
            Log.i(TAG, "Predicted GPS: Lat " + predictedLat + ", Lon " + predictedLon + " from " + contributingReferences + " images with total weight " + totalWeight);
        } else {
            predictedGpsMessage = "Predicted GPS: Not enough matches or no reference images with GPS data found.";
            Log.w(TAG, "Could not predict GPS. Total weight or contributing references is zero.");
        }

        String originalGps = "Original GPS: None";
        if (targetImage.gpsCoordinates != null) {
            originalGps = String.format(Locale.US, "Original GPS: Lat %.6f, Lon %.6f",
                          targetImage.gpsCoordinates.latitude, targetImage.gpsCoordinates.longitude);
        }

        new AlertDialog.Builder(this)
                .setTitle("GPS Prediction Result")
                .setMessage(targetImage.imageUri.getLastPathSegment() + "\n" +
                            originalGps + "\n" +
                            predictedGpsMessage)
                .setPositiveButton("OK", null)
                .show();
    }

    private void extractGpsFromUri(Uri imageUri) {
        if (imageUri == null) return;

        try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
            if (inputStream != null) {
                Metadata metadata = JpegMetadataReader.readMetadata(inputStream);
                GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
                if (gpsDir != null && gpsDir.containsTag(GpsDirectory.TAG_LATITUDE)
                        && gpsDir.containsTag(GpsDirectory.TAG_LONGITUDE)) {
                    Log.i(TAG, "GpsDirectory found with Lat/Lon tags.");
                    GeoLocation loc = gpsDir.getGeoLocation();
                    if (loc != null) {
                        Log.i(TAG, "GeoLocation non-null. Raw Lat: " + loc.getLatitude() + ", Raw Lon: " + loc.getLongitude() + " for " + imageUri.getLastPathSegment());
                        GpsCoordinates newCoords = new GpsCoordinates(loc.getLatitude(), loc.getLongitude(), imageUri);
                        synchronized (imageGpsCoordinates) {
                            imageGpsCoordinates.add(newCoords);
                        }
                        Log.i(TAG, "Added to imageGpsCoordinates: " + newCoords.toString());
                    } else {
                        Log.w(TAG, "GeoLocation object was NULL for " + imageUri.getLastPathSegment());
                    }
                } else {
                    Log.w(TAG, "GpsDirectory not found or missing essential Lat/Lon tags for " + imageUri.getLastPathSegment());
                }
            }
        } catch (IOException | ImageProcessingException e) {
            Log.e(TAG, "Error extracting GPS metadata for " + imageUri.getLastPathSegment(), e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) mOpenCvCameraView.disableView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mOpenCvCameraView != null) mOpenCvCameraView.enableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        return inputFrame.rgba();
    }

    @Override
    public List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    private File createTempFileFromUri(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            File file = File.createTempFile("sift_image_", ".tmp", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return file;
        } catch (IOException e) {
            Log.e(TAG, "Failed to create temporary file from URI: " + uri.toString(), e);
            return null;
        }
    }
}
