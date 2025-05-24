package ds.namingnote.Agents;


import ds.namingnote.Utilities.Utilities;

import java.io.Serializable;
import java.util.*;

public class FileInfo implements Serializable {
    private static final long serialVersionUID = 1L; // Good practice for Serializable

    private String filename;

    //for these to become node object i need to send node as refrence in the replicationService!
    private String owner = null ;

    private Set<String> replicationLocations = null;

    private boolean isLocked;
    private String lockedByNodeId; //  Node IP that holds the lock, 0 or -1 if not locked
    private long version; // For optimistic locking or simple conflict resolution during sync
    private int fileHash;

    // Constructors, getters, setters, equals, hashCode

    public FileInfo() {}



    public FileInfo(String filename, String owner , String replicator) {
        this.replicationLocations = new HashSet<>();

        if (owner != null)
            this.owner = owner;
        if (replicator != null)
            this.replicationLocations.add(replicator);

        this.filename = filename;
        this.isLocked = false;
        this.lockedByNodeId = null; // Or some other indicator for not locked
        this.version = System.currentTimeMillis(); // Initial version

        fileHash = Utilities.mapHash(filename);
    }





    // Getters and Setters for all fields...
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) {
        isLocked = locked;
        this.updateVersion();
    }
    public String getLockedByNodeIp() { return lockedByNodeId; }
    public void setLockedByNodeIp(String lockedByNodeId) {
        this.lockedByNodeId = lockedByNodeId;
        this.updateVersion();
    }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public void updateVersion(){
        version = System.currentTimeMillis();
    }


    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
        this.updateVersion();
    }

    public Set<String> getReplicationLocations() {
        return replicationLocations;
    }

    public void setReplicationLocations(Set<String> replicationLocations) {
        this.replicationLocations = replicationLocations;
        this.updateVersion();
    }

    public void removeReplicationLocation(String ipOfRepLoc){
        this.replicationLocations.remove(ipOfRepLoc);
        this.updateVersion();
    }

    public void mergeRepLocations(FileInfo otherFileInfo){
        this.replicationLocations.addAll(otherFileInfo.getReplicationLocations());
        this.updateVersion();
    }

    public boolean containsAsReference(String ipOfReference){
        return this.replicationLocations.contains(ipOfReference);
    }

    public int getFileHash() {
        return fileHash;
    }

    public void setFileHash(int fileHash) {
        this.fileHash = fileHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileInfo fileInfo = (FileInfo) o;
        return Objects.equals(filename, fileInfo.filename); // Primarily identified by filename
    }

    @Override
    public int hashCode() {
        return Objects.hash(filename);
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "filename='" + filename + '\'' +
                ", ownerNode=" + owner +
                ", isLocked=" + isLocked +
                ", lockedByNodeId=" + lockedByNodeId +
                ", version=" + version +
                '}';
    }
}