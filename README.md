# Image GPS Prediction using SIFT

This Android application estimates the GPS coordinates of a target image by leveraging SIFT (Scale-Invariant Feature Transform) features from a set of reference images that have known GPS locations.

## How It Works

1.  **Select Images from Gallery**: 
    *   Tap the "Select Photos from Gallery" button to choose 2 to 30 images from your device.
    *   At least one of these images should ideally have GPS data embedded in its EXIF metadata.
    *   One image will later be selected as the "target image" for which the GPS coordinates will be predicted.

2.  **SIFT Feature Extraction**:
    *   The application employs the SIFT algorithm to detect distinctive keypoints and compute their descriptors for each selected image.
    *   To optimize performance and manage memory, images are automatically resized if their dimensions exceed a predefined threshold (e.g., 1024 pixels on the longest side).
    *   This computationally intensive process is performed in a background thread (`AsyncTask`) to keep the UI responsive.

3.  **GPS Metadata Reading**:
    *   The application reads GPS coordinates (latitude and longitude) from the EXIF metadata of each selected image.
    *   It uses the `metadata-extractor` library (by Drew Noakes) for robust EXIF data parsing.

4.  **Target Image Selection**:
    *   After the SIFT features are extracted and GPS data is read for all images, a dialog appears.
    *   This dialog lists all the images that were found to contain GPS data.
    *   You select one image from this list to be the "target image." The application will then attempt to predict the GPS coordinates for this target image, which can be compared against its original GPS if known.

5.  **SIFT Feature Matching & GPS Prediction**:
    *   The SIFT descriptors of the chosen target image are matched against the SIFT descriptors of all other selected images (now serving as "reference images") that possess GPS data.
    *   The matching is done using a `DescriptorMatcher` (e.g., FLANN-based matcher).
    *   Lowe's ratio test is applied to filter for high-quality ("good") matches between the target and each reference image.

6.  **Weighted GPS Averaging**:
    *   The GPS coordinates of the target image are then predicted by calculating a weighted average of the GPS coordinates of the valid reference images.
    *   The weight assigned to each reference image in this calculation is directly proportional to the number of good SIFT matches it shares with the target image. More matches imply a higher visual similarity and thus a greater influence on the predicted location.

7.  **Displaying the Result**:
    *   Finally, an alert dialog displays both the original GPS coordinates (if they were available for the target image) and the newly predicted GPS coordinates.

## Key Features

*   Selection of multiple images from the gallery.
*   Extraction of SIFT keypoints and descriptors.
*   Reading of GPS EXIF metadata from images.
*   Prediction of GPS coordinates for a target image based on visual similarity (SIFT matches) to reference images.
*   Background processing for SIFT extraction to maintain UI responsiveness.
*   Dynamic image resizing to manage memory and performance.

## Technical Details

For a more detailed explanation of how the SIFT algorithm is specifically used in this project, please see [SIFT Implementation Details](./SIFT_DETAILS.md).

## Building from Source

For instructions on how to clone the repository, set up your environment, and build the project from source, please see [Building from Source](./BUILDING.md).

## Core Libraries Used

*   **OpenCV for Android**: Used for SIFT feature detection, description, and matching (`org.opencv.features2d.SIFT`, `org.opencv.features2d.DescriptorMatcher`).
*   **metadata-extractor**: Used for reading EXIF (including GPS) metadata from image files (`com.drewnoakes:metadata-extractor`).
*   **Android SDK Components**: Standard Android UI components, `AsyncTask` for background tasks, `Intent` for image selection, `AlertDialog` for user interaction.

## Project Purpose

This application serves as a research tool and a practical demonstration of using computer vision techniques (SIFT) combined with GPS metadata to perform location estimation for an image based on a set of visually similar, geo-tagged reference images. 