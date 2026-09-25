package com.example.kbanalyzer.manager;

import com.example.kbanalyzer.KnockbackAnalyzer;
import com.example.kbanalyzer.data.KnockbackSample;
import com.example.kbanalyzer.data.PlayerKnockbackSample;
import com.example.kbanalyzer.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

public class TrackingManager {

    private final List<KnockbackSample> localHits = new ArrayList<>();
    private boolean trackingLocal = false;
    private int targetHits = 5;
    private boolean waitingForDistance = false;
    private KnockbackSample lastHit = null;
    private long lastHitTime = 0L;

    private boolean trackingPlayer = false;
    private String trackedPlayerName = "";
    private final List<PlayerKnockbackSample> playerHits = new ArrayList<>();
    private boolean hurtFlag = false;

    private boolean recordingVelocity = false;

    private boolean centeringActive = false;
    private double targetX, targetY, targetZ;
    private float targetYaw, targetPitch;
    private int centerTicks = 0;
    private static final int SMOOTH_TICKS = 10;

    public void reset() {
        trackingLocal = false;
        trackingPlayer = false;
        recordingVelocity = false;
        centeringActive = false;
        waitingForDistance = false;
        lastHit = null;
        hurtFlag = false;
        localHits.clear();
        playerHits.clear();
    }

    // ============ Local player tracking ============

    public void startTrackingLocal(int hits) {
        localHits.clear();
        trackingLocal = true;
        targetHits = hits;
        ChatUtil.sendMessage("&b[KB] &7Started tracking &b" + hits + " &7hits. Get hit!");
        KnockbackAnalyzer.logger.info("[KB] Started local tracking for {} hits", hits);
    }

    public void handleLocalVelocity(S12PacketEntityVelocity packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        int motionX = packet.getMotionX();
        int motionY = packet.getMotionY();
        int motionZ = packet.getMotionZ();

        double horizontal = Math.sqrt(motionX * motionX + motionZ * motionZ) / 8000.0D;
        double vertical = Math.abs(motionY) / 8000.0D + 0.03D;

        boolean sprinting = mc.thePlayer.isSprinting();
        boolean onGround = mc.thePlayer.onGround;

        if (recordingVelocity) {
            double vx = mc.thePlayer.motionX;
            double vy = mc.thePlayer.motionY;
            double vz = mc.thePlayer.motionZ;
            double hVel = Math.sqrt(vx * vx + vz * vz);
            double vVel = Math.abs(vy);

            ChatUtil.sendMessage("&3Current Velocity:");
            ChatUtil.sendMessage("&7X: &b" + fmt(vx) + "&7 Y: &b" + fmt(vy) + "&7 Z: &b" + fmt(vz));
            ChatUtil.sendMessage("&7Horizontal: &b" + fmt(hVel) + " &7blocks/tick");
            ChatUtil.sendMessage("&7Vertical:   &b" + fmt(vVel) + " &7blocks/tick");
            ChatUtil.sendMessage("&7" + (sprinting ? "&bSprinting" : "&7Not Sprinting") + " &7| " + (onGround ? "&bOn Ground" : "&7Airborne"));
            ChatUtil.sendMessage("&7----------------------------------------------------");
        }

        if (!trackingLocal) return;

        KnockbackSample data = new KnockbackSample();
        data.horizontal = horizontal;
        data.vertical = vertical;
        data.sprinting = sprinting;
        data.onGround = onGround;
        data.posX = mc.thePlayer.posX;
        data.posZ = mc.thePlayer.posZ;

        localHits.add(data);
        lastHit = data;
        lastHitTime = System.currentTimeMillis();
        waitingForDistance = true;

        ChatUtil.sendMessage("&3Hit " + localHits.size() + "&7/&b" + targetHits);
        ChatUtil.sendMessage("&7Horizontal: &b" + fmt(horizontal) + " &7blocks");
        ChatUtil.sendMessage("&7Vertical:   &b" + fmt(vertical) + " &7blocks");
        ChatUtil.sendMessage("&7" + (sprinting ? "&bSprinting" : "&7Not Sprinting") + " &7| " + (onGround ? "&bOn Ground" : "&7Airborne"));
        ChatUtil.sendMessage("&7----------------------------------------------------");

        if (localHits.size() >= targetHits) {
            showLocalStats();
            trackingLocal = false;
        }
    }

