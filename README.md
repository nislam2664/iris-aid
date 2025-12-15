# IrisAid

## Project Structure

- **app/src/main/java/com/example/irisaid/**
  - `FaceOverlayView.kt` – Custom Android View that draws visual overlays on top of a camera preview to indicate detected faces and facial landmarks
  - `HandLandmarkerHelper.kt` - Helper class for detecting and tracking hand landmarks using Google MediaPipe’s Hand Landmarker API
  - `HandOverlayView.kt` - Custom Android View that draws hand landmarks and their connections on a canvas utilizing Google MediaPipe's Hand Landmarker API
  - `MainActivity.kt` – Main page, dropdown menu, instructions, book selection
  - `ModeActivity.kt` – Displays book excerpts into pages, handles text zoom and page navigation for each mode
    
- **app/src/main/res/**
  - `drawable/` – Images, icons, book covers
  - `layout/` – XML layout files for activities
  - `raw` - Audio files (demo, start, stop)
  - `values/` – Strings, dimensions, colors, themes

- **Other files**
  - `build.gradle` – Gradle configuration
  - `README.md` – This file
  - `.gitignore` – Files/folders excluded from Git
 
## How to Run

1. Download Android Studio if not already installed.
2. Clone the repository into desired location.
3. Open folder in Android Studio.
4. Build and run on a device with a front-facing camera (or use phone simulator given by Android Studio).
