@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
call gradlew.bat clean assembleDebug --stacktrace > build_log.txt 2>&1
type build_log.txt
