package simpleplayer;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

import static simpleplayer.RobotPlayer.*;

public class Builder {

    private static MapLocation lastTargetMovementLocation = null;

    private static boolean tryMoveToRandomNearbyTarget() throws GameActionException {
        if (lastTargetMovementLocation != null && locAtStartOfTurn.distanceSquaredTo(lastTargetMovementLocation) <= 2) {
            lastTargetMovementLocation = null;
        }
        if (lastTargetMovementLocation == null) {
            int x = locAtStartOfTurn.x;
            int y = locAtStartOfTurn.y;
            int radius = 8;
            int minX = Math.max(0, x - radius);
            int minY = Math.max(0, y - radius);
            int maxX = Math.min(rc.getMapWidth(), x + radius + 1);
            int maxY = Math.min(rc.getMapHeight(), y + radius + 1);
            int rangeX = maxX - minX;
            int rangeY = maxY - minY;
            lastTargetMovementLocation = new MapLocation(gen.nextInt(rangeX) + minX, gen.nextInt(rangeY) + minY);
        }
        pathfinder.move(lastTargetMovementLocation);
        return true;
    }

    public static void runBuilder() throws GameActionException {
        if (rc.senseLead(locAtStartOfTurn) == 0) {
            rc.disintegrate();
        }
        tryMoveToRandomNearbyTarget();
    }
}
