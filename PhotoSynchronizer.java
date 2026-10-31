import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;

public class PhotoSynchronizer {
    private static final String LAPTOP_DESTINATION_FOLDER = "C:\\Users\\dafat\\Pictures\\SDCard";
    private static final String SD_CARD_SOURCE_FOLDER = "DCIM";
    
    private static final Logger logger = Logger.getLogger(PhotoSynchronizer.class.getName());
    private static final Map<String, Boolean> knownDrives = new ConcurrentHashMap<>();
    private static final String systemDrive = System.getenv("SystemDrive") + "\\";
    private static ProgressWindow progressWindow;
    
    public static void main(String[] args) {
        setupLogging();
        logger.info("Java SD Card Syncer is running (v1 - GUI Progress)...");
        logger.info("Will sync to: " + LAPTOP_DESTINATION_FOLDER);
        
        progressWindow = new ProgressWindow();
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        
        Runnable driveChecker = () -> {
            try {
                logger.info("--- Starting drive check cycle ---");
                java.util.List<String> drives = getLogicalDrives();
                logger.info("Drives found in current check: " + drives);
                
                Set<String> currentDrives = new HashSet<>(drives);
                
                for (String drive : drives) {
                    if (!knownDrives.containsKey(drive) && !drive.equals(systemDrive)) {
                        logger.info("New drive detected: " + drive);
                        knownDrives.put(drive, true);
                        CompletableFuture.runAsync(() -> syncDrive(drive));
                    }
                }
                
                Iterator<Map.Entry<String, Boolean>> iterator = knownDrives.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Boolean> entry = iterator.next();
                    String drive = entry.getKey();
                    if (!currentDrives.contains(drive)) {
                        logger.info("Drive removed: " + drive);
                        iterator.remove();
                    }
                }
                
                logger.info("--- Finished drive check cycle ---");
            } catch (Exception e) {
                logger.severe("Error during drive check: " + e.getMessage());
            }
        };
        
