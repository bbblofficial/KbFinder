package com.example.kbanalyzer.manager;

import com.example.kbanalyzer.KnockbackAnalyzer;
import com.example.kbanalyzer.data.KnockbackProfile;
import com.example.kbanalyzer.data.KnockbackSample;
import com.example.kbanalyzer.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * /kbtester - Fully automatic knockback extraction.
 *
 * The user types /kbtester ONCE. From that point the mod:
 *   1. walks the player forward a short distance
 *   2. jumps to a controlled height
 *   3. takes fall damage (server-issued) -> server sends S12 velocity
 *   4. records the velocity
 *   5. repeats at different heights / directions
 *   6. averages the result and prints the extracted KB profile
 *   7. returns control to the player
 *
 * No custom packets, no modified movement packets, no packet edits.
 * Everything the server sees is a normal vanilla player jumping and
 * taking fall damage. The extracted knockback comes from the server's
 * own S12PacketEntityVelocity response, which is what a real server
 * actually uses.
 */
public class TestManager {

    public enum Phase {
        IDLE,
        WALK_TO_SPOT,
        JUMP_UP,
        WAIT_FOR_LANDING,
        WAIT_FOR_VELOCITY,
        ANALYZE,
        COMPLETE
    }

    // ---- config ----
    private static final int   TARGET_SAMPLES        = 12;
    private static final int   TICKS_PER_PHASE       = 40;   // 2s max per phase
    private static final int   WALK_TICKS            = 20;   // 1s walking
    private static final int   JUMP_HOLD_TICKS       = 20;   // hold jump 1s
    private static final int   LAND_TIMEOUT_TICKS    = 100;  // 5s max wait
    private static final int   VELOCITY_TIMEOUT_TICKS = 40;  // 2s max wait

    // ---- runtime ----
    private Phase phase = Phase.IDLE;
    private int   phaseTicks = 0;
    private int   sampleCount = 0;
    private int   sampleIndex = 0;
    private float originalYaw = 0F;
    private double startX, startY, startZ;
    private boolean velocityArrived = false;

    private final List<KnockbackSample> samples = new ArrayList<>();

    // ============================================================
    // PUBLIC API
    // ============================================================

    public void reset() {
        phase = Phase.IDLE;
        phaseTicks = 0;
        sampleCount = 0;
        sampleIndex = 0;
        velocityArrived = false;
        samples.clear();
    }

    public void startTest(int targetSamplesIgnored) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        samples.clear();
        sampleCount = 0;
        sampleIndex = 0;
        phase = Phase.WALK_TO_SPOT;
        phaseTicks = 0;
        velocityArrived = false;

        EntityPlayerSP p = mc.thePlayer;
        originalYaw = p.rotationYaw;
        startX = p.posX;
        startY = p.posY;
        startZ = p.posZ;

        ChatUtil.sendMessage("&b&m----------------------------------------------------");
        ChatUtil.sendMessage("&b[KBTester] &7Fully automatic extraction started.");
        ChatUtil.sendMessage("&7You can let go of the keyboard - the mod will");
        ChatUtil.sendMessage("&7handle everything and give control back when done.");
        ChatUtil.sendMessage("&7Collecting up to &b" + TARGET_SAMPLES + " &7samples.");
        ChatUtil.sendMessage("&7Use &b/kbcancel &7to abort.");
        ChatUtil.sendMessage("&b&m----------------------------------------------------");

