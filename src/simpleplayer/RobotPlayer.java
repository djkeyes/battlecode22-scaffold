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
        String key;
        if (us == Team.A) {
            key = "bc.testing.team-a-seed";
        } else {
            key = "bc.testing.team-b-seed";
        }
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(value);
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

    private static int numSacrificialBuildersBuilt = 0;

    private static void runArchon() throws GameActionException {

        Communication.clearUnitCounts();

        if (!rc.isActionReady()) {
            return;
        }

        int numNearbyAttackers = 0;
        int numNearbyArchons = 0;
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
            } else if (r.type == RobotType.ARCHON) {
                ++numNearbyArchons;
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
        // estimate of the number of tiles a single worker can manage. tunable.
        final int TILES_MANTAINED_BY_WORKER_PER_TURN = 5;
        effectiveNumNearbyLeadWorkersNeeded += (numNearbyLead + 4) / TILES_MANTAINED_BY_WORKER_PER_TURN;

        int[] unitCounts = Communication.getLastTurnUnitCount();

        int TARGET_MINERS_PER_QUADRANT = 5;
        int numQuadrants = 0;
        int x = locAtStartOfTurn.x;
        int y = locAtStartOfTurn.y;
        int widthm1 = rc.getMapWidth() - 1;
        int heightm1 = rc.getMapHeight() - 1;
        boolean away_from_left = x * x >= myType.visionRadiusSquared;
        boolean away_from_bot = y * y >= myType.visionRadiusSquared;
        boolean away_from_right = (x - widthm1) * (x - widthm1) >= myType.visionRadiusSquared;
        boolean away_from_top = (y - heightm1) * (y - heightm1) >= myType.visionRadiusSquared;
        if (away_from_left) {
            if (away_from_bot) {
                numQuadrants += 1;
            }
            if (away_from_top) {
                numQuadrants += 1;
            }
        }
        if (away_from_right) {
            if (away_from_bot) {
                numQuadrants += 1;
            }
            if (away_from_top) {
                numQuadrants += 1;
            }
        }
        if (numQuadrants == 0) {
            numQuadrants = 1;
        }

        if (numNearbyArchons == 0 && numNearbyAttackers == 0 && numNearbyMiners < TARGET_MINERS_PER_QUADRANT * numQuadrants) {
            // No attackers nearby. Should probably farm economy
            if (effectiveNumNearbyLeadWorkersNeeded <= numNearbyMiners && numSacrificialBuildersBuilt < 10) {
                // need to be creative, seems nothing good is nearby
                // build a builder to grow a lead mine
                if (RobotType.BUILDER.buildCostLead <= ourLead) {
                    for (final Direction d : Directions.RANDOM_DIRECTION_PERMUTATION) {
                        if (rc.canBuildRobot(RobotType.BUILDER, d)) {
                            rc.buildRobot(RobotType.BUILDER, d);
                            numSacrificialBuildersBuilt++;
                            return;
                        }
                    }
                }
            } else {
                // build workers
                if (RobotType.MINER.buildCostLead <= ourLead) {
                    for (final Direction d : Directions.RANDOM_DIRECTION_PERMUTATION) {
                        if (rc.canBuildRobot(RobotType.MINER, d)) {
                            rc.buildRobot(RobotType.MINER, d);
                            return;
                        }
                    }
                }
            }
        } else {
            // There are attackers nearby. Build workers if it's necessary to bootstrap, otherwise soldiers
            if (rc.getRoundNum() < 10 && (unitCounts[RobotType.MINER.ordinal()] < 5 || numNearbyMiners < effectiveNumNearbyLeadWorkersNeeded + 1)) {
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

    private static final int RADIUS_TO_CONSIDER_FOR_ASSIGNMENT = 3;
    private static final int DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT = 2 * RADIUS_TO_CONSIDER_FOR_ASSIGNMENT + 1;
    private static int[][] minerAssignments = null;
    private static long[][] resourcesAvailable = null;
    private static RobotInfo[] minersToConsider = null;

    private static MapLocation[] nearbyLead;
    private static MapLocation[] nearbyGold;

    private static final int CENTER_PRIORITY = 2000000;
    private static final int EDGE_PRIORITY = 1000000;
    private static final int PRIORITY_MOD = 1000000;

    private static void assignResources() throws GameActionException {
        if (minerAssignments == null) {
            minerAssignments = new int[DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT][DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT];
            resourcesAvailable = new long[DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT][DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT];
            minersToConsider = new RobotInfo[25];
        } else {
            // Surprisingly, reallocating is cheaper than Arrays.fill or System.arraycopy. So that's dumb.
            minerAssignments = new int[DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT][DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT];
            resourcesAvailable = new long[DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT][DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT];
        }

        // assign nearby resources to nearby miners, in order to encourage miners to stay in place once they've found a
        // good spot.
        // Ideally, this should resolve conflicts between miners without communication. That is, if two miners can see
        // each other and see some resources, they should reach the same conclusion about who gets those resources.
        // Of course, since miners don't have infinite sight, they might not choose the same alotment.
        // For now, I use the following rule:
        // Miners own any tile they are adjacent to. If there is a tie, on top of > edge adjacent > diagonal adjacent.
        // If there is a tie after that, the robot with the higher ID wins.
        // Also, if there's a tile with a lot of resources (more than RESOURCE_ALLOCATION_PER_TILE), we can assign
        // multiple miners to it, up to amount/RESOURCE_ALLOCATION_PER_TILE.

        for (int i = nearbyLead.length; --i >= 0; ) {
            MapLocation loc = nearbyLead[i];
            int dx = loc.x - locAtStartOfTurn.x;
            if (dx > RADIUS_TO_CONSIDER_FOR_ASSIGNMENT || dx < -RADIUS_TO_CONSIDER_FOR_ASSIGNMENT) {
                continue;
            }
            int dy = loc.y - locAtStartOfTurn.y;
            if (dy > RADIUS_TO_CONSIDER_FOR_ASSIGNMENT || dy < -RADIUS_TO_CONSIDER_FOR_ASSIGNMENT) {
                continue;
            }
            int amount = rc.senseLead(loc);
            resourcesAvailable[dx + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][dy + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] += amount;
        }
        for (int i = nearbyGold.length; --i >= 0; ) {
            MapLocation loc = nearbyGold[i];
            int dx = loc.x - locAtStartOfTurn.x;
            if (dx > RADIUS_TO_CONSIDER_FOR_ASSIGNMENT || dx < -RADIUS_TO_CONSIDER_FOR_ASSIGNMENT) {
                continue;
            }
            int dy = loc.y - locAtStartOfTurn.y;
            if (dy > RADIUS_TO_CONSIDER_FOR_ASSIGNMENT || dy < -RADIUS_TO_CONSIDER_FOR_ASSIGNMENT) {
                continue;
            }
            int amount = rc.senseGold(loc);
            resourcesAvailable[dx + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][dy + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] += amount;
        }

        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(8, us);
        int numMiners = 0;
        for (int i = nearbyRobots.length; --i >= 0; ) {
            if (nearbyRobots[i].type == RobotType.MINER) {
                minersToConsider[numMiners] = nearbyRobots[i];
                ++numMiners;
            }
        }
        minersToConsider[numMiners] = rc.senseRobot(id);
        minersToConsider[numMiners + 1] = null;

        int myX = locAtStartOfTurn.x;
        int myY = locAtStartOfTurn.y;
        final int RESOURCE_ALLOCATION_PER_TILE = 10;
        // first, assign on-location
        {
            for (int i = 0; minersToConsider[i] != null; ++i) {
                RobotInfo miner = minersToConsider[i];
                MapLocation loc = miner.location;
                int relX = loc.x - myX + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT;
                int relY = loc.y - myY + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT;
                int minerId = miner.getID();
                if (resourcesAvailable[relX][relY] > 0) {
                    minerAssignments[relX][relY] = CENTER_PRIORITY + minerId;
                    resourcesAvailable[relX][relY] -= RESOURCE_ALLOCATION_PER_TILE;
                    if (resourcesAvailable[relX][relY] == 0) {
                        // assign a negative number, so other miners know this resource is contested
                        resourcesAvailable[relX][relY] = -1;
                    }
                }
            }
        }
        // next, edges
        {
            for (int i = 0; minersToConsider[i] != null; ++i) {
                RobotInfo miner = minersToConsider[i];
                MapLocation loc = miner.location;
                int minerId = miner.getID();
                // TODO: unroll these loops, if you're tight on bytecodes
                for (int dx = -1; dx <= 1; dx += 2) {
                    int relX = loc.x - myX + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT + dx;
                    int relY = loc.y - myY + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT;
                    if (resourcesAvailable[relX][relY] > 0) {
                        // still some resources here
                        resourcesAvailable[relX][relY] -= RESOURCE_ALLOCATION_PER_TILE;
                        if (resourcesAvailable[relX][relY] == 0) {
                            resourcesAvailable[relX][relY] = -1;
                        }
                        if (minerAssignments[relX][relY] < EDGE_PRIORITY + minerId) {
                            minerAssignments[relX][relY] = EDGE_PRIORITY + minerId;
                        }
                    } else if (resourcesAvailable[relX][relY] < 0 && minerAssignments[relX][relY] < EDGE_PRIORITY + minerId) {
                        minerAssignments[relX][relY] = EDGE_PRIORITY + minerId;
                    }
                }
                for (int dy = -1; dy <= 1; dy += 2) {
                    int relX = loc.x - myX + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT;
                    int relY = loc.y - myY + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT + dy;
                    if (resourcesAvailable[relX][relY] > 0) {
                        resourcesAvailable[relX][relY] -= RESOURCE_ALLOCATION_PER_TILE;
                        if (resourcesAvailable[relX][relY] == 0) {
                            resourcesAvailable[relX][relY] = -1;
                        }
                        if (minerAssignments[relX][relY] < EDGE_PRIORITY + minerId) {
                            minerAssignments[relX][relY] = EDGE_PRIORITY + minerId;
                        }
                    } else if (resourcesAvailable[relX][relY] < 0 && minerAssignments[relX][relY] < EDGE_PRIORITY + minerId) {
                        minerAssignments[relX][relY] = EDGE_PRIORITY + minerId;
                    }
                }
            }
        }
        // next, corners
        {
            for (int i = 0; minersToConsider[i] != null; ++i) {
                RobotInfo miner = minersToConsider[i];
                MapLocation loc = miner.location;
                int minerId = miner.getID();
                for (int dx = -1; dx <= 1; dx += 2) {
                    for (int dy = -1; dy <= 1; dy += 2) {
                        int relX = loc.x - myX + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT + dx;
                        int relY = loc.y - myY + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT + dy;
                        if (resourcesAvailable[relX][relY] > 0) {
                            resourcesAvailable[relX][relY] -= 5;
                            if (resourcesAvailable[relX][relY] == 0) {
                                resourcesAvailable[relX][relY] = -1;
                            }
                            if (minerAssignments[relX][relY] < minerId) {
                                minerAssignments[relX][relY] = minerId;
                            }
                        } else if (resourcesAvailable[relX][relY] < 0 && minerAssignments[relX][relY] < minerId) {
                            minerAssignments[relX][relY] = minerId;
                        }
                    }
                }
            }
        }
    }

    private static void runMiner() throws GameActionException {
        nearbyLead = rc.senseNearbyLocationsWithLead(myType.visionRadiusSquared);
        nearbyGold = rc.senseNearbyLocationsWithGold(myType.visionRadiusSquared);

        // initializes minerAssignments, which can be used to share resources with other miners
        assignResources();

        // now check the ones assigned to us. How many did we get?
        int numTilesAssignedToUs = 0;
        for (int relX = RADIUS_TO_CONSIDER_FOR_ASSIGNMENT - 1; relX <= RADIUS_TO_CONSIDER_FOR_ASSIGNMENT + 1; ++relX) {
            for (int relY = RADIUS_TO_CONSIDER_FOR_ASSIGNMENT - 1; relY <= RADIUS_TO_CONSIDER_FOR_ASSIGNMENT + 1; ++relY) {
                if (minerAssignments[relX][relY] % PRIORITY_MOD == id) {
                    ++numTilesAssignedToUs;
                }
            }
        }

        // See if there are any farms that can be harvested right now. If there are none, but we have some assigned to
        // us, just hang around our assignments.

        int myX = locAtStartOfTurn.x;
        int myY = locAtStartOfTurn.y;
        MapLocation nearestVisibleUnassignedLeadLocation = null;
        MapLocation secondNearestUnassignedVisibleLeadLocation = null;
        int closestLeadRadiusSquared = Integer.MAX_VALUE;
        int secondLeadClosestRadiusSquared = Integer.MAX_VALUE;
        for (int i = 0; i < nearbyLead.length; ++i) {
            MapLocation resourceLocation = nearbyLead[i];
            int dx = resourceLocation.x - myX;
            int dy = resourceLocation.y - myY;
            int relX = dx + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT;
            int relY = dy + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT;
            if (relX > 0 && relY > 0 && relX < minerAssignments.length && relY < minerAssignments.length) {
                if (minerAssignments[relX][relY] != 0) {
                    // this is assigned to someone (if it's assigned to us, we'll consider it later). skip it.
                    continue;
                }
            }
            if (rc.senseLead(resourceLocation) < MIN_LEAD_AMOUNT) {
                continue;
            }
            int rSquare = dx * dx + dy * dy;
            if (rSquare < closestLeadRadiusSquared) {
                secondLeadClosestRadiusSquared = closestLeadRadiusSquared;
                secondNearestUnassignedVisibleLeadLocation = nearestVisibleUnassignedLeadLocation;
                closestLeadRadiusSquared = rSquare;
                nearestVisibleUnassignedLeadLocation = resourceLocation;
            } else if (rSquare == closestLeadRadiusSquared) {
                secondLeadClosestRadiusSquared = rSquare;
                secondNearestUnassignedVisibleLeadLocation = resourceLocation;
            }
        }
        MapLocation nearestVisibleUnassignedGoldLocation = null;
        MapLocation secondNearestUnassignedVisibleGoldLocation = null;
        int closestGoldRadiusSquared = Integer.MAX_VALUE;
        int secondClosestGoldRadiusSquared = Integer.MAX_VALUE;
        for (int i = 0; i < nearbyGold.length; ++i) {
            MapLocation resourceLocation = nearbyGold[i];
            int dx = resourceLocation.x - myX;
            int dy = resourceLocation.y - myY;
            int relX = dx + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT;
            int relY = dy + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT;
            if (relX > 0 && relY > 0 && relX < minerAssignments.length && relY < minerAssignments.length) {
                if (minerAssignments[relX][relY] != 0) {
                    // this is assigned to someone (if it's assigned to us, we'll consider it later). skip it.
                    continue;
                }
            }
            int rSquare = dx * dx + dy * dy;
            if (rSquare < closestGoldRadiusSquared) {
                secondClosestGoldRadiusSquared = closestGoldRadiusSquared;
                secondNearestUnassignedVisibleGoldLocation = nearestVisibleUnassignedGoldLocation;
                closestGoldRadiusSquared = rSquare;
                nearestVisibleUnassignedGoldLocation = resourceLocation;
            } else if (rSquare == closestGoldRadiusSquared) {
                secondClosestGoldRadiusSquared = rSquare;
                secondNearestUnassignedVisibleGoldLocation = resourceLocation;
            }
        }

        // if there are multiple options, prefer gold, since it's a tiebreaker
        MapLocation closestResource = nearestVisibleUnassignedGoldLocation;
        int closestRadiusSquared = closestGoldRadiusSquared;
        MapLocation secondClosestResource = secondNearestUnassignedVisibleGoldLocation;
        int secondClosestRadiusSquared = secondClosestGoldRadiusSquared;
        if (closestResource == null) {
            closestResource = nearestVisibleUnassignedLeadLocation;
            closestRadiusSquared = closestLeadRadiusSquared;
            secondClosestResource = secondNearestUnassignedVisibleLeadLocation;
            secondClosestRadiusSquared = secondLeadClosestRadiusSquared;
        } else if (secondClosestResource == null) {
            secondClosestResource = nearestVisibleUnassignedLeadLocation;
            secondClosestRadiusSquared = closestLeadRadiusSquared;
        }

        if (numTilesAssignedToUs >= 5) {
            // just stay here
            lastTargetMiningLocation = closestResource;
            tryMiningAdjacentTiles();
            return;
        }

        // travel to nearest resource
        if (lastTargetMiningLocation != null && locAtStartOfTurn.distanceSquaredTo(lastTargetMiningLocation) <= 2) {
            lastTargetMiningLocation = null;
        }
        if (closestResource != null) {
            lastTargetMiningLocation = closestResource;
        }
        if (lastTargetMiningLocation == null) {
            // pick somewhere at random and move towards it
            lastTargetMiningLocation = new MapLocation(gen.nextInt(rc.getMapWidth()), gen.nextInt(rc.getMapHeight()));
        }

        tryMiningAdjacentTiles();

        // if we mined out, go somewhere new
        if (lastTargetMiningLocation == null && secondClosestResource != null) {
            lastTargetMiningLocation = secondClosestResource;
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
        int dx = -1, dy = -1;
        while (rc.isActionReady()) {
            if (target == null) {
                // need to pathfind somewhere new
                if (areWeAtTarget) {
                    lastTargetMiningLocation = null;
                }
                break;
            }

            int goldLeft = rc.senseGold(target);
            int leadLeft = rc.senseLead(target);
            while (rc.isActionReady() && goldLeft > 0) {
                rc.mineGold(target);
                goldLeft -= 1;
            }
            while (rc.isActionReady() && leadLeft >= MIN_LEAD_AMOUNT) {
                rc.mineLead(target);
                leadLeft -= 1;
            }

            if (goldLeft == 0 && leadLeft < MIN_LEAD_AMOUNT) {
                target = null;
            } else {
                // on action cooldown
                break;
            }

            // see if anything adjacent to us still has resources
            while (dx != 2) {
                MapLocation nextLoc = locAfterMovement.translate(dx, dy);
                if (rc.onTheMap(nextLoc)) {
                    int goldAmount = rc.senseGold(nextLoc);
                    if (goldAmount > 0) {
                        target = nextLoc;
                        break;
                    }
                    int leadAmount = rc.senseLead(nextLoc);
                    if (leadAmount >= MIN_LEAD_AMOUNT) {
                        target = nextLoc;
                        break;
                    }
                }
                ++dy;
                if (dy == 2) {
                    dy = -1;
                    ++dx;
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
        if (rc.senseLead(locAtStartOfTurn) == 0) {
            rc.disintegrate();
        }
        tryMoveToRandomTarget();
    }

}
