package simpleplayer;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

import static simpleplayer.RobotPlayer.*;

public class Grid5x5Strategy extends GridStrategy {

    public static int BLOCK_SIZE = 5;

    public static int MAX_SHARED_ARRAY_IDX_M1 = 63;

    public static int BITS_PER_BLOCK = 2;
    public static int BLOCKS_PER_ARRAY_IDX = 8;

    private int rows, cols;
    private boolean initialized = false;

    private void init() {
        cols = rc.getMapWidth() / BLOCK_SIZE;
        rows = rc.getMapHeight() / BLOCK_SIZE;
        initialized = true;
    }

    public void updatePerTurn() throws GameActionException {
        if (!initialized) {
            init();
        }

        // need to check local tiles for:
        // - lead
        // - workers
        // - enemies
        // - allies

        MapLocation loc = rc.getLocation();
        int gridX = loc.x / BLOCK_SIZE;
        int gridY = loc.y / BLOCK_SIZE;
        int left = gridX * BLOCK_SIZE;
        int bot = gridY * BLOCK_SIZE;
        int offsetX = loc.x - left;
        int offsetY = loc.y - bot;
        // TODO: if we are a laboratory, our vision range is large enough to scan several 5x5 blocks (or more if they're
        //  smaller blocks)
        // TODO: if we are a worker, we can only see the entire block if we are standing in the middle. However,
        //  even if we are near the edge, it might be worthwhile to report based on incomplete information.
        if (myType.visionRadiusSquared == 20) {
            int dx = offsetX - 2;
            int dy = offsetY - 2;
            if (dx * dx + dy * dy >= 5) {
                return;
            }
        }
        scan(gridX, gridY, left, bot, true);
    }

    @Override
    public void setAttackLocation(MapLocation loc) throws GameActionException {
        if (!initialized) {
            init();
        }

        int gridX = loc.x / BLOCK_SIZE;
        int gridY = loc.y / BLOCK_SIZE;

        int gridIdx = gridX * rows + gridY;
        int commIdx = MAX_SHARED_ARRAY_IDX_M1 - gridIdx / BLOCKS_PER_ARRAY_IDX;
        int commOffset = gridIdx % BLOCKS_PER_ARRAY_IDX;

        int value = rc.readSharedArray(commIdx);
        // 1 denotes needing an attacker
        value = value | (1 << (BITS_PER_BLOCK * commOffset + 1));
        rc.writeSharedArray(commIdx, value);
    }

