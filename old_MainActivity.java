there are some issues on this code(fix it):
package com.eslgelec.imageprocessng;

import android.os.Bundle;
import android.util.Log;
import android.view\.SurfaceView;
import android.view\.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
// import androidx.exifinterface.media.ExifInterface; // Commented out, using metadata-extractor now

import com.drew.metadata.MetadataException;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.SIFT;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.widget.Button;

import java.io.File;
import java.io.FileOutputStream;

// Added for AsyncTask
import android.os.AsyncTask;

import com.drewenoakes.metadata.Directory;
import com.drewenoakes.metadata.Metadata;
import com.drewenoakes.metadata.MetadataException;
import com.drewenoakes.metadata.exif.GpsDirectory;
import com.drewenoakes.metadata.jpeg.JpegMetadataReader;
import com.drewenoakes.metadata.ImageProcessingException;
import com.drewenoakes.metadata.GeoLocation;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
private static final String TAG = "OCVSample::Activity";
private CameraBridgeViewBase mOpenCvCameraView;

```
private static final int PICK_IMAGES_REQUEST_CODE = 100;
private static final int READ_STORAGE_PERMISSION_REQUEST_CODE = 101;

private Button selectImagesButton;

private List<Uri> selectedImageUris;
private List<GpsCoordinates> imageGpsCoordinates;
private List<ImageFeatureData> allImageFeatures;

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
        int kpCount = (keyPoints != null && !keyPoints.empty()) ? (int)keyPoints.total() : 0;
        int descCount = (descriptors != null && !descriptors.empty()) ? descriptors.rows() : 0;
        return "Image: " + imageUri.getLastPathSegment() + ", " + gpsStr +
               ", KeyPoints: " + kpCount + ", Descriptors: " + descCount;
    }
}

public void MaınActivity() {
    Log.i(TAG, "Instantiated new " + this.getClass());
}

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.i(TAG, "called onCreate");
    if (OpenCVLoader.initLocal()) {
        Log.i(TAG, "OpenCV loaded successfully");
    } else {
        Log.e(TAG, "OpenCV initialization failed!");
        (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show();
        return;
    }
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.activity_main);

    mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial1_activity_java_surface_view);

    mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

    mOpenCvCameraView.setCvCameraViewListener(this);

    selectedImageUris = new ArrayList<>();
    imageGpsCoordinates = new ArrayList<>();
    allImageFeatures = new ArrayList<>();

    selectImagesButton = findViewById(R.id.select_images_button);
    selectImagesButton.setOnClickListener(v -> {
        if (checkAndRequestPermissions()) {
            openGalleryToSelectImages();
        }
    });
}

private boolean checkAndRequestPermissions() {
    String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permission = Manifest.permission.READ_MEDIA_IMAGES;
    }

    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, new String[]{permission}, READ_STORAGE_PERMISSION_REQUEST_CODE);
        return false;
    }
    return true;
}

@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == READ_STORAGE_PERMISSION_REQUEST_CODE) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Storage permission granted");
            openGalleryToSelectImages();
        } else {
            Log.e(TAG, "Storage permission denied");
            Toast.makeText(this, "Storage permission is required to select images.", Toast.LENGTH_LONG).show();
        }
    }
}

private void openGalleryToSelectImages() {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    intent.setType("image/*");
    startActivityForResult(Intent.createChooser(intent, "Select Pictures"), PICK_IMAGES_REQUEST_CODE);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == PICK_IMAGES_REQUEST_CODE && resultCode == RESULT_OK) {
        selectedImageUris.clear();
        imageGpsCoordinates.clear();
        if (allImageFeatures != null) {
            for (ImageFeatureData featureData : allImageFeatures) {
                featureData.releaseMats();
            }
            allImageFeatures.clear();
        }

        if (data != null) {
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
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

            if (!selectedImageUris.isEmpty()) {
                Log.i(TAG, "Selected " + selectedImageUris.size() + " images.");
                Toast.makeText(this, "Selected " + selectedImageUris.size() + " images. Check logs for GPS data.", Toast.LENGTH_LONG).show();
                // Now extract SIFT features using AsyncTask
                new SiftExtractionTask().execute(new ArrayList<>(selectedImageUris)); // Pass a copy of the list
            }
        }
    }
}

// AsyncTask to handle SIFT feature extraction in the background
private class SiftExtractionTask extends AsyncTask<List<Uri>, String, String> {

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        Log.i(TAG, "SIFT Extraction Task: onPreExecute - Starting SIFT feature extraction...");
        Toast.makeText(MainActivity.this, "SIFT feature extraction starting...", Toast.LENGTH_SHORT).show();
        // Potentially disable the button here to prevent multiple clicks
        if (selectImagesButton != null) {
            selectImagesButton.setEnabled(false);
        }
    }

    @Override
    protected String doInBackground(List<Uri>... params) {
        List<Uri> urisToProcess = params[0];
        if (urisToProcess == null || urisToProcess.isEmpty()) {
            return "No images to process.";
        }

        // Ensure allImageFeatures is cleared here before populating in background
        // (Moved from onActivityResult to ensure it's handled before background processing starts)
        // and Mats are released. This needs to be done on UI thread if it modifies UI-bound data
        // or if releaseMats() itself isn't thread-safe. For simplicity, we do it here but for complex
        // scenarios, ensure thread safety or use runOnUiThread for UI-bound list clearing.
        // Let's assume allImageFeatures is cleared in onActivityResult already (as it is currently).

        SIFT sift = null;
        try {
            sift = SIFT.create();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "SIFT Extraction Task: Failed to create SIFT. UnsatisfiedLinkError: " + e.getMessage());
            return "SIFT algorithm could not be initialized. Check OpenCV setup.";
        }

        if (sift == null) {
            Log.e(TAG, "SIFT Extraction Task: SIFT.create() returned null.");
            return "SIFT not available. Check OpenCV build.";
        }

        int processedImageCount = 0;
        for (Uri imageUri : urisToProcess) {
            if (isCancelled()) break; // Check if the task has been cancelled
            try {
                publishProgress("Processing: " + imageUri.getLastPathSegment());
                File tempFile = createTempFileFromUri(imageUri);
                if (tempFile == null) {
                    Log.w(TAG, "SIFT Extraction Task: Could not create temporary file for URI: " + imageUri);
                    continue;
                }

                Mat imageMat = Imgcodecs.imread(tempFile.getAbsolutePath());
                tempFile.delete();

                if (imageMat.empty()) {
                    Log.w(TAG, "SIFT Extraction Task: Failed to load image into Mat: " + imageUri.toString());
                    continue;
                }

                // Resize the image to a manageable size to reduce memory and processing load
                Mat resizedMat = new Mat();
                final int MAX_IMAGE_DIMENSION = 1024;
                int originalWidth = imageMat.width();
                int originalHeight = imageMat.height();
                double aspectRatio = (double) originalWidth / originalHeight;
                int newWidth, newHeight;

                if (originalWidth > originalHeight) {
                    newWidth = MAX_IMAGE_DIMENSION;
                    newHeight = (int) (newWidth / aspectRatio);
                } else {
                    newHeight = MAX_IMAGE_DIMENSION;
                    newWidth = (int) (newHeight * aspectRatio);
                }

                if (originalWidth > MAX_IMAGE_DIMENSION || originalHeight > MAX_IMAGE_DIMENSION) {
                    org.opencv.imgproc.Imgproc.resize(imageMat, resizedMat, new org.opencv.core.Size(newWidth, newHeight), 0, 0, org.opencv.imgproc.Imgproc.INTER_AREA);
                    Log.i(TAG, "SIFT Extraction Task: Resized image " + imageUri.getLastPathSegment() + " from [" + originalWidth + "x" + originalHeight + "] to [" + newWidth + "x" + newHeight + "]");
                } else {
                    resizedMat = imageMat.clone(); // No need to resize, just clone
                    Log.i(TAG, "SIFT Extraction Task: Image " + imageUri.getLastPathSegment() + " [" + originalWidth + "x" + originalHeight + "] is already within max dimensions. No resize needed.");
                }
                // imageMat can be released if it was resized and resizedMat is now the one to use
                if (resizedMat != imageMat) { // if resize happened, imageMat is different from resizedMat
                    imageMat.release();
                }

                Mat grayMat = new Mat();
                if (resizedMat.channels() > 1) {
                    org.opencv.imgproc.Imgproc.cvtColor(resizedMat, grayMat, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
                } else {
                    grayMat = resizedMat.clone(); // Clone if it's already gray
                }
                // resizedMat can be released now as grayMat holds the data (or a clone of it)
                resizedMat.release();

                MatOfKeyPoint keyPoints = new MatOfKeyPoint();
                Mat descriptors = new Mat();

                sift.detectAndCompute(grayMat, new Mat(), keyPoints, descriptors);

                GpsCoordinates currentGps = null;
                // Accessing imageGpsCoordinates needs to be thread-safe or it should be passed to AsyncTask
                // For simplicity, assuming it's populated before this task and not modified during.
                // A better approach would be to pass necessary GPS data along with URIs.
                synchronized (imageGpsCoordinates) { // Basic synchronization
                    for (GpsCoordinates gps : imageGpsCoordinates) {
                        if (gps.imageUri.equals(imageUri)) {
                            currentGps = gps;
                            break;
                        }
                    }
                }
                
                // Adding to allImageFeatures should also be synchronized if accessed from UI thread
                ImageFeatureData feature = new ImageFeatureData(imageUri, currentGps, keyPoints, descriptors);
                synchronized (allImageFeatures) { // Basic synchronization
                    allImageFeatures.add(feature);
                }
                
                Log.i(TAG, "SIFT Extraction Task: Features for " + imageUri.getLastPathSegment() + ": " + keyPoints.total() + " keypoints, " + descriptors.rows() + " descriptors");
                processedImageCount++;

                // imageMat and grayMat are already released or managed above
                // keyPoints and descriptors are stored in allImageFeatures, will be released later

            } catch (Exception e) {
                Log.e(TAG, "SIFT Extraction Task: Error processing image: " + imageUri.toString(), e);
                // Optionally publish an error message for this specific image
            }
        }
        return "SIFT feature extraction finished for " + processedImageCount + " of " + urisToProcess.size() + " images.";
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        if (values != null && values.length > 0) {
            //Toast.makeText(MainActivity.this, values[0], Toast.LENGTH_SHORT).show(); // Can be too spammy
            Log.i(TAG, "SIFT Extraction Task: onProgressUpdate - " + values[0]);
        }
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        Log.i(TAG, "SIFT Extraction Task: onPostExecute - " + result);
        Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
        if (selectImagesButton != null) {
            selectImagesButton.setEnabled(true);
        }
        // TODO: Now that SIFT features are extracted and stored in allImageFeatures,
        // proceed to the next step: Feature Matching and GPS Estimation.
        // For example, call a new method: startFeatureMatching();
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        Log.w(TAG, "SIFT Extraction Task: Cancelled.");
        Toast.makeText(MainActivity.this, "SIFT extraction cancelled.", Toast.LENGTH_SHORT).show();
        if (selectImagesButton != null) {
            selectImagesButton.setEnabled(true);
        }
        // Ensure any partially processed data or resources are cleaned up if necessary
    }
}

// This method is now effectively replaced by SiftExtractionTask
private void extractSiftFeatures() {
    // Content moved to SiftExtractionTask.doInBackground()
    // This method can be removed or kept for other purposes if needed.
    Log.d(TAG, "extractSiftFeatures() called but logic is now in SiftExtractionTask.");
}

private void extractGpsFromUri(Uri imageUri) {
    if (imageUri == null) return;

    try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
        if (inputStream != null) {
            Metadata metadata = JpegMetadataReader.readMetadata(inputStream);
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);

            if (gpsDirectory != null && gpsDirectory.containsTag(GpsDirectory.TAG_LATITUDE) && gpsDirectory.containsTag(GpsDirectory.TAG_LONGITUDE)) {
                // GeoLocation class can be used for more robust latitude/longitude parsing
                GeoLocation geoLocation = gpsDirectory.getGeoLocation();
                if (geoLocation != null) {
                    double latitude = geoLocation.getLatitude();
                    double longitude = geoLocation.getLongitude();
                    GpsCoordinates coords = new GpsCoordinates(latitude, longitude, imageUri);
                    synchronized (imageGpsCoordinates) {
                        imageGpsCoordinates.add(coords);
                    }
                    Log.i(TAG, "GPS Data (metadata-extractor) for " + imageUri.getLastPathSegment() + ": Lat=" + latitude + ", Lon=" + longitude);

                    // Log all GPS tags found by metadata-extractor for this image
                    Log.d(TAG, "--- Logging all GPS Tags from metadata-extractor for: " + imageUri.getLastPathSegment() + " ---");
                    for (com.drewenoakes.metadata.Tag tag : gpsDirectory.getTags()) {
                        Log.d(TAG, String.format("[%s] - %s = %s", gpsDirectory.getName(), tag.getTagName(), tag.getDescription()));
                    }
                    Log.d(TAG, "--- Finished logging GPS Tags from metadata-extractor for: " + imageUri.getLastPathSegment() + " ---");

                } else {
                    Log.w(TAG, "GeoLocation could not be obtained from GpsDirectory for image: " + imageUri.getLastPathSegment());
                }
            } else {
                Log.w(TAG, "No GPS data found using metadata-extractor for image: " + imageUri.getLastPathSegment());
                if (gpsDirectory != null) {
                    Log.d(TAG, "GpsDirectory found, but missing essential tags (Lat/Lon). Available tags:");
                    for (com.drewenoakes.metadata.Tag tag : gpsDirectory.getTags()) {
                        Log.d(TAG, String.format("[%s] - %s = %s", gpsDirectory.getName(), tag.getTagName(), tag.getDescription()));
                    }
                } else {
                    Log.d(TAG, "GpsDirectory itself is null.");
                    // Log all directories and tags if GPS directory is not found, for debugging
                    Log.d(TAG, "--- All Metadata Directories for: " + imageUri.getLastPathSegment() + " ---");
                    for (Directory dir : metadata.getDirectories()) {
                        Log.d(TAG, "Directory: " + dir.getName());
                        for (com.drewenoakes.metadata.Tag tag : dir.getTags()) {
                            Log.d(TAG, String.format("  [%s] - %s = %s", dir.getName(), tag.getTagName(), tag.getDescription()));
                        }
                    }
                    Log.d(TAG, "--- Finished logging all metadata for: " + imageUri.getLastPathSegment() + " ---");
                }
            }
        }
    } catch (IOException e) {
        Log.e(TAG, "Error reading image file for metadata: " + imageUri.getLastPathSegment(), e);
    } catch (ImageProcessingException e) {
        Log.e(TAG, "Error processing image with metadata-extractor for " + imageUri.getLastPathSegment(), e);
    } catch (MetadataException e) {
        Log.e(TAG, "Error extracting GPS metadata for " + imageUri.getLastPathSegment(), e);
    } catch (Exception e) { // Catch any other unexpected exception
        Log.e(TAG, "Unexpected error during GPS extraction with metadata-extractor for " + imageUri.getLastPathSegment(), e);
    }
}

@Override
public void onPause()
{
    super.onPause();
    if (mOpenCvCameraView != null)
        mOpenCvCameraView.disableView();
}

@Override
public void onResume()
{
    super.onResume();
    if (mOpenCvCameraView != null)
        mOpenCvCameraView.enableView();
}

@Override
public List<? extends CameraBridgeViewBase> getCameraViewList() {
    return Collections.singletonList(mOpenCvCameraView);
}

@Override
public void onDestroy() {
    super.onDestroy();
    if (mOpenCvCameraView != null)
        mOpenCvCameraView.disableView();
    if (allImageFeatures != null) {
        for (ImageFeatureData featureData : allImageFeatures) {
            featureData.releaseMats();
        }
        allImageFeatures.clear();
        Log.i(TAG, "Released SIFT features in onDestroy.");
    }
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

private File createTempFileFromUri(Uri uri) {
    File tempFile = null;
    try {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream != null) {
            tempFile = File.createTempFile("sift_image_", ".tmp", getCacheDir());
            tempFile.deleteOnExit();
            FileOutputStream out = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            out.close();
        }
    } catch (IOException e) {
        Log.e(TAG, "Failed to create temporary file from URI: " + uri.toString(), e);
        return null;
    }
    return tempFile;
}
```

}
