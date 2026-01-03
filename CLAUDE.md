# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Build Commands
- **Debug APK**: `./gradlew.bat assembleDebug` (Windows) or `./gradlew assembleDebug` (Unix)
  - Output: `app/build/outputs/apk/debug/app-debug.apk`
- **Install to device**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- **Clean build**: `./gradlew.bat clean` then rebuild

### Testing Commands
- **Run unit tests**: `./gradlew.bat test`
- **Run instrumentation tests**: `./gradlew.bat connectedAndroidTest`
- **Test specific class**: Not configured - uses default Android test structure

### Backend Development
- **Start backend server**: Navigate to `server/` directory and run `mvn spring-boot:run`
  - HTTP endpoint: `http://localhost:8080`
  - WebSocket endpoint: `ws://localhost:8080/ws/obstacle`
- **Build backend JAR**: `mvn clean package` in `server/` directory

## Architecture Overview

### Core Architecture Pattern
The application follows a **Singleton-based Service Locator** pattern with clear separation of concerns across five main components:

1. **MainActivity**: Entry point that coordinates core managers and handles UI/permissions
2. **VoiceManager**: Singleton managing TTS (Text-to-Speech) and ASR (Automatic Speech Recognition) using Android's built-in services
3. **NetworkClient**: Singleton handling all network communication with comprehensive mock mode support (`Config.MOCK_MODE = true`)
4. **ImageCaptureManager**: Singleton managing CameraX operations for image capture and video streaming
5. **FeatureRouter**: Singleton coordinating feature flows based on user intent

### Feature Implementation Status
All five core features are fully implemented in mock mode:
- **NAVIGATION**: Voice-guided navigation with step-by-step instructions
- **OBSTACLE_AVOIDANCE**: Real-time obstacle detection via video stream over WebSocket
- **QA_VOICE**: General voice Q&A with follow-up capabilities
- **OCR**: Text recognition from captured images
- **SCENE_DESCRIPTION**: Environmental scene understanding

### Network Communication
- **Mock Mode**: Controlled by `Config.MOCK_MODE` flag - simulates all network responses with realistic fake data
- **Real Mode**: Uses OkHttp for REST API calls and WebSocket connections
- **Key APIs**:
  - `askQuestion()`: Voice Q&A functionality
  - `uploadVisionFrame()`: Single image upload for OCR/scene description
  - `connectVisionStream()`: WebSocket connection for real-time vision processing
  - `requestNavigation()`: Navigation route planning

### Camera Integration
- Uses **CameraX** library (v1.3.1) for camera operations
- **Single frame capture**: High-resolution images for OCR/scene analysis
- **Video streaming**: Real-time YUV to JPEG conversion for obstacle avoidance
- Proper lifecycle binding to Activity/Fragment

### Voice System
- **TTS**: Uses Android's `TextToSpeech` with automatic fallback to system engines for Chinese language support
- **ASR**: Uses Android's built-in `SpeechRecognizer` with comprehensive error handling
- **Thread safety**: Proper queue management and callback handling for voice operations

### Current Configuration
- **Target SDK**: 34 (Android 14)
- **Min SDK**: 24 (Android 7.0)
- **Java Compatibility**: Java 11
- **Gradle Version**: 8.13 with AGP 8.13.2
- **Mock Mode**: Currently enabled (`Config.MOCK_MODE = true`) for development/testing

### Interface Specifications
The project follows detailed interface specifications documented in `interface.md` covering:
- REST API endpoints for voice commands, vision frames, and navigation
- WebSocket protocols for real-time vision streaming
- AutoGLM automation control protocol with action mapping
- JSON request/response formats for all services

When making changes, ensure compatibility with these interface specifications and maintain the mock mode capability for offline development.