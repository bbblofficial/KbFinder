package com.example.kbanalyzer.manager;

import com.example.kbanalyzer.KnockbackAnalyzer;
import com.example.kbanalyzer.data.KnockbackProfile;
import com.example.kbanalyzer.data.KnockbackSample;
import com.example.kbanalyzer.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /kbtester passive engine.
 *
 * Fully automatic knockback extraction.
 *
 * It listens to EVERY S12PacketEntityVelocity packet sent by the server
 * (for any nearby player). Each such packet represents a server-applied
 * velocity change - i.e. a knockback event - and can be used to reverse
 * engineer the server's knockback configuration.
 *
 * The mod never sends a single packet, never moves the player, never
 * attacks anything. It only reads. This makes it 100% undetectable.
 */
public class TestManager {

    public enum Phase {
        IDLE,
        OBSERVING,
        COMPLETE
    }

    private Phase phase = Phase.IDLE;
    private final List<KnockbackSample> samples = new ArrayList<>();
    private int targetSamples = 25;
    private long startTime = 0;
    private long lastSampleTime = 0;

    private static final long OBSERVE_TIMEOUT_MS = 120_000L;   // 2 minutes max
    private static final long IDLE_COMPLETE_MS  = 15_000L;     // 15s with no hits -> finish

    // Signature dedup: ignore the same packet value arriving twice in a row
    private final Map<Integer, Long> lastHitTimePerEntity = new HashMap<>();

    public void reset() {
        phase = Phase.IDLE;
        samples.clear();
        targetSamples = 25;
        startTime = 0;
        lastSampleTime = 0;
        lastHitTimePerEntity.clear();
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    public void startTest(int targetSamples) {
        this.samples.clear();
        this.targetSamples = targetSamples;
        this.phase = Phase.OBSERVING;
        this.startTime = System.currentTimeMillis();
        this.lastSampleTime = 0;
        this.lastHitTimePerEntity.clear();

        ChatUtil.sendMessage("&b&m----------------------------------------------------");
        ChatUtil.sendMessage("&b[KBTester] &7Passive extraction started.");
        ChatUtil.sendMessage("&7Do &bnothing&7. Just play normally.");
        ChatUtil.sendMessage("&7The mod will silently observe nearby hits.");
        ChatUtil.sendMessage("&7Target: &b" + targetSamples + " &7samples.");
        ChatUtil.sendMessage("&7Use &b/kbcancel &7to finish early.");
        ChatUtil.sendMessage("&b&m----------------------------------------------------");

        KnockbackAnalyzer.logger.info("[KBTester] Passive observation started, target={}", targetSamples);
    }

    public void cancel() {
        if (phase == Phase.OBSERVING) {
            ChatUtil.sendMessage("&b[KBTester] &7Finishing early, analyzing collected data...");
            complete();
        }
    }

    public boolean isRunning() {
        return phase == Phase.OBSERVING;
    }

    // ============================================================
    // PASSIVE PACKET OBSERVATION
    // ============================================================

    /**
     * Called for EVERY S12PacketEntityVelocity the server sends.
     * We do NOT distinguish between self and others here - both are valid
     * knockback data points. But we ignore non-player entities to avoid
     * noise from item/XP/mob movement.
     */
    public void observeVelocity(S12PacketEntityVelocity packet) {
        if (phase != Phase.OBSERVING) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;

        int entityId = packet.getEntityID();

        Entity entity = mc.theWorld.getEntityByID(entityId);
        if (entity == null) return;

        // Only players produce real PvP knockback we care about
        if (!(entity instanceof EntityPlayer)) return;

        // Extract velocity from packet (fixed-point *8000 encoding)
        double vx = packet.getMotionX() / 8000.0D;
        double vy = packet.getMotionY() / 8000.0D;
        double vz = packet.getMotionZ() / 8000.0D;

        double horizontal = Math.sqrt(vx * vx + vz * vz);
        double vertical = vy;

        // ---- Filter out non-knockback velocities ----
        // Real player-vs-player knockback lands in these ranges on virtually
        // every 1.8.9 server (including modified-KB servers):
        //   horizontal: 0.15 - 3.0
        //   vertical:   0.05 - 1.0 (mostly upward)
        // Anything outside is movement / explosion / misc.
        if (horizontal < 0.15D || horizontal > 3.0D) return;
        if (vertical < 0.05D || vertical > 1.0D) return;

        // Dedup: some servers send near-identical packets in the same tick
        long now = System.currentTimeMillis();
        Long last = lastHitTimePerEntity.get(entityId);
        if (last != null && now - last < 50L) return;
        lastHitTimePerEntity.put(entityId, now);

        // ---- Record the sample ----
        KnockbackSample s = new KnockbackSample();
        s.horizontal = horizontal;
        s.vertical = vertical;
        s.sprinting = false;    // unknown for remote players
        s.onGround = false;     // unknown for remote players
        s.posX = entity.posX;
        s.posZ = entity.posZ;
        s.timestamp = now;

        samples.add(s);
        lastSampleTime = now;

        String src = (mc.thePlayer != null && entityId == mc.thePlayer.getEntityId())
                ? "&a(you)" : "&7" + entity.getName();

        ChatUtil.sendMessage("&b[KBTester] &7KB captured from " + src
                + " &7-> H=&b" + fmt(horizontal) + " &7V=&b" + fmt(vertical)
                + " &7[" + samples.size() + "/" + targetSamples + "]");

        if (samples.size() >= targetSamples) {
            complete();
        }
    }

    // ============================================================
    // TICK / TIMEOUT / AUTO-COMPLETE
    // ============================================================

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (phase != Phase.OBSERVING) return;

        long now = System.currentTimeMillis();

        if (now - startTime > OBSERVE_TIMEOUT_MS) {
            ChatUtil.sendMessage("&b[KBTester] &cTimeout (2 min), analyzing...");
            complete();
            return;
        }

        if (lastSampleTime > 0 && now - lastSampleTime > IDLE_COMPLETE_MS) {
            ChatUtil.sendMessage("&b[KBTester] &7No new hits for 15s, analyzing...");
            complete();
        }
    }

    // ============================================================
    // FINALIZE + REPORT
    // ============================================================

    private void complete() {
        if (phase == Phase.COMPLETE) return;
        phase = Phase.COMPLETE;

        if (samples.isEmpty()) {
            ChatUtil.sendMessage("&c[KBTester] &7No knockback events observed.");
            ChatUtil.sendMessage("&7Nothing to extract. Try again when a fight is");
            ChatUtil.sendMessage("&7happening near you on the server.");
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
        ChatUtil.sendMessage("&b&lExtracted Multipliers &7(vs vanilla 1.8.9)");
        ChatUtil.sendMessage("  &7Horizontal: &b" + String.format("%.3fx", p.getHorizontalMultiplier()));
        ChatUtil.sendMessage("  &7Vertical:   &b" + String.format("%.3fx", p.getVerticalMultiplier()));
        ChatUtil.sendMessage("");
        ChatUtil.sendMessage("&b&lVerdict");
        ChatUtil.sendMessage("  " + p.getVerdict());
        ChatUtil.sendMessage("&b&m----------------------------------------------------");
    }

    private static String fmt(double d) {
        return String.format("%.6f", d);
    }
}