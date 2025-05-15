package ds.namingnote.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalJsonMap<K, V> extends HashMap<K, List<V>> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String FILE_PATH;

    public LocalJsonMap(String filepath) {
        this.FILE_PATH = filepath;
        Map<K, List<V>> loadedMap = getMapFromJSON();
        super.putAll(loadedMap);
    }

    @Override
    public List<V> put(K key, List<V> value) {
        List<V> result = super.put(key, value);
        updateJSON();
        return result;
    }

    public List<V> putSingle(K key, V value) {
        List<V> tempList = super.get(key);

        if (tempList == null) {
            tempList = new ArrayList<>();
            tempList.add(value);
            super.put(key, tempList);
        } else {
            tempList.add(value);
            //super should already be updated cause templist is reference -> check
        }

        updateJSON();
        return tempList; // Return the modified list
    }



    public void addValue(K key, V value) {
        List<V> list = get(key);
        if (list == null) {
            list = new ArrayList<>();
            put(key, list);
        } else {
            list.add(value);
        }
        updateJSON();
    }

    @Override
    public List<V> remove(Object key) {
        List<V> result = super.remove(key);
        updateJSON();
        return result;
    }

    // If you want to remove a specific value from a list associated with a key
    public boolean removeValue(K key, V value) {
        List<V> list = get(key);
        if (list != null) {
            boolean removed = list.remove(value);
            if (removed) {
                updateJSON();
                return true;
            }
        }
        return false;
    }

    /**
     * Update the JSON file with the current objects that are stored in memory in map
     */
    public void updateJSON() {
        File file = new File(FILE_PATH);
        try {
            objectMapper.writeValue(file, this);
            System.out.println("LocalJsonMap updated and saved to " + FILE_PATH + " successfully!");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error writing to JSON", e);
        }
    }

    /**
     * Get all key-value pairs contained in the JSON file.
     * @return all of the key-value pairs from the JSON "database".
     */
    public Map<K, List<V>> getMapFromJSON() {
        File file = new File(FILE_PATH);
        try {
            if (file.exists() && file.length() > 0) {
                return objectMapper.readValue(file, new TypeReference<HashMap<K, List<V>>>() {}); //prob wont work!!!
            } else {
                return new HashMap<>();
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading JSON file", e);
        }
    }

    /**
     * Deletes the JSON file associated with this map.
     */
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




}



