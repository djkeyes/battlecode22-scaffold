package simpleplayer;

import battlecode.common.MapLocation;

import static simpleplayer.RobotPlayer.rc;

/**
 * Provides utility functions to reason about map symmetry.
 */
public final class MapSymmetry {

    public static final int VERTICAL_SYMMETRY = 0;
    // note: 180 degree rotation is at index 1, which is convenient for some algorithms
    public static final int ROTATIONAL_SYMMETRY = 1;
    public static final int HORIZONTAL_SYMMETRY = 2;

    public static final boolean[] isSymmetryPossible = {true, true, true};

    public static void init() {
        if (rc.getMapWidth() != rc.getMapHeight()) {
            isSymmetryPossible[1] = false;
        }
        // TODO: communicate other information based on terrain scouting and the message array.
    }

    public static MapLocation[] getAllSymmetricCoords(final MapLocation location) {
        int numSymmetries = (isSymmetryPossible[0] ? 1 : 0) + (isSymmetryPossible[1] ? 1 : 0) + (isSymmetryPossible[2] ? 1 : 0);
        MapLocation[] result = new MapLocation[numSymmetries];
        int tail = 0;
        if (isSymmetryPossible[0]) {
            result[tail++] = getVerticalSymmetricCoords(location);
        }
        if (isSymmetryPossible[1]) {
            result[tail++] = getRotationalSymmetricCoords(location);
        }
        if (isSymmetryPossible[2]) {
            result[tail] = getHorizontalSymmetricCoords(location);
        }

        return result;
    }

    public static MapLocation getSymmetricCoords(final MapLocation location,
                                                 final int symmetry) {
        switch (symmetry) {
            case VERTICAL_SYMMETRY:
                return getVerticalSymmetricCoords(location);
            case HORIZONTAL_SYMMETRY:
                return getHorizontalSymmetricCoords(location);
            case ROTATIONAL_SYMMETRY:
                return getRotationalSymmetricCoords(location);
        }
        return getVerticalSymmetricCoords(location);
    }

    public static MapLocation getVerticalSymmetricCoords(final MapLocation location) {
        return new MapLocation(rc.getMapWidth() - location.x - 1, location.y);
    }

    public static MapLocation getHorizontalSymmetricCoords(final MapLocation location) {
        return new MapLocation(location.x, rc.getMapHeight() - location.y - 1);
    }

    public static MapLocation getRotationalSymmetricCoords(final MapLocation location) {
        return new MapLocation(rc.getMapWidth() - location.x - 1, rc.getMapHeight() - location.y - 1);
    }
}