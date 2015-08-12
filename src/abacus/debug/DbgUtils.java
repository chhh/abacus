package abacus.debug;

import org.hsqldb.util.DatabaseManagerSwing;

/**
 * @author Dmitry Avtonomov
 */
public class DbgUtils {
    public static final boolean DEBUG = true;

    private DbgUtils() {}

    public static void dbGuiInMem() {
        String [] dbGuiParams = new String[] {"--url",  "jdbc:hsqldb:mem:memoryDB", "--noexit"};
        DatabaseManagerSwing.main(dbGuiParams);
    }

    public static void sleep() {
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
