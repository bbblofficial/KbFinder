package com.example.kbanalyzer.commands;

import com.example.kbanalyzer.KnockbackAnalyzer;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.List;

public class CommandKBTrack extends CommandBase {

    @Override
    public String getCommandName() {
        return "kbtrack";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/kbtrack <player> - Track knockback for a specific player";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            if (KnockbackAnalyzer.getInstance().getTrackingManager().isTrackingPlayer()) {
                KnockbackAnalyzer.getInstance().getTrackingManager().stopTrackingPlayer();
            } else {
                sender.addChatMessage(new ChatComponentText("/kbtrack <player>"));
                sender.addChatMessage(new ChatComponentText("Example: /kbtrack Notch"));
            }
            return;
        }

        KnockbackAnalyzer.getInstance().getTrackingManager().startTrackingPlayer(args[0]);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null) {
                List<String> names = new ArrayList<>();
                for (EntityPlayer p : mc.theWorld.playerEntities) {
                    names.add(p.getName());
                }
                return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
            }
        }
        return null;
    }
}
