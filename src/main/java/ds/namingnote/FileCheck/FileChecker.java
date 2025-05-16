package ds.namingnote.FileCheck;

import ds.namingnote.Service.ReplicationService;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ds.namingnote.Config.NNConf.FILES_DIR;

public class FileChecker implements Runnable{
    private static final Logger LOGGER = Logger.getLogger(FileChecker.class.getName());
    private boolean running = true;

    private ReplicationService replicationService;


    public FileChecker(ReplicationService replicationService) {
        this.replicationService = replicationService;
    }

    @Override
    public void run() {
        try {
            // Specify the directory which supposed to be watched
            Path directoryPath = Paths.get(FILES_DIR);

            // Create a WatchService
            WatchService watchService = FileSystems.getDefault().newWatchService();

            // Register the directory for specific events
            directoryPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            System.out.println("Watching directory: " + directoryPath);

            // Infinite loop to continuously watch for events
            while (running) {
                if (Thread.currentThread().isInterrupted()) {
                    running = false;
                    break;
                }

                WatchKey key;
                try {
                    // Retrieve and remove the next watch key, waiting if necessary
                    key = watchService.take();
                } catch (InterruptedException e) {
                    LOGGER.log(Level.INFO, "File watcher interrupted.", e);
                    running = false; // Exit the loop if interrupted
                    break;
                } catch (ClosedWatchServiceException e) {
                    LOGGER.log(Level.WARNING, "Watch service closed.", e);
                    running = false; // Exit the loop if the service is closed
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    // Get the type of event and the affected file
                    WatchEvent.Kind<?> kind = event.kind();
                    Path fileName = (Path) event.context();
                    Path fullFileName = directoryPath.resolve(fileName);

                    if (InternalFileWriteTracker.wasWrittenInternally(fileName.toString())) {
                        LOGGER.info("Ignored internally-written file: " + fileName);
                        continue;
                    }

                    // Handle the specific event
                    if (kind == StandardWatchEventKinds.OVERFLOW) {

                        LOGGER.log(Level.WARNING, "Watch event overflow - events might have been lost.");

                    } else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {

                        LOGGER.log(Level.INFO, "File created: " + fullFileName);
                        replicationService.fileAdded(fullFileName.toFile());

                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {

                        LOGGER.log(Level.INFO, "File deleted: " + fileName);

                        // Add specific handling for file deletion
                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {

                        LOGGER.log(Level.INFO, "File modified: " + fileName);
                        replicationService.fileAdded(fullFileName.toFile());

                        // Add specific handling for file modification
                    }
                }

                // To receive further events, reset the key.
                // If the key is no longer valid, the directory is inaccessible
                // or the watch service has been closed.
                boolean valid = key.reset();
                if (!valid) {
                    running = false; // Exit the loop if the key is invalid
                }
            }


        }
        catch (IOException e) {
             LOGGER.log(Level.SEVERE, "IOException in file watcher.", e);
        }

    }
}