    public void showLocalStats() {
        if (localHits.isEmpty()) return;

        ChatUtil.sendMessage("&b====================================================");
        ChatUtil.sendMessage("&bKNOCKBACK STATISTICS &7(" + localHits.size() + " hits)");
        ChatUtil.sendMessage("&b====================================================");

        double sumH = 0, sumV = 0, sumD = 0;
        double minH = Double.MAX_VALUE, maxH = 0;
        double minV = Double.MAX_VALUE, maxV = 0;
        double minD = Double.MAX_VALUE, maxD = 0;
        int distCount = 0, sprintCount = 0, groundCount = 0;

        for (KnockbackSample d : localHits) {
            sumH += d.horizontal;
            sumV += d.vertical;
            minH = Math.min(minH, d.horizontal);
            maxH = Math.max(maxH, d.horizontal);
            minV = Math.min(minV, d.vertical);
            maxV = Math.max(maxV, d.vertical);
            if (d.sprinting) sprintCount++;
            if (d.onGround) groundCount++;
            if (d.distance >= 0) {
                sumD += d.distance;
                minD = Math.min(minD, d.distance);
                maxD = Math.max(maxD, d.distance);
                distCount++;
            }
        }

        int size = localHits.size();
        ChatUtil.sendMessage("&bHorizontal &7[Min: &b" + fmt(minH) + "&7] [Max: &b" + fmt(maxH) + "&7] [AVG: &b" + fmt(sumH / size) + "&7]");
        ChatUtil.sendMessage("&bVertical   &7[Min: &b" + fmt(minV) + "&7] [Max: &b" + fmt(maxV) + "&7] [AVG: &b" + fmt(sumV / size) + "&7]");
        if (distCount > 0) {
            ChatUtil.sendMessage("&bDistance   &7[Min: &b" + fmt4(minD) + "&7] [Max: &b" + fmt4(maxD) + "&7] [AVG: &b" + fmt4(sumD / distCount) + "&7] &7blocks");
        }
        ChatUtil.sendMessage("&bSprinting  &7" + sprintCount + "/" + size + " hits &7(" + (sprintCount * 100 / size) + "%)");
        ChatUtil.sendMessage("&bOn Ground  &7" + groundCount + "/" + size + " hits &7(" + (groundCount * 100 / size) + "%)");
        ChatUtil.sendMessage("&b====================================================");

        localHits.clear();
    }

    // ============ Remote player tracking ============

    public void startTrackingPlayer(String playerName) {
        if (trackingPlayer) showPlayerStats();
        trackingPlayer = true;
        trackedPlayerName = playerName;
        playerHits.clear();
        hurtFlag = false;
        ChatUtil.sendMessage("&b[KB] &7Now tracking player: &b" + playerName);
        ChatUtil.sendMessage("&b[KB] &7Use &b/kbcancel &7to stop and show stats!");
        KnockbackAnalyzer.logger.info("[KB] Started tracking player: {}", playerName);
    }

    public void stopTrackingPlayer() {
        if (trackingPlayer) {
            showPlayerStats();
            trackingPlayer = false;
            trackedPlayerName = "";
            playerHits.clear();
            hurtFlag = false;
        }
    }

