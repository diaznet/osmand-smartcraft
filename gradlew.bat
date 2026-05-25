@rem
@rem Gradle startup script for Windows
@rem
@if "%DEBUG%"=="" @echo off
setlocal

set DIRNAME=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Use Android Studio's bundled JDK if JAVA_HOME is not set or points to unsupported version
if defined JAVA_HOME (
    set "JAVACMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVACMD=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
)

if not exist "%JAVACMD%" (
    echo ERROR: JAVA_HOME is not set and Android Studio JDK not found.
    exit /b 1
)

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVACMD%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
endlocal
