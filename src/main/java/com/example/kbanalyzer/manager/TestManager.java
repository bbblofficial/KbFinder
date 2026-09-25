package com.example.kbanalyzer.manager;

import com.example.kbanalyzer.KnockbackAnalyzer;
import com.example.kbanalyzer.data.KnockbackProfile;
import com.example.kbanalyzer.data.KnockbackSample;
import com.example.kbanalyzer.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The /kbtester engine.
 *
 * Automatically extracts the knockback settings of the current server
 * by sampling incoming S12PacketEntityVelocity packets while the player
 * is getting hit.
 *
 * It does NOT send any custom packets to the server, so it's completely
 * undetectable by anti-cheats (client-side only).
 */
public class TestManager {

    public enum Phase {
        IDLE,
        WAITING_FOR_HITS,
        COMPLETE
    }

    private Phase phase = Phase.IDLE;
    private final List<KnockbackSample> samples = new ArrayList<>();
    private int targetSamples = 10;
    private long startTime = 0;
    private long lastSampleTime = 0;
    private static final long SAMPLE_TIMEOUT_MS = 30_000;
    private static final long AUTO_COMPLETE_IDLE_MS = 5_000;

    private boolean autoStop = true;

    public void reset() {
        phase = Phase.IDLE;
        samples.clear();
        targetSamples = 10;
        startTime = 0;
        lastSampleTime = 0;
    }

    public void startTest(int targetSamples) {
        this.samples.clear();
        this.targetSamples = targetSamples;
        this.phase = Phase.WAITING_FOR_HITS;
        this.startTime = System.currentTimeMillis();
        this.lastSampleTime = 0;

        ChatUtil.sendMessage("&b&m----------------------------------------------------");
        ChatUtil.sendMessage("&b[KBTester] &7Automatic knockback extraction started.");
        ChatUtil.sendMessage("&7Collecting up to &b" + targetSamples + " &7samples.");
        ChatUtil.sendMessage("&7Just play normally &7- get hit by players.");
        ChatUtil.sendMessage("&7Use &b/kbcancel &7to stop early.");
        ChatUtil.sendMessage("&b&m----------------------------------------------------");
        KnockbackAnalyzer.logger.info("[KBTester] Started, target={}", targetSamples);
    }

