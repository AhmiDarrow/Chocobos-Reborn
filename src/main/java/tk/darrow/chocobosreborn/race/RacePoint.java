package tk.darrow.chocobosreborn.race;

public record RacePoint(double x, double y, double z) {
	public RacePoint subtract(RacePoint other) {
		return new RacePoint(x - other.x, y - other.y, z - other.z);
	}

	public double distanceToSqr(RacePoint other) {
		double dx = x - other.x;
		double dy = y - other.y;
		double dz = z - other.z;
		return dx * dx + dy * dy + dz * dz;
	}
}
