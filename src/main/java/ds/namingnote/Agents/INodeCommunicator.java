package ds.namingnote.Agents;

import ds.namingnote.Multicast.MulticastSender;
import ds.namingnote.Utilities.Node;
import ds.namingnote.model.LocalFile;

import java.util.List;
import java.util.Map;

public interface INodeCommunicator {
    List<LocalFile> requestFileList(Node node);
    void broadcastAgentState(List<LocalFile> ownedFiles, MulticastSender multicastSender);
}
