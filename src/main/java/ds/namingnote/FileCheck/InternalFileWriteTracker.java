package ds.namingnote.FileCheck;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class InternalFileWriteTracker {
    private static final Set<String> internallyWrittenFiles = Collections.synchronizedSet(new HashSet<>());

    public static void mark(String filename) {
        internallyWrittenFiles.add(filename);
    }

    public static boolean wasWrittenInternally(String filename) {
        return internallyWrittenFiles.remove(filename); // remove on check
    }
}

