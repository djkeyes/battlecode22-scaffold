package simpleplayer;

import battlecode.common.GameConstants;
import battlecode.common.MapLocation;

/**
 * A grid that stores booleans, backed by longs.
 * <p>
 * In the current battlecode implementation, allocating a bool[64][64] costs around 4000 bytecodes, but allocating a
 * long[64] costs around 70. Of course, it's slightly more expensive to get and set values in the array, so only use
 * this if you do sparse array access. And of course, this trick only works for booleans--it wouldn't work if you
 * need a double[][].
 */
public final class BooleanGrid {

    private static final int BITS_IN_BACKING_DATATYPE = 64;
    private long[] grid;

    public BooleanGrid() {
        // assumes evenly divisible
        reset();
    }

    public void reset() {
        grid = new long[(GameConstants.MAP_MAX_HEIGHT * GameConstants.MAP_MAX_WIDTH + BITS_IN_BACKING_DATATYPE - 1) / BITS_IN_BACKING_DATATYPE];
    }

    public boolean get(final int x, final int y) {
        final int gridIdx = y * GameConstants.MAP_MAX_WIDTH + x;
        final int elementIdx = gridIdx / BITS_IN_BACKING_DATATYPE;
        final int bitIdx = gridIdx % BITS_IN_BACKING_DATATYPE;
        return ((grid[elementIdx] >> bitIdx) & 1L) != 0;
    }

    public boolean get(final MapLocation loc) {
        final int gridIdx = loc.y * GameConstants.MAP_MAX_WIDTH + loc.x;
        final int elementIdx = gridIdx / BITS_IN_BACKING_DATATYPE;
        final int bitIdx = gridIdx % BITS_IN_BACKING_DATATYPE;
        return ((grid[elementIdx] >> bitIdx) & 1L) != 0;
    }

    public void setTrue(final int x, final int y) {
        final int gridIdx = y * GameConstants.MAP_MAX_WIDTH + x;
        final int elementIdx = gridIdx / BITS_IN_BACKING_DATATYPE;
        final long bitIdx = gridIdx % BITS_IN_BACKING_DATATYPE;
        grid[elementIdx] |= (1L << bitIdx);
    }

    public void setTrue(final MapLocation loc) {
        final int gridIdx = loc.y * GameConstants.MAP_MAX_WIDTH + loc.x;
        final int elementIdx = gridIdx / BITS_IN_BACKING_DATATYPE;
        final long bitIdx = gridIdx % BITS_IN_BACKING_DATATYPE;
        grid[elementIdx] |= (1L << bitIdx);
    }

    public void setFalse(final int x, final int y) {
        final int gridIdx = y * GameConstants.MAP_MAX_WIDTH + x;
        final int elementIdx = gridIdx / BITS_IN_BACKING_DATATYPE;
        final long bitIdx = gridIdx % BITS_IN_BACKING_DATATYPE;
        grid[elementIdx] &= ~(1L << bitIdx);
    }

    public void setFalse(final MapLocation loc) {
        final int gridIdx = loc.y * GameConstants.MAP_MAX_WIDTH + loc.x;
        final int elementIdx = gridIdx / BITS_IN_BACKING_DATATYPE;
        final long bitIdx = gridIdx % BITS_IN_BACKING_DATATYPE;
        grid[elementIdx] &= ~(1L << bitIdx);
    }
}