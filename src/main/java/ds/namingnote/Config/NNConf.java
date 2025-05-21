package ds.namingnote.Config;


public class    NNConf {

    ////////////NamingNode specs
    public static final int NAMINGNODE_PORT = 8082;

    //////////NamingServer specs
    public static final String NAMINGSERVER_HOST = "172.19.0.6"; // Corresponds to g2c1
    public static final int NAMINGSERVER_PORT = 8083;


    ////////////Multicast specs
    public static final String MULTICAST_GROUP = "230.0.0.1";
    public static final int MULTICAST_PORT = 4446;
    public static final String NODE_PREFIX = "joining";  // Example filter keyword
    public static final String LOCK_PREFIX = "locking";

    ////// File location

    public static final String FILES_DIR = "src/main/resources/Files";


    //reference map path
    public static final String GLOBAL_MAP_PATH = "src/main/resources/GlobalMap.json";




}