    public void handleTrackedVelocity(S12PacketEntityVelocity packet) {
        if (!trackingPlayer) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;

        Entity entity = mc.theWorld.getEntityByID(packet.getEntityID());
        if (entity == null || !entity.getName().equalsIgnoreCase(trackedPlayerName)) return;

        double motionX = packet.getMotionX() / 8000.0D;
        double motionY = packet.getMotionY() / 8000.0D;
        double motionZ = packet.getMotionZ() / 8000.0D;
        double vertical = motionY;
        double horizontal = Math.sqrt(motionX * motionX + motionZ * motionZ);

        PlayerKnockbackSample data = new PlayerKnockbackSample();
        data.horizontal = horizontal;
        data.vertical = vertical;
        data.rawX = motionX;
        data.rawY = motionY;
        data.rawZ = motionZ;
        data.pos = entityPos(entity);
        data.wasHurt = hurtFlag;

        playerHits.add(data);

        ChatUtil.sendMessage("&bKnockback " + fmt(motionX) + " " + fmt(motionY) + " " + fmt(motionZ));
        ChatUtil.sendMessage("&bTracked Player " + data.pos);
        ChatUtil.sendMessage("&7Vertical: &b" + fmt(vertical));
        ChatUtil.sendMessage("&7Horizontal: &b" + fmt(horizontal));
        ChatUtil.sendMessage("&7----------------------------------------------------");

        hurtFlag = false;
    }

    public void handleEntityHurt(S19PacketEntityStatus packet) {
        if (!trackingPlayer) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;

        Entity entity = packet.getEntity(mc.theWorld);
        if (entity == null || !entity.getName().equalsIgnoreCase(trackedPlayerName)) return;

        String pos = entityPos(entity);
        ChatUtil.sendMessage("&c" + trackedPlayerName + " was hurt. " + pos);
        hurtFlag = true;
    }

    public void showPlayerStats() {
        if (playerHits.isEmpty()) {
            ChatUtil.sendMessage("&c[KB] No knockback data recorded for " + trackedPlayerName);
            return;
        }

        ChatUtil.sendMessage("&b====================================================");
        ChatUtil.sendMessage("&bKNOCKBACK STATISTICS FOR &e" + trackedPlayerName);
        ChatUtil.sendMessage("&b====================================================");

        double sumH = 0, sumV = 0;
        double minH = Double.MAX_VALUE, maxH = 0;
        double minV = Double.MAX_VALUE, maxV = 0;

        for (PlayerKnockbackSample d : playerHits) {
            sumH += d.horizontal;
            sumV += d.vertical;
            minH = Math.min(minH, d.horizontal);
            maxH = Math.max(maxH, d.horizontal);
            minV = Math.min(minV, d.vertical);
            maxV = Math.max(maxV, d.vertical);
        }

        int size = playerHits.size();
        ChatUtil.sendMessage("&bHorizontal &7[Min: &b" + fmt(minH) + "&7] [Max: &b" + fmt(maxH) + "&7] [AVG: &b" + fmt(sumH / size) + "&7]");
        ChatUtil.sendMessage("&bVertical   &7[Min: &b" + fmt(minV) + "&7] [Max: &b" + fmt(maxV) + "&7] [AVG: &b" + fmt(sumV / size) + "&7]");
        ChatUtil.sendMessage("&b====================================================");
        ChatUtil.sendMessage("&7Total knockback events recorded: &b" + size);
        ChatUtil.sendMessage("&b====================================================");
    }

    // ============ Velocity recording ============

    public void startVelocityRecording() {
        recordingVelocity = true;
        ChatUtil.sendMessage("&b[KB] &7Continuous velocity display started. Use &b/kbcancel &7to stop!");
    }

    // ============ Cancel ============

    public void cancelAll() {
        boolean something = false;

        if (trackingLocal) {
            trackingLocal = false;
            localHits.clear();
            waitingForDistance = false;
            lastHit = null;
            ChatUtil.sendMessage("&b[KB] &7Local tracking cancelled!");
            something = true;
        }
        if (recordingVelocity) {
            recordingVelocity = false;
            ChatUtil.sendMessage("&b[KB] &7Velocity display stopped!");
            something = true;
        }
        if (trackingPlayer) {
            stopTrackingPlayer();
            ChatUtil.sendMessage("&b[KB] &7Player tracking stopped!");
            something = true;
        }
        if (centeringActive) {
            centeringActive = false;
            centerTicks = 0;
            ChatUtil.sendMessage("&b[KB] &7Centering cancelled!");
            something = true;
        }

        if (!something) {
            ChatUtil.sendMessage("&b[KB] &7No active tracking or recording to cancel!");
        }
    }

