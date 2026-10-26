@echo off
set "JAVA_HOME=D:\jdk-17\jdk-17.0.12+7"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_USER_HOME=D:\.gradle"
set "ANDROID_HOME=D:\android-sdk"
set "ANDROID_SDK_ROOT=D:\android-sdk"
cd /d "D:\reel-counter"
call "C:\Users\HP\.gradle\wrapper\dists\gradle-8.14.3-all\10utluxaxniiv4wxiphsi49nj\gradle-8.14.3\bin\gradle.bat" assembleRelease -x test -x lint --no-daemon --max-workers=2
if exist "D:\reel-counter\app\build\outputs\apk\release\app-release.apk" (
    copy /Y "D:\reel-counter\app\build\outputs\apk\release\app-release.apk" "D:\reel-counter\rotcount-v1.0.1.apk"
    copy /Y "D:\reel-counter\app\build\outputs\apk\release\app-release.apk" "D:\reel-counter\rotcount-v1.0.0.apk"
)
