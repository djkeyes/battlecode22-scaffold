package simpleplayer;

import battlecode.common.Clock;

public class Debug {

    public static final boolean SHOULD_LOG_BYTECODES = false;
    public static final boolean SHOULD_SHOW_VISUALIZATIONS = false;

    /**
     * Gets the current bytecode count. Designed to be used with toc.
     *
     * @return The current bytecode count
     */
    public static int tic() {
        if (!SHOULD_LOG_BYTECODES) {
            return 0;
        }
        return Clock.getBytecodeNum();
    }

    /**
     * Prints a debug message about the elapsed bytecode usage. Designed to be used with tic().
     *
     * @param start      The start bytecode count, from tic()
     * @param shortName  A short description, to show in the debug message.
     * @param maxElapsed The last value returned from this function, to be use for the running max
     * @return The max of the elapsed time and maxElapsed
     */
    public static int toc(int start, String shortName, int maxElapsed) {
        if (!SHOULD_LOG_BYTECODES) {
            return 0;
        }
        int end = Clock.getBytecodeNum();
        int elapsed = end - start;
        maxElapsed = Math.max(maxElapsed, elapsed);
        System.out.println("[" + shortName + "] bcs: " + elapsed + ", max bcs: " + maxElapsed);
        return maxElapsed;
    }
}
