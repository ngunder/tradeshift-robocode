package soldatenko;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.Event;
import robocode.HitByBulletEvent;
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

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.util.Comparator.comparingDouble;
import static java.util.Comparator.comparingLong;

public class SamsFirstBot extends AdvancedRobot {

    Radar radar = new Radar();
    GunControl gun = new GunControl();
    ShootDetector shootDetector = new ShootDetector();

    LinkedList<Long> oldesRobotTime = new LinkedList<>();

    public void run() {
        // setColors(Color.red,Color.blue,Color.green); // body,gun,radar

        radar.init();
        gun.init();

        while (true) {

            ahead(100);

            back(100);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        radar.onScannedRobot(e);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        setTurnRight(90);
    }

    public void onHitWall(HitWallEvent e) {
        if (Utils.getRandom().nextFloat() > 0.5) {
            back(20);
        } else {
            ahead(20);
        }
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        radar.onRobotDeath(event);
    }

    @Override
    public void onStatus(StatusEvent e) {
        System.out.println("T1: " + getTime() + " T2: " + e.getTime());
        getAllEvents().forEach(e1 ->
                System.out.println("  " + e1.getClass().getSimpleName() + " time: " + e1.getTime() + " " + e1.getPriority())
        );
        System.out.println("                            Heat: " + getGunHeat());
        System.out.println("                          Energy: " + getEnergy());

        radar.onStatus(e);
        gun.onStatus(e);
        radar.spottedRobots.values().stream().min(comparingLong(r -> r.time)).ifPresent(r -> {
            long elapsed = getTime() - r.time;

            oldesRobotTime.add(elapsed);
            if (oldesRobotTime.size() > 30) {
                oldesRobotTime.removeFirst();
            }
        });
        shootDetector.onStatus(e);
    }

    @Override
    public void onPaint(Graphics2D g) {
        g.setColor(Color.WHITE);
        radar.spottedRobots.values().forEach(r -> {
            g.setColor(Color.WHITE);
            g.drawOval((int) r.x, (int) r.y, 10, 10);
            g.setColor(Color.WHITE);
            Point2D pr = getPredictedPoint(r);
            g.fillOval((int) pr.getX(), (int) pr.getY(), 10, 10);
            g.drawString(r.name, (float) r.x + 50, (float) r.y);
        });

        RobotStatus target = gun.getTarget();
        if (target != null) {
            g.setColor(Color.RED);
            g.drawLine((int) target.x - 50, (int) target.y, (int) target.x + 50, (int) target.y);
            g.drawLine((int) target.x, (int) target.y - 50, (int) target.x, (int) target.y + 50);
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
        shootDetector.onPaint(g);
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

    private Point2D getPredictedPoint(RobotStatus r) {
        double distance = getDistanceTo(r);
        double timeToReachTarget = distance / Rules.getBulletSpeed(1);
        double targetMoveTime = getTime() - r.time + timeToReachTarget;

        return getPredictedPosition(r, targetMoveTime);
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
            Optional<RobotStatus> bestTarget = radar.spottedRobots.values()
                    .stream()
                    .filter(r -> Math.abs(getGunTurnToTarget(r)) < Rules.GUN_TURN_RATE)
                    .min(comparingDouble(SamsFirstBot.this::getDistanceTo));
            if (getGunHeat() == 0d && bestTarget.isPresent()) {
                double gunTurn = getGunTurnToTarget(bestTarget.get());
                setTurnGunRight(gunTurn);
                if (getDistanceTo(bestTarget.get()) < 100) {
                    setFire(1);
                    System.out.println("                       Fire 1");
                } else {
                    setFire(0.1);
                    System.out.println("                       Fire 0.1");
                }
                targetName = bestTarget.get().name;
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
            Point2D point = getPredictedPoint(r);
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

    class ShootDetector {
        HashMap<String, RobotStatus> robots = new HashMap<>();
        LinkedList<ShootWave> waves = new LinkedList<>();

        void onStatus(StatusEvent e) {
            Vector<Event> events = getAllEvents();

//            // todo cancel hit by bullet waves
//            for (BulletHitBulletEvent event : filter(events, BulletHitBulletEvent.class)) {
//                Bullet hitBullet = event.getHitBullet();
//
////                for (ShootWave wave : waves) {
////                    if (eq(wave.x, hitBullet.getX(), 5)
////                            && eq(wave.y, hitBullet.getY(), 5)
////                            && eq(wave.energyDrop, hitBullet.getPower(), 0.1)) {
////                        wave.color = Color.YELLOW;
////                        System.out.println("Wave " + wave.x + "," + wave.y + " p: " + wave.energyDrop);
////                    }
////                }
//
//
//                Bullet bullet = event.getBullet();
//                System.out.println("Hit bullet x: " + hitBullet.getX() + " y: " + hitBullet.getY() + " name: " + hitBullet.getName());
//                System.out.println("    bullet x: " + bullet.getX() + " y: " + bullet.getY() + " name: " + bullet.getName());
//            }

            // Register collision with me
            for (HitRobotEvent event : filter(events, HitRobotEvent.class)) {
                RobotStatus enemy = robots.get(event.getName());
                if (enemy != null) {
                    enemy.energy -= 0.6d;
                }
            }

            // register enemy energy loss by my hits
            for (BulletHitEvent event : filter(events, BulletHitEvent.class)) {
                RobotStatus enemy = robots.get(event.getName());
                if (enemy != null) {
                    enemy.energy -= Rules.getBulletDamage(event.getBullet().getPower());
                }
            }

            // Detect energyDrop
            List<ScannedRobotEvent> scans = filter(events, ScannedRobotEvent.class);
            for (ScannedRobotEvent event : scans) {
                RobotStatus oldStatus = robots.get(event.getName());
                if (oldStatus != null) {
                    detect(oldStatus, new RobotStatus(event));
                }
            }
            for (ScannedRobotEvent event : scans) {
                robots.put(event.getName(), new RobotStatus(event));
            }

            // Remove dead robot's waves
            for (RobotDeathEvent event : filter(events, RobotDeathEvent.class)) {
                robots.remove(event.getName());
                waves.removeIf(w -> w.name.equals(event.getName()));
            }
        }

        void detect(RobotStatus oldStatus, RobotStatus newStatus) {
            double energyDrop = oldStatus.energy - newStatus.energy;
            if (energyDrop >= 0.1 && energyDrop <= 3) {

                ArrayList<RobotStatus> shooterEnemies = new ArrayList<>(robots.values());
                shooterEnemies.removeIf(r -> newStatus.name.equals(r.name));
                shooterEnemies.add(new RobotStatus(SamsFirstBot.this));
                for (RobotStatus enemy : shooterEnemies) {
                    ShootWave wave = new ShootWave();
                    wave.name = oldStatus.name;
                    wave.x = oldStatus.x;
                    wave.y = oldStatus.y;
                    wave.time = oldStatus.time;
                    wave.energyDrop = energyDrop;
                    wave.directionRad = Math.atan2(enemy.x - oldStatus.x, enemy.y - oldStatus.y);
                    wave.color = Color.RED;

                    waves.add(wave);

                    double distance = Point2D.distance(oldStatus.x, oldStatus.y, enemy.x, enemy.y);
                    double timeToReachTarget = distance / Rules.getBulletSpeed(energyDrop);
                    Point2D predictedPosition = getPredictedPosition(enemy, timeToReachTarget);

                    ShootWave wave2 = new ShootWave();
                    wave.name = oldStatus.name;
                    wave2.x = oldStatus.x;
                    wave2.y = oldStatus.y;
                    wave2.time = oldStatus.time;
                    wave2.energyDrop = energyDrop;
                    wave2.directionRad = Math.atan2(predictedPosition.getX() - oldStatus.x, predictedPosition.getY() - oldStatus.y);
                    wave2.color = Color.GREEN;
                    waves.add(wave2);
                }
            }
        }

        void onPaint(Graphics2D g) {
            Iterator<ShootWave> iterator = waves.iterator();
            while (iterator.hasNext()) {
                ShootWave wave = iterator.next();
                double radius = (getTime() - wave.time) * Rules.getBulletSpeed(wave.energyDrop);
                double degree = (wave.directionRad / Math.PI * 180) - 90; // Arc's 0 is different.
                g.setColor(wave.color);
                Arc2D.Double arc = new Arc2D.Double(wave.x - radius, wave.y - radius, radius * 2, radius * 2,
                        degree - 5, 10, Arc2D.OPEN);
                g.draw(arc);
                if (radius > max(getBattleFieldWidth(), getBattleFieldHeight())) {
                    iterator.remove();
                }
            }
            System.out.println("Waves: " + waves.size());
        }
    }

    class ShootWave {
        String name;
        double directionRad;
        double time;
        double x;
        double y;
        double energyDrop;
        Color color;
    }
}

