package simpleplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.Random;

public class Pathfinding {

    // this is a little buggy (no pun intended) in some edge cases, so we
    // should either:
    // -improve this bug implementation, or
    // -create something smarter, like constructing a map on the fly (per robot,
    // since there's no global team memory this year) and BFSing on the map when
    // necessary

    private static Movement movementStrategy;
    private static MapLocation target;

    // bug mode state
    private static boolean inBugMode = false;
    private static boolean isGoingLeft;
    private static int distSqToTargetAtBugModeStart;
    private static Direction dirAtStart;

    private static int turnsSinceBlocked = 0;
    private static int numTurns = 0;

    public static int PATIENCE = 3;

    private static Direction lastMoveDir;

    public static void setTarget(MapLocation target, Movement movementStrategy) {
        if (!target.equals(Pathfinding.target) || Pathfinding.movementStrategy != movementStrategy) {
            Pathfinding.target = target;
            inBugMode = false;
            Pathfinding.movementStrategy = movementStrategy;
        }
    }

    // precondition to this method: rc.isMovementReady() should return true
    public static boolean pathfindToward(RobotController rc, Random gen) throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        if (movementStrategy.atGoal(rc, target)) {
            return false;
        }

        if (inBugMode) {
            if ((turnsSinceBlocked >= PATIENCE)
                    || ((numTurns <= 0 || numTurns >= 8) && curLoc.distanceSquaredTo(target) <= distSqToTargetAtBugModeStart)) {
                inBugMode = false;
            }
        }

        if (!inBugMode) {
            Direction dirToMove = null;
            Direction dirToTarget = curLoc.directionTo(target);

            if (canMove(rc, dirToTarget)) {
                dirToMove = dirToTarget;
            } else {
                Direction[] dirs = new Direction[2];
                Direction dirLeft = dirToTarget.rotateLeft();
                Direction dirRight = dirToTarget.rotateRight();
                if (curLoc.add(dirLeft).distanceSquaredTo(target) < curLoc.add(dirRight).distanceSquaredTo(target)) {
                    dirs[0] = dirLeft;
                    dirs[1] = dirRight;
                } else {
                    dirs[0] = dirRight;
                    dirs[1] = dirLeft;
                }
                for (Direction dir : dirs) {
                    if (canMove(rc, dir)) {
                        dirToMove = dir;
                        break;
                    }
                }
            }
            if (dirToMove != null) {
                move(rc, dirToMove);
                return true;
            } else if (dirToMove == null) {
                inBugMode = true;
                resetBugMode(curLoc, gen);
            }
        }

        if (inBugMode) {
            // BUGMODE

            boolean onMapEdge;
            if (isGoingLeft) {
                onMapEdge = !rc.onTheMap(curLoc.add(lastMoveDir.rotateLeft()));
            } else {
                onMapEdge = !rc.onTheMap(curLoc.add(lastMoveDir.rotateRight()));
            }
            if (onMapEdge) {
                isGoingLeft = !isGoingLeft;
                resetBugMode(curLoc, gen);
            }

            turnsSinceBlocked++;
            Direction possibleDir = dirAtStart;
            Direction dir = null;

            for (int i = 8; i-- > 0;) {
                if (turnsSinceBlocked >= PATIENCE) {
                    if (canMoveIfImpatient(rc, possibleDir)) {
                        dir = possibleDir;
                        break;
                    }
                } else {
                    if (canMove(rc, possibleDir)) {
                        dir = possibleDir;
                        break;
                    }
                }

                if (isGoingLeft) {
                    possibleDir = possibleDir.rotateRight();
                } else {
                    possibleDir = possibleDir.rotateLeft();
                }
                turnsSinceBlocked = 0;
            }

            if (dir != null) {
                move(rc, dir);
                numTurns += calculateBugRotation(dir);
                lastMoveDir = dir;
                if (isGoingLeft) {
                    dirAtStart = dir.rotateLeft().rotateLeft();
                } else {
                    dirAtStart = dir.rotateLeft().rotateRight();
                }
            }

        }

        return false;
    }

    private static void move(RobotController rc, Direction dirToMove) throws GameActionException {
        movementStrategy.move(rc, dirToMove);
    }

    private static int numRightRotations(Direction start, Direction end) {
        return (end.ordinal() - start.ordinal() + 8) % 8;
    }

    private static int numLeftRotations(Direction start, Direction end) {
        return (-end.ordinal() + start.ordinal() + 8) % 8;
    }

    private static int calculateBugRotation(Direction moveDir) {
        if (isGoingLeft) {
            return numRightRotations(dirAtStart, moveDir) - numRightRotations(dirAtStart, lastMoveDir);
        } else {
            return numLeftRotations(dirAtStart, moveDir) - numLeftRotations(dirAtStart, lastMoveDir);
        }
    }

    private static boolean canMove(RobotController rc, Direction dir) throws GameActionException {
        return movementStrategy.canMove(rc, dir);
    }

    private static boolean canMoveIfImpatient(RobotController rc, Direction dir) {
        return movementStrategy.canMoveIfImpatient(rc, dir);
    }

    public static void resetBugMode(MapLocation curLoc, Random gen) {
        // // try to intelligently choose on which side we will keep the
        // // wall
        // Direction leftTryDir = bugLastMoveDir.rotateLeft();
        // for (int i = 0; i < 3; i++) {
        // if (!canMove(leftTryDir))
        // leftTryDir = leftTryDir.rotateLeft();
        // else
        // break;
        // }
        // Direction rightTryDir = bugLastMoveDir.rotateRight();
        // for (int i = 0; i < 3; i++) {
        // if (!canMove(rightTryDir))
        // rightTryDir = rightTryDir.rotateRight();
        // else
        // break;
        // }
        // if (dest.distanceSquaredTo(here.add(leftTryDir)) <
        // dest.distanceSquaredTo(here.add(rightTryDir))) {
        // bugWallSide = WallSide.RIGHT;
        // } else {
        // bugWallSide = WallSide.LEFT;
        // }

        distSqToTargetAtBugModeStart = curLoc.distanceSquaredTo(target);
        dirAtStart = lastMoveDir = curLoc.directionTo(target);
        numTurns = 0;
        turnsSinceBlocked = 0;

        isGoingLeft = gen.nextBoolean();
    }
}