package simpleplayer;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {

    /**
     * Array containing all the possible movement directions.
     */
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    private static int id;
    private static Team us;
    private static Team them;
    private static Random gen;
    private static RobotType myType;

    private static final Movement simpleMovement = new SimpleMovement();
    private static final Movement rubbleAverseMovement = new RubbleAverseMovement();

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        id = rc.getID();
        us = rc.getTeam();
        them = us.opponent();
        gen = new Random(id);
        myType = rc.getType();

        Directions.initRandomDirections(gen);

        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                while (true) {
                    switch (myType) {
                        case ARCHON:
                            runArchon(rc);
                            break;
                        case MINER:
                            runMiner(rc);
                            break;
                        case SOLDIER:
                            runSoldier(rc);
                            break;
                        case LABORATORY:
                        case WATCHTOWER:
                        case BUILDER:
                        case SAGE:
                            break;
                    }
                    Clock.yield();
                }
            } catch (Exception e) {
                // :(
                e.printStackTrace();
                Clock.yield();
            }
        }
    }

    private static void runArchon(RobotController rc) throws GameActionException {
        final int robotCount = rc.getRobotCount();
        final int ourLead = rc.getTeamLeadAmount(us);

        if (!rc.isActionReady()) {
            return;
        }

        if (robotCount < 10) {
            // build workers
            if (RobotType.MINER.buildCostLead <= ourLead) {
                for (final Direction d : Directions.RANDOM_DIRECTION_PERMUTATION) {
                    if (rc.canBuildRobot(RobotType.MINER, d)) {
                        rc.buildRobot(RobotType.MINER, d);
                        return;
                    }
                }
            }

        } else {
            // build soldiers
            if (RobotType.SOLDIER.buildCostLead <= ourLead) {
                for (final Direction d : Directions.RANDOM_DIRECTION_PERMUTATION) {
                    if (rc.canBuildRobot(RobotType.SOLDIER, d)) {
                        rc.buildRobot(RobotType.SOLDIER, d);
                        return;
                    }
                }
            }
        }
    }

    private static MapLocation lastTargetMiningLocation = null;

    private static void runMiner(RobotController rc) throws GameActionException {
        MapLocation curLoc = rc.getLocation();

        MapLocation nearestVisibleLeadLocation = null;
        // check for any visible lead
        // TODO: use spiral coordinates, so we can terminate early
        int closestRadiusSquared = Integer.MAX_VALUE;
        for (int x = 0; x * x <= myType.visionRadiusSquared; ++x) {
            int startY = 0;
            if (x == 0) {
                startY = 1;
            }
            for (int y = startY; y * y + x * x <= myType.visionRadiusSquared; ++y) {
                for (int sx = (x != 0 ? -1 : 1); sx <= 1; sx += 2) {
                    for (int sy = (y != 0 ? -1 : 1); sy <= 1; sy += 2) {
                        MapLocation nextLoc = curLoc.translate(sx * x, sy * y);
                        if (!rc.onTheMap(nextLoc)) {
                            continue;
                        }
                        int leadAmount = rc.senseLead(nextLoc);
                        if (leadAmount >= 2) {
                            int rSquare = x * x + y * y;
                            if (rSquare < closestRadiusSquared) {
                                closestRadiusSquared = rSquare;
                                nearestVisibleLeadLocation = nextLoc;
                            }
                        }
                    }
                }
            }
        }

        if (lastTargetMiningLocation != null && curLoc.distanceSquaredTo(lastTargetMiningLocation) <= 2) {
            lastTargetMiningLocation = null;
        }
        if (nearestVisibleLeadLocation != null) {
            lastTargetMiningLocation = nearestVisibleLeadLocation;
        }
        if (lastTargetMiningLocation == null) {
            // pick somewhere at random and move towards it
            lastTargetMiningLocation = new MapLocation(gen.nextInt(rc.getMapWidth()), gen.nextInt(rc.getMapHeight()));
        }

        if (closestRadiusSquared <= myType.actionRadiusSquared) {
            // can mine now
            if (rc.isActionReady()) {
                //if (rc.canMineLead(lastTargetMiningLocation)) {
                rc.mineLead(lastTargetMiningLocation);
                //}
            }

            lastTargetMiningLocation = null;
            return;
        }

        // okay to path away from target briefly, if we're bugging
        final double outOfVisionRangeMultiplier = 1.2f;
        if (closestRadiusSquared <= myType.visionRadiusSquared * outOfVisionRangeMultiplier) {
            Pathfinding.setTarget(lastTargetMiningLocation, simpleMovement);
        } else {
            Pathfinding.setTarget(lastTargetMiningLocation, rubbleAverseMovement);
        }
        if (Pathfinding.pathfindToward(rc, gen)) {
            return;
        }

    }

    private static MapLocation lastTargetAttackLocation = null;

    private static void runSoldier(RobotController rc) throws GameActionException {
        MapLocation curLoc = rc.getLocation();

        if (lastTargetAttackLocation != null && curLoc.distanceSquaredTo(lastTargetAttackLocation) <= 2) {
            lastTargetAttackLocation = null;
        }


        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(curLoc, myType.visionRadiusSquared, us);
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(curLoc, myType.visionRadiusSquared, them);

        // do micro if we're near enemies
        if (nearbyEnemies.length > 0) {
            doMicro(rc, nearbyAllies, nearbyEnemies);
            lastTargetAttackLocation = null;
            return;
        }

        if (rc.isMovementReady()) {
            // move randomly if too crowded
            if (rc.senseNearbyRobots(2, us).length >= 4) {
                for (Direction d : Directions.RANDOM_DIRECTION_PERMUTATION) {
                    if (rc.canMove(d)) {
                        rc.move(d);
                        return;
                    }
                }
            }

            // move randomly
            // TODO: deduce archon positions, and go there.
            if (lastTargetAttackLocation == null) {
                lastTargetAttackLocation = new MapLocation(gen.nextInt(rc.getMapWidth()), gen.nextInt(rc.getMapHeight()));
            }
            Pathfinding.setTarget(lastTargetAttackLocation, rubbleAverseMovement);
            if (Pathfinding.pathfindToward(rc, gen)) {
                return;
            }
        }
    }

    private static final CautiousMovement cautious = new CautiousMovement();
    private static final SimpleMovement aggressive = new SimpleMovement();

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
                            retreat(rc, nearbyEnemies, false);
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
                            Pathfinding.setTarget(weakestLoc, aggressive);
                            Pathfinding.pathfindToward(rc, gen);
                        }
                    }
                    return;
                }
            } else {
                // retreat
                if (rc.isMovementReady()) {
                    retreat(rc, nearbyEnemies, false);
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
                            Pathfinding.setTarget(weakestLoc, aggressive);
                        } else {
                            // few allies, be careful
                            cautious.setNearbyEnemies(nearbyEnemies);
                            Pathfinding.setTarget(weakestLoc, cautious);
                        }
                        Pathfinding.pathfindToward(rc, gen);
                    }
                }
                return;
            }

        }
    }

    public static boolean retreat(RobotController rc, RobotInfo[] nearbyEnemies, boolean clearRubbleAggressively)
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
                MapLocation next = curLoc.add(d);
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
        } else if (unsafeDirToMove != null) {
            // better to move than stand still
            rc.move(unsafeDirToMove);
        }

        return true;
    }

    // TODO: several of these methods are very similar. extract their
    // similarities somehow?
    public static RobotInfo getWeakestInRange(MapLocation curLoc, RobotInfo[] nearby) {
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

    public static RobotInfo getWeakest(RobotInfo[] nearby) {
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
