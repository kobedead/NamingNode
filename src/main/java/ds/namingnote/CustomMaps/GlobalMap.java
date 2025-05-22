package ds.namingnote.CustomMaps;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ds.namingnote.Agents.FileInfo;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static ds.namingnote.Config.NNConf.GLOBAL_MAP_PATH;

public class GlobalMap {

    // Singleton instance variable, volatile to ensure visibility across threads
    private static volatile GlobalMap instance;

    // Lock for synchronizing the singleton instance creation (double-checked locking)
    private static final Object INSTANCE_LOCK = new Object();

    // Lock for synchronizing file I/O operations to prevent concurrent file corruption
    private static final Object FILE_IO_LOCK = new Object();

    // ObjectMapper for JSON serialization/deserialization
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Path to the JSON file where the map data is persisted
    private final String FILE_PATH = GLOBAL_MAP_PATH;

    // The internal map, using ConcurrentHashMap for thread-safe in-memory operations
    private final ConcurrentHashMap<String, FileInfo> internalMap;

    /**
     * Private constructor to enforce the singleton pattern.
     * Loads the initial state from the JSON file into the internal ConcurrentHashMap.
     */
    private GlobalMap() {
        // Load initial data from JSON into a temporary HashMap, then populate ConcurrentHashMap
        Map<String, FileInfo> loadedData = loadJSON();
        this.internalMap = new ConcurrentHashMap<>(loadedData);
    }

    /**
     * Returns the singleton instance of GlobalMap.
     * Uses double-checked locking to ensure thread-safe lazy initialization.
     * @return The single instance of GlobalMap.
     */
    public static GlobalMap getInstance() {
        // First check outside the synchronized block for performance
        if (instance == null) {
            // Synchronize on a class-level lock to ensure only one thread initializes
            synchronized (INSTANCE_LOCK) {
                // Second check inside the synchronized block to prevent multiple initializations
                if (instance == null) {
                    instance = new GlobalMap();
                }
            }
        }
        return instance;
    }

    // - operations dedicated to FileInfo 'manipulation'
    public FileInfo putReplicationReference(String filename , String ipOfReference){
        //i want to add a replication Reference to a files FileInfo
        return this.put( filename ,new FileInfo(filename , null , ipOfReference ));
    }


    public void removeReplicationReference(String filename, String ipOfRef) {
        FileInfo currentFileInfo = internalMap.get(filename);
        if (currentFileInfo != null) {
            currentFileInfo.removeReplicationLocation(ipOfRef);
            saveJSON();
        }
        // If FileInfo doesn't exist for the filename, there's nothing to remove.
    }


    // Method to set the owner of a FileInfo object
    public void setOwner(String key, String newOwner) {
        FileInfo currentFileInfo = internalMap.get(key);
        if (currentFileInfo != null) {
            currentFileInfo.setOwner(newOwner);
            saveJSON(); // Persist the change
        }
        else {
            put(key , new FileInfo(key , newOwner , null));
        }
    }









