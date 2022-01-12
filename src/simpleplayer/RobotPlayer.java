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

    public static final BFS pathfinder = new BFSDroid();

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

                    pathfinder.initTurn();

                    switch (myType) {
                        case ARCHON:
                            Archon.runArchon();
                            break;
                        case MINER:
                            Miner.runMiner();
                            break;
                        case SOLDIER:
                            Soldier.runSoldier();
                            break;
                        case LABORATORY:
                            break;
                        case WATCHTOWER:
                            Watchtower.runWatchtower();
                            break;
                        case BUILDER:
                            Builder.runBuilder();
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

}