    private void scan(int gridX, int gridY, int left, int bot, boolean areWeInBLock) throws GameActionException {
        RobotInfo[] allRobotsInBlock = rc.senseNearbyRobots(new MapLocation(left + 2, bot + 2), 18, null);
        int numOurWorkers = 0;
        int numOurAttackers = 0;
        int numTheirAttackers = 0;
        boolean theyHaveNonAttackers = false;
        for (int i = allRobotsInBlock.length; --i >= 0; ) {
            RobotInfo robot = allRobotsInBlock[i];
            if (robot.team == us) {
                if (robot.type == RobotType.MINER) {
                    ++numOurWorkers;
                } else if (robot.type.canAttack()) {
                    ++numOurAttackers;
                }
            } else {
                if (robot.type.canAttack() || robot.type == RobotType.ARCHON) {
                    ++numTheirAttackers;
                } else {
                    theyHaveNonAttackers = true;
                }
            }
        }
        if (areWeInBLock) {
            if (myType == RobotType.MINER) {
                ++numOurWorkers;
            } else if (myType.canAttack()) {
                ++numOurAttackers;
            }
        }

        int heightm1 = rc.getMapHeight() - 1;
        int widthm1 = rc.getMapWidth() - 1;
        double totalResources = 0;
        for (int i = Math.min(widthm1, left + BLOCK_SIZE - 1) - left; --i >= 0; ) {
            for (int j = Math.min(heightm1, bot + BLOCK_SIZE - 1) - bot; --j >= 0; ) {
                MapLocation loc = new MapLocation(left + i, bot + j);
                int lead = rc.senseLead(loc);
                int gold = rc.senseGold(loc);
                if (lead >= 1) {
                    // the base lead only gives us 3 lead every 50 turns, so only count it as that.
                    lead--;
                    // if there's a lot of lead, we can only mine a small amount of it
                    lead = Math.min(lead, 45);
                    totalResources += lead + 3. / 50.;
                }
                totalResources += gold;
            }
        }
        double crowdedFactor = 0.75;
        boolean tooCrowded = allRobotsInBlock.length > BLOCK_SIZE * BLOCK_SIZE * crowdedFactor;
        boolean needsWorker = totalResources > numOurWorkers * 5 && !tooCrowded;
        boolean needsAttacker = (numOurAttackers < numTheirAttackers) || (numTheirAttackers == 0 && theyHaveNonAttackers);

        int gridIdx = gridX * rows + gridY;
        int commIdx = MAX_SHARED_ARRAY_IDX_M1 - gridIdx / BLOCKS_PER_ARRAY_IDX;
        int commOffset = gridIdx % BLOCKS_PER_ARRAY_IDX;

        int value = rc.readSharedArray(commIdx);
        if (needsWorker) {
            // 0 denotes needing a worker, the default
            value = value & ~(1 << (BITS_PER_BLOCK * commOffset));
        } else {
            // 1 denotes sufficient workers
            value = value | (1 << (BITS_PER_BLOCK * commOffset));
        }
        if (needsAttacker) {
            // 1 denotes needing an attacker
            value = value | (1 << (BITS_PER_BLOCK * commOffset + 1));
        } else {
            // 0 denotes sufficient attackers, the default
            value = value & ~(1 << (BITS_PER_BLOCK * commOffset + 1));
        }
        rc.writeSharedArray(commIdx, value);
    }