        driveChecker.run();
        scheduler.scheduleAtFixedRate(driveChecker, 8, 8, TimeUnit.SECONDS);
    }
    
    private static void setupLogging() {
        try {
            File logDir = new File("log");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            FileHandler fileHandler = new FileHandler("log/syncer-log.txt", true);
            SimpleFormatter formatter = new SimpleFormatter();
            fileHandler.setFormatter(formatter);
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Failed to setup logging: " + e.getMessage());
        }
    }
    
    private static java.util.List<String> getLogicalDrives() {
        java.util.List<String> drives = new ArrayList<>();
        File[] roots = File.listRoots();
        for (File root : roots) {
            String path = root.getAbsolutePath();
            if (path.matches("[A-Z]:\\\\")) {
                drives.add(path);
            }
        }
        return drives;
    }
    
    private static void syncDrive(String driveLetter) {
        String sourcePath = Paths.get(driveLetter, SD_CARD_SOURCE_FOLDER).toString();
        logger.info("Starting sync from: " + sourcePath);
        
        File sourceDir = new File(sourcePath);
        if (!sourceDir.exists()) {
            logger.info("Source path " + sourcePath + " not found on drive " + driveLetter + ". Nothing to sync.");
            return;
        }
        
        try {
            int totalFiles = countFiles(sourceDir);
            if (totalFiles == 0) {
                logger.info("No files found to sync on drive " + driveLetter);
                return;
            }
            
            logger.info("Found " + totalFiles + " files to process on drive " + driveLetter);
            
            boolean userConfirmed = showSyncConfirmation(driveLetter, totalFiles);
            if (!userConfirmed) {
                logger.info("User cancelled sync for drive " + driveLetter);
                return;
            }
            
            SwingUtilities.invokeLater(() -> progressWindow.showProgress(driveLetter, totalFiles));
            
            Thread.sleep(1000);
            
            SyncResult result = syncFiles(sourceDir, new File(LAPTOP_DESTINATION_FOLDER), totalFiles);
            
            logger.info("Sync finished for drive " + driveLetter + ". Files copied: " + result.copiedFiles);
            
            Timer timer = new Timer(2000, e -> progressWindow.hideProgress());
            timer.setRepeats(false);
            timer.start();
            
        } catch (Exception e) {
            logger.severe("Error during sync for drive " + driveLetter + ": " + e.getMessage());
        }
    }
    
    private static boolean showSyncConfirmation(String driveLetter, int totalFiles) {
        final boolean[] result = {false};
        try {
            SwingUtilities.invokeAndWait(() -> {
                String message = String.format(
                    "SD Card detected on drive %s\n\n" +
                    "Found %d files to sync to:\n%s\n\n" +
                    "Do you want to start the sync now?",
                    driveLetter, totalFiles, LAPTOP_DESTINATION_FOLDER
                );
                
                int choice = JOptionPane.showConfirmDialog(
                    null,
                    message,
                    "SD Card Sync Confirmation",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                );
                
                result[0] = (choice == JOptionPane.YES_OPTION);
            });
        } catch (Exception e) {
            logger.severe("Error showing confirmation dialog: " + e.getMessage());
            return false;
        }
        
        return result[0];
    }
    
    private static int countFiles(File directory) {
        int count = 0;
        if (!directory.exists() || !directory.isDirectory()) {
            return count;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    count += countFiles(file);
                } else {
                    count++;
                }
            }
        }
        return count;
    }
    
    private static SyncResult syncFiles(File sourceDir, File destDir, int totalFiles) throws IOException {
        SyncResult result = new SyncResult();
        syncFilesRecursive(sourceDir, destDir, sourceDir, result, totalFiles);
        return result;
    }
    
    private static void syncFilesRecursive(File currentSource, File destBase, File sourceBase, 
                                         SyncResult result, int totalFiles) throws IOException {
        File[] files = currentSource.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                syncFilesRecursive(file, destBase, sourceBase, result, totalFiles);
            } else {
                Path sourcePath = sourceBase.toPath();
                Path filePath = file.toPath();
                Path relativePath = sourcePath.relativize(filePath);
                
                File destFile = destBase.toPath().resolve(relativePath).toFile();
                
                if (destFile.exists()) {
                    result.copiedFiles++;
                    SwingUtilities.invokeLater(() -> 
                        progressWindow.updateProgress(result.copiedFiles, totalFiles, file.getName()));
                    continue;
                }
                
                destFile.getParentFile().mkdirs();
                
                try {
                    Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Copied: " + file.getName());
                    result.copiedFiles++;
                    
                    SwingUtilities.invokeLater(() -> 
                        progressWindow.updateProgress(result.copiedFiles, totalFiles, file.getName()));
                    
                } catch (IOException e) {
                    logger.severe("Error copying " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }
    
    private static class SyncResult {
        int copiedFiles = 0;
    }
}

class ProgressWindow {
    private JFrame frame;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JLabel fileLabel;
    private boolean isShown = false;
    
    public void showProgress(String driveLetter, int totalFiles) {
        if (isShown) {
            return;
        }
        
        isShown = true;
        
        frame = new JFrame("Photo Sync Progress - SD Card (" + driveLetter + ")");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(450, 200);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setResizable(false);
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        statusLabel = new JLabel("Initializing sync...");
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(statusLabel);
        
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(progressBar);
        
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        fileLabel = new JLabel(" ");
        fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fileLabel);
        
        frame.add(panel);
        frame.setVisible(true);
    }
    
    public void updateProgress(int current, int total, String filename) {
        if (!isShown || frame == null) {
            return;
        }
        
        int percentage = (int) ((double) current / total * 100);
        
        statusLabel.setText(String.format("Progress: %d/%d files (%d%%)", current, total, percentage));
        progressBar.setValue(percentage);
        fileLabel.setText("Current: " + filename);
    }
    
    public void hideProgress() {
        if (!isShown || frame == null) {
            return;
        }
        
        statusLabel.setText("Sync completed!");
        progressBar.setValue(100);
        fileLabel.setText("");
        
        Timer timer = new Timer(2000, e -> {
            frame.dispose();
            isShown = false;
        });
        timer.setRepeats(false);
        timer.start();
    }
}
