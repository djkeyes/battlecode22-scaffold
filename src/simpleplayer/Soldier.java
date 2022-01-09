package simpleplayer;

import battlecode.common.*;

import static simpleplayer.RobotPlayer.*;

public class Soldier {

    public static void runSoldier() throws GameActionException {
        RobotInfo[] nearbyAllies = visibleAllies;
        RobotInfo[] nearbyEnemies = visibleEnemies;

        // do micro if we're near enemies
        if (nearbyEnemies.length > 0) {
            doMicro(rc, nearbyAllies, nearbyEnemies);
            return;
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
        for (MapLocation loc : enemyArchonLocations) {
            int distsq = loc.distanceSquaredTo(locAtStartOfTurn);
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

    public static void doMicro(RobotController rc, RobotInfo[] nearbyAllies, RobotInfo[] nearbyEnemies) throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        final int atkRangeSq = myType.actionRadiusSquared;

        // first check if anyone can shoot us
        // if the ones close-by are too powerful, retreat
        // then check if we can shoot anyone
        // if we can, shoot them
        // otherwise advance

        // TODO: this assume all units are equal.
        // we should probably weigh these somehow. things with long range
        // are more powerful. things with high dps and low health are also
        // juicy targets.
        int numCanShootUs = 0;
        boolean[] canShootThem = new boolean[nearbyAllies.length];
        double minThreatHealth = Double.MAX_VALUE;
        double highestAtk = 0;
        MapLocation weakestThreat = null;
        boolean canWeOutrangeAnyThreats = false;
        for (RobotInfo enemy : nearbyEnemies) {
            if (enemy.type == RobotType.ARCHON || enemy.type == RobotType.LABORATORY || enemy.type == RobotType.MINER || enemy.type == RobotType.BUILDER) {
                continue;
            }
            int distSq = enemy.location.distanceSquaredTo(curLoc);
            if (distSq <= enemy.type.actionRadiusSquared) {
                numCanShootUs++;

                highestAtk = Math.max(highestAtk, enemy.type.getDamage(enemy.level));

                if (!canWeOutrangeAnyThreats && atkRangeSq >= enemy.type.actionRadiusSquared) {
                    canWeOutrangeAnyThreats = true;
                }

                if (distSq <= atkRangeSq) {
                    double health = enemy.health;

                    if (health <= minThreatHealth) {
                        minThreatHealth = health;
                        weakestThreat = enemy.location;
                    }
                }

                for (int i = canShootThem.length; --i >= 0; ) {
                    RobotInfo ally = nearbyAllies[i];
                    if (ally.type == RobotType.ARCHON || ally.type == RobotType.LABORATORY
                            || ally.type == RobotType.MINER
                            || ally.type == RobotType.BUILDER) {
                        continue;
                    }

                    if (ally.location.distanceSquaredTo(enemy.location) <= ally.type.actionRadiusSquared) {
                        canShootThem[i] = true;
                    }
                }
            }
        }

        if (numCanShootUs > 0) {
            int numCanShootThem = 0;
            for (int i = canShootThem.length; --i >= 0; ) {
                if (canShootThem[i]) {
                    numCanShootThem++;
                }
            }

            if (rc.getHealth() > highestAtk * 3 && numCanShootThem + 1 >= numCanShootUs) {
                // attack
                if (weakestThreat != null) {
                    // pick one that's an immediate threat
                    if (rc.isActionReady()) {
                        rc.attack(weakestThreat);
                    } else if (canWeOutrangeAnyThreats) {
                        // if we're on cooldown, we can still move
                        // for every unit
                        if (rc.isMovementReady()) {
                            retreat(rc, nearbyEnemies);
                        }
                    }
                    return;
                } else {
                    if (rc.isActionReady()) {
                        RobotInfo weakest = getWeakestThreat(curLoc, nearbyEnemies);
                        MapLocation weakestLoc = null;
                        if (weakest != null) {
                            weakestLoc = weakest.location;
                        }
                        if (weakestLoc != null) {
                            pathfinder.move(weakestLoc);
                        }
                    }
                    return;
                }
            } else {
                // retreat
                if (rc.isMovementReady()) {
                    retreat(rc, nearbyEnemies);
                }
                return;
            }
        } else {
            // check if we can shoot anyone without moving
            RobotInfo weakest = getWeakestInRange(curLoc, nearbyEnemies);
            if (weakest != null) {
                if (rc.isActionReady()) {
                    rc.attack(weakest.location);
                }
                return;
            } else {
                // path toward the weakest person nearby
                if (rc.isMovementReady()) {
                    weakest = getWeakest(nearbyEnemies);
                    MapLocation weakestLoc = null;
                    if (weakest != null) {
                        weakestLoc = weakest.location;
                    }
                    if (weakestLoc != null) {
                        if (nearbyAllies.length > nearbyEnemies.length) {
                            // lots of allies, be aggressive
                            pathfinder.move(weakestLoc);
                        } else {
                            // few allies, be careful
                            // TODO: in this situation, be cautious
                            pathfinder.move(weakestLoc);
                        }
                    }
                }
                return;
            }

        }
    }

    private static boolean retreat(RobotController rc, RobotInfo[] nearbyEnemies)
            throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        boolean[] isAwayFromEnemy = Directions.dirsAwayFrom(nearbyEnemies, curLoc);

        Direction dirToMove = null;
        Direction unsafeDirToMove = null;
        int dirLen = Directions.RANDOM_DIRECTION_PERMUTATION.length;
        int start = gen.nextInt(dirLen);
        int i = start;
        do {
            Direction d = Directions.RANDOM_DIRECTION_PERMUTATION[i];
            if (isAwayFromEnemy[Directions.dirToInt(d)]) {
                if (rc.canMove(d)) {
                    // if there's a free spot, take advantage of it
                    // immediately
                    dirToMove = d;
                    break;
                }
            } else if (unsafeDirToMove == null && rc.canMove(d)) {
                unsafeDirToMove = d;
            }

            i = (i + 1) % dirLen;
        } while (i != start);

        if (dirToMove != null) {
            rc.move(dirToMove);
            locAfterMovement = locAtStartOfTurn.add(dirToMove);
        } else if (unsafeDirToMove != null) {
            // better to move than stand still
            rc.move(unsafeDirToMove);
            locAfterMovement = locAtStartOfTurn.add(unsafeDirToMove);
        }

        return true;
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

    private static RobotInfo getWeakest(RobotInfo[] nearby) {
        RobotInfo result = null;
        double minHealth = Double.MAX_VALUE;
        for (int i = nearby.length; --i >= 0; ) {
            RobotInfo enemy = nearby[i];
            double health = enemy.health;
            if (health < minHealth) {
                minHealth = health;
                result = enemy;
            }
        }
        return result;
    }

    private static RobotInfo getWeakestThreat(MapLocation curLoc, RobotInfo[] nearby) {
        RobotInfo result = null;
        double minHealth = Double.MAX_VALUE;
        for (int i = nearby.length; --i >= 0; ) {
            RobotInfo enemy = nearby[i];
            if (enemy.location.distanceSquaredTo(curLoc) > enemy.type.actionRadiusSquared) {
                continue;
            }
            double health = enemy.health;
            if (health < minHealth) {
                minHealth = health;
                result = enemy;
            }
        }
        return result;
    }

}