    @Override
    public MapLocation findClosestMiningLocation() throws GameActionException {
        if (!initialized) {
            init();
        }
        int gridX = locAtStartOfTurn.x / BLOCK_SIZE;
        int gridY = locAtStartOfTurn.y / BLOCK_SIZE;

        // first check adjacent
        if (gridX > 0) {
            int x = gridX - 1;
            int y = gridY;
            int idx = x * rows + y;
            int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
            int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
            int value = rc.readSharedArray(commIdx);
            boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
            if (needsWorker) {
                return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
            }
        }
        if (gridX < cols - 1) {
            int x = gridX + 1;
            int y = gridY;
            int idx = x * rows + y;
            int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
            int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
            int value = rc.readSharedArray(commIdx);
            boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
            if (needsWorker) {
                return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
            }
        }
        if (gridY > 0) {
            int x = gridX;
            int y = gridY - 1;
            int idx = x * rows + y;
            int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
            int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
            int value = rc.readSharedArray(commIdx);
            boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
            if (needsWorker) {
                return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
            }
        }
        if (gridY < rows - 1) {
            int x = gridX;
            int y = gridY + 1;
            int idx = x * rows + y;
            int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
            int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
            int value = rc.readSharedArray(commIdx);
            boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
            if (needsWorker) {
                return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
            }
        }
        // then check diagonal
        if (gridX > 0) {
            if (gridY > 0) {
                int x = gridX - 1;
                int y = gridY - 1;
                int idx = x * rows + y;
                int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                int value = rc.readSharedArray(commIdx);
                boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
                if (needsWorker) {
                    return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                }
            }
            if (gridY < rows - 1) {
                int x = gridX - 1;
                int y = gridY + 1;
                int idx = x * rows + y;
                int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                int value = rc.readSharedArray(commIdx);
                boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
                if (needsWorker) {
                    return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                }
            }
        }
        if (gridX < cols - 1) {
            if (gridY > 0) {
                int x = gridX + 1;
                int y = gridY - 1;
                int idx = x * rows + y;
                int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                int value = rc.readSharedArray(commIdx);
                boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
                if (needsWorker) {
                    return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                }
            }
            if (gridY < rows - 1) {
                int x = gridX + 1;
                int y = gridY + 1;
                int idx = x * rows + y;
                int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                int value = rc.readSharedArray(commIdx);
                boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
                if (needsWorker) {
                    return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                }
            }
        }
        // then go in a spiral
        for (int r = 2; r < Math.max(Math.max(gridX + 1, gridY + 1), Math.max(cols - gridX, rows - gridY)); ++r) {
            for (int i = 2 * r - 1; --i >= 0; ) {
                {
                    int x = gridX - r + i;
                    int y = gridY - r;
                    if (x >= 0 && y >= 0 && x < cols && y < rows) {
                        int idx = x * rows + y;
                        int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                        int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                        int value = rc.readSharedArray(commIdx);
                        boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
                        if (needsWorker) {
                            return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                        }
                    }
                }
                {
                    int x = gridX + r;
                    int y = gridY - r + i;
                    if (x >= 0 && y >= 0 && x < cols && y < rows) {
                        int idx = x * rows + y;
                        int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                        int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                        int value = rc.readSharedArray(commIdx);
                        boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
                        if (needsWorker) {
                            return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                        }
                    }
                }
                {
                    int x = gridX + r - i;
                    int y = gridY + r;
                    if (x >= 0 && y >= 0 && x < cols && y < rows) {
                        int idx = x * rows + y;
                        int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                        int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                        int value = rc.readSharedArray(commIdx);
                        boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
                        if (needsWorker) {
                            return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                        }
                    }
                }
                {
                    int x = gridX - r;
                    int y = gridY + r - i;
                    if (x >= 0 && y >= 0 && x < cols && y < rows) {
                        int idx = x * rows + y;
                        int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                        int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                        int value = rc.readSharedArray(commIdx);
                        boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
                        if (needsWorker) {
                            return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                        }
                    }
                }
            }
        }

        return null;
    }

