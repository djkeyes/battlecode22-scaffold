package simpleplayer;

import battlecode.common.*;

import static simpleplayer.RobotPlayer.*;

public class Watchtower {

    private static final int TURNS_TO_WAIT_IF_SEEN_ENEMY = 49;
    private static int turnsSinceSeenEnemy = TURNS_TO_WAIT_IF_SEEN_ENEMY;

    public static void runWatchtower() throws GameActionException {
        RobotInfo[] nearbyEnemies = visibleEnemies;

        // do micro if we're near enemies
        if (nearbyEnemies.length > 0) {
            if (rc.getMode() == RobotMode.PORTABLE) {
                if (rc.isTransformReady()) {
                    rc.transform();
                }
            } else {
                doMicro(rc, nearbyEnemies);
            }
            turnsSinceSeenEnemy = 0;
            return;
        }

        turnsSinceSeenEnemy++;
        if (turnsSinceSeenEnemy >= TURNS_TO_WAIT_IF_SEEN_ENEMY) {
            if (rc.getMode() == RobotMode.TURRET) {
                if (rc.isTransformReady()) {
                    rc.transform();
                }
            }
        }

        if (rc.isMovementReady()) {
            // move randomly if too crowded
            if (rc.senseNearbyRobots(2, us).length >= 4) {
                for (Direction d : Directions.RANDOM_DIRECTION_PERMUTATION) {
                    if (rc.canMove(d)) {
                        rc.move(d);
                        locAfterMovement = locAtStartOfTurn.add(d);
                        return;
                    }
                }
            }

            if (tryMoveToClosestKnownEnemyArchonLocation()) {
                return;
            }

            if (tryMoveToLocationUnderAttack()) {
                return;
            }

            if (tryMoveToInitialEnemyArchonLocations()) {
                return;
            }
            tryMoveToRandomTarget();

        }
    }

    private static boolean tryMoveToClosestKnownEnemyArchonLocation() throws GameActionException {
        MapLocation[] enemyArchonLocations = Communication.readEnemyArchonLocations();
        MapLocation closest = null;
        int closestDist = Integer.MAX_VALUE;
        for (int i = 0; i < enemyArchonLocations.length; ++i) {
            MapLocation loc = enemyArchonLocations[i];
            if (loc == null) {
                continue;
            }
            int distsq = loc.distanceSquaredTo(locAtStartOfTurn);
            if (distsq <= myType.visionRadiusSquared) {
                // we should have seen it by this point. let everyone else know it's gone.
                Communication.clearEnemyArchonLocation(i);
                continue;
            }
            if (distsq < closestDist) {
                closestDist = distsq;
                closest = loc;
            }
        }
        if (closest == null) {
            return false;
        }
        pathfinder.move(closest);
        return true;
    }

    private static boolean tryMoveToLocationUnderAttack() throws GameActionException {
        MapLocation underAttackLocation = GridStrategy.instance.findClosestAttackLocation();
        if (underAttackLocation == null) {
            return false;
        }
        pathfinder.move(underAttackLocation);
        return true;
    }

    private static MapLocation myInitialLocation = null;
    private static boolean[] checkedArchonLocation = null;
    private static MapLocation lastArchonTarget = null;
    private static int curSymmetryInvestigation = -1;

    private static boolean tryMoveToInitialEnemyArchonLocations() throws GameActionException {
        if (checkedArchonLocation == null) {
            // For now, just store our initial position and try to path towards it
            // TODO: broadcast initial archon data, so we can check all possible locations
            checkedArchonLocation = new boolean[3];
            for (int i = 0; i < 3; ++i) {
                if (!MapSymmetry.isSymmetryPossible[i]) {
                    checkedArchonLocation[i] = true;
                }
            }
            myInitialLocation = locAtStartOfTurn;
        }

        if (lastArchonTarget != null && locAtStartOfTurn.distanceSquaredTo(lastArchonTarget) < myType.visionRadiusSquared) {
            lastArchonTarget = null;
        }

        if (lastArchonTarget == null) {
            for (int i = curSymmetryInvestigation + 1; i < 3; ++i) {
                if (!checkedArchonLocation[i]) {
                    lastArchonTarget = MapSymmetry.getSymmetricCoords(myInitialLocation, i);
                    curSymmetryInvestigation = i;
                    break;
                }
            }
        }
        if (lastArchonTarget == null) {
            return false;
        }
        pathfinder.move(lastArchonTarget);
        return true;
    }

    private static MapLocation lastTargetAttackLocation = null;

    private static boolean tryMoveToRandomTarget() throws GameActionException {
        if (lastTargetAttackLocation != null && locAtStartOfTurn.distanceSquaredTo(lastTargetAttackLocation) <= 2) {
            lastTargetAttackLocation = null;
        }
        if (lastTargetAttackLocation == null) {
            lastTargetAttackLocation = new MapLocation(gen.nextInt(rc.getMapWidth()), gen.nextInt(rc.getMapHeight()));
        }
        pathfinder.move(lastTargetAttackLocation);
        return true;
    }

    public static void doMicro(RobotController rc, RobotInfo[] nearbyEnemies) throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        final int atkRangeSq = myType.actionRadiusSquared;

        // Turret micro is simple.
        // Check if we can shoot anyone
        // if we can, shoot them.

        // TODO: this assume all units are equal.
        // we should probably weigh these somehow. things with long range
        // are more powerful. things with high dps and low health are also
        // juicy targets.
        double minThreatHealth = Double.MAX_VALUE;
        MapLocation weakestThreat = null;
        for (RobotInfo enemy : nearbyEnemies) {
            if (enemy.type == RobotType.ARCHON || enemy.type == RobotType.LABORATORY || enemy.type == RobotType.MINER || enemy.type == RobotType.BUILDER) {
                continue;
            }
            int distSq = enemy.location.distanceSquaredTo(curLoc);
            if (distSq <= enemy.type.actionRadiusSquared) {
                if (distSq <= atkRangeSq) {
                    double health = enemy.health;

                    if (health <= minThreatHealth) {
                        minThreatHealth = health;
                        weakestThreat = enemy.location;
                    }
                }
            }
        }

        if (weakestThreat != null) {
            // pick one that's an immediate threat
            if (rc.isActionReady()) {
                rc.attack(weakestThreat);
            }
        } else {
            // check if we can shoot anyone
            RobotInfo weakest = getWeakestInRange(curLoc, nearbyEnemies);
            if (weakest != null) {
                if (rc.isActionReady()) {
                    rc.attack(weakest.location);
                }
            }

        }
    }

    // TODO: several of these methods are very similar. extract their
    // similarities somehow?
    private static RobotInfo getWeakestInRange(MapLocation curLoc, RobotInfo[] nearby) {
        RobotInfo result = null;
        double minHealth = Double.MAX_VALUE;
        for (int i = nearby.length; --i >= 0; ) {
            RobotInfo enemy = nearby[i];
            if (enemy.location.distanceSquaredTo(curLoc) <= myType.actionRadiusSquared) {
                double health = enemy.health;
                if (health < minHealth) {
                    minHealth = health;
                    result = enemy;
                }
            }
        }
        return result;
    }

}