    // ============ Centering ============

    public void centerOnBlock() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        if (centeringActive) {
            ChatUtil.sendMessage("&b[KB] &7Already centering!");
            return;
        }

        BlockPos playerPos = mc.thePlayer.getPosition();
        BlockPos abovePos = playerPos.up();

        targetX = abovePos.getX() + 0.5D;
        targetZ = abovePos.getZ() + 0.5D;
        targetY = abovePos.getY() - 1.0D;
        targetYaw = 90.0F;
        targetPitch = 0.0F;

        centeringActive = true;
        centerTicks = 0;

        ChatUtil.sendMessage("&b[KB] &7Moving to center of block at &b" + abovePos.getX() + ", " + abovePos.getY() + ", " + abovePos.getZ());
        ChatUtil.sendMessage("&b[KB] &7Smoothly adjusting yaw to &b90 &7and pitch to &b0");
    }

    // ============ Tick ============

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        if (centeringActive) {
            if (centerTicks >= SMOOTH_TICKS) {
                mc.thePlayer.setPosition(targetX, targetY, targetZ);
                mc.thePlayer.rotationYaw = targetYaw;
                mc.thePlayer.rotationPitch = targetPitch;
                centeringActive = false;
                centerTicks = 0;
                ChatUtil.sendMessage("&b[KB] &7Finished centering!");
                return;
            }

            float progress = centerTicks / (float) SMOOTH_TICKS;
            float eased = 1.0F - (float) Math.pow(1.0F - progress, 3.0D);

            double cx = mc.thePlayer.posX;
            double cy = mc.thePlayer.posY;
            double cz = mc.thePlayer.posZ;
            mc.thePlayer.setPosition(
                cx + (targetX - cx) * eased,
                cy + (targetY - cy) * eased,
                cz + (targetZ - cz) * eased
            );

            float currentYaw = mc.thePlayer.rotationYaw;
            float currentPitch = mc.thePlayer.rotationPitch;
            float yawDiff = targetYaw - currentYaw;
            while (yawDiff > 180.0F) yawDiff -= 360.0F;
            while (yawDiff < -180.0F) yawDiff += 360.0F;

            mc.thePlayer.rotationYaw = currentYaw + yawDiff * eased;
            mc.thePlayer.rotationPitch = currentPitch + (targetPitch - currentPitch) * eased;

            centerTicks++;
        }

        if (waitingForDistance && lastHit != null) {
            long elapsed = System.currentTimeMillis() - lastHitTime;
            if (elapsed > 500L) {
                double dx = mc.thePlayer.posX - lastHit.posX;
                double dz = mc.thePlayer.posZ - lastHit.posZ;
                lastHit.distance = Math.sqrt(dx * dx + dz * dz);
                waitingForDistance = false;
                lastHit = null;
            }
        }
    }

    // ============ Getters ============

    public boolean isTrackingLocal() { return trackingLocal; }
    public boolean isTrackingPlayer() { return trackingPlayer; }
    public boolean isRecordingVelocity() { return recordingVelocity; }
    public boolean isCenteringActive() { return centeringActive; }
    public String getTrackedPlayerName() { return trackedPlayerName; }
    public List<PlayerKnockbackSample> getPlayerHits() { return playerHits; }

    // ============ Utilities ============

    private static String entityPos(Entity e) {
        if (e == null) return "NULL";
        return String.format("%.2f %.2f %.2f %.1f", e.posX, e.posY, e.posZ, e.rotationYaw);
    }

    private static String fmt(double d) {
        return String.format("%.6f", d);
    }

    private static String fmt4(double d) {
        return String.format("%.4f", d);
    }
}
