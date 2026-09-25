package com.example.kbanalyzer.commands;

import com.example.kbanalyzer.KnockbackAnalyzer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.util.List;

/**
 * /kbtester - Fully automatic passive knockback extraction.
 *
 *   /kbtester          -> 25 samples
 *   /kbtester <count>  -> <count> samples (5-100)
 *
 * No movement. No attacks. No outgoing packets.
 */
public class CommandKBTester extends CommandBase {

    @Override
    public String getCommandName() {
        return "kbtester";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/kbtester [samples] - Passively extract server knockback settings";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        int count = 25;

        if (args.length >= 1) {
            try {
                count = Integer.parseInt(args[0]);
                if (count < 5 || count > 100) {
                    sender.addChatMessage(new ChatComponentText(
                            "Sample count must be between 5 and 100"));
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
            return getListOfStringsMatchingLastWord(args, "10", "20", "25", "50", "100");
        }
        return null;
    }
}