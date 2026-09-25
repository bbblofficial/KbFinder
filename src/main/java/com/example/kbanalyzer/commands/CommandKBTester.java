package com.example.kbanalyzer.commands;

import com.example.kbanalyzer.KnockbackAnalyzer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.util.List;

/**
 * /kbtester - The main feature.
 *
 * Automatically extracts the full knockback configuration of the current
 * server by passively observing incoming velocity packets. No outgoing
 * packets are modified or sent, so it is 100% undetectable server-side.
 *
 * Usage:
 *   /kbtester          -> collect 10 samples
 *   /kbtester <count>  -> collect <count> samples (1-100)
 */
public class CommandKBTester extends CommandBase {

    @Override
    public String getCommandName() {
        return "kbtester";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/kbtester [samples] - Auto-extract server knockback settings (client-side only)";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        int count = 10;

        if (args.length >= 1) {
            try {
                count = Integer.parseInt(args[0]);
                if (count < 1 || count > 100) {
                    sender.addChatMessage(new ChatComponentText("Sample count must be between 1 and 100"));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.addChatMessage(new ChatComponentText("Enter a valid number"));
                return;
            }
        }

        KnockbackAnalyzer.getInstance().getTestManager().startTest(count);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "5", "10", "15", "20", "30", "50");
        }
        return null;
    }
}
