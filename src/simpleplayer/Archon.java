package simpleplayer;

import battlecode.common.*;

import static simpleplayer.DebugProperties.*;
import static simpleplayer.RobotPlayer.*;

public class Archon {

    private static int[] unitCounts;

    public static void initArchon() throws GameActionException {
        Communication.writeArchonLocation();

        guessSymmetricArchonLocations();
    }

    private static void guessSymmetricArchonLocations() throws GameActionException {
        MapLocation loc = rc.getLocation();
        for (int i = 0; i < 3; ++i) {
            if (MapSymmetry.isSymmetryPossible[i]) {
                MapLocation symmetricCoords = MapSymmetry.getSymmetricCoords(loc, i);
                GridStrategy.instance.setAttackLocation(symmetricCoords);
            }
        }
    }

    public static void runArchon() throws GameActionException {

        Communication.clearUnitCounts();

        checkUnderAttack();

        unitCounts = Communication.getLastTurnUnitCount();

        if (!rc.isActionReady()) {
            return;
        }


        if (areWeUnderAttackAndShouldGiveUp) {
            tryHealingNearbyUnits();
            return;
        }
        if (areWeUnderAttack) {
            if (areWeUnderAttackAndWinning) {
                if (anyOtherArchonsNeedMoney && ourLead < (RobotType.SOLDIER.buildCostLead + RobotType.MINER.buildCostLead)) {
                    // let the other archons have the money
                    tryHealingNearbyUnits();
                    return;
                } else {
                    // follow normal build order
                    handleNormalBuildOrder();
                    tryHealingNearbyUnits();
                    return;
                }
            } else {
                handleUnderAttackBuildOrder();
                tryHealingNearbyUnits();
                return;
            }
        } else {
            if (anyOtherArchonsNeedMoney && ourLead < (RobotType.SOLDIER.buildCostLead + RobotType.MINER.buildCostLead)) {
                // let the other archons have the money
                tryHealingNearbyUnits();
                return;
            } else {
                // follow normal build order
                handleNormalBuildOrder();
                tryHealingNearbyUnits();
                return;
            }
        }


    }

