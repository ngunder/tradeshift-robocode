package nicks;
import robocode.*;
import java.awt.Color;

// API help : http://robocode.sourceforge.net/docs/robocode/robocode/Robot.html

/**
 * DemoNick - a robot by Nicholas Gunder
 */
public class DemoNick extends Robot
{
	/**
	 * run: DemoNick's default behavior
	 */
	public void run() {
		// Initialization of the robot should be put here

		// After trying out your robot, try uncommenting the import at the top,
		// and the next line:

		setColors(Color.red,Color.red,Color.red); // body,gun,radar

		// Robot main loop
		while(true) {
			// Replace the next 4 lines with any behavior you would like
			ahead(50);
			turnRadarRight(360);
			turnRight(30);
			ahead(50);
			turnLeft(30);
		}
	}

	/**
	 * onScannedRobot: What to do when you see another robot
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
        double turnGunAmt = (getHeading() + e.getBearing()) - getGunHeading();
        turnGunRight(turnGunAmt);
        if (e.getDistance() < 100) {
            fire(3);
        } else {
            fire(1);
        }
	}

	/**
	 * onHitByBullet: What to do when you're hit by a bullet
	 */
	public void onHitByBullet(HitByBulletEvent e) {
		// Replace the next line with any behavior you would like
        back(300);
		turnRight(45);
		back(300);
	}
	
	/**
	 * onHitWall: What to do when you hit a wall
	 */
	public void onHitWall(HitWallEvent e) {
		// Replace the next line with any behavior you would like
		back(20);
	}	
}
