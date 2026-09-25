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
 *   2. jumps to build height
 *   3. lands and takes fall damage (server-issued) -> server sends S12
 *   4. records the velocity
 *   5. repeats at different spots / heights
 *   6. averages the result and prints the extracted KB profile
 *   7. returns control to the player
 *
 * Uses movementInput (the same thing real key presses modify) so the
 * server sees exactly what it sees from a normal player bunny-hopping.
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
    private static final int TARGET_SAMPLES         = 12;
    private static final int WALK_TICKS             = 20;
    private static final int JUMP_HOLD_TICKS        = 20;
    private static final int LAND_TIMEOUT_TICKS     = 100;
    private static final int VELOCITY_TIMEOUT_TICKS = 40;

    // ---- runtime ----
    private Phase phase = Phase.IDLE;
    private int   phaseTicks = 0;
    private int   sampleCount = 0;
    private boolean velocityArrived = false;

    private final List<KnockbackSample> samples = new ArrayList<>();

    // ============================================================
    // PUBLIC API
    // ============================================================

    public void reset() {
        phase = Phase.IDLE;
        phaseTicks = 0;
        sampleCount = 0;
        velocityArrived = false;
        samples.clear();
    }

    public void startTest(int unused) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        samples.clear();
        sampleCount = 0;
        phase = Phase.WALK_TO_SPOT;
        phaseTicks = 0;
        velocityArrived = false;

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
        releaseInputs();

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
    // MAIN TICK - state machine
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

            case WALK_TO_SPOT: {
                p.movementInput.moveForward = 1.0F;
                p.movementInput.jump = false;

                if (phaseTicks >= WALK_TICKS) {
                    p.movementInput.moveForward = 0F;
                    enter(Phase.JUMP_UP);
                }
                break;
            }

            case JUMP_UP: {
                p.movementInput.jump = true;

                if (phaseTicks >= JUMP_HOLD_TICKS) {
                    p.movementInput.jump = false;
                    enter(Phase.WAIT_FOR_LANDING);
                }
                break;
            }

            case WAIT_FOR_LANDING: {
                if (p.onGround && phaseTicks > 10) {
                    enter(Phase.WAIT_FOR_VELOCITY);
                } else if (phaseTicks > LAND_TIMEOUT_TICKS) {
                    enter(Phase.WALK_TO_SPOT);
                }
                break;
            }

            case WAIT_FOR_VELOCITY: {
                if (velocityArrived) {
                    velocityArrived = false;
                    if (sampleCount < TARGET_SAMPLES) {
                        enter(Phase.WALK_TO_SPOT);
                    }
                } else if (phaseTicks > VELOCITY_TIMEOUT_TICKS) {
                    enter(Phase.WALK_TO_SPOT);
                }
                break;
            }

            case ANALYZE: {
                releaseInputs();
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

    private void releaseInputs() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        mc.thePlayer.movementInput.moveForward = 0F;
        mc.thePlayer.movementInput.moveStrafe  = 0F;
        mc.thePlayer.movementInput.jump        = false;
        mc.thePlayer.movementInput.sneak       = false;
    }

    private void finishAndReport() {
        releaseInputs();

        if (samples.isEmpty()) {
            ChatUtil.sendMessage("&c[KBTester] No samples were captured.");
            phase = Phase.IDLE;
            return;
        }

        KnockbackProfile profile = KnockbackProfile.fromSamples(samples);
        printProfile(profile);

        samples.clear();
        sampleCount = 0;
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