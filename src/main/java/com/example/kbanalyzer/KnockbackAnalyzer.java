package com.example.kbanalyzer;

import com.example.kbanalyzer.commands.CommandFindKB;
import com.example.kbanalyzer.commands.CommandKBCancel;
import com.example.kbanalyzer.commands.CommandKBCenter;
import com.example.kbanalyzer.commands.CommandKBTester;
import com.example.kbanalyzer.commands.CommandKBTrack;
import com.example.kbanalyzer.commands.CommandKBVelocity;
import com.example.kbanalyzer.handler.PacketInterceptor;
import com.example.kbanalyzer.manager.TestManager;
import com.example.kbanalyzer.manager.TrackingManager;
import com.example.kbanalyzer.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = KnockbackAnalyzer.MODID,
    name = KnockbackAnalyzer.NAME,
    version = KnockbackAnalyzer.VERSION,
    acceptedMinecraftVersions = "[1.8.9]",
    clientSideOnly = true
)
public class KnockbackAnalyzer {

    public static final String MODID = "kbanalyzer";
    public static final String NAME = "Knockback Analyzer";
    public static final String VERSION = "2.0.0";

    public static Logger logger;
    private static KnockbackAnalyzer instance;

    private final TrackingManager trackingManager = new TrackingManager();
    private final TestManager testManager = new TestManager();
    private PacketInterceptor packetInterceptor;

    public static KnockbackAnalyzer getInstance() {
        return instance;
    }

    public TrackingManager getTrackingManager() {
        return trackingManager;
    }

    public TestManager getTestManager() {
        return testManager;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        instance = this;
        logger.info("[KB] Knockback Analyzer v{} loading...", VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(trackingManager);
        MinecraftForge.EVENT_BUS.register(testManager);

        ClientCommandHandler.instance.registerCommand(new CommandFindKB());
        ClientCommandHandler.instance.registerCommand(new CommandKBCenter());
        ClientCommandHandler.instance.registerCommand(new CommandKBCancel());
        ClientCommandHandler.instance.registerCommand(new CommandKBVelocity());
        ClientCommandHandler.instance.registerCommand(new CommandKBTrack());
        ClientCommandHandler.instance.registerCommand(new CommandKBTester());

        logger.info("[KB] Commands registered!");
    }

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        logger.info("[KB] Connected to server, resetting state");
        trackingManager.reset();
        testManager.reset();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() != null) {
            injectPacketHandler(mc);
        }
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        logger.info("[KB] Disconnected, resetting state");
        trackingManager.reset();
        testManager.reset();
        if (packetInterceptor != null) {
            packetInterceptor.uninject();
            packetInterceptor = null;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.getNetHandler() == null) return;

        if (packetInterceptor == null || !packetInterceptor.isInjected()) {
            injectPacketHandler(mc);
        }

        trackingManager.onTick(mc);
        testManager.onTick(mc);
    }

    private void injectPacketHandler(Minecraft mc) {
        try {
            if (packetInterceptor == null) {
                packetInterceptor = new PacketInterceptor(trackingManager, testManager);
            }
            packetInterceptor.inject(mc);
        } catch (Exception e) {
            logger.error("[KB] Failed to inject packet handler: {}", e.getMessage());
        }
    }

    public PacketInterceptor getPacketInterceptor() {
        return packetInterceptor;
    }

    public static void chat(String msg) {
        ChatUtil.sendMessage(msg);
    }
}
