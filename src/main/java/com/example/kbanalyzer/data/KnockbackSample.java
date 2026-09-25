package com.example.kbanalyzer.data;

/**
 * A single knockback sample from the local player.
 */
public class KnockbackSample {
    public double horizontal;
    public double vertical;
    public double posX;
    public double posZ;
    public double distance = -1.0D;
    public boolean sprinting;
    public boolean onGround;
    public long timestamp;

    public KnockbackSample() {
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("KB[H=%.6f V=%.6f sprint=%s ground=%s dist=%.4f]",
                horizontal, vertical, sprinting, onGround, distance);
    }
}
