# S Camera Pro — этап 1

Камера в стиле One UI: превью 3:4, зум-кнопки (.6 / 1 / 2 / 4 / 10 по реальным возможностям камеры),
pinch-to-zoom, тап по экрану = фокус, вспышка, переворот камеры, съёмка в Pictures/SCameraPro,
миниатюра последнего снимка, карусель режимов (пока рабочий только «Фотография»).

## Сборка без Android Studio (Kubuntu)
1. `sudo apt install openjdk-17-jdk` (подойдёт и 21)
2. Android command-line tools с developer.android.com, затем:
   `sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"`
3. `export ANDROID_HOME=~/Android/Sdk` (или создай `local.properties` с `sdk.dir=...`)
4. Один раз получить wrapper: `gradle wrapper --gradle-version 8.9` (Gradle через sdkman)
5. `./gradlew assembleDebug`, затем `adb install app/build/outputs/apk/debug/app-debug.apk`

## Если ноутбук не тянет
Залей папку в GitHub: workflow `.github/workflows/build.yml` соберёт APK в облаке (Artifacts).
