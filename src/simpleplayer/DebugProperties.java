package simpleplayer;

import battlecode.common.Team;

import static simpleplayer.RobotPlayer.us;

public class DebugProperties {

    public static boolean LOCAL_TESTING_ENABLED = false;

    public static int soldierThreshold = 50;
    public static int minerTileMantained = 10;
    public static boolean shouldRush = true;

    static {
        if (LOCAL_TESTING_ENABLED) {
            String param = us == Team.A ? System.getProperty("bc.testing.team-a-param") : System.getProperty("bc.testing.team-b-param");
            String soldierThresholdProp = null;
            String minerTileMantainedProp = null;
            String shouldRushProp = null;
            if (param != null && param.length() > 0) {
                String[] tokens = param.split(",");
                soldierThresholdProp = tokens[0];
                minerTileMantainedProp = tokens[1];
                shouldRushProp = tokens[2];
            }
            if (soldierThresholdProp != null) {
                soldierThreshold = Integer.parseInt(soldierThresholdProp);
            }
            if (minerTileMantainedProp != null) {
                minerTileMantained = Integer.parseInt(minerTileMantainedProp);
            }
            if (shouldRushProp != null) {
                shouldRush = Boolean.parseBoolean(shouldRushProp);
            }
        }
    }

}
