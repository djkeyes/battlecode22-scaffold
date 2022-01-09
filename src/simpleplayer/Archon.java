package simpleplayer;

import battlecode.common.*;

import static simpleplayer.RobotPlayer.*;

public class Archon {

    private static int numSacrificialBuildersBuilt = 0;

    public static void runArchon() throws GameActionException {

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

        int TARGET_NEARBY_MINERS = 20;
        int x = locAtStartOfTurn.x;
        int y = locAtStartOfTurn.y;
        int widthm1 = rc.getMapWidth() - 1;
        int heightm1 = rc.getMapHeight() - 1;
        int visionRadius = Util.sqrt(myType.visionRadiusSquared);
        int xBelow = Math.max(0, x - visionRadius);
        int yBelow = Math.max(0, y - visionRadius);
        int xAbove = Math.min(widthm1, x + visionRadius);
        int yAbove = Math.min(heightm1, y + visionRadius);
        double areaNearby = (xAbove - xBelow + 1) * (yAbove - yBelow + 1);
        // This isn't quite right, because we're slicing circles, but it's a rule of thumb for building miners.
        double visionDiameter = 2 * visionRadius + 1;
        double fractionOfArableLand = areaNearby / (visionDiameter * visionDiameter);

        if (numNearbyArchons == 0 && numNearbyAttackers == 0 && numNearbyMiners < TARGET_NEARBY_MINERS * fractionOfArableLand) {
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
}
