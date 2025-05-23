package ds.namingnote.Agents;

import ds.namingnote.CustomMaps.GlobalMap;
import ds.namingnote.Service.NodeService;
import ds.namingnote.Service.ReplicationService;
import ds.namingnote.Utilities.Node;

import java.io.File; // For checking local file existence
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.logging.Logger;

import static ds.namingnote.Config.NNConf.FILES_DIR;

public class FailureAgent implements Runnable, Serializable {
    @Serial
    private static final long serialVersionUID = 3L;
    private static final Logger logger = Logger.getLogger(FailureAgent.class.getName());

    //these are set by the creator
    private final Node failingNode;
    private final Node newOwnerNode;   // The node that will take ownership (e.g., previous of failed)
    private final Node originatorNode; // Node that started this agent's journey


    // Transient fields, to be set by the execution environment on each node
    private transient Node currentNode; //current node visiting
    private transient Node nextNode;    //next node to visit
    private transient ReplicationService replicationService;
    private transient GlobalMap globalMap;
    private transient String filesDir ; // e.g. NNConf.FILES_DIR

    public FailureAgent(Node failingNode, Node newOwnerNode, Node originatorNode) {
        this.failingNode = failingNode;
        this.newOwnerNode = newOwnerNode;
        this.originatorNode = originatorNode;
    }

    public void initialize(Node currentNode, Node nextNode, ReplicationService replicationService , String filesDir) {
        this.currentNode = currentNode;
        this.nextNode = nextNode;
        this.replicationService = replicationService;
        this.filesDir = filesDir;
        this.globalMap = GlobalMap.getInstance();       //check if this works if it is transmitted to next node?
    }

    //so i guess the overall structure will be :

    //create failure agent if ping doesn't work (maybe check that both neighbors dont activate it)
    //failure agent checks files on node its on and looks for replicated files from failed node
    //if found -> newOwnerNode needs to be the new owner of the file + update the FILEINFO

    //after this is done -> agent needs to be serialized and send to next node in ring
    //the agent will stop once the originating node = nextnode



    @Override
    public void run() {
        if ( globalMap == null || replicationService == null || filesDir == null) {
            logger.severe("FailureAgent not properly initialized on node " + currentNode.getIP() +  "UNKNOWN" + ". Terminating run.");
            return;
        }

        logger.info("FailureAgent running on node " + currentNode.getIP() +
                " (Originator: " + originatorNode.getIP() + "). Handling failure of: " + failingNode.getIP() +
                ". New owner: " + newOwnerNode.getIP());


        //best thing to do would be to dont pass the agent and just check the globalFileList, but since we need
        //to pass the agent i will check all local files and compare to globalFileList


        File dir = new File(FILES_DIR);
        File[] localFiles = dir.listFiles();
        if (localFiles != null) {
            //go over all local files of this node
            for (File localFile : localFiles) {
                if (localFile.isFile()) {
                    String filename = localFile.getName();
                    //check if the FileInfo of this file is present -> should be
                    if (globalMap.containsKey(filename)){
                        FileInfo info = globalMap.get(filename);
                        if (Objects.equals(info.getOwner(), failingNode.getIP())){
                            //here a file in found locally with owner is failed node
                            logger.info(" FailureAgent: Found a file that belongs to the Failed Node");
                            //if the new owner doesnt have the file already -> send
                            if (info.containsAsReference(newOwnerNode.getIP())){
                                logger.info(" FailureAgent: The NewOwner node already has the file of the Failed node as Replica");

                            }else{
                                logger.info(" FailureAgent: The NewOwner node Doesnt have the file -> send it ");
                                //the new node doesnt have the file already -> send file with ref = node -> owner
                                replicationService.sendFile(newOwnerNode.getIP(),localFile, newOwnerNode.getIP());
                                //we can implement that the receiving method also sets the owner in his map -> faster propagation
                            }

                            //we update the owner of the file in the map -> SHOULD BE SYNCED !!!
                            globalMap.setOwner(filename , newOwnerNode.getIP());
                            globalMap.removeReplicationReference(filename ,newOwnerNode.getIP());

                        }
                    }

                }
            }

            logger.fine("FailureAgent: Scanned the full directory and normally send all the files");

            //now for the retransmission

        }else
            logger.severe("FailureAgent : DictList is null when try scanning over local files");

        // The globalFileList on this node is now updated. SyncAgent will propagate changes.
        logger.info("FailureAgent finished processing on node " + currentNode.getID() + ". File list updated.");
    }

    public Node getOriginatorNode() {
        return originatorNode;
    }
}