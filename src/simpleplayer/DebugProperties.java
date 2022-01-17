package simpleplayer;

import battlecode.common.Team;

import static simpleplayer.RobotPlayer.us;

public class DebugProperties {

    public static boolean LOCAL_TESTING_ENABLED = false;

    public static int soldierThreshold = 20;
    public static int minerTileMantained = 10;

    static {
        if (LOCAL_TESTING_ENABLED) {
            String param = us == Team.A ? System.getProperty("bc.testing.team-a-param") : System.getProperty("bc.testing.team-b-param");
            String soldierThresholdProp = null;
            String minerTileMantainedProp = null;
            if (param != null && param.length() > 0) {
                String[] tokens = param.split(",");
                soldierThresholdProp = tokens[0];
                minerTileMantainedProp = tokens[1];
            }
            soldierThreshold = 20;
            if (soldierThresholdProp != null) {
                soldierThreshold = Integer.parseInt(soldierThresholdProp);
            }
            minerTileMantained = 10;
            if (minerTileMantainedProp != null) {
                minerTileMantained = Integer.parseInt(minerTileMantainedProp);
            }
        }
    }

}
