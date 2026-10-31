@echo off
echo Compiling Java Photo Synchronizer...
javac PhotoSynchronizer.java

if %ERRORLEVEL% EQU 0 (
    echo Compilation successful!
    echo Creating JAR file...
    "C:\Program Files\Java\jdk-25\bin\jar.exe" cfm PhotoSynchronizer.jar manifest.txt *.class
    
    if %ERRORLEVEL% EQU 0 (
        echo JAR file created successfully!
        echo.
        echo You can now run the application with:
        echo java -jar PhotoSynchronizer.jar
        echo.
        echo Or run directly with:
        echo java PhotoSynchronizer
    ) else (
        echo Failed to create JAR file.
    )
) else (
    echo Compilation failed!
)

pause
