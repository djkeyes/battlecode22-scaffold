package simpleplayer;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

public abstract class GridStrategy {

        public static GridStrategy instance = new Grid5x5Strategy();

        public abstract void updatePerTurn() throws GameActionException;
        public abstract MapLocation findClosestMiningLocation() throws GameActionException;
        public abstract MapLocation findClosestAttackLocation() throws GameActionException;
        public abstract void showDebug() throws GameActionException;
}
