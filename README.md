# IrisAid: Multi-modal, Low-Effort Reading Accessibility for Mobile Devices

## Abstract

Mobile devices’ small screens and dense interface elements create significant barriers for users with visual or motor impairments. Built-in magnification and accessibility tools often require precise gestures or complex navigation, which can be difficult for users with limited dexterity or tremors. This makes reading on smartphones a frustrating and inefficient task for many. To address this, IrisAid introduces a multi-modal mobile reading accessibility tool that supports flexible, low-effort interaction. The system combines multiple reading assistance modes, including physical button-based zoom, voice commands, face-distance–based magnification, and hand-gesture–based navigation using landmark detection. These modes enable users to adjust text size and navigate content with minimal reliance on precise touch gestures. A functional Android prototype demonstrates the feasibility of integrating these interaction modes into a unified reading aid. A heuristic usability evaluation is planned to examine learnability, efficiency, and user satisfaction compared to standard mobile accessibility tools. The findings aim to guide the design of adaptive and inclusive mobile reading interfaces.

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
