package simpleplayer;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {

    public static RobotController rc;
    // constant over lifetime of robot
    public static int id;
    public static Team us;
    public static Team them;
    public static Random gen;
    public static RobotType myType;
    // changes per-turn
    public static MapLocation locAtStartOfTurn;
    public static int robotCount;
    public static int archonCount;
    public static int ourLead;
    public static int ourGold;
    public static MapLocation locAfterMovement; // anyone calling rc.move() MUST update this variable

    private static final Movement simpleMovement = new SimpleMovement();
    private static final Movement rubbleAverseMovement = new RubbleAverseMovement(30);

    @SuppressWarnings("unused")
    public static void run(RobotController inputRobotController) {
        rc = inputRobotController;
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
                    locAtStartOfTurn = rc.getLocation();
                    locAfterMovement = rc.getLocation();
                    robotCount = rc.getRobotCount();
                    archonCount = rc.getArchonCount();
                    ourLead = rc.getTeamLeadAmount(us);
                    ourGold = rc.getTeamLeadAmount(us);

                    String debugMessage = "AC: " + rc.getActionCooldownTurns() + ", MC: " + rc.getMovementCooldownTurns();
                    rc.setIndicatorString(debugMessage);

                    switch (myType) {
                        case ARCHON:
                            runArchon();
                            break;
                        case MINER:
                            runMiner();
                            break;
                        case SOLDIER:
                            runSoldier();
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

    private static void runArchon() throws GameActionException {

        if (!rc.isActionReady()) {
            return;
        }

        if (robotCount - archonCount < 7) {
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

    private static final int MIN_LEAD_AMOUNT = 2;

    private static void runMiner() throws GameActionException {
        MapLocation nearestVisibleLeadLocation = null;
        MapLocation secondNearestVisibleLeadLocation = null;

        // want to leave 1 lead afterwards

        // check for any visible lead
        // TODO: use spiral coordinates, so we can terminate early
        int closestRadiusSquared = Integer.MAX_VALUE;
        int secondClosestRadiusSquared = Integer.MAX_VALUE;
        for (int x = 0; x * x <= myType.visionRadiusSquared; ++x) {
            for (int y = 0; y * y + x * x <= myType.visionRadiusSquared; ++y) {
                for (int sx = (x != 0 ? -1 : 1); sx <= 1; sx += 2) {
                    for (int sy = (y != 0 ? -1 : 1); sy <= 1; sy += 2) {
                        MapLocation nextLoc = locAtStartOfTurn.translate(sx * x, sy * y);
                        if (!rc.onTheMap(nextLoc)) {
                            continue;
                        }
                        int leadAmount = rc.senseLead(nextLoc);
                        if (leadAmount >= MIN_LEAD_AMOUNT) {
                            int rSquare = x * x + y * y;
                            if (rSquare < closestRadiusSquared) {
                                secondClosestRadiusSquared = closestRadiusSquared;
                                secondNearestVisibleLeadLocation = nearestVisibleLeadLocation;
                                closestRadiusSquared = rSquare;
                                nearestVisibleLeadLocation = nextLoc;
                            } else if (rSquare == closestRadiusSquared) {
                                secondClosestRadiusSquared = rSquare;
                                secondNearestVisibleLeadLocation = nextLoc;
                            }
                        }
                    }
                }
            }
        }

        if (lastTargetMiningLocation != null && locAtStartOfTurn.distanceSquaredTo(lastTargetMiningLocation) <= 2) {
            lastTargetMiningLocation = null;
        }
        if (nearestVisibleLeadLocation != null) {
            lastTargetMiningLocation = nearestVisibleLeadLocation;
        }
        if (lastTargetMiningLocation == null) {
            // pick somewhere at random and move towards it
            lastTargetMiningLocation = new MapLocation(gen.nextInt(rc.getMapWidth()), gen.nextInt(rc.getMapHeight()));
        }

        tryMiningAdjacentTiles();

        // if we mined out, go somewhere new
        if (lastTargetMiningLocation == null && secondNearestVisibleLeadLocation != null) {
            lastTargetMiningLocation = secondNearestVisibleLeadLocation;
        }
        if (lastTargetMiningLocation == null) {
            // pick somewhere at random and move towards it
            lastTargetMiningLocation = new MapLocation(gen.nextInt(rc.getMapWidth()), gen.nextInt(rc.getMapHeight()));
        }

        // okay to path away from target briefly, if we're bugging
        final double outOfVisionRangeMultiplier = 1.2f;
        if (closestRadiusSquared <= myType.visionRadiusSquared * outOfVisionRangeMultiplier) {
            Pathfinding.setTarget(lastTargetMiningLocation, simpleMovement);
        } else {
            Pathfinding.setTarget(lastTargetMiningLocation, rubbleAverseMovement);
        }
        Pathfinding.pathfindToward();

        // maybe new mines opened up
        tryMiningAdjacentTiles();

    }

    private static void tryMiningAdjacentTiles() throws GameActionException {
        MapLocation target = lastTargetMiningLocation;
        boolean areWeAtTarget = lastTargetMiningLocation != null && locAfterMovement.distanceSquaredTo(target) <= myType.actionRadiusSquared;
        if (!areWeAtTarget) {
            // just check the nearby stuff. we're not at the destination yet.
            target = locAfterMovement;
        }
        while (rc.isActionReady()) {
            if (target == null) {
                // need to pathfind somewhere new
                if (areWeAtTarget) {
                    lastTargetMiningLocation = null;
                }
                break;
            }
            int resourceLeft = rc.senseLead(target);
            while (rc.isActionReady() && resourceLeft >= MIN_LEAD_AMOUNT) {
                rc.mineLead(target);
                resourceLeft -= 1;
            }

            if (resourceLeft < MIN_LEAD_AMOUNT) {
                target = null;
            } else {
                // on action cooldown
                break;
            }

            // see if anything adjacent to us still has resources
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dy = -1; dy <= 1; ++dy) {
                    MapLocation nextLoc = locAfterMovement.translate(dx, dy);
                    if (!rc.onTheMap(nextLoc)) {
                        continue;
                    }
                    int leadAmount = rc.senseLead(nextLoc);
                    if (leadAmount >= MIN_LEAD_AMOUNT) {
                        target = nextLoc;
                        break;
                    }
                }
                if (target != null) {
                    break;
                }
            }

        }
    }

    private static void runSoldier() throws GameActionException {


        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(locAtStartOfTurn, myType.visionRadiusSquared, us);
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(locAtStartOfTurn, myType.visionRadiusSquared, them);

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

            // move randomly
            // TODO: deduce archon positions, and go there.

            if (tryMoveToInitialEnemyArchonLocations()) {
                return;
            }
            tryMoveToRandomTarget();

        }
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
        Pathfinding.setTarget(lastArchonTarget, rubbleAverseMovement);
        Pathfinding.pathfindToward();
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
        Pathfinding.setTarget(lastTargetAttackLocation, rubbleAverseMovement);
        Pathfinding.pathfindToward();
        return true;
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
                            Pathfinding.setTarget(weakestLoc, aggressive);
                            Pathfinding.pathfindToward();
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
                            Pathfinding.setTarget(weakestLoc, aggressive);
                        } else {
                            // few allies, be careful
                            cautious.setNearbyEnemies(nearbyEnemies);
                            Pathfinding.setTarget(weakestLoc, cautious);
                        }
                        Pathfinding.pathfindToward();
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
