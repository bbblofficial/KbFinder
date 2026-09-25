package com.example.kbanalyzer.commands;

import com.example.kbanalyzer.KnockbackAnalyzer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.util.List;

public class CommandFindKB extends CommandBase {

    @Override
    public String getCommandName() {
        return "findkb";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/findkb <hits> - Track knockback from <hits> hits";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.addChatMessage(new ChatComponentText("/findkb <number of hits>"));
            sender.addChatMessage(new ChatComponentText("Example: /findkb 10"));
            return;
        }

        try {
            int hits = Integer.parseInt(args[0]);
            if (hits < 1 || hits > 50) {
                sender.addChatMessage(new ChatComponentText("Use a number between 1 and 50"));
                return;
            }
            KnockbackAnalyzer.getInstance().getTrackingManager().startTrackingLocal(hits);
        } catch (NumberFormatException e) {
            sender.addChatMessage(new ChatComponentText("Enter a valid number"));
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "5", "10", "15", "20", "25", "30");
        }
        return null;
    }
}
