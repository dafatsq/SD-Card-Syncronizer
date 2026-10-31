# SD Card Photo Synchronizer

A lightweight Java background utility for Windows that automatically detects inserted SD cards and synchronizes photos/media from the card's `DCIM` folder to a local folder on your computer, complete with a confirmation prompt and live Swing progress window.

## Features

- **Automatic Drive Detection**: Polls logical drives every 8 seconds in the background and detects newly mounted drives (automatically ignoring the system drive).
- **Smart Re-insertion Handling**: Tracks connected drives and resets state when a drive is removed, so re-inserting an SD card triggers detection again.
- **DCIM Source Check**: Automatically looks for a `DCIM` folder on the newly detected drive and counts total files before prompting.
- **User Confirmation Dialog**: Displays a confirmation prompt with the detected drive letter, file count, and destination path before copying anything.
- **Incremental File Sync**: Preserves the folder hierarchy inside `DCIM` and skips files that already exist in the destination directory.
- **Live GUI Progress Bar**: Shows an always-on-top Swing progress window with file count, percentage, and current filename, which auto-closes upon completion.
- **Persistent Logging**: Writes activity and error logs to `log/syncer-log.txt`.

## Project Structure

```text
.
├── PhotoSynchronizer.java   # Main application source (drive watcher, file syncer, Swing GUI)
├── PhotoSynchronizer.jar    # Prebuilt executable JAR
├── build.bat                # Windows batch script to compile and package the JAR
├── manifest.txt             # JAR manifest specifying the Main-Class
└── log/
    └── syncer-log.txt       # Application runtime logs
```

## Requirements

- **OS**: Windows (uses Windows drive letter patterns `[A-Z]:\` and `SystemDrive` environment variable)
- **Java**: JDK / JRE 8 or newer (`build.bat` is configured for JDK 25 by default)

## Configuration

Before building, you can customize the source and destination paths at the top of `PhotoSynchronizer.java`:

```java
private static final String LAPTOP_DESTINATION_FOLDER = "C:\\Users\\dafat\\Pictures\\SDCard";
private static final String SD_CARD_SOURCE_FOLDER = "DCIM";
```

## Building

### Using `build.bat` (Windows)

Run the included batch script:

```bat
build.bat
```

> **Note:** `build.bat` references `"C:\Program Files\Java\jdk-25\bin\jar.exe"`. If your JDK is installed in a different path or `jar` is already on your `PATH`, update `build.bat` or run the manual commands below.

### Manual Build

```bash
javac PhotoSynchronizer.java
jar cfm PhotoSynchronizer.jar manifest.txt *.class
```

## Running the Application

Run the packaged JAR:

```bash
java -jar PhotoSynchronizer.jar
```

Or run the compiled class directly:

```bash
java PhotoSynchronizer
```

### Running on Windows Startup (Optional)

To have the synchronizer run silently in the background whenever you log into Windows:
1. Press `Win + R`, type `shell:startup`, and press **Enter**.
2. Create a shortcut to `PhotoSynchronizer.jar` (or a `.vbs` / `.bat` launcher using `javaw -jar PhotoSynchronizer.jar`) inside the Startup folder.

## How It Works

1. On startup, `PhotoSynchronizer` initializes logging to `log/syncer-log.txt` and starts a scheduled check every **8 seconds**.
2. When a new drive letter is detected (excluding `C:\` / `%SystemDrive%`), it checks for `<Drive>:\DCIM`.
3. If `DCIM` exists and contains files, a dialog asks whether you want to start syncing.
4. Upon confirmation, all new files inside `DCIM` (including subdirectories) are copied to `LAPTOP_DESTINATION_FOLDER` while updating the progress bar.
