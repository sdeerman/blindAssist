# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BlindAssist is an Android accessibility application for visually impaired users, featuring voice interaction and AI-powered UI automation via AutoGLM. The architecture consists of:

1. **Android Client** (`app/`) - Native Android app with voice recognition (ASR), text-to-speech (TTS), and accessibility service
2. **Spring Boot Backend** (`server/`) - Java backend acting as bridge between Android client and Python AutoGLM model
3. **Python AutoGLM Model** (external) - AI model that controls device UI through the backend

## Development Commands

### Android Client
- **Build debug APK**: `./gradlew.bat assembleDebug` (Windows) or `./gradlew assembleDebug` (Unix)
- **Install to device**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- **Clean build**: `./gradlew.bat clean assembleDebug`

### Spring Boot Backend
- **Run locally**: `cd server && mvn spring-boot:run`
- **Build JAR**: `cd server && mvn clean package`
- **Run JAR**: `java -jar server/target/blindassist-server-0.0.1-SNAPSHOT.jar`

### Network Configuration
The project supports two network modes depending on your environment:

**USB Debugging Mode** (for AP-isolated networks):
- Set `SERVER_URL = "ws://localhost:8090/ws/agent"` in `AgentManager.java`
- Run `adb reverse tcp:8090 tcp:8090` to forward ports before testing

**Standard LAN Mode**:
- Set `SERVER_URL` to your computer's LAN IP (e.g., `"ws://192.168.1.100:8090/ws/agent"`)
- Ensure firewall allows port 8090

### Development Modes
- **User Mode** (`Config.DEBUG_MODE = false`): Voice interaction interface with press-to-talk
- **Debug Mode** (`Config.DEBUG_MODE = true`): Manual text input for testing
- **Mock Mode** (`Config.MOCK_MODE = true`): Uses fake data instead of real network calls

## Code Architecture

### Android Client Structure
- **Core Classes**:
  - `MainActivity.java`: Main activity with dual UI modes
  - `VoiceManager.java`: ASR/TTS management using Android native SpeechRecognizer and TextToSpeech
  - `FeatureRouter.java`: Routes voice commands to appropriate features (navigation, QA, OCR, etc.)
  - `NetworkClient.java`: HTTP/WebSocket communication layer (currently uses mock responses)
  - `AgentManager.java`: WebSocket communication for AutoGLM control
  - `AutoGLMService.java`: Accessibility service for UI automation (Tap, Swipe, Type, Launch App)
  - `AccessibilityScreenshotManager.java`: Silent screenshot capture via accessibility service
  - `Config.java`: Feature flags for development modes

- **Key Components**:
  - `ImageCaptureManager.java`: CameraX integration for image capture (currently TODO implementation)
  - `AppRegistry.java`: Maps app names to package names for reliable Launch actions
  - `AudioRecorderManager.java`: Audio recording management (PCM, 16kHz)

### Backend Server Structure
- **WebSocket Endpoints**:
  - `/ws/agent`: Handles Android client connections and bridges to Python AutoGLM
  - Server runs on port 8090 (configured in `application.properties`)

- **Key Services**:
  - `AgentService.java`: Manages Python client connections per session
  - `AgentWebSocketHandler.java`: Handles Android client WebSocket messages
  - `PythonAgentClient.java`: Connects to external Python AutoGLM service

- **Configuration**:
  - Python service URL: `PYTHON_SERVER_BASE_URI = "ws://your_model_ip:8080/ws/agent/"` in `AgentService.java`

### Current Implementation Status
Based on the README checklist and recent commits:
- ✅ Voice recognition (ASR) implemented using Android native SpeechRecognizer
- ✅ TTS works across different Android devices (Google and non-Google services)
- ✅ Feature wheel UI with visual feedback and optimized interaction
- ✅ AutoGLM accessibility service for UI automation
- ✅ WebSocket communication for real-time control
- ⏳ CameraX integration for image capture needs implementation
- ⏳ Real backend API integration (currently using mock responses)
- ⏳ Response parsing from actual API endpoints instead of hardcoded strings

## Key Files to Reference

- **Network Configuration**: `app/src/main/java/com/example/test_android_dev/manager/AgentManager.java` (line 23)
- **Backend Bridge**: `server/src/main/java/com/blindassist/server/service/AgentService.java` (line 22)
- **Development Modes**: `app/src/main/java/com/example/test_android_dev/Config.java`
- **Build Configuration**: `app/build.gradle`, `server/pom.xml`, `gradle.properties`
- **Permissions**: `app/src/main/AndroidManifest.xml` (RECORD_AUDIO, CAMERA, ACCESSIBILITY_SERVICE, QUERY_ALL_PACKAGES)

## Testing Approach

The project currently relies on manual testing due to minimal automated test infrastructure:
- Android: Basic JUnit test template exists but no actual tests implemented
- Backend: No test files found in server directory
- Primary testing method: Build debug APK and test on physical Android device

When making changes, ensure compatibility with both Google and non-Google Android devices, as the TTS system includes fallback logic for devices without Google Play Services.