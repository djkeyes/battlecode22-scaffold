package simpleplayer;

import battlecode.common.*;

import static simpleplayer.RobotPlayer.*;

public class Builder {

    private static MapLocation lastTargetMovementLocation = null;

    private static boolean tryMoveToRandomNearbyTarget() throws GameActionException {
        if (lastTargetMovementLocation != null && locAtStartOfTurn.distanceSquaredTo(lastTargetMovementLocation) <= 2) {
            lastTargetMovementLocation = null;
        }
        if (lastTargetMovementLocation == null) {
            int x = locAtStartOfTurn.x;
            int y = locAtStartOfTurn.y;
            int radius = 8;
            int minX = Math.max(0, x - radius);
            int minY = Math.max(0, y - radius);
            int maxX = Math.min(rc.getMapWidth(), x + radius + 1);
            int maxY = Math.min(rc.getMapHeight(), y + radius + 1);
            int rangeX = maxX - minX;
            int rangeY = maxY - minY;
            lastTargetMovementLocation = new MapLocation(gen.nextInt(rangeX) + minX, gen.nextInt(rangeY) + minY);
        }
        pathfinder.move(lastTargetMovementLocation);
        return true;
    }

    public static void runBuilder() throws GameActionException {
        if (visibleAllies.length > 30 && rc.getRoundNum() < 1300 && rc.senseLead(locAtStartOfTurn) == 0) {
            rc.disintegrate();
        }
        tryHealingNearbyUnits();
        if (!adjacentToPrototypes()) {
            tryBuildingWatchtowers();
            tryMoveToRandomNearbyTarget();
            tryHealingNearbyUnits();
        }
    }

    private static boolean adjacentToPrototypes() {
        for (int i = actableAllies.length; --i >= 0; ) {
            if (actableAllies[i].mode == RobotMode.PROTOTYPE) {
                return true;
            }
        }
        return false;
    }

    private static void tryBuildingWatchtowers() throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }
        if (ourLead < RobotType.WATCHTOWER.buildCostLead) {
            return;
        }
        buildInLowestRubbleDirection(RobotType.WATCHTOWER);
    }

    private static void buildInLowestRubbleDirection(RobotType type) throws GameActionException {
        int minRubble = Integer.MAX_VALUE;
        Direction bestDir = null;
        for (final Direction d : Directions.RANDOM_DIRECTION_PERMUTATION) {
            if (rc.canBuildRobot(type, d)) {
                int rubble = rc.senseRubble(locAfterMovement.add(d));
                if (rubble < minRubble) {
                    minRubble = rubble;
                    bestDir = d;
                }
            }
        }
        if (bestDir != null) {
            rc.buildRobot(type, bestDir);
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
        RobotInfo weakestArchon = null;
        int weakestArchonHp = Integer.MAX_VALUE;
        for (RobotInfo ally : nearbyAllies) {
            RobotType allyType = ally.getType();
            if (ally.health == allyType.getMaxHealth(ally.level)) {
                continue;
            }
            if (!ally.type.isBuilding()) {
                continue;
            }
            if (allyType.canAttack()) {
                if (ally.health < weakestCombatUnitHp) {
                    weakestCombatUnit = ally;
                    weakestCombatUnitHp = ally.health;
                }
            } else if (allyType == RobotType.ARCHON) {
                if (ally.health < weakestArchonHp) {
                    weakestArchonHp = ally.health;
                    weakestArchon = ally;
                }
            } else {
                if (ally.health < weakestNonCombatUnitHp) {
                    weakestNonCombatUnitHp = ally.health;
                    weakestNoncombatUnit = ally;
                }
            }
        }
        if (weakestArchon != null) {
            rc.repair(weakestArchon.location);
        } else if (weakestCombatUnit != null) {
            rc.repair(weakestCombatUnit.location);
        } else if (weakestNoncombatUnit != null) {
            rc.repair(weakestNoncombatUnit.location);
        }
    }

}
