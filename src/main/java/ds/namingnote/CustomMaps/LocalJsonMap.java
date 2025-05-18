package ds.namingnote.CustomMaps;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LocalJsonMap<K , V> extends HashMap<K, V> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String FILE_PATH;

    public LocalJsonMap(String filepath) {
        this.FILE_PATH = filepath;
        Map<K, V> loadedMap = getMapFromJSON();
        super.putAll(loadedMap);
    }

    @Override
    public V put(K key, V value) {
        V result = super.put(key, value);
        updateJSON();
        return result;
    }

    @Override
    public V remove(Object key) {
        V result = super.remove(key);
        updateJSON();
        return result;
    }

    public void removeValue(Object value) {
        super.values().remove(value);
        updateJSON();
    }

    /**
     * Update the JSON file with the current objects that are stored in memory in map
     */
    public void updateJSON() {
        File file = new File(FILE_PATH);
        try {
            // Write updated data back to the file
            objectMapper.writeValue(file, this); // 'this' is now a sorted TreeMap
            System.out.println("Sorted map updated and saved to map.json successfully! Map size is now " + size());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error writing to JSON", e);
        }
    }

    /**
     * Get all key value pairs contained in the Map.JSON file.
     * @return all of the key value pairs from the JSON "database"
     */
    public Map<K, V> getMapFromJSON() {
        File file = new File(FILE_PATH);  // Assuming FILE_PATH is defined
        try {
            // Check if the file exists and has content
            if (file.exists() && file.length() > 0) {
                // Read the content from the file and map it to a TreeMap
                return objectMapper.readValue(file, new TypeReference<HashMap<K, V>>() {}); //prob wont work!!!
            } else {
                // Return an empty TreeMap if the file is empty or doesn't exist
                return new HashMap<>();
            }
        } catch (IOException e) {
            // Handle any IO exceptions (e.g., file read issues)
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading JSON file", e);
        }
    }

    public void deleteJsonFile() {
        File file = new File(FILE_PATH);
        if (file.exists()) {
            if (file.delete()) {
                System.out.println("Successfully deleted JSON file: " + FILE_PATH);
            } else {
                System.err.println("Failed to delete JSON file: " + FILE_PATH);
                // Consider logging this failure appropriately
            }
        } else {
            System.out.println("JSON file does not exist, no need to delete: " + FILE_PATH);
        }
    }


    public String getFILE_PATH() {
        return FILE_PATH;
    }
}
