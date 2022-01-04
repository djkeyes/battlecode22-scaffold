package simpleplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public interface Movement {

    boolean atGoal(RobotController rc, MapLocation target);

    void move(RobotController rc, Direction dirToMove) throws GameActionException;

    boolean canMove(RobotController rc, Direction dir) throws GameActionException;

    boolean canMoveIfImpatient(RobotController rc, Direction dir);

}