package ds.namingnote.Config;


public class    NNConf {

    ////////////NamingNode specs
    public static final int NAMINGNODE_PORT = 8082;

    //////////NamingServer specs
    public static final String NAMINGSERVER_HOST = "172.19.0.6"; // Corresponds to g2c1
    public static final int NAMINGSERVER_PORT = 8083;


    ////////////Multicast specs
    public static final String MULTICAST_GROUP = "230.0.0.1";
    public static final int Multicast_PORT = 4446;
    public static final String PREFIX = "joining";  // Example filter keyword

    ////// File location

    public static final String FILES_DIR = "Files";


    //reference map path
    public static final String whoHas_MAP_PATH = "WhoReplicatedMyFiles.json";
    public static final String localRep_MAP_PATH = "FilesIReplicated.json";






}
