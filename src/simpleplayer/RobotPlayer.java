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
    public static RobotInfo[] visibleAllies;
    public static RobotInfo[] actableAllies;
    public static RobotInfo[] visibleEnemies;
    public static RobotInfo[] actableEnemies;
    public static MapLocation locAfterMovement; // anyone calling rc.move() MUST update this variable

    private static final Movement simpleMovement = new SimpleMovement();
    private static final Movement rubbleAverseMovement = new RubbleAverseMovement(30);

    private static int getRandomSeed() {
        if (us == Team.A) {
            return Integer.parseInt(System.getProperty("bc.testing.team-a-seed"));
        } else {
            return Integer.parseInt(System.getProperty("bc.testing.team-b-seed"));
        }
    }

    @SuppressWarnings("unused")
    public static void run(RobotController inputRobotController) {
        final int randomSeed = getRandomSeed();
        rc = inputRobotController;
        id = rc.getID();
        us = rc.getTeam();
        them = us.opponent();
        gen = new Random(id * 131071L + randomSeed);
        myType = rc.getType();

        Directions.initRandomDirections(gen);

        // if more types need custom behavior, consider spliting them into separate files
        try {
            if (myType == RobotType.ARCHON) {
                locAtStartOfTurn = rc.getLocation();
                Communication.writeArchonLocation();
            }
        } catch (Exception e) {
            // :(
            e.printStackTrace();
            Clock.yield();
        }

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
                    visibleAllies = rc.senseNearbyRobots(myType.visionRadiusSquared, us);
                    actableAllies = rc.senseNearbyRobots(myType.actionRadiusSquared, us);
                    visibleEnemies = rc.senseNearbyRobots(myType.visionRadiusSquared, them);
                    actableEnemies = rc.senseNearbyRobots(myType.actionRadiusSquared, them);

                    String debugMessage = "AC: " + rc.getActionCooldownTurns() + ", MC: " + rc.getMovementCooldownTurns();
                    rc.setIndicatorString(debugMessage);

                    reportEnemyArchons();

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
                            break;
                        case WATCHTOWER:
                            break;
                        case BUILDER:
                            runBuilder();
                            break;
                        case SAGE:
                            break;
                    }

                    Communication.incrementUnitCount();

                    Clock.yield();
                }
            } catch (Exception e) {
                // :(
                e.printStackTrace();
                Clock.yield();
            }
        }
    }

    private static void reportEnemyArchons() throws GameActionException {
        for (RobotInfo r : visibleEnemies) {
            if (r.type != RobotType.ARCHON) {
                continue;
            }
            Communication.writeEnemyArchonLocation(r);
        }
    }

    private static void runArchon() throws GameActionException {

        Communication.clearUnitCounts();

        if (!rc.isActionReady()) {
            return;
        }

        int numNearbyAttackers = 0;
        int numNearbyMiners = 0;
        int numNearbyLead = 0;
        int effectiveNumNearbyLeadWorkersNeeded = 0;
        for (RobotInfo r : visibleAllies) {
            if (r.type == RobotType.MINER) {
                ++numNearbyMiners;
            }
        }
        for (RobotInfo r : visibleEnemies) {
            if (r.type.canAttack()) {
                ++numNearbyAttackers;
            }
        }
        MapLocation[] leadLocations = rc.senseNearbyLocationsWithLead(myType.visionRadiusSquared);
        numNearbyLead = leadLocations.length;
        for (MapLocation location : leadLocations) {
            int lead = rc.senseLead(location);
            // some tiles are practically infinite, so we can't mine them all in one game.
            lead = Math.min(lead, 9 * 5 * GameConstants.GAME_MAX_NUMBER_OF_ROUNDS);
            // how many times could we mine from this tile for 45 lead each time over, say, the next 100 turns?
            // for comparison, a lead tile with 1 lead can be mined for 5 lead once every 20 turns, so 25 per 100 turns
            int timeHorizon = 40;
            double timeToMine = Math.min((lead - 1) / 45., timeHorizon);
            int numWorkers = (int) (timeToMine / 40. * 9);
            effectiveNumNearbyLeadWorkersNeeded += numWorkers;
        }
        effectiveNumNearbyLeadWorkersNeeded += (numNearbyLead + 4) / 5;

        int[] unitCounts = Communication.getLastTurnUnitCount();

        if (numNearbyAttackers == 0 && effectiveNumNearbyLeadWorkersNeeded <= 0) {
            // need to be creative, seems nothing good is nearby
            // build a builder to grow a lead mine
            if (RobotType.BUILDER.buildCostLead <= ourLead) {
                for (final Direction d : Directions.RANDOM_DIRECTION_PERMUTATION) {
                    if (rc.canBuildRobot(RobotType.BUILDER, d)) {
                        rc.buildRobot(RobotType.BUILDER, d);
                        return;
                    }
                }
            }
        } else if (unitCounts[RobotType.MINER.ordinal()] < 5 || numNearbyMiners < effectiveNumNearbyLeadWorkersNeeded + 1) {
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

        tryHealingNearbyUnits();
    }

    private static void tryHealingNearbyUnits() throws GameActionException {
        RobotInfo[] nearbyAllies = actableAllies;
        RobotInfo weakestCombatUnit = null;
        int weakestCombatUnitHp = Integer.MAX_VALUE;
        RobotInfo weakestNoncombatUnit = null;
        int weakestNonCombatUnitHp = Integer.MAX_VALUE;
        //RobotInfo weakestArchon = null;
        //int weakestArchonHp = Integer.MAX_VALUE;
        for (RobotInfo ally : nearbyAllies) {
            RobotType allyType = ally.getType();
            if (ally.health == allyType.getMaxHealth(ally.level)) {
                continue;
            }
            /*if (allyType == RobotType.ARCHON) {
                if (ally.health < weakestArchonHp) {
                    weakestArchon = ally;
                    weakestArchonHp = ally.health;
                }
            } else */
            if (allyType.canAttack()) {
                if (ally.health < weakestCombatUnitHp) {
                    weakestCombatUnit = ally;
                    weakestCombatUnitHp = ally.health;
                }
            } else {
                if (ally.health < weakestNonCombatUnitHp) {
                    weakestNonCombatUnitHp = ally.health;
                    weakestNoncombatUnit = ally;
                }
            }
        }
        /*if (weakestArchon != null) {
            rc.repair(weakestArchon.location);
        } else*/
        if (weakestCombatUnit != null) {
            rc.repair(weakestCombatUnit.location);
        } else if (weakestNoncombatUnit != null) {
            rc.repair(weakestNoncombatUnit.location);
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
        Pathfinding.setTarget(closest, rubbleAverseMovement);
        Pathfinding.pathfindToward();
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

    private static void runBuilder() throws GameActionException {
        if (rc.senseRubble(locAtStartOfTurn) == 0) {
            rc.disintegrate();
        }
        tryMoveToRandomTarget();
    }

}
