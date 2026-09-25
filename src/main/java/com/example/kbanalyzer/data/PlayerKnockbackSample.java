package com.example.kbanalyzer.data;

/**
 * A single knockback sample for a tracked remote player.
 */
public class PlayerKnockbackSample {
    public double horizontal;
    public double vertical;
    public double rawX;
    public double rawY;
    public double rawZ;
    public String pos;
    public long timestamp;
    public boolean wasHurt;

    public PlayerKnockbackSample() {
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("PlayerKB[x=%.4f y=%.4f z=%.4f H=%.6f V=%.6f hurt=%s]",
                rawX, rawY, rawZ, horizontal, vertical, wasHurt);
    }
}