    private static void handleNormalBuildOrder() throws GameActionException {

        int numNearbyMiners = 0;
        int effectiveNumNearbyLeadWorkersNeeded = 0;
        for (RobotInfo r : visibleAllies) {
            if (r.type == RobotType.MINER) {
                ++numNearbyMiners;
            }
        }
        MapLocation[] leadLocations = rc.senseNearbyLocationsWithLead(myType.visionRadiusSquared);
        int numNearbyLead = leadLocations.length;
        for (MapLocation location : leadLocations) {
            int lead = rc.senseLead(location);
            // some tiles are practically infinite, so we can't mine them all in one game.
            lead = Math.min(lead, 9 * 5 * GameConstants.GAME_MAX_NUMBER_OF_ROUNDS);
            // how many times could we mine from this tile for 45 lead each time over, say, the next 100 turns?
            // for comparison, a lead tile with 1 lead can be mined for 3 lead once every 50 turns, so 6 per 100 turns
            int timeHorizon = 40;
            double timeToMine = Math.min((lead - 1) / 45., timeHorizon);
            int numWorkers = (int) (timeToMine / 40. * 9);
            effectiveNumNearbyLeadWorkersNeeded += numWorkers;
        }

        // estimate of the number of tiles a single worker can manage. tunable.
        final int TILES_MANTAINED_BY_WORKER_PER_TURN = minerTileMantained;
        effectiveNumNearbyLeadWorkersNeeded += (numNearbyLead + TILES_MANTAINED_BY_WORKER_PER_TURN - 1) / TILES_MANTAINED_BY_WORKER_PER_TURN;

        if (effectiveNumNearbyLeadWorkersNeeded <= numNearbyMiners) {
            // too many workers. But maybe some land is unexplored, so it's worthwhile tomake more
            MapLocation loc = GridStrategy.instance.findClosestMiningLocation();
            if (loc != null && loc.distanceSquaredTo(locAtStartOfTurn) < 10 * 10) {
                effectiveNumNearbyLeadWorkersNeeded = numNearbyMiners + 1;
            }
        }

        if (effectiveNumNearbyLeadWorkersNeeded > numNearbyMiners) {
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
            // Build workers if it's necessary to bootstrap, otherwise soldiers
            if (rc.getRoundNum() < 10 && (unitCounts[RobotType.MINER.ordinal()] < 2)) {
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
                if (unitCounts[RobotType.SOLDIER.ordinal()] < soldierThreshold) {
                    // build soldiers
                    if (RobotType.SOLDIER.buildCostLead <= ourLead) {
                        for (final Direction d : Directions.RANDOM_DIRECTION_PERMUTATION) {
                            if (rc.canBuildRobot(RobotType.SOLDIER, d)) {
                                rc.buildRobot(RobotType.SOLDIER, d);
                                return;
                            }
                        }
                    }
                } else {
                    if (unitCounts[RobotType.BUILDER.ordinal()] <= 0.25 * Math.min(200, unitCounts[RobotType.WATCHTOWER.ordinal()])) {
                        // build builders
                        for (final Direction d : Directions.RANDOM_DIRECTION_PERMUTATION) {
                            if (rc.canBuildRobot(RobotType.BUILDER, d)) {
                                rc.buildRobot(RobotType.BUILDER, d);
                                return;
                            }
                        }
                    } else {
                        // let the builders build watchtowers. only add other stuff if we're loaded
                        if (ourLead > RobotType.WATCHTOWER.buildCostLead + RobotType.SOLDIER.buildCostLead) {
                            for (final Direction d : Directions.RANDOM_DIRECTION_PERMUTATION) {
                                if (rc.canBuildRobot(RobotType.SOLDIER, d)) {
                                    rc.buildRobot(RobotType.SOLDIER, d);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void handleUnderAttackBuildOrder() throws GameActionException {
        // There are attackers nearby. Build workers if it's necessary to bootstrap, otherwise soldiers
        if (rc.getRoundNum() < 10 || (unitCounts[RobotType.MINER.ordinal()] < 2 * archonCount)) {
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

    private static void tryHealingNearbyUnits() throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }
        RobotInfo[] nearbyAllies = actableAllies;
        RobotInfo weakestCombatUnit = null;
        int weakestCombatUnitHp = Integer.MAX_VALUE;
        RobotInfo weakestNoncombatUnit = null;
        int weakestNonCombatUnitHp = Integer.MAX_VALUE;
        for (RobotInfo ally : nearbyAllies) {
            RobotType allyType = ally.getType();
            if (ally.health == allyType.getMaxHealth(ally.level)) {
                continue;
            }
            if (allyType.isBuilding()) {
                continue;
            }
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
        if (weakestCombatUnit != null) {
            rc.repair(weakestCombatUnit.location);
        } else if (weakestNoncombatUnit != null) {
            rc.repair(weakestNoncombatUnit.location);
        }
    }

    private static boolean areWeUnderAttack = false;
    private static boolean areWeUnderAttackAndWinning = false;
    private static boolean areWeUnderAttackAndShouldGiveUp = false;
    private static boolean anyOtherArchonsNeedMoney = false;

    private static void checkUnderAttack() throws GameActionException {
        // TODO: if archons get destroyed, this can be left with stale value. Check the archon count and re-initialize.
        Communication.cacheArchonsUnderAttack();

        int numFriendlyAttackers = 0;
        for (int i = 0; i < visibleAllies.length; ++i) {
            if (visibleAllies[i].type.canAttack()) {
                ++numFriendlyAttackers;
            }
        }
        int numEnemyAttackers = 0;
        int netEnemyDamagePerTurn = 0;
        for (int i = 0; i < visibleEnemies.length; ++i) {
            RobotInfo enemy = visibleEnemies[i];
            RobotType enemyType = enemy.type;
            if (enemyType.canAttack()) {
                ++numEnemyAttackers;
                netEnemyDamagePerTurn += enemy.type.getDamage(enemy.level);
            } else if (enemyType == RobotType.ARCHON && rc.getRoundNum() > 2) {
                ++numEnemyAttackers;
            }
        }

        if (numEnemyAttackers == 0) {
            MapLocation closestUnderAttack = GridStrategy.instance.findClosestAttackLocation();
            if (closestUnderAttack != null && closestUnderAttack.distanceSquaredTo(locAtStartOfTurn) < 15 * 15) {
                ++numEnemyAttackers;
            }
        }

        boolean anyOtherArchonsOkay = false;
        for (int i = rc.getArchonCount(); --i >= 0; ) {
            if (i == Communication.myArchonIndex) {
                continue;
            }
            if (!Communication.readArchonUnderAttack(i)) {
                anyOtherArchonsOkay = true;
            }
            if (Communication.readArchonNeedsMoneyForSoldier(i)) {
                anyOtherArchonsNeedMoney = true;
            }
        }

        areWeUnderAttack = numEnemyAttackers > 0;

        if (shouldRush) {
            areWeUnderAttack = true;
        }

        areWeUnderAttackAndWinning = false;
        areWeUnderAttackAndShouldGiveUp = false;
        if (areWeUnderAttack) {
            // If we're winning decicively, just broadcast information. If other archons are under attack, they might
            // choose to take preference for resources.
            // If we're severely outnumbered, and other archons might have a better chance, so store a flag to mark
            // that we should give up.


            if (numFriendlyAttackers >= 2 * numEnemyAttackers) {
                areWeUnderAttackAndWinning = !shouldRush;
            } else {
                if (numFriendlyAttackers <= 2 && netEnemyDamagePerTurn >= RobotType.SOLDIER.health) {
                    // they can 1-shot soldiers, so probably not worth fighting here.
                    // TODO: retreat if there's an exit.
                    if (rc.getArchonCount() > 1) {
                        if (anyOtherArchonsOkay) {
                            areWeUnderAttackAndShouldGiveUp = true;
                        }
                    }
                }
            }

            if (!Communication.readArchonUnderAttack(Communication.myArchonIndex) || (Communication.readArchonNeedsMoneyForSoldier(Communication.myArchonIndex) == areWeUnderAttackAndShouldGiveUp)) {
                Communication.writeArchonUnderAttack(true, !areWeUnderAttackAndShouldGiveUp);
            }
        } else {
            if (Communication.readArchonUnderAttack(Communication.myArchonIndex) || Communication.readArchonNeedsMoneyForSoldier(Communication.myArchonIndex)) {
                Communication.writeArchonUnderAttack(false, false);
            }
        }

        if (Debug.SHOULD_SHOW_VISUALIZATIONS) {
            if (areWeUnderAttack && !areWeUnderAttackAndWinning) {
                rc.setIndicatorDot(locAtStartOfTurn, 0, 0, 0);
            } else {
                rc.setIndicatorDot(locAtStartOfTurn, 255, 255, 255);
            }
        }
    }
}
