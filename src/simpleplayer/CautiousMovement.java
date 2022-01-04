package simpleplayer;

import battlecode.common.*;

public class CautiousMovement implements Movement {
    // movement strategy that avoids enemies

    private RobotInfo[] nearbyEnemies = null;

    // PRECONDITION:
    // this method MUST be updated with nearby enemies before doing any
    // pathfinding.
    public void setNearbyEnemies(RobotInfo[] nearbyEnemies) {
        this.nearbyEnemies = nearbyEnemies;
    }

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
        if (!rc.canMove(dir)) {
            return false;
        }
        MapLocation curLoc = rc.getLocation();
        return !inEnemyRange(curLoc.add(dir), nearbyEnemies);
    }

    @Override
    public boolean canMoveIfImpatient(RobotController rc, Direction dir) {
        return canMove(rc, dir);
    }

    public static boolean inEnemyRange(MapLocation loc, RobotInfo[] nearbyEnemies) {
        for (RobotInfo enemy : nearbyEnemies) {
            if (!enemy.type.canAttack()) {
                continue;
            }
            int distSq = loc.distanceSquaredTo(enemy.location);
            if (distSq <= enemy.type.actionRadiusSquared) {
                return true;
            }
        }
        return false;
    }
}