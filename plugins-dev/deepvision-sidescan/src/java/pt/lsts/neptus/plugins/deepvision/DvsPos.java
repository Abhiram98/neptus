package pt.lsts.neptus.plugins.deepvision;

public class DvsPos {
    public static final int SIZE = 24; // Bytes

    private double latitude;    // [rad] WGS84
    private double longitude;   // [rad] WGS84
    private float speed;        // [m/s]
    private float heading;      // [rad]

    private long timestamp;     // milliseconds

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getHeading() {
        return heading;
    }

    public void setHeading(float heading) {
        this.heading = heading;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getLatitudeDegrees() {
        return Math.toDegrees(latitude);
    }

    public double getLongitudeDegrees() {
        return Math.toDegrees(longitude);
    }

}