    // --- Map-like operations, delegating to internalMap and persisting changes ---

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old value is replaced
     * and its properties are merged with the new value.
     * The changes are then persisted to the JSON file.
     * @param key The key with which the specified value is to be associated.
     * @param singleFileInfo The value to be associated with the specified key.
     * @return The previous value associated with key, or null if there was no mapping for key.
     */
    public FileInfo put(String key, FileInfo singleFileInfo) {
        // Retrieve the old value before potential modification or replacement
        FileInfo oldValue = internalMap.get(key);

        // Check if the key already exists to decide between merging or direct put
        if (internalMap.containsKey(key)) {
            // Get the existing mutable FileInfo object reference from the map
            FileInfo existingFileInfo = internalMap.get(key);

            // Update existing FileInfo properties based on the incoming singleFileInfo
            // Assuming FileInfo is mutable and modifications to existingFileInfo
            // directly reflect in the internalMap.
            if (singleFileInfo.getOwner() != null) {
                existingFileInfo.setOwner(singleFileInfo.getOwner());
            }
            if (singleFileInfo.getReplicationLocations() != null) {
                // Add all replication locations from the new info to the existing one
                existingFileInfo.getReplicationLocations().addAll(singleFileInfo.getReplicationLocations());
            }
            // No need to call internalMap.put(key, existingFileInfo) again here
            // if existingFileInfo was modified in place.
        } else {
            // If the key does not exist, simply add the new file info
            internalMap.put(key, singleFileInfo);
        }

        saveJSON(); // Persist changes to the JSON file after the map operation
        return oldValue;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     * @param key The key whose associated value is to be returned.
     * @return The value to which the specified key is mapped, or {@code null} if this map contains no mapping for the key.
     */
    public FileInfo get(String key) {
        return internalMap.get(key);
    }

    /**
     * Removes the mapping for a key from this map if it is present.
     * The changes are then persisted to the JSON file.
     * @param key The key whose mapping is to be removed from the map.
     * @return The previous value associated with {@code key}, or {@code null} if there was no mapping for {@code key}.
     */
    public FileInfo remove(String key) {
        FileInfo removedValue = internalMap.remove(key);
        // Only save to JSON if an entry was actually removed
        if (removedValue != null) {
            saveJSON();
        }
        return removedValue;
    }

    /**
     * Returns {@code true} if this map contains a mapping for the specified key.
     * @param key The key whose presence in this map is to be tested.
     * @return {@code true} if this map contains a mapping for the specified key.
     */
    public boolean containsKey(String key) {
        return internalMap.containsKey(key);
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are reflected in the set, and vice-versa.
     * @return A set view of the keys contained in this map.
     */
    public Set<String> keySet() {
        return internalMap.keySet();
    }

    /**
     * Returns the number of key-value mappings in this map.
     * @return The number of key-value mappings in this map.
     */
    public int size() {
        return internalMap.size();
    }

    /**
     * Returns {@code true} if this map contains no key-value mappings.
     * @return {@code true} if this map contains no key-value mappings.
     */
    public boolean isEmpty() {
        return internalMap.isEmpty();
    }

    /**
     * Removes all of the mappings from this map. The map will be empty after this call returns.
     * The changes are then persisted to the JSON file.
     */
    public void clear() {
        internalMap.clear();
        saveJSON();
    }

    // --- JSON Persistence Methods ---

    /**
     * Updates the JSON file with the current objects stored in the in-memory map.
     * This method is synchronized using {@code FILE_IO_LOCK} to prevent concurrent file writes,
     * ensuring data integrity of the JSON file.
     */
    private void saveJSON() {
        // Synchronize on a dedicated lock for file I/O
        synchronized (FILE_IO_LOCK) {
            File file = new File(FILE_PATH);
            try {
                // Write the current contents of the ConcurrentHashMap to the file
                // ObjectMapper can handle ConcurrentHashMap directly.
                objectMapper.writeValue(file, internalMap);
                System.out.println("GlobalMap updated and saved to " + FILE_PATH + " successfully! Map size is now " + size());
            } catch (IOException e) {
                // Wrap IOException in a Spring-specific exception for consistent error handling
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error writing to JSON file: " + FILE_PATH, e);
            }
        }
    }

    /**
     * Reads all key-value pairs from the JSON file into a Map.
     * This method is primarily called during the singleton's initialization.
     * It's also synchronized to prevent conflicts if multiple threads try to read the file
     * during initialization (though the singleton's `INSTANCE_LOCK` primarily handles this).
     * @return A Map containing all key-value pairs from the JSON "database".
     */
    private Map<String, FileInfo> loadJSON() {
        // Synchronize on a dedicated lock for file I/O
        synchronized (FILE_IO_LOCK) {
            File file = new File(FILE_PATH);
            try {
                // Check if the file exists and has content before attempting to read
                if (file.exists() && file.length() > 0) {
                    // Read the content from the file and map it to a HashMap
                    return objectMapper.readValue(file, new TypeReference<HashMap<String, FileInfo>>() {});
                } else {
                    // Return an empty HashMap if the file is empty or doesn't exist
                    return new HashMap<>();
                }
            } catch (IOException e) {
                // Handle any IO exceptions (e.g., file read issues)
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading JSON file: " + FILE_PATH, e);
            }
        }
    }

    /**
     * Deletes the JSON file associated with this map.
     * This operation is synchronized to prevent conflicts with ongoing file I/O.
     */
    public void deleteJsonFile() {
        // Synchronize on a dedicated lock for file I/O
        synchronized (FILE_IO_LOCK) {
            File file = new File(FILE_PATH);
            if (file.exists()) {
                if (file.delete()) {
                    System.out.println("Successfully deleted JSON file: " + FILE_PATH);
                } else {
                    System.err.println("Failed to delete JSON file: " + FILE_PATH);
                }
            } else {
                System.out.println("JSON file does not exist, no need to delete: " + FILE_PATH);
            }
        }
    }

    /**
     * Returns the file path used by this GlobalMap instance.
     * @return The file path string.
     */
    public String getFILE_PATH() {
        return FILE_PATH;
    }

    /**
     * Merges the contents of another map into this GlobalMap.
     * This method handles new entries, updates common entries based on versioning and ownership,
     * and persists the combined state to the JSON file.
     * @param otherMap The map to merge into this GlobalMap.
     */
    public void mergeFileLists(Map<String, FileInfo> otherMap) {
        // Get key sets for efficient comparison
        Set<String> localSet = internalMap.keySet();
        Set<String> remoteSet = otherMap.keySet();

        // Find keys present ONLY in the remote map
        Set<String> keysOnlyInRemoteMap = new HashSet<>(remoteSet);
        keysOnlyInRemoteMap.removeAll(localSet);

        // Add new entries from the remote map to the internal map
        for (String key : keysOnlyInRemoteMap) {
            // IMPORTANT: Use deepCopy() to ensure you're adding an independent copy
            // and not sharing mutable FileInfo objects directly from otherMap.         CHECK!!!!
            internalMap.put(key, otherMap.get(key));
        }

        // Find keys present in both maps for conflict resolution
        Set<String> commonKeys = new HashSet<>(localSet);
        commonKeys.retainAll(remoteSet);

        // Resolve conflicts for common keys
        for (String key : commonKeys) {
            // Get the current local and remote FileInfo objects
            FileInfo localFileInfo = internalMap.get(key); // Get the mutable object reference from the map
            FileInfo remoteFileInfo = otherMap.get(key);

            // Always merge replication locations
            localFileInfo.mergeRepLocations(remoteFileInfo);

            // Logic to update owner based on version and existence
            if (localFileInfo.getOwner() == null) {
                localFileInfo.setOwner(remoteFileInfo.getOwner());
            } else if (remoteFileInfo.getOwner() != null) {
                if (!Objects.equals(remoteFileInfo.getOwner(), localFileInfo.getOwner())) {
                    if (remoteFileInfo.getVersion() > localFileInfo.getVersion()) {
                        localFileInfo.setOwner(remoteFileInfo.getOwner());
                    }
                }
            }

            // If remote version is more recent, take over locking information
            if (remoteFileInfo.getVersion() > localFileInfo.getVersion()) {
                localFileInfo.setLocked(remoteFileInfo.isLocked());
                localFileInfo.setLockedByNodeIp(remoteFileInfo.getLockedByNodeIp());
            }

            // Update the file info version of the local entry
            localFileInfo.updateVersion();
            // No need to call internalMap.put(key, localFileInfo) again here
            // if localFileInfo was modified in place.
        }
        saveJSON(); // Persist all changes to the JSON file after the merge operation completes
    }

    /**
     * Returns a snapshot of the current global map data.
     * This returns a new HashMap containing the same key-value pairs
     * as the internal ConcurrentHashMap, providing a thread-safe way
     * to access the data for operations like sending to another node.
     * Modifications to the returned map do not affect the internal GlobalMap.
     *
     * @return A HashMap representing the current state of the global map.
     */
    public Map<String, FileInfo> getGlobalMapData() {
        // Create a new HashMap from the entries of the ConcurrentHashMap.
        // This provides a point-in-time snapshot that is safe to iterate over
        // and send to another node without worrying about concurrent modifications.

        System.out.println("The internal map of the globalMap is asked Map : " + internalMap);
        return new HashMap<>(internalMap);


    }

}