    @Override
    public MapLocation findClosestAttackLocation() throws GameActionException {
        if (!initialized) {
            init();
        }
        int gridX = locAtStartOfTurn.x / BLOCK_SIZE;
        int gridY = locAtStartOfTurn.y / BLOCK_SIZE;

        // first check adjacent
        if (gridX > 0) {
            int x = gridX - 1;
            int y = gridY;
            int idx = x * rows + y;
            int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
            int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
            int value = rc.readSharedArray(commIdx);
            boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
            if (needsAttacker) {
                return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
            }
        }
        if (gridX < cols - 1) {
            int x = gridX + 1;
            int y = gridY;
            int idx = x * rows + y;
            int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
            int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
            int value = rc.readSharedArray(commIdx);
            boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
            if (needsAttacker) {
                return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
            }
        }
        if (gridY > 0) {
            int x = gridX;
            int y = gridY - 1;
            int idx = x * rows + y;
            int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
            int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
            int value = rc.readSharedArray(commIdx);
            boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
            if (needsAttacker) {
                return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
            }
        }
        if (gridY < rows - 1) {
            int x = gridX;
            int y = gridY + 1;
            int idx = x * rows + y;
            int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
            int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
            int value = rc.readSharedArray(commIdx);
            boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
            if (needsAttacker) {
                return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
            }
        }
        // then check diagonal
        if (gridX > 0) {
            if (gridY > 0) {
                int x = gridX - 1;
                int y = gridY - 1;
                int idx = x * rows + y;
                int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                int value = rc.readSharedArray(commIdx);
                boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
                if (needsAttacker) {
                    return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                }
            }
            if (gridY < rows - 1) {
                int x = gridX - 1;
                int y = gridY + 1;
                int idx = x * rows + y;
                int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                int value = rc.readSharedArray(commIdx);
                boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
                if (needsAttacker) {
                    return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                }
            }
        }
        if (gridX < cols - 1) {
            if (gridY > 0) {
                int x = gridX + 1;
                int y = gridY - 1;
                int idx = x * rows + y;
                int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                int value = rc.readSharedArray(commIdx);
                boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
                if (needsAttacker) {
                    return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                }
            }
            if (gridY < rows - 1) {
                int x = gridX + 1;
                int y = gridY + 1;
                int idx = x * rows + y;
                int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                int value = rc.readSharedArray(commIdx);
                boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
                if (needsAttacker) {
                    return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                }
            }
        }
        // then go in a spiral
        for (int r = 2; r < Math.max(Math.max(gridX + 1, gridY + 1), Math.max(cols - gridX, rows - gridY)); ++r) {
            for (int i = 2 * r - 1; --i >= 0; ) {
                {
                    int x = gridX - r + i;
                    int y = gridY - r;
                    if (x >= 0 && y >= 0 && x < cols && y < rows) {
                        int idx = x * rows + y;
                        int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                        int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                        int value = rc.readSharedArray(commIdx);
                        boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
                        if (needsAttacker) {
                            return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                        }
                    }
                }
                {
                    int x = gridX + r;
                    int y = gridY - r + i;
                    if (x >= 0 && y >= 0 && x < cols && y < rows) {
                        int idx = x * rows + y;
                        int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                        int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                        int value = rc.readSharedArray(commIdx);
                        boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
                        if (needsAttacker) {
                            return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                        }
                    }
                }
                {
                    int x = gridX + r - i;
                    int y = gridY + r;
                    if (x >= 0 && y >= 0 && x < cols && y < rows) {
                        int idx = x * rows + y;
                        int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                        int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                        int value = rc.readSharedArray(commIdx);
                        boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
                        if (needsAttacker) {
                            return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                        }
                    }
                }
                {
                    int x = gridX - r;
                    int y = gridY + r - i;
                    if (x >= 0 && y >= 0 && x < cols && y < rows) {
                        int idx = x * rows + y;
                        int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                        int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                        int value = rc.readSharedArray(commIdx);
                        boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
                        if (needsAttacker) {
                            return new MapLocation(x * BLOCK_SIZE + BLOCK_SIZE / 2, y * BLOCK_SIZE + BLOCK_SIZE / 2);
                        }
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void showDebug() throws GameActionException {
        if (!Debug.SHOULD_SHOW_VISUALIZATIONS) {
            return;
        }
        if (!initialized) {
            init();
        }
        for (int idx = 0, c = 0; c < cols; ++c) {
            for (int r = 0; r < rows; ++r, ++idx) {
                idx = c * rows + r;
                int commIdx = MAX_SHARED_ARRAY_IDX_M1 - idx / BLOCKS_PER_ARRAY_IDX;
                int commOffset = idx % BLOCKS_PER_ARRAY_IDX;
                int value = rc.readSharedArray(commIdx);
                boolean needsWorker = (((value >> (BITS_PER_BLOCK * commOffset)) & 1) == 0);
                boolean needsAttacker = (((value >> (BITS_PER_BLOCK * commOffset + 1)) & 1) > 0);
                MapLocation loc1 = new MapLocation(c * BLOCK_SIZE, r * BLOCK_SIZE);
                MapLocation loc2 = new MapLocation(c * BLOCK_SIZE + 1, r * BLOCK_SIZE);
                if (needsWorker) {
                    rc.setIndicatorDot(loc1, 0, 255, 0);
                } else {
                    rc.setIndicatorDot(loc1, 255, 0, 0);
                }
                if (needsAttacker) {
                    rc.setIndicatorDot(loc2, 0, 0, 255);
                } else {
                    rc.setIndicatorDot(loc2, 127, 0, 32);
                }
            }
        }

    }
}
