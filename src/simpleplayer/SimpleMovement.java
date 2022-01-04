package simpleplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class SimpleMovement implements Movement {
    // movement strategy that just checks for obstructions

    @Override
    public boolean atGoal(RobotController rc, MapLocation target) {
        return rc.getLocation().equals(target);
    }

    @Override
    public void move(RobotController rc, Direction dirToMove) throws GameActionException {
        rc.move(dirToMove);
    }

    @Override
    public boolean canMove(RobotController rc, Direction dir) {
        return rc.canMove(dir);
    }

    @Override
    public boolean canMoveIfImpatient(RobotController rc, Direction dir) {
        return rc.canMove(dir);
    }

}