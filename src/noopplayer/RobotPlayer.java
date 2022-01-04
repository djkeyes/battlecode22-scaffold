package noopplayer;

import battlecode.common.*;

public strictfp class RobotPlayer {

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        //noinspection InfiniteLoopStatement
        while (true) {
            Clock.yield();
        }
    }

}
