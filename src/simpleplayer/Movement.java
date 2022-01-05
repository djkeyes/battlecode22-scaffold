package simpleplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

public interface Movement {

    boolean atGoal(MapLocation target);

    void move(Direction dirToMove) throws GameActionException;

    boolean canMove(Direction dir) throws GameActionException;

    boolean canMoveIfImpatient(Direction dir);

}