    public void handleLocalVelocity(S12PacketEntityVelocity packet) {
        if (phase != Phase.WAITING_FOR_HITS) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        int motionX = packet.getMotionX();
        int motionY = packet.getMotionY();
        int motionZ = packet.getMotionZ();

        double horizontal = Math.sqrt(motionX * motionX + motionZ * motionZ) / 8000.0D;
        double vertical = Math.abs(motionY) / 8000.0D + 0.03D;

        if (horizontal < 0.05 && vertical < 0.1) return;

        KnockbackSample sample = new KnockbackSample();
        sample.horizontal = horizontal;
        sample.vertical = vertical;
        sample.sprinting = mc.thePlayer.isSprinting();
        sample.onGround = mc.thePlayer.onGround;
        sample.posX = mc.thePlayer.posX;
        sample.posZ = mc.thePlayer.posZ;

        samples.add(sample);
        lastSampleTime = System.currentTimeMillis();

        ChatUtil.sendMessage("&b[KBTester] &7Sample &b" + samples.size() + "&7/&b" + targetSamples
                + " &7-> H=&b" + fmt(horizontal) + " &7V=&b" + fmt(vertical)
                + " &7[" + (sample.sprinting ? "&bSprint" : "&7Walk") + "&7/&b"
                + (sample.onGround ? "Ground" : "Air") + "&7]");

        if (samples.size() >= targetSamples) {
            complete();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (phase != Phase.WAITING_FOR_HITS) return;

        long now = System.currentTimeMillis();

        if (now - startTime > SAMPLE_TIMEOUT_MS) {
            ChatUtil.sendMessage("&b[KBTester] &cTimeout reached, analyzing partial data...");
            complete();
            return;
        }

        if (autoStop && lastSampleTime > 0 && now - lastSampleTime > AUTO_COMPLETE_IDLE_MS) {
            ChatUtil.sendMessage("&b[KBTester] &7No new hits for 5s, analyzing...");
            complete();
        }
    }

    private void complete() {
        if (phase == Phase.COMPLETE) return;
        phase = Phase.COMPLETE;

        if (samples.isEmpty()) {
            ChatUtil.sendMessage("&c[KBTester] No samples collected. Get hit and try again.");
            phase = Phase.IDLE;
            return;
        }

        KnockbackProfile profile = KnockbackProfile.fromSamples(samples);
        printProfile(profile);

        phase = Phase.IDLE;
    }

    private void printProfile(KnockbackProfile p) {
        ChatUtil.sendMessage("&b&m----------------------------------------------------");
        ChatUtil.sendMessage("&b&lKNOCKBACK PROFILE EXTRACTED");
        ChatUtil.sendMessage("&b&m----------------------------------------------------");
        ChatUtil.sendMessage("&7Samples: &b" + p.sampleCount);
        ChatUtil.sendMessage("");
        ChatUtil.sendMessage("&b&lHorizontal KB &7(blocks/tick)");
        ChatUtil.sendMessage("  &7Min: &b" + fmt(p.horizontalMin)
                + "  &7Max: &b" + fmt(p.horizontalMax)
                + "  &7Avg: &b" + fmt(p.horizontalAvg));
        ChatUtil.sendMessage("");
        ChatUtil.sendMessage("&b&lVertical KB &7(blocks/tick)");
        ChatUtil.sendMessage("  &7Min: &b" + fmt(p.verticalMin)
                + "  &7Max: &b" + fmt(p.verticalMax)
                + "  &7Avg: &b" + fmt(p.verticalAvg));
        ChatUtil.sendMessage("");
        if (p.distanceAvg >= 0) {
            ChatUtil.sendMessage("&b&lDistance");
            ChatUtil.sendMessage("  &7Min: &b" + fmt4(p.distanceMin)
                    + "  &7Max: &b" + fmt4(p.distanceMax)
                    + "  &7Avg: &b" + fmt4(p.distanceAvg));
            ChatUtil.sendMessage("");
        }
        ChatUtil.sendMessage("&b&lStance");
        ChatUtil.sendMessage("  &7Sprinting: &b" + p.sprintCount + "/" + p.sampleCount
                + " &7(" + (p.sprintCount * 100 / p.sampleCount) + "%)");
        ChatUtil.sendMessage("  &7On Ground: &b" + p.groundCount + "/" + p.sampleCount
                + " &7(" + (p.groundCount * 100 / p.sampleCount) + "%)");
        ChatUtil.sendMessage("");
        ChatUtil.sendMessage("&b&lExtracted Multipliers &7(vs vanilla 1.8.9)");
        ChatUtil.sendMessage("  &7Horizontal: &b" + String.format("%.3fx", p.getHorizontalMultiplier()));
        ChatUtil.sendMessage("  &7Vertical:   &b" + String.format("%.3fx", p.getVerticalMultiplier()));
        ChatUtil.sendMessage("");
        ChatUtil.sendMessage("&b&lVerdict");
        ChatUtil.sendMessage("  " + p.getVerdict());
        ChatUtil.sendMessage("&b&m----------------------------------------------------");
    }

    public void cancel() {
        if (phase == Phase.WAITING_FOR_HITS) {
            ChatUtil.sendMessage("&b[KBTester] &7Stopped early, analyzing collected data...");
            complete();
        }
    }

    public boolean isRunning() {
        return phase == Phase.WAITING_FOR_HITS;
    }

    private static String fmt(double d) {
        return String.format("%.6f", d);
    }

    private static String fmt4(double d) {
        return String.format("%.4f", d);
    }
}
