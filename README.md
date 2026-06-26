# FinancialAwareness

Android app for financial simulation, surplus planning, optimization, charts, profile comparison, PDF reporting, and OpenRouter-based AI analysis.

## Features

- Financial input management with persistent profiles
- Detailed surplus calculator for work and pension phases
- Optimization and sensitivity analysis
- Charts and comparison mode
- PDF export
- OpenRouter AI assistant and report generation

## Tech Stack

- Kotlin
- Jetpack Compose
- Android Gradle
- SharedPreferences persistence
- OpenRouter API

## Requirements

- Android Studio
- JDK 17
- Android SDK configured in the local environment

## Setup

```bash
git clone https://github.com/jagones84/FinantialAwareness.git
cd FinantialAwareness
./gradlew.bat assembleDebug
```

## AI Setup

- Open the AI Agent screen in the app
- Insert your OpenRouter API key
- Select the desired model
- Default model: `qwen/qwen3.7-plus`

## Build And Test

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

## Notes

- Keep `local.properties` and signing material out of the repository
- The app stores local data on-device using SharedPreferences
