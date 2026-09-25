package com.example.kbanalyzer.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated knockback profile - the "extracted settings".
 * This is what /kbtester produces.
 */
public class KnockbackProfile {
    public int sampleCount;

    public double horizontalMin, horizontalMax, horizontalAvg;
    public double verticalMin, verticalMax, verticalAvg;
    public double distanceMin, distanceMax, distanceAvg;

    public int sprintCount;
    public int groundCount;
    public int airCount;

    public final List<KnockbackSample> samples = new ArrayList<>();

    public static KnockbackProfile fromSamples(List<KnockbackSample> samples) {
        KnockbackProfile p = new KnockbackProfile();
        if (samples.isEmpty()) return p;

        p.sampleCount = samples.size();
        p.samples.addAll(samples);

        double sumH = 0, sumV = 0, sumD = 0;
        double minH = Double.MAX_VALUE, maxH = 0;
        double minV = Double.MAX_VALUE, maxV = 0;
        double minD = Double.MAX_VALUE, maxD = 0;
        int distCount = 0;

        for (KnockbackSample s : samples) {
            sumH += s.horizontal;
            sumV += s.vertical;
            minH = Math.min(minH, s.horizontal);
            maxH = Math.max(maxH, s.horizontal);
            minV = Math.min(minV, s.vertical);
            maxV = Math.max(maxV, s.vertical);

            if (s.distance >= 0) {
                sumD += s.distance;
                minD = Math.min(minD, s.distance);
                maxD = Math.max(maxD, s.distance);
                distCount++;
            }

            if (s.sprinting) p.sprintCount++;
            if (s.onGround) p.groundCount++; else p.airCount++;
        }

        p.horizontalMin = minH;
        p.horizontalMax = maxH;
        p.horizontalAvg = sumH / samples.size();
        p.verticalMin = minV;
        p.verticalMax = maxV;
        p.verticalAvg = sumV / samples.size();

        if (distCount > 0) {
            p.distanceMin = minD;
            p.distanceMax = maxD;
            p.distanceAvg = sumD / distCount;
        } else {
            p.distanceMin = p.distanceMax = p.distanceAvg = -1;
        }

        return p;
    }

    public double getHorizontalMultiplier() {
        if (horizontalAvg <= 0) return 1.0;
        double expected = (sprintCount > sampleCount / 2) ? 0.6 : 0.4;
        return horizontalAvg / expected;
    }

    public double getVerticalMultiplier() {
        if (verticalAvg <= 0) return 1.0;
        return verticalAvg / 0.4;
    }

    public String getVerdict() {
        double hMul = getHorizontalMultiplier();
        double vMul = getVerticalMultiplier();

        if (Math.abs(hMul - 1.0) < 0.1 && Math.abs(vMul - 1.0) < 0.1) {
            return "&aVanilla knockback detected";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("&cModified knockback detected! ");
        if (hMul > 1.1) sb.append("&7Horizontal is &b").append(String.format("%.2fx", hMul)).append("&7 higher. ");
        else if (hMul < 0.9) sb.append("&7Horizontal is &b").append(String.format("%.2fx", hMul)).append("&7 lower. ");
        if (vMul > 1.1) sb.append("&7Vertical is &b").append(String.format("%.2fx", vMul)).append("&7 higher. ");
        else if (vMul < 0.9) sb.append("&7Vertical is &b").append(String.format("%.2fx", vMul)).append("&7 lower. ");
        return sb.toString();
    }
}
