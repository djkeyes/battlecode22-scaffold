package simpleplayer;

import battlecode.common.*;

import static simpleplayer.RobotPlayer.*;

// Largely copied from https://github.com/IvanGeffner/battlecode2021

public abstract class BFS {

    final int BYTECODE_REMAINING = 2500;
    final int GREEDY_TURNS = 4;

    Pathfinding path = new Pathfinding();
    BooleanGrid visitedLocation = new BooleanGrid();

    int turnsGreedy = 0;

    MapLocation currentTarget = null;

    void reset() {
        turnsGreedy = 0;
        visitedLocation.reset();
    }

    void update(MapLocation target) {
        if (currentTarget == null || target.distanceSquaredTo(currentTarget) > 0) {
            reset();
        } else --turnsGreedy;
        currentTarget = target;
        visitedLocation.setTrue(rc.getLocation());
    }

    void activateGreedy() {
        turnsGreedy = GREEDY_TURNS;
    }

    void initTurn() {
        path.initTurn();
    }

    void move(MapLocation target) throws GameActionException {
        move(target, false);
    }

    void move(MapLocation target, boolean greedy) throws GameActionException {
        if (target == null) return;
        if (!rc.isMovementReady()) return;
        if (rc.getLocation().distanceSquaredTo(target) == 0) return;

        update(target);

        if (!greedy && turnsGreedy <= 0) {

            //System.err.println("Using bfs");
            Direction dir = getBestDir(target);
            if (dir != null && !visitedLocation.get(rc.getLocation().add(dir))) {
                // we've been here before, so just try moving straight
                if (!rc.canMove(dir)) return;
                rc.move(dir);
                locAfterMovement = locAtStartOfTurn.add(dir);
                return;
            } else activateGreedy();
        }

        if (Clock.getBytecodesLeft() >= BYTECODE_REMAINING) {
            //System.err.println("Using greedy");
            //System.out.println("Before pathfinding " + Clock.getBytecodeNum());
            path.move(target);
            //System.out.println("After pathfinding " + Clock.getBytecodeNum());
            --turnsGreedy;
        }
    }

    abstract Direction getBestDir(MapLocation target) throws GameActionException;


}