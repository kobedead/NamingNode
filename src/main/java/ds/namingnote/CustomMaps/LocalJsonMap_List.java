package ds.namingnote.CustomMaps;

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

public class LocalJsonMap_List<K, V> extends LocalJsonMap<K, List<V>> {


    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalJsonMap_List(String filepath ) {
        super(filepath);
        Map<K, List<V>> loadedMap = getMapFromJSON();
        super.putAll(loadedMap);
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

        //fileIReplicated :         syncAgent.addFileIReplicated(new ReferenceDTO((String) value, (String) key));
        //WhoHas :         syncAgent.addWhoHasMyFile(new ReferenceDTO((String) value, (String) key));

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
     * Get all key-value pairs contained in the JSON file.
     * @return all of the key-value pairs from the JSON "database".
     */
    @Override
    public Map<K, List<V>> getMapFromJSON() {
        File file = new File(super.getFILE_PATH());
        try {
            if (file.exists() && file.length() > 0) {                           //this is needed for the override
                return objectMapper.readValue(file, new TypeReference<HashMap<K, List<V>>>() {}); //prob wont work!!!
            } else {
                return new HashMap<>();
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading JSON file", e);
        }
    }




}



