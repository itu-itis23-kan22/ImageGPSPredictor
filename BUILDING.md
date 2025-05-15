# Building the Project from Source

This guide explains how to set up your development environment and build the Image GPS Prediction Android application from its source code on GitHub.

## Prerequisites

Before you begin, ensure you have the following software installed and configured on your system:

1.  **Git**: For cloning the project repository. You can download it from [git-scm.com](https://git-scm.com/).
2.  **Android Studio**: The latest stable version is recommended. You can download it from the [Android Developer website](https://developer.android.com/studio).
3.  **Java Development Kit (JDK) 17**: This project requires JDK 17 to compile. 
    *   You can download OpenJDK 17 (e.g., from [Adoptium Temurin](https://adoptium.net/) or [Azul Zulu](https://www.azul.com/downloads/?package=jdk)).
    *   Ensure Android Studio is configured to use this JDK for Gradle projects. You might need to set the `org.gradle.java.home` property in your global `gradle.properties` file or configure it within Android Studio's settings (`File > Settings/Preferences > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK`). The project includes a `gradle.properties` file with `org.gradle.java.home=/Users/hasankan/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home`, but you **must** change this path to point to your local JDK 17 installation.
4.  **Android SDK**: 
    *   Make sure you have the necessary Android SDK Platform installed (the project uses `compileSdk = 34`).
    *   You will also need the Android SDK Build-Tools.
    *   These can be installed or updated via Android Studio's SDK Manager (`File > Settings/Preferences > Appearance & Behavior > System Settings > Android SDK`).
5.  **Android NDK (Native Development Kit)**: Required for building the native C++ parts of the OpenCV library used in this project.
    *   Install the NDK via Android Studio's SDK Manager (under the "SDK Tools" tab).
6.  **CMake**: Used by Gradle to build native code (OpenCV).
    *   Install CMake via Android Studio's SDK Manager (under the "SDK Tools" tab).

## Build Steps

1.  **Clone the Repository**:
    Open your terminal or command prompt and clone the project repository from GitHub:
    ```bash
    git clone https://github.com/itu-itis23-kan22/ImageGPSPredictor.git
    ```
    This will create a directory named `ImageGPSPredictor` (or `ImageProcessng kopyası` if you cloned the original name) containing the project files.

2.  **Open the Project in Android Studio**:
    *   Launch Android Studio.
    *   Select "Open" (or "Open an Existing Project").
    *   Navigate to the directory where you cloned the project and select it.

3.  **Gradle Sync**:
    *   Android Studio will automatically attempt to sync the project with Gradle. This process downloads dependencies and configures the project structure.
    *   Pay attention to any error messages in the "Build" output window. Common issues at this stage might be related to missing SDK components, incorrect JDK configuration, or NDK/CMake setup.
    *   If prompted, allow Android Studio to install any missing SDK components.
    *   Ensure your JDK 17 path is correctly set for Gradle as mentioned in the prerequisites. If you encounter Java version errors, this is the most likely cause.

4.  **Build the Project**:
    Once Gradle sync completes successfully, you can build the project:
    *   **To generate a debug APK**: 
        *   Go to `Build > Build Bundle(s) / APK(s) > Build APK(s)` in the Android Studio menu.
        *   Alternatively, open the Terminal window within Android Studio (`View > Tool Windows > Terminal`) and run:
          ```bash
          ./gradlew assembleDebug
          ```
        The generated APK (`app-debug.apk`) will be located in the `app/build/outputs/apk/debug/` directory.
    *   **To run the app on an emulator or connected device**:
        *   Select an available emulator or connect a physical Android device (ensure USB debugging is enabled on the device).
        *   Click the "Run 'app'" button (green play icon) in the Android Studio toolbar, or select `Run > Run 'app'`.

## Troubleshooting

*   **Java Version Errors**: Ensure JDK 17 is installed and that Android Studio / Gradle is configured to use it. Double-check the `org.gradle.java.home` path in your system's global `gradle.properties` or the project's `gradle.properties` (and modify it for your local setup).
*   **NDK / CMake Errors**: Make sure NDK and CMake are installed via the SDK Manager in Android Studio. Errors like "Ninja: build.ninja:35: loading 'CMakeFiles/rules.ninja': No such file or directory" often point to issues with the native build setup. Sometimes, cleaning the project (`Build > Clean Project`) and rebuilding, or invalidating caches and restarting Android Studio (`File > Invalidate Caches / Restart...`) can help.
*   **OpenCV Native Library Issues**: If the OpenCV module fails to build, ensure NDK and CMake are correctly configured. The project is set up to build OpenCV from the sources included in the `openCV` module directory.

If you encounter other issues, check the error messages in the Android Studio "Build" or "Logcat" windows for more specific details. 