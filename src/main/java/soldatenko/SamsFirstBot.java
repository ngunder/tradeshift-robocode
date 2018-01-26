package soldatenko;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.Event;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.RobotDeathEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.StatusEvent;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Vector;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.util.Comparator.comparingDouble;
import static java.util.Comparator.comparingLong;

public class SamsFirstBot extends AdvancedRobot {

    Radar radar = new Radar();
    GunControl gun = new GunControl();
    CruiseControl cruiseControl = new CruiseControl();

    LinkedList<Long> oldesRobotTime = new LinkedList<>();

    public void run() {
        radar.init();
        gun.init();

        while (true) {
            doNothing();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        radar.onScannedRobot(e);
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        radar.onRobotDeath(event);
    }

    @Override
    public void onStatus(StatusEvent e) {
        radar.onStatus(e);
        gun.onStatus(e);
        radar.spottedRobots.values().stream().min(comparingLong(r -> r.time)).ifPresent(r -> {
            long elapsed = getTime() - r.time;

            oldesRobotTime.add(elapsed);
            if (oldesRobotTime.size() > 30) {
                oldesRobotTime.removeFirst();
            }
        });
        cruiseControl.onStatus(e);
    }

    @Override
    public void onPaint(Graphics2D g) {
        g.setColor(Color.WHITE);
        radar.spottedRobots.values().forEach(r -> {
            g.setColor(Color.WHITE);
            double distance = getDistanceTo(r);
            double bulletPower = (distance < 150 ? 3 : (distance < 300 ? 1 : 0.1));
            double targetMoveTime = getTime() - r.time + distance / Rules.getBulletSpeed(bulletPower);

            Point2D pr = getPredictedPosition(r, targetMoveTime);
            g.fillOval((int) pr.getX(), (int) pr.getY(), 10, 10);
            g.drawLine((int) pr.getX(), (int) pr.getY(), (int) r.x, (int) r.y);
        });

        RobotStatus target = gun.getTarget();
        if (target != null) {
            Stroke oldStroke = g.getStroke();
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(3));
            g.drawLine((int) target.x - 50, (int) target.y, (int) target.x + 50, (int) target.y);
            g.drawLine((int) target.x, (int) target.y - 50, (int) target.x, (int) target.y + 50);
            g.setStroke(oldStroke);
        }

        radar.spottedRobots.values().stream().min(comparingLong(r -> r.time)).ifPresent(r -> {
            long elapsed = getTime() - r.time;
            g.setColor(Color.WHITE);
            g.drawString("Oldest seen " + elapsed + " turn ago.", 50, 100);
        });

        g.setColor(Color.WHITE);

        int x = 50;
        for (Long elapsed : oldesRobotTime) {
            g.drawRect(x, 50, 9, (int) (elapsed * 5));
            x += 10;
        }
        cruiseControl.onPaint(g);
    }

    boolean eq(double a, double b, double delta) {
        return abs(a - b) < delta;
    }

    <T extends Event> List<T> filter(List<Event> list, Class<T> klass) {
        ArrayList<T> result = new ArrayList<>();
        for (Event event : list) {
            if (event.getClass().isAssignableFrom(klass)) {
                result.add(klass.cast(event));
            }
        }
        return result;
    }

    private double radarTo(RobotStatus target) {
        double headingToEnemy = getHeading() + target.bearing;
        return Utils.normalRelativeAngleDegrees(headingToEnemy - getRadarHeading());
    }

    private boolean isParallel(double degree1, double degree2) {
        degree1 = Utils.normalAbsoluteAngleDegrees(degree1);
        degree2 = Utils.normalAbsoluteAngleDegrees(degree2);
        return eq(degree1, degree2, 5)
                || eq(degree1, Utils.normalAbsoluteAngleDegrees(degree2 + 180), 5);
    }

    private Point2D getPredictedPosition(RobotStatus r, double timeOffset) {
        double x = r.x + r.velocity * timeOffset * Math.sin(r.bodyHeadingDegree * Math.PI / 180);
        double y = r.y + r.velocity * timeOffset * Math.cos(r.bodyHeadingDegree * Math.PI / 180);

        return new Point2D.Double(x, y);
    }

    private double getDistanceTo(RobotStatus r) {
        return Point2D.distance(getX(), getY(), r.x, r.y);
    }

    class Radar {
        HashMap<String, RobotStatus> spottedRobots = new HashMap<>();
        double scanDirection = 1;

        LinkedHashMap<String, Double> enemyNames = new LinkedHashMap<>(5, 2, true);
        String robotNameToSpot = null;

        void init() {
            setAdjustRadarForGunTurn(true);
            setTurnRadarRight(scanDirection * Double.POSITIVE_INFINITY);
        }

        void onScannedRobot(ScannedRobotEvent e) {
            spottedRobots.put(e.getName(), new RobotStatus(e));
            enemyNames.put(e.getName(), getHeadingRadians() + e.getBearingRadians());

            if (e.getName().equals(robotNameToSpot) || (robotNameToSpot == null && enemyNames.size() == getOthers())) {
                robotNameToSpot = enemyNames.keySet().iterator().next();
                double angle = Utils.normalRelativeAngle(enemyNames.values().iterator().next()
                        - getRadarHeadingRadians());
                scanDirection = Math.signum(angle);
                setTurnRadarRightRadians(scanDirection * Double.POSITIVE_INFINITY);
            }
        }

        void onStatus(StatusEvent e) {

        }

        void onRobotDeath(RobotDeathEvent e) {
            spottedRobots.remove(e.getName());
            enemyNames.remove(e.getName());
            if (e.getName().equals(robotNameToSpot)) {
                robotNameToSpot = null;
            }
        }
    }

    class GunControl {
        String targetName;

        void init() {
            setAdjustGunForRobotTurn(true);
        }

        void onStatus(StatusEvent e) {
            for (HitRobotEvent event : filter(getAllEvents(), HitRobotEvent.class)) {
                targetName = event.getName();
            }
            Optional<RobotStatus> bestTarget = radar.spottedRobots.values()
                    .stream()
                    .filter(r -> Math.abs(getGunTurnToTarget(r)) < Rules.GUN_TURN_RATE)
                    .min(comparingDouble(SamsFirstBot.this::getDistanceTo));
            if (getGunHeat() == 0d && bestTarget.isPresent()) {
                double gunTurn = getGunTurnToTarget(bestTarget.get());
                setTurnGunRight(gunTurn);
                double distance = getDistanceTo(bestTarget.get());
                targetName = bestTarget.get().name;
                if (distance < 150) {
                    setFire(3);
                } else if (distance < 300) {
                    setFire(1);
                } else {
                    setFire(0.1);
                    targetName = null;
                }
            } else if (getGunHeat() > 0.8) {
                radar.spottedRobots.values()
                        .stream()
                        .min(comparingDouble(SamsFirstBot.this::getDistanceTo))
                        .ifPresent(r -> {
                            targetName = r.name;
                            setTurnGunRight(getGunTurnToTarget(r));
                        });
            } else {
                if (getTarget() == null) {
                    radar.spottedRobots.values()
                            .stream()
                            .min(comparingDouble(SamsFirstBot.this::getDistanceTo))
                            .ifPresent(r -> targetName = r.name);
                }

                RobotStatus target = getTarget();
                if (target != null) {
                    double gunTurn = getGunTurnToTarget(target);
                    setTurnGunRight(gunTurn);
                }
            }
        }

        private double getGunTurnToTarget(RobotStatus r) {
            double distance = getDistanceTo(r);
            double bulletPower = (distance < 150 ? 3 : (distance < 300 ? 1 : 0.1));
            double targetMoveTime = getTime() - r.time + distance / Rules.getBulletSpeed(bulletPower);

            Point2D point = getPredictedPosition(r, targetMoveTime);
            double headingToEnemy = Math.atan2(point.getX() - getX(), point.getY() - getY()) / Math.PI * 180;
            return Utils.normalRelativeAngleDegrees(headingToEnemy - getGunHeading());
        }

        RobotStatus getTarget() {
            return radar.spottedRobots
                    .values()
                    .stream()
                    .filter(r -> r.name.equals(targetName))
                    .findFirst()
                    .orElse(null);
        }
    }

    class CruiseControl {
        double positionX;
        double positionY;
        boolean isBackward;

        void onStatus(StatusEvent e) {
            if (abs(getDistanceRemaining()) < 5) {
                positionX = 50 + (getBattleFieldWidth() - 100) * Utils.getRandom().nextFloat();
                positionY = 50 + (getBattleFieldHeight() - 100) * Utils.getRandom().nextFloat();

                double directionRad = Math.atan2(positionX - getX(), positionY - getY());

                if (isBackward) {
                    setBack(Point2D.distance(getX(), getY(), positionX, positionY));
                    setTurnRightRadians(Utils.normalRelativeAngle(directionRad - getHeadingRadians() + PI));
                } else {
                    setTurnRightRadians(Utils.normalRelativeAngle(directionRad - getHeadingRadians()));
                    setAhead(Point2D.distance(getX(), getY(), positionX, positionY));
                }
            }
            for (HitRobotEvent event : filter(getAllEvents(), HitRobotEvent.class)) {
                setAhead(0);
                setTurnRight(0);
            }
            for (HitWallEvent event : filter(getAllEvents(), HitWallEvent.class)) {
                isBackward = !isBackward;
            }
        }

        void onPaint(Graphics2D g) {
            g.setColor(Color.GREEN);
            g.fillOval((int) positionX - 5, (int) positionY - 5, 10, 10);
            g.drawLine((int) positionX, (int) positionY, (int) getX(), (int) getY());
        }
    }

    class RobotStatus {
        double energy;
        double x;
        double y;
        double bodyHeadingDegree;
        double bearing;
        double velocity;
        String name;
        long time;

        public RobotStatus() {
        }

        RobotStatus(ScannedRobotEvent e) {
            energy = e.getEnergy();
            double angleRadians = e.getBearingRadians() + getHeadingRadians();
            x = (getX() + e.getDistance() * Math.sin(angleRadians));
            y = (getY() + e.getDistance() * Math.cos(angleRadians));
            bodyHeadingDegree = e.getHeading();
            bearing = e.getBearing();
            velocity = e.getVelocity();

            name = e.getName();
            time = e.getTime();
        }

        RobotStatus(SamsFirstBot robot) {
            energy = robot.getEnergy();
            x = robot.getX();
            y = robot.getY();
            bodyHeadingDegree = robot.getHeading();
            bearing = 0;
            velocity = robot.getVelocity();
            name = robot.getName();
            time = robot.getTime();

        }
    }
}

