package simpleplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

import static simpleplayer.RobotPlayer.*;

public class SimpleMovement implements Movement {
    // movement strategy that just checks for obstructions

    @Override
    public boolean atGoal(MapLocation target) {
        return rc.getLocation().equals(target);
    }

    @Override
    public void move(Direction dirToMove) throws GameActionException {
        rc.move(dirToMove);
        locAfterMovement = locAtStartOfTurn.add(dirToMove);
    }

    @Override
    public boolean canMove(Direction dir) {
        return rc.canMove(dir);
    }

    @Override
    public boolean canMoveIfImpatient(Direction dir) {
        return rc.canMove(dir);
    }

}