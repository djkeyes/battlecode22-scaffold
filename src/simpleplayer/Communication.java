package simpleplayer;

import battlecode.common.*;

import static simpleplayer.RobotPlayer.*;

public class Communication {
    // Recommended usage:
    // We have 64 shorts available, or 1024 bits total. We can assign some data to each bit, and read/write as
    // necessary. Of course, binary data is best, but if your data takes up more space, it is what it is.
    //
    // If your data is less than 16 bits, it's generally best to align it to a single short, so you don't need to
    // read/write multiple entries. But, again, if things are too dense, you may have to pack it across boundaries.
    //
    // Here are some things that might be important:
    // - Our archon positions (fits in 2^12 bits each, 48 total)
    // - Their archon positions (fits in 2^12 bits each, 48 total)
    // - Unit counts (7 unit types to choose, less than 2^12 of each, 84 total)
    // - Attack/defence locations (2^12 per location)
    // - Precomputed map distances
    // - Map symmetry data (3 kinds of symmetry, 2^2)
    //
    // This file provides a simple implementation of several of these, but it may be work reimplementing in the future.

    private static final int ARCHON_INDEX_OFFSET = 0;
    private static final int ARCHON_INDEX_LENGTH = 4;
    private static final int ENEMY_ARCHON_INDEX_OFFSET = ARCHON_INDEX_OFFSET + ARCHON_INDEX_LENGTH;
    private static final int ENEMY_ARCHON_INDEX_LENGTH = 4;
    private static final int UNIT_COUNT_OFFSET = ENEMY_ARCHON_INDEX_OFFSET + ENEMY_ARCHON_INDEX_LENGTH;
    private static final int UNIT_COUNT_LENGTH = 14;

    public static int myArchonIndex = -1;

    public static void initArchonIndex() throws GameActionException {
        for (myArchonIndex = 0; myArchonIndex < 4; ++myArchonIndex) {
            MapLocation otherArchonLoc = readInterestingLocationWithCoarseExpiry(myArchonIndex);
            if (otherArchonLoc == null) {
                break;
            }
        }
    }

    public static void writeArchonLocation() throws GameActionException {
        if (myArchonIndex == -1) {
            initArchonIndex();
        }
        writeInterestingLocationWithCoarseExpiry(myArchonIndex, locAtStartOfTurn);
    }

    public static MapLocation[] readArchonLocations() throws GameActionException {
        MapLocation a0 = readInterestingLocationWithCoarseExpiry(0);
        MapLocation a1 = readInterestingLocationWithCoarseExpiry(1);
        MapLocation a2 = readInterestingLocationWithCoarseExpiry(2);
        MapLocation a3 = readInterestingLocationWithCoarseExpiry(3);
        int numReported = (a0 != null ? 1 : 0) + (a1 != null ? 1 : 0) + (a2 != null ? 1 : 0) + (a3 != null ? 1 : 0);
        MapLocation[] result = new MapLocation[numReported];
        int tail = 0;
        if (a0 != null) {
            result[tail++] = a0;
        }
        if (a1 != null) {
            result[tail++] = a1;
        }
        if (a2 != null) {
            result[tail++] = a2;
        }
        if (a3 != null) {
            result[tail] = a3;
        }
        return result;
    }

    public static void writeEnemyArchonLocation(RobotInfo enemyArchon) throws GameActionException {
        // This isn't documented anywhere, but archons always seem to get IDs in the range 2-9, and they always seems
        // to alternate between teams (might be a quirk of the map generator). This means we can easily hash enemy
        // archons to unique ids in [0,3].
        // TODO: might want to check if this has already been stored, otherwise the write is a waste.
        int enemyArchonIndex = enemyArchon.ID / 2 - 1;
        writeInterestingLocationWithCoarseExpiry(ENEMY_ARCHON_INDEX_OFFSET + enemyArchonIndex, enemyArchon.location);
    }

    public static MapLocation[] readEnemyArchonLocations() throws GameActionException {
        MapLocation a0 = readInterestingLocationWithCoarseExpiry(ENEMY_ARCHON_INDEX_OFFSET + 0);
        MapLocation a1 = readInterestingLocationWithCoarseExpiry(ENEMY_ARCHON_INDEX_OFFSET + 1);
        MapLocation a2 = readInterestingLocationWithCoarseExpiry(ENEMY_ARCHON_INDEX_OFFSET + 2);
        MapLocation a3 = readInterestingLocationWithCoarseExpiry(ENEMY_ARCHON_INDEX_OFFSET + 3);
        int numReported = (a0 != null ? 1 : 0) + (a1 != null ? 1 : 0) + (a2 != null ? 1 : 0) + (a3 != null ? 1 : 0);
        MapLocation[] result = new MapLocation[numReported];
        int tail = 0;
        if (a0 != null) {
            result[tail++] = a0;
        }
        if (a1 != null) {
            result[tail++] = a1;
        }
        if (a2 != null) {
            result[tail++] = a2;
        }
        if (a3 != null) {
            result[tail] = a3;
        }
        return result;
    }

    private static void writeInterestingLocationSparse(int index, MapLocation location) throws GameActionException {
        // writes a location using 2^12 bits, leaving 4 bits unused
        // actually, this uses slightly less than 2^12
        // offset by 1, so that 0 represents null values
        int encoded = location.x * GameConstants.MAP_MAX_HEIGHT + location.y + 1;
        rc.writeSharedArray(index, encoded);
    }

    private static MapLocation readInterestingLocationSparse(int index) throws GameActionException {
        int encoded = rc.readSharedArray(index);
        if (encoded == 0) {
            return null;
        }
        encoded -= 1;
        int y = encoded % GameConstants.MAP_MAX_HEIGHT;
        int x = encoded / GameConstants.MAP_MAX_HEIGHT;
        return new MapLocation(x, y);
    }

    // number of coarse timestamps over the course of the game
    private static final int TIMESTAMP_GRANULARITY =
            0xFFFF / (GameConstants.MAP_MAX_WIDTH * GameConstants.MAP_MAX_HEIGHT);

    private static void writeInterestingLocationWithCoarseExpiry(int index, MapLocation location) throws GameActionException {
        // writes a location using around 2^12 bits, using the remaining bits to store a timestamp which expires
        // after about 100-200 turns
        int timestamp = TIMESTAMP_GRANULARITY * rc.getRoundNum() / (GameConstants.GAME_MAX_NUMBER_OF_ROUNDS + 1);

        int encoded = (location.x * GameConstants.MAP_MAX_HEIGHT + location.y) * TIMESTAMP_GRANULARITY + timestamp + 1;
        rc.writeSharedArray(index, encoded);
    }

    private static MapLocation readInterestingLocationWithCoarseExpiry(int index) throws GameActionException {
        int encoded = rc.readSharedArray(index);
        if (encoded == 0) {
            return null;
        }
        encoded -= 1;
        int timestamp = encoded % TIMESTAMP_GRANULARITY;
        int curTimestamp = TIMESTAMP_GRANULARITY * rc.getRoundNum() / (GameConstants.GAME_MAX_NUMBER_OF_ROUNDS + 1);
        if (curTimestamp - timestamp > 1) {
            // this event was broadcasted at least 2 epochs ago, so probably it's out of date
            return null;
        }

        encoded /= TIMESTAMP_GRANULARITY;
        int y = encoded % GameConstants.MAP_MAX_HEIGHT;
        int x = encoded / GameConstants.MAP_MAX_HEIGHT;
        return new MapLocation(x, y);
    }

    private static final int ODD_UNIT_COUNT_OFFSET = 7;

    // mantain 2 unit counts: 1 for the current turn, one for the last turn
    // (we could also sample less frequently to save bytecounts/comms space if we need)
    // Each count is 7 12-bit numbers (can go up to 4096), although in real games, they will probably be in low 100s.
    // If we pack them like this:
    // aaaaaaaaaaaabbbb|bbbbbbbbcccccccc|ccccdddddddddddd|eeeeeeeeeeeeffff|ffffffffgggggggg|gggg
    // Then they fit into 6 shorts/
    // On the otherhand, if we pack them like this:
    // aaaaaaaaaaaa....|bbbbbbbbbbbb....|cccccccccccc....|dddddddddddd....|eeeeeeeeeeee...|ffffffffffff....|gggggggggggg....|
    // Then they fit into 7 shorts, but editing a single value only requires accessing one short, which helps other
    // bots.
    public static void clearUnitCounts() throws GameActionException {
        // this method should only be called by an archon, to clear counts
        int curOffset = 0;
        if (rc.getRoundNum() % 2 == 1) {
            curOffset = 7;
        }

        if (archonCount > 1) {
            // need to check if it's been cleared already
            int reportedCount = rc.readSharedArray(UNIT_COUNT_OFFSET + curOffset + RobotType.ARCHON.ordinal());
            // if we're first, the reported count should be last turn's archon count, which is equal to or greater to
            // the current archon count. Or it might be the first turn of the game, so it's 0.
            // if we're the second or third or fourth archon, that won't hold, since the counts will start coming in.
            if (reportedCount > 0 && reportedCount < archonCount) {
                return;
            }
        }
        for (int i=0; i < 7; ++i) {
            rc.writeSharedArray(UNIT_COUNT_OFFSET + curOffset + i, 0);
        }
    }

    public static void incrementUnitCount() throws GameActionException {
        int curOffset = 0;
        if (rc.getRoundNum() % 2 == 1) {
            curOffset = 7;
        }

        int oldValue = rc.readSharedArray(UNIT_COUNT_OFFSET + curOffset + myType.ordinal());
        rc.writeSharedArray(UNIT_COUNT_OFFSET + curOffset + myType.ordinal(), oldValue + 1);
    }

    public static int[] getLastTurnUnitCount() throws GameActionException {
        int[] result = new int[7];
        int lastOffset = 0;
        if (rc.getRoundNum() % 2 == 0) {
            lastOffset = 7;
        }

        for (int i = 0; i < 7; ++i) {
            result[i] = rc.readSharedArray(UNIT_COUNT_OFFSET + lastOffset + i);
        }
        return result;
    }
}
