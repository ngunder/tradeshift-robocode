package bgr;

import robocode.*;

public class BreakBot extends Robot {
	
	static double foundHeading = 0;
	
	public void run() {
        while (true) {
            ahead(200);
            turnLeft(foundHeading);
            turnGunRight(foundHeading);
            back(100);
            turnGunRight(360);
        }
    }
 
    public void onScannedRobot(ScannedRobotEvent e) {
        fire(5);
        foundHeading = e.getHeading();
    }
}
