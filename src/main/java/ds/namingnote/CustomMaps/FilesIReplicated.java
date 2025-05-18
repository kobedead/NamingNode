package ds.namingnote.CustomMaps;

import ds.namingnote.Agents.SyncAgent;
import ds.namingnote.Utilities.ReferenceDTO;

import java.util.List;


public class FilesIReplicated<K, V> extends LocalJsonMap_List<K,V> {

    private SyncAgent syncAgent;


    public FilesIReplicated(String filepath, SyncAgent syncAgent) {
        super(filepath);
        this.syncAgent = syncAgent;
    }

    @Override
    public List<V> putSingle(K key, V value) {
        List<V> ret = super.putSingle(key,value);
        //then when there is a entry i need to notify the syncagent
        syncAgent.addFileIReplicated(new ReferenceDTO((String) value, (String) key));
        return ret;
    }


    }



