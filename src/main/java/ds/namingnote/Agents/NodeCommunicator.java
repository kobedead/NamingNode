package ds.namingnote.Agents;

import ds.namingnote.Config.NNConf;
import ds.namingnote.Multicast.MulticastSender;
import ds.namingnote.Utilities.Node;
import ds.namingnote.model.LocalFile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

public class NodeCommunicator implements INodeCommunicator {

    @Override
    public List<LocalFile> requestFileList(Node node) {
        String uri = "http://" + node.getIP() + ":" + NNConf.NAMINGNODE_PORT + "/node/agent/fileList";

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<LocalFile>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    requestEntity,
                    new ParameterizedTypeReference<List<LocalFile>>() {}
            );

            return response.getBody();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public void broadcastAgentState(List<LocalFile> ownedFiles, MulticastSender multicastSender) {
        for (LocalFile ownedFile : ownedFiles) {
            multicastSender.sendLockNotification(ownedFile.getFileName(), ownedFile.isLocked());
        }
    }
}