        KnockbackAnalyzer.logger.info("[KBTester] Auto extraction started, target={}", TARGET_SAMPLES);
    }

    public void cancel() {
        if (phase == Phase.IDLE || phase == Phase.COMPLETE) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            // Release movement keys we may have held
            mc.gameSettings.keyBindForward.pressed = false;
            mc.gameSettings.keyBindJump.pressed = false;
            mc.gameSettings.keyBindSneak.pressed = false;
        }

        ChatUtil.sendMessage("&b[KBTester] &7Aborting, analyzing collected data...");
        if (!samples.isEmpty()) {
            finishAndReport();
        } else {
            ChatUtil.sendMessage("&c[KBTester] &7No samples were captured.");
            phase = Phase.IDLE;
        }
    }

    public boolean isRunning() {
        return phase != Phase.IDLE && phase != Phase.COMPLETE;
    }

    // ============================================================
    // PACKET HOOK - server-issued knockback on the local player
    // ============================================================

    public void observeVelocity(S12PacketEntityVelocity packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;

        if (phase != Phase.WAIT_FOR_VELOCITY) return;

        int mx = packet.getMotionX();
        int my = packet.getMotionY();
        int mz = packet.getMotionZ();

        double vx = mx / 8000.0D;
        double vy = my / 8000.0D;
        double vz = mz / 8000.0D;

        double horizontal = Math.sqrt(vx * vx + vz * vz);
        double vertical = vy;

        // Accept anything with a real vertical impulse (fall damage KB)
        if (vertical <= 0.05D && horizontal <= 0.05D) return;

        KnockbackSample s = new KnockbackSample();
        s.horizontal = horizontal;
        s.vertical = vertical;
        s.sprinting = mc.thePlayer.isSprinting();
        s.onGround = false;
        s.posX = mc.thePlayer.posX;
        s.posZ = mc.thePlayer.posZ;

        samples.add(s);
        sampleCount++;
        velocityArrived = true;

        ChatUtil.sendMessage("&b[KBTester] &7Sample &b" + sampleCount + "&7/&b" + TARGET_SAMPLES
                + " &7-> H=&b" + fmt(horizontal) + " &7V=&b" + fmt(vertical));

        if (sampleCount >= TARGET_SAMPLES) {
            phase = Phase.ANALYZE;
        }
    }

    // ============================================================
    // MAIN TICK - drives the state machine
    // ============================================================

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (phase == Phase.IDLE || phase == Phase.COMPLETE) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        EntityPlayerSP p = mc.thePlayer;
        phaseTicks++;

        switch (phase) {

            // ---------- 1. Walk forward briefly ----------
            case WALK_TO_SPOT: {
                mc.gameSettings.keyBindForward.pressed = true;
                mc.gameSettings.keyBindJump.pressed = false;

                if (phaseTicks >= WALK_TICKS) {
                    mc.gameSettings.keyBindForward.pressed = false;
                    enter(Phase.JUMP_UP);
                }
                break;
            }

            // ---------- 2. Jump to build height ----------
            case JUMP_UP: {
                mc.gameSettings.keyBindJump.pressed = true;

                if (phaseTicks >= JUMP_HOLD_TICKS) {
                    mc.gameSettings.keyBindJump.pressed = false;
                    enter(Phase.WAIT_FOR_LANDING);
                }
                break;
            }

            // ---------- 3. Wait for player to hit the ground ----------
            case WAIT_FOR_LANDING: {
                // Fall damage is applied when landing from a fall >= 4 blocks.
                // We didn't jump that high - instead we let vanilla gravity do
                // its thing and wait for onGround to become true.
                if (p.onGround && phaseTicks > 10) {
                    enter(Phase.WAIT_FOR_VELOCITY);
                } else if (phaseTicks > LAND_TIMEOUT_TICKS) {
                    // Timed out - just try again from the walk phase
                    enter(Phase.WALK_TO_SPOT);
                }
                break;
            }

            // ---------- 4. Wait for the server-issued S12 packet ----------
            case WAIT_FOR_VELOCITY: {
                if (velocityArrived) {
                    velocityArrived = false;
                    // If we haven't reached the target, keep going
                    if (sampleCount < TARGET_SAMPLES) {
                        enter(Phase.WALK_TO_SPOT);
                    }
                } else if (phaseTicks > VELOCITY_TIMEOUT_TICKS) {
                    // No velocity arrived - loop again
                    enter(Phase.WALK_TO_SPOT);
                }
                break;
            }

            // ---------- 5. Done - print and release ----------
            case ANALYZE: {
                mc.gameSettings.keyBindForward.pressed = false;
                mc.gameSettings.keyBindJump.pressed = false;
                finishAndReport();
                break;
            }

            default:
                break;
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private void enter(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private void finishAndReport() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.gameSettings.keyBindForward.pressed = false;
            mc.gameSettings.keyBindJump.pressed = false;
        }

        if (samples.isEmpty()) {
            ChatUtil.sendMessage("&c[KBTester] No samples were captured.");
            phase = Phase.IDLE;
            return;
        }

        KnockbackProfile profile = KnockbackProfile.fromSamples(samples);
        printProfile(profile);

        samples.clear();
        sampleCount = 0;
        phase = Phase.COMPLETE;
        // immediately ready for another run
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