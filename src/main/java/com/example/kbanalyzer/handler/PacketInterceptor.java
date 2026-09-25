package com.example.kbanalyzer.handler;

import com.example.kbanalyzer.KnockbackAnalyzer;
import com.example.kbanalyzer.manager.TestManager;
import com.example.kbanalyzer.manager.TrackingManager;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;

/**
 * Intercepts incoming packets client-side only.
 * Does NOT modify outgoing packets, so no server-side detection.
 */
public class PacketInterceptor extends ChannelDuplexHandler {

    private static final String HANDLER_NAME = "kbanalyzer_handler";

    private final TrackingManager trackingManager;
    private final TestManager testManager;
    private boolean injected = false;

    public PacketInterceptor(TrackingManager trackingManager, TestManager testManager) {
        this.trackingManager = trackingManager;
        this.testManager = testManager;
    }

    public boolean isInjected() {
        return injected;
    }

    public void inject(Minecraft mc) {
        try {
            ChannelPipeline pipeline = mc.getNetHandler().getNetworkManager().channel().pipeline();

            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
            }

            pipeline.addBefore("packet_handler", HANDLER_NAME, this);
            injected = true;
            KnockbackAnalyzer.logger.info("[KB] Packet handler injected");
        } catch (Exception e) {
            injected = false;
            KnockbackAnalyzer.logger.error("[KB] Injection failed: {}", e.getMessage());
        }
    }

    public void uninject() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getNetHandler() == null) return;

            ChannelPipeline pipeline = mc.getNetHandler().getNetworkManager().channel().pipeline();
            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
            }
            injected = false;
        } catch (Exception ignored) {
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (msg instanceof S12PacketEntityVelocity) {
                handleVelocity((S12PacketEntityVelocity) msg);
            } else if (msg instanceof S19PacketEntityStatus) {
                handleEntityStatus((S19PacketEntityStatus) msg);
            }
        } catch (Exception e) {
            KnockbackAnalyzer.logger.error("[KB] Error processing packet: {}", e.getMessage());
        }

        super.channelRead(ctx, msg);
    }

    private void handleVelocity(S12PacketEntityVelocity packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        int entityId = packet.getEntityID();

        if (entityId == mc.thePlayer.getEntityId()) {
            trackingManager.handleLocalVelocity(packet);
            testManager.handleLocalVelocity(packet);
        }

        trackingManager.handleTrackedVelocity(packet);
    }

    private void handleEntityStatus(S19PacketEntityStatus packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;

        if (packet.getOpCode() == 2) {
            trackingManager.handleEntityHurt(packet);
        }
    }
}
