package simpleplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

import static simpleplayer.RobotPlayer.rc;

public class RubbleAverseMovement implements Movement {
    // movement strategy that checks for obstructions or too much rubble

    @Override
    public boolean atGoal(MapLocation target) {
        return rc.getLocation().equals(target);
    }

    @Override
    public void move(Direction dirToMove) throws GameActionException {
        rc.move(dirToMove);
    }

    @Override
    public boolean canMove(Direction dir) throws GameActionException {
        MapLocation adj = rc.getLocation().add(dir);
        if (rc.onTheMap(adj) && rc.senseRubble(adj) >= 20) {
            return false;
        }
        return rc.canMove(dir);
    }

    @Override
    public boolean canMoveIfImpatient(Direction dir) {
        return rc.canMove(dir);
    }

}