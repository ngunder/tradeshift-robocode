package bgr;

import robocode.*;
import robocode.util.Utils;

import java.awt.Color;

public class BreakBot extends Robot {
	
	static double foundHeading = 0;
	
	public void run() {
		setColors(Color.CYAN,Color.MAGENTA,Color.ORANGE);
		
		setAdjustGunForRobotTurn(true);
		setAdjustRadarForGunTurn(true);	
		
        while (true) {
            ahead(100);
            turnRadarRight(360);
        }
    }
 
    public void onScannedRobot(ScannedRobotEvent e) {
    		turnRadarRight(2.0 * Utils.normalRelativeAngleDegrees(getHeading() + e.getBearing() - getRadarHeading()));
        turnGunRight((getHeading() + e.getBearing()) - getGunHeading());
        fire(5);
        
    }
    
    public void onHitByBullet(HitByBulletEvent e) {
    		turnRight(90);
    }
    
    public void onHitWall(HitWallEvent e) {
    		back(100);
    		turnLeft(90);
	}
}
