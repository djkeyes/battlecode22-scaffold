package simpleplayer;

import battlecode.common.*;

import static simpleplayer.RobotPlayer.*;

public class Miner {

    private static MapLocation lastTargetMiningLocation = null;

    private static final int MIN_LEAD_AMOUNT = 2;

    private static final int RADIUS_TO_CONSIDER_FOR_ASSIGNMENT = 1;
    private static final int DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT = 2 * RADIUS_TO_CONSIDER_FOR_ASSIGNMENT + 1;
    //private static int[][] minerAssignments = null;
    private static long[][] resourcesAvailable = null;
    private static RobotInfo[] minersToConsider = null;

    private static MapLocation[] nearbyLead;
    private static MapLocation[] nearbyGold;

    private static int assignResources() throws GameActionException {

        if (resourcesAvailable == null) {
            //minerAssignments = new int[DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT][DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT];
            resourcesAvailable = new long[DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT][DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT];
            minersToConsider = new RobotInfo[25];
        } else {
            // Surprisingly, reallocating is cheaper than Arrays.fill or System.arraycopy. So that's dumb.
            //minerAssignments = new int[DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT][DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT];
            //resourcesAvailable = new long[DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT][DIAMETER_TO_CONSIDER_FOR_ASSIGNMENT];
        }

        // assign nearby resources to nearby miners, in order to encourage miners to stay in place once they've found a
        // good spot.
        // Ideally, this should resolve conflicts between miners without communication. That is, if two miners can see
        // each other and see some resources, they should reach the same conclusion about who gets those resources.
        // Of course, since miners don't have infinite sight, they might not choose the same alotment.
        // For now, I use the following rule:
        // Miners own any tile they are adjacent to. If there is a tie, on top of > edge adjacent > diagonal adjacent.
        // If there is a tie after that, it's broken by the order in minersToConsider, which is arbitray.
        // Also, if there's a tile with a lot of resources (more than RESOURCE_ALLOCATION_PER_TILE), we can assign
        // multiple miners to it, up to amount/RESOURCE_ALLOCATION_PER_TILE.

        {
            // for dense resource maps, this seems fast for scanning the immediate area
            MapLocation loc = locAtStartOfTurn;
            resourcesAvailable[RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][RADIUS_TO_CONSIDER_FOR_ASSIGNMENT]
                    = rc.onTheMap(loc) ? (rc.senseLead(loc) + rc.senseGold(loc)) : 0;
            loc = loc.add(Direction.EAST);
            resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][RADIUS_TO_CONSIDER_FOR_ASSIGNMENT]
                    = rc.onTheMap(loc) ? (rc.senseLead(loc) + rc.senseGold(loc)) : 0;
            loc = loc.add(Direction.NORTH);
            resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT]
                    = rc.onTheMap(loc) ? (rc.senseLead(loc) + rc.senseGold(loc)) : 0;
            loc = loc.add(Direction.WEST);
            resourcesAvailable[RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT]
                    = rc.onTheMap(loc) ? (rc.senseLead(loc) + rc.senseGold(loc)) : 0;
            loc = loc.add(Direction.WEST);
            resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT]
                    = rc.onTheMap(loc) ? (rc.senseLead(loc) + rc.senseGold(loc)) : 0;
            loc = loc.add(Direction.SOUTH);
            resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][RADIUS_TO_CONSIDER_FOR_ASSIGNMENT]
                    = rc.onTheMap(loc) ? (rc.senseLead(loc) + rc.senseGold(loc)) : 0;
            loc = loc.add(Direction.SOUTH);
            resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT]
                    = rc.onTheMap(loc) ? (rc.senseLead(loc) + rc.senseGold(loc)) : 0;
            loc = loc.add(Direction.EAST);
            resourcesAvailable[RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT]
                    = rc.onTheMap(loc) ? (rc.senseLead(loc) + rc.senseGold(loc)) : 0;
            loc = loc.add(Direction.EAST);
            resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT]
                    = rc.onTheMap(loc) ? (rc.senseLead(loc) + rc.senseGold(loc)) : 0;
        }

        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(8, us);
        int numMiners = 0;
        for (int i = nearbyRobots.length; --i >= 0; ) {
            if (nearbyRobots[i].type == RobotType.MINER) {
                minersToConsider[numMiners] = nearbyRobots[i];
                ++numMiners;
            }
        }
        minersToConsider[numMiners] = null;

        int myX = locAtStartOfTurn.x;
        int myY = locAtStartOfTurn.y;
        final int RESOURCE_ALLOCATION_PER_TILE = 10;
        {
            for (int i = 0; minersToConsider[i] != null; ++i) {
                // this unrolled version seems faster than a complicated series of for-loops
                RobotInfo miner = minersToConsider[i];
                MapLocation loc = miner.location;
                int relX = loc.x - myX + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT;
                int relY = loc.y - myY + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT;
                switch (relX) {
                    case -2:
                        switch (relY) {
                            case -2:
                                // far corner
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case -1:
                                // slant angle
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 0:
                                // edge, one space away
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 1:
                                // slant angle
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 2:
                                // far corner
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                        }
                        break;
                    case -1:
                        switch (relY) {
                            case -2:
                                // slant angle
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case -1:
                                // diag adj
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 0:
                                // adj
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 1:
                                // diag adj
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 2:
                                // slant angle
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                        }
                        break;
                    case 0:
                        switch (relY) {
                            case -2:
                                // edge, one space away
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case -1:
                                // adj
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 1:
                                // adj
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 2:
                                // edge, one space away
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                        }
                        break;
                    case 1:
                        switch (relY) {
                            case -2:
                                // slant angle
                                resourcesAvailable[-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case -1:
                                // diag adj
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 0:
                                // adj
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 1:
                                // diag adj
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 2:
                                // slant angle
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                        }
                        break;
                    case 2:
                        switch (relY) {
                            case -2:
                                // far corner
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case -1:
                                // slant angle
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 0:
                                // edge, one space away
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][-1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 1:
                                // slant angle
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][0 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                            case 2:
                                // far corner
                                resourcesAvailable[1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT][1 + RADIUS_TO_CONSIDER_FOR_ASSIGNMENT] -= RESOURCE_ALLOCATION_PER_TILE;
                                break;
                        }
                        break;
                }
            }
        }

        int numTilesAssignedToUs = 0;
        for (int relX = RADIUS_TO_CONSIDER_FOR_ASSIGNMENT - 1; relX <= RADIUS_TO_CONSIDER_FOR_ASSIGNMENT + 1; ++relX) {
            for (int relY = RADIUS_TO_CONSIDER_FOR_ASSIGNMENT - 1; relY <= RADIUS_TO_CONSIDER_FOR_ASSIGNMENT + 1; ++relY) {
                if (resourcesAvailable[relX][relY] > 0) {
                    ++numTilesAssignedToUs;
                }
            }
        }
        return numTilesAssignedToUs;
    }

    private static MapLocation findClosestFighter() {
        int closestDistSq = Integer.MAX_VALUE;
        MapLocation closest = null;
        for (int i = visibleAllies.length; --i >= 0; ) {
            RobotInfo ally = visibleAllies[i];
            if (!ally.type.canAttack()) {
                continue;
            }
            if (ally.type == RobotType.WATCHTOWER && ally.mode != RobotMode.PORTABLE) {
                continue;
            }
            int distSq = locAfterMovement.distanceSquaredTo(ally.location);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = ally.location;
            }
        }
        return closest;
    }

    private static int maxAssignmentBytecodes = 0;

    public static void runMiner() throws GameActionException {
        nearbyLead = rc.senseNearbyLocationsWithLead(myType.visionRadiusSquared);
        nearbyGold = rc.senseNearbyLocationsWithGold(myType.visionRadiusSquared);

        // initializes minerAssignments, which can be used to share resources with other miners
        int start = Debug.tic();
        int numTilesAssignedToUs = assignResources();
        maxAssignmentBytecodes = Debug.toc(start, "Miner-assignResources", maxAssignmentBytecodes);

        // now check the ones assigned to us. How many did we get?

        if (numTilesAssignedToUs >= 5) {
            // just stay here
            lastTargetMiningLocation = locAtStartOfTurn;
            tryMiningAdjacentTiles();
            return;
        }

        //int before2 = Clock.getBytecodeNum();

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
            if (dx >= -1 && dx <= 1 && dy >= -1 && dy <= 1) {
                // this is already adjacent to us, so don't consider it for the search.
                continue;
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
            if (dx >= -1 && dx <= 1 && dy >= -1 && dy <= 1) {
                // this is already adjacent to us, so don't consider it for the search.
                continue;
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
        MapLocation secondClosestResource = secondNearestUnassignedVisibleGoldLocation;
        if (closestResource == null) {
            closestResource = nearestVisibleUnassignedLeadLocation;
            secondClosestResource = secondNearestUnassignedVisibleLeadLocation;
        } else if (secondClosestResource == null) {
            secondClosestResource = nearestVisibleUnassignedLeadLocation;
        }
        //int after2 = Clock.getBytecodeNum();
        //System.out.println("bcs to find nearest resources: " + (after2 - before2));

        tryMiningAdjacentTiles();

        // continue to travel to last target
        // if we're already there, look for somewhere new to go
        if (lastTargetMiningLocation != null && locAtStartOfTurn.distanceSquaredTo(lastTargetMiningLocation) <= 2) {
            lastTargetMiningLocation = null;
        }
        // alternatively, if we have a better target, try visiting it
        MapLocation newTargetMiningLocation = null;
        if (closestResource != null && (rc.senseLead(closestResource) > 1 || rc.senseGold(closestResource) > 0)) {
            newTargetMiningLocation = closestResource;
        }
        if (newTargetMiningLocation == null && secondClosestResource != null
                && (rc.senseLead(secondClosestResource) > 1 || rc.senseGold(secondClosestResource) > 0)) {
            // if we mined out, go somewhere new
            newTargetMiningLocation = secondClosestResource;
        }
        if (newTargetMiningLocation == null) {
            // try following a fighter, will probably find rubble to loot
            newTargetMiningLocation = findClosestFighter();
        }
        if (newTargetMiningLocation != null) {
            lastTargetMiningLocation = newTargetMiningLocation;
        }
        if (lastTargetMiningLocation == null) {
            // If we just reset, and we don't have an override, pick somewhere at random and move towards it
            lastTargetMiningLocation = new MapLocation(gen.nextInt(rc.getMapWidth()), gen.nextInt(rc.getMapHeight()));
        }

        pathfinder.move(lastTargetMiningLocation);

        // maybe new mines opened up
        tryMiningAdjacentTiles();

    }

    private static void tryMiningAdjacentTiles() throws GameActionException {
        MapLocation target = locAfterMovement;
        int dx = -1, dy = -1;
        while (rc.isActionReady()) {
            if (target == null) {
                // need to pathfind somewhere new, no more resources
                return;
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
                // on action cooldown, but there might be more resources left
                return;
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
}
