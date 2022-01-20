package simpleplayer;

import battlecode.common.*;

import static simpleplayer.RobotPlayer.*;

public class Soldier {

    private static MapLocation nearestArchonLocation;
    private static boolean shouldHeal = false;

    public static void runSoldier() throws GameActionException {
        RobotInfo[] nearbyAllies = visibleAllies;
        RobotInfo[] nearbyEnemies = visibleEnemies;

        nearestArchonLocation = findNearestArchonLocation();
        shouldHeal = (shouldHeal && rc.getHealth() < myType.getMaxHealth(rc.getLevel())) || rc.getHealth() < 18;

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
            if (shouldHeal) {
                moveToFriendlyArchon();
                return;
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

    private static MapLocation findNearestArchonLocation() throws GameActionException {
        MapLocation[] archonLocations = Communication.readArchonLocations();
        MapLocation closestArchon = null;
        int closestDistSq = Integer.MAX_VALUE;
        for (MapLocation a : archonLocations) {
            int distSq = a.distanceSquaredTo(locAtStartOfTurn);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closestArchon = a;
            }
        }
        if (closestArchon == null) {
            return myInitialLocation;
        }
        return closestArchon;
    }

    private static void moveToFriendlyArchon() throws GameActionException {
        pathfinder.move(nearestArchonLocation);
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

    private static boolean[] checkedArchonLocation = null;
    private static MapLocation lastArchonTarget = null;
    private static int curSymmetryInvestigation = -1;

    private static boolean tryMoveToInitialEnemyArchonLocations() throws GameActionException {
        if (checkedArchonLocation == null) {
            // Just store our initial position and try to path towards it. This is a fallback in case the broadcasted
            // symmetric locations didn't work out.
            checkedArchonLocation = new boolean[3];
            for (int i = 0; i < 3; ++i) {
                if (!MapSymmetry.isSymmetryPossible[i]) {
                    checkedArchonLocation[i] = true;
                }
            }
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
        for (RobotInfo enemy : nearbyEnemies) {
            if (enemy.type == RobotType.ARCHON || enemy.type == RobotType.LABORATORY || enemy.type == RobotType.MINER || enemy.type == RobotType.BUILDER) {
                continue;
            }
            int distSq = enemy.location.distanceSquaredTo(curLoc);
            if (distSq <= enemy.type.actionRadiusSquared) {
                numCanShootUs++;

                highestAtk = Math.max(highestAtk, enemy.type.getDamage(enemy.level));

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

            if (rc.getHealth() > highestAtk * 3 && numCanShootThem + 1 >= numCanShootUs && !shouldHeal) {
                // attack
                if (weakestThreat != null) {
                    // pick one that's an immediate threat
                    if (rc.isActionReady()) {
                        rc.attack(weakestThreat);
                    }
                    reposition(locAtStartOfTurn.directionTo(weakestThreat).opposite());
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
                            weakest = getWeakestInRange(locAfterMovement, nearbyEnemies);
                            if (weakest != null) {
                                rc.attack(weakest.location);
                            }
                        }
                    }
                    return;
                }
            } else {
                // retreat
                if (rc.isActionReady()) {
                    RobotInfo weakest = getWeakestInRange(curLoc, nearbyEnemies);
                    if (weakest != null) {
                        rc.attack(weakest.location);
                    }
                }
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
                if (shouldHeal && rc.isMovementReady()) {
                    moveToFriendlyArchon();
                }
                return;
            } else {
                // path toward the weakest person nearby
                if (rc.isMovementReady()) {
                    if (shouldHeal) {
                        moveToFriendlyArchon();
                    } else {
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
                            if (rc.isActionReady() && locAfterMovement.distanceSquaredTo(weakestLoc) < myType.actionRadiusSquared) {
                                rc.attack(weakest.location);
                            }
                        }
                    }
                }
                return;
            }

        }
    }

    private static void reposition(Direction back)
            throws GameActionException {
        // reposition to a place with better rubble
        // prefer to sidestrafe, and then to move backwards
        if (!rc.isMovementReady()) {
            return;
        }
        Direction backLeft = back.rotateLeft();
        Direction left = backLeft.rotateLeft();
        Direction backRight = back.rotateLeft();
        Direction right = backRight.rotateRight().rotateRight();

        int curRubble = rc.senseRubble(locAtStartOfTurn);

        MapLocation leftLoc = locAtStartOfTurn.add(left);
        int leftRubble = rc.canSenseLocation(leftLoc) ? rc.senseRubble(leftLoc) : 101;
        MapLocation rightLoc = locAtStartOfTurn.add(right);
        int rightRubble = rc.canSenseLocation(rightLoc) ? rc.senseRubble(rightLoc) : 101;
        if (leftRubble < curRubble || rightRubble < curRubble) {
            if (leftRubble < rightRubble) {
                rc.move(left);
                return;
            } else {
                rc.move(right);
                return;
            }
        }

        leftLoc = locAtStartOfTurn.add(backLeft);
        leftRubble = rc.canSenseLocation(leftLoc) ? rc.senseRubble(leftLoc) : 101;
        rightLoc = locAtStartOfTurn.add(backRight);
        rightRubble = rc.canSenseLocation(rightLoc) ? rc.senseRubble(rightLoc) : 101;
        if (leftRubble < curRubble || rightRubble < curRubble) {
            if (leftRubble < rightRubble) {
                rc.move(backLeft);
                return;
            } else {
                rc.move(backRight);
                return;
            }
        }

        MapLocation backLoc = locAtStartOfTurn.add(back);
        int backRubble = rc.canSenseLocation(backLoc) ? rc.senseRubble(backLoc) : 101;
        if (backRubble < curRubble) {
            rc.move(backLeft);
        }


    }

    private static boolean retreat(RobotController rc, RobotInfo[] nearbyEnemies)
            throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        boolean[] isAwayFromEnemy = Directions.dirsAwayFrom(nearbyEnemies, curLoc);

        Direction dirToMove = null;
        int lowestSafeRubble = 101;
        Direction unsafeDirToMove = null;
        int lowestUnsafeRubble = 101;
        int dirLen = Directions.RANDOM_DIRECTION_PERMUTATION.length;
        int start = gen.nextInt(dirLen);
        int i = start;
        do {
            Direction d = Directions.RANDOM_DIRECTION_PERMUTATION[i];
            MapLocation next = locAtStartOfTurn.add(d);
            if (rc.onTheMap(next)) {
                int rubbleAmount = rc.senseRubble(next);
                if (isAwayFromEnemy[Directions.dirToInt(d)]) {
                    if (rc.canMove(d)) {
                        if (rubbleAmount < lowestSafeRubble) {
                            lowestSafeRubble = rubbleAmount;
                            dirToMove = d;
                        }
                    }
                } else if (unsafeDirToMove == null && rc.canMove(d)) {
                    if (rubbleAmount < lowestUnsafeRubble) {
                        lowestUnsafeRubble = rubbleAmount;
                        unsafeDirToMove = d;
                    }
                }
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
