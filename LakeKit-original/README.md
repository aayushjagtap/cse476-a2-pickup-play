# LakeKit

1. Unzip LakeKit.zip.
2. In Android Studio, choose File > Open and select this folder (the one
   containing settings.gradle.kts). Don't use File > New > Import Project.
3. If prompted, choose "Trust Project".
4. If Studio says Gradle 8.14.3 is incompatible with JVM 25, open Settings >
   Build, Execution, Deployment > Build Tools > Gradle and set Gradle JDK to
   17 or 21. If neither is installed, install JDK 21 first.
5. If Studio offers to upgrade the Android Gradle Plugin, decline. The project
   is set up for 8.13.2.
6. Let Gradle sync, then run the `app` configuration on an API 34 emulator.
   If the emulator shows only a black screen, cold boot it from Device Manager.

The app downloads its data from NOAA's Ludington gauge, so the emulator needs
network access.
