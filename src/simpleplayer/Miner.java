package simpleplayer;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

import static simpleplayer.RobotPlayer.*;

public class Miner {

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

    public static void runMiner() throws GameActionException {
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
        pathfinder.move(lastTargetMiningLocation);

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
}
