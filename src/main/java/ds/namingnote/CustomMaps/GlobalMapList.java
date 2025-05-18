package ds.namingnote.CustomMaps;

import ds.namingnote.Agents.FileInfo;
import ds.namingnote.Agents.SyncAgent;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GlobalMapList extends LocalJsonMap<String, FileInfo> {


    //we know the data type will be <String, FileInfo> , if this changes -> CHANGE CODE BELOW!!!!


    //this will have logic to merge global maps from the sync agent


    public GlobalMapList(String filepath, SyncAgent syncAgent) {
        super(filepath);
    }

    @Override
    public FileInfo put(String key, FileInfo singleFileInfo) {
        // According to the standard Map.put contract,
        // this method should return the *previous* value associated with the key.
        FileInfo oldValue = null; // Initialize to null

        // Check if the key already exists in the map
        if (super.containsKey(key)) {
            // Get a reference to the existing FileInfo object from the map
            FileInfo existingFileInfo = super.get(key);
            oldValue = existingFileInfo; // Store the existing object as the 'oldValue'


            // Set owner of the file if the incoming singleFileInfo has one
            if (singleFileInfo.getOwner() != null) {
                existingFileInfo.setOwner(singleFileInfo.getOwner());
            }

            // Add replication locations from the incoming singleFileInfo
            if (singleFileInfo.getReplicationLocations() != null) {
                // This directly modifies the collection inside 'existingFileInfo'
                existingFileInfo.getReplicationLocations().addAll(singleFileInfo.getReplicationLocations());
            }

        } else {
            // If the key does not exist, simply add the new file info
            oldValue = super.put(key, singleFileInfo);
        }

        super.updateJSON();

        return oldValue;

    }


    public void mergeFileLists(Map<String, FileInfo> otherMap){
        //this map and the map given should be merged in this map


        //Get the key sets from both maps
        Set<String> localSet = this.keySet();
        Set<String> remoteSet = otherMap.keySet();


        // Find keys present ONLY in remoteMAP
        Set<String> keysOnlyInRemoteMap = new HashSet<>(remoteSet);
        keysOnlyInRemoteMap.removeAll(localSet); // Removes all elements that are also in keySet1

        //these entries need to be added to this map
        super.putAll((Map<? extends String, ? extends FileInfo>) keysOnlyInRemoteMap);


        //Find keys present in both maps
        Set<String> commonKeys = new HashSet<>(localSet);
        commonKeys.retainAll(remoteSet); // Retains only elements that are also in keySet2

        //for the common keys we need to resolve conflicts

        for (String key : commonKeys){
            FileInfo localFileInfo = this.get(key);
            FileInfo remoteFileInfo = otherMap.get(key);
            //always merge the replications -> removal will be handled by failure i think
            localFileInfo.mergeRepLocations(remoteFileInfo);

            //set owner of file if remotes owner is different
            if (remoteFileInfo.getOwner() != null)
                if (!Objects.equals(remoteFileInfo.getOwner(), localFileInfo.getOwner()))
                    if (remoteFileInfo.getVersion() > localFileInfo.getVersion())
                        localFileInfo.setOwner(remoteFileInfo.getOwner());



            //if remote version is more recent -> take over locking
            //maybe locking could be updated instantly better through rest mappings directly
            if (remoteFileInfo.getVersion() > localFileInfo.getVersion()) {
                localFileInfo.setLocked(remoteFileInfo.isLocked());
                localFileInfo.setLockedByNodeId(remoteFileInfo.getLockedByNodeId());
            }


            //update the file info version
            localFileInfo.updateVersion();

        }

    }

}
