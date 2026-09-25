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
                S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) msg;

                Minecraft mc = Minecraft.getMinecraft();
                if (mc.thePlayer != null
                        && velocity.getEntityID() == mc.thePlayer.getEntityId()) {
                    testManager.observeVelocity(velocity);
                    trackingManager.handleLocalVelocity(velocity);
                }
                trackingManager.handleTrackedVelocity(velocity);

            } else if (msg instanceof S19PacketEntityStatus) {
                trackingManager.handleEntityHurt((S19PacketEntityStatus) msg);
            }
        } catch (Exception e) {
            KnockbackAnalyzer.logger.error("[KB] Error processing packet: {}", e.getMessage());
        }

        super.channelRead(ctx, msg);
    }
}