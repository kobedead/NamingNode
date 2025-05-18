package ds.namingnote.Agents;

import java.io.Serializable;
import java.util.UUID;

public interface MobileAgent extends Runnable, Serializable {
    String getAgentId();
    void setAgentId(String id);

    /** Called once after creation or deserialization on a new node, before run(). */
    void init();

    /** For mobile agents: called before serialization for moving to another node. */
    void beforeMove();

    /** For mobile agents: called after deserialization on a new node, after init(). */
    void afterMove();

    /** Checks if a mobile agent should terminate its journey. */
    boolean SouldTerminateJourney(); // Renamed for clarity

    /** Signals an agent that it should stop its current execution (e.g., a long-running loop). */
    void stopExecution();
}