package ds.namingnote.Utilities;

import org.springframework.context.annotation.Bean;


public class Utilities {


    /**
     * Hashing function to hash incoming names (based on given hashing algorithm)
     * @param text name of the node or file to be hashed
     * @return hashed integer value
     */

    public static int mapHash (String text){
        int hashCode = text.hashCode();
        int max = Integer.MAX_VALUE;
        int min = Integer.MIN_VALUE;

        // Ensure the hashCode is always positive
        int adjustedHash = Math.abs(hashCode);

        // Mapping hashCode from (Integer.MIN_VALUE, Integer.MAX_VALUE) to (0, 32768)
        return (int) (((long) adjustedHash * 32768) / ((long) max - min));
    }


}
