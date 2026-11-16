# LakeLog starter

This is the starting project for Part 1 of the CSE476 Fall 2026 exam. It builds
and launches, and that's all it does. Everything the exam asks for is yours to
build.

## Open and run it

1. Unzip LakeLog-Starter.zip.
2. In Android Studio, choose File > Open and select this folder (the one
   containing settings.gradle.kts). Don't use File > New > Import Project.
3. If prompted, choose "Trust Project".
4. If Studio says Gradle 8.14.3 is incompatible with JVM 25, open Settings >
   Build, Execution, Deployment > Build Tools > Gradle and set Gradle JDK to
   17 or 21. If neither is installed, install JDK 21 first.
5. If Studio offers to upgrade the Android Gradle Plugin, decline. The project
   is set up for 8.13.2.
6. Let Gradle sync, then run the `app` configuration on an API 34 emulator.
   You should see an empty main screen titled LakeLog. If the emulator shows
   only a black screen, cold boot it from Device Manager.

## What's here

- Kotlin and XML, minSdk 26, targetSdk 34, package `edu.msu.cse476.lakelog`.
- Three Activities, declared in `AndroidManifest.xml`: `MainActivity` (the
  launcher), `ReadingsActivity`, and `NoteActivity`. Each shows only a
  placeholder.
- One layout per Activity under `app/src/main/res/layout`, plus `strings.xml`
  and `themes.xml`.

## Where your work goes

Replace the placeholder layouts and build out the three Activities, adding any
classes, resources, permissions, and libraries your design needs. Nothing is
wired up: there's no navigation, networking, storage, sensor code, or list.
You may rename, restructure, or replace anything here.

## What to submit

Submit your completed LakeLog project folder and your LakeLog.apk, as the exam
describes. Don't submit this starter unchanged.
