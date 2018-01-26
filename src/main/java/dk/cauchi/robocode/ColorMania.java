package dk.cauchi.robocode;

import robocode.*;
import robocode.util.Utils;

import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;

// API help : http://robocode.sourceforge.net/docs/robocode/robocode/Robot.html

/**
 * ColorMania - a robot by (your name here)
 */
public class ColorMania extends AdvancedRobot
{
    // Color
    private int[] colorCode = {254,0,0};
    private int[] rgbUpDown = {-1,1,1};


    // directions
    private int direction = 1;
    private int directionCount = 0;

    private int turnCount = 0;
    private int turnDirection = 1;



    private double lastY = 400;
    private double lastX = 400;

    private int xMin = 100;
    private int xMax = 700;

    private int yMin = 100;
    private int yMax = 500;

    private boolean dontMove = false;
    private boolean dontShoot = false;
    private boolean linearTargeting = true;
    private double speedAdjust = 0.01;

    public double getSpeedAdjust() {
        return speedAdjust;
    }


    private void standardMove() {
        if (isDontMove())
            return;

        double x = getX();
        double y = getY();
        double speedAdjust = 1;

        if (x > xMax && x > lastX)  {
            speedAdjust = getSpeedAdjust();
        } else if (x < xMin && x < lastX) {
            speedAdjust = getSpeedAdjust();
        }


        if (y > yMax && y > lastY) {
            speedAdjust = getSpeedAdjust();
        } else if (y < yMin && y < lastY) {
            speedAdjust = getSpeedAdjust();
        }

        if (turnCount++ > 200) {
            turnCount = directionCount = ThreadLocalRandom.current().nextInt(0, 100);
            turnDirection = turnDirection * -1;
        }

        if (directionCount++ > 300) {
            directionCount = ThreadLocalRandom.current().nextInt(0, 200);
            direction = direction * -1;
        }



        setAhead(direction*100*speedAdjust);
        setTurnLeftRadians(turnDirection * Math.PI * 0.5);

        lastX = x;
        lastY = y;
    }


    public static void main(String[] args) {
        ColorMania dd = new ColorMania();
        while (true) {
            dd.slideColors();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {}
        }
    }


    public void slideColors(){

        //R
        slide();

        Color body = new Color(colorCode[0], colorCode[1], colorCode[2]);
        Color turret = new Color(0, 0, 0);
        Color radar = new Color(colorCode[1], colorCode[2], colorCode[0]);
        setColors(body, turret, radar);

    }

    private void slide() {
        for (int i = 0; i < colorCode.length; i++) {
            colorCode[i] = colorCode[i]+ 5 * i * rgbUpDown[i];
            if (colorCode[i]>254 || colorCode[i]<0) {
                rgbUpDown[i] = rgbUpDown[i] * -1;
                colorCode[i] = colorCode[i]+ 5 * 1 *rgbUpDown[i];
            }
        }
    }


    public void init() {
        setLinearTargeting(true);

    }

    /**
     * run: ColorMania's default behavior
     */
    public void run() {
         // Robot main loop
        out.println("let's go!!!!!qertqwert");
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        int counter = 0;
        setBodyColor(new Color(colorCode[0], colorCode[1], colorCode[2]));

        //Colorist colorist = new Colorist(this);
        //colorist.start();

        init();

        turnRadarRightRadians(Double.POSITIVE_INFINITY);



        while(true) {
            slideColors();
            standardMove();
            out.println("HOW CAN THIS EVER HAPPEN!! (with more that one bot)");
            turnRadarRightRadians(Double.POSITIVE_INFINITY);

            break;
            //changeColor();
            // Replace the next 4 lines with any behavior you would like

//            setTurnGunRightRadians());
//            fireBullet(1);

            //this.slideColors();
            //changeColor();
            //turnGunRight(360);
            //changeColor();
            //back(100);
            //changeColor();
            //turnGunRight(360);
        }
    }

    /**
     * onScannedRobot: What to do when you see another robot
     */
    public void onScannedRobot(ScannedRobotEvent e) {
        slideColors();


        // Absolute bearing to target
        double absoluteBearingToBot =  getHeadingRadians() + e.getBearingRadians();


        // Radar stuff to narrow in and stay on target
        // Subtract current radar heading to get turn required
        double radarTurn = absoluteBearingToBot - getRadarHeadingRadians();
        setTurnRadarRightRadians(1.9 * Utils.normalRelativeAngle(radarTurn));


        // so where is this dude going?
        double bVelocity = e.getVelocity();


        double bBearing = e.getBearingRadians(); // -PI and PI - angle compared to my position

        //between 0 and 2PI, 0 = up, 0.5 PI = left, PI = down, 1.5 PI = right
        double bHeading = e.getHeadingRadians();

        out.println("absoluteBearingToBot" + absoluteBearingToBot/Math.PI);
        out.println("bVelocity" + bVelocity);
        out.println("bBearing " + bBearing/Math.PI);
        out.println("bHeading " + bHeading/Math.PI);

        double bX = getX() + e.getDistance() * Math.sin(absoluteBearingToBot);
        double bY = getY() + e.getDistance() * Math.cos(absoluteBearingToBot);
        out.println("bot position: " + (int) bX + ","+ (int) bY);


        // turn canon to where bot is
        double canonTurnToCurrentBotPos  = absoluteBearingToBot - getGunHeadingRadians();


        double canonTurnLiniearTargeting =
                Utils.normalRelativeAngle(
                        canonTurnToCurrentBotPos +
                        (e.getVelocity() * Math.sin(e.getHeadingRadians() - absoluteBearingToBot) / 13.0)
                );


        if (isLinearTargeting())
            setTurnGunRightRadians(canonTurnLiniearTargeting);
        else
            setTurnGunRightRadians((canonTurnToCurrentBotPos + canonTurnLiniearTargeting)/2);



        // moderate bullet power based on distance to target
        if (!isDontShoot())
            setFire((500 - e.getDistance())/90);



        standardMove();

    }


    /**
     * onHitByBullet: What to do when you're hit by a bullet
     */
    public void onHitByBullet(HitByBulletEvent e) {
        // Replace the next line with any behavior you would like

        slideColors();

    }

    /**
     * onHitWall: What to do when you hit a wall
     */
    public void onHitWall(HitWallEvent e) {
        out.println("I should never...");
        slideColors();
        out.print(e.getBearingRadians());
        if (e.getBearingRadians() > Math.PI) {
            setTurnLeftRadians(Math.PI * 0.5);
        } else {
            setTurnLeftRadians(Math.PI * -0.5);
        }
    }

    public boolean isDontMove() {
        return dontMove;
    }

    public void setDontMove(boolean dontMove) {
        this.dontMove = dontMove;
    }

    public boolean isDontShoot() {
        return dontShoot;
    }

    public void setDontShoot(boolean dontShoot) {
        this.dontShoot = dontShoot;
    }

    public boolean isLinearTargeting() {
        return linearTargeting;
    }

    public void setLinearTargeting(boolean linearTargeting) {
        this.linearTargeting = linearTargeting;
    }

    class Colorist extends Thread
    {
        ColorMania robot;

        Colorist(ColorMania robot) {
            this.robot = robot;
        }

        public void run() {
            out.println("let's color crazy!!");

            while (true) {
                robot.slideColors();
                try {
                    Thread.sleep(500);
                    Thread.yield();
                } catch (InterruptedException e) {}
            }
        }

    }

}
