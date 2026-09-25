package com.example.kbanalyzer.commands;

import com.example.kbanalyzer.KnockbackAnalyzer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;

import java.util.List;

public class CommandKBCenter extends CommandBase {

    @Override
    public String getCommandName() {
        return "kbcenter";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/kbcenter - Smoothly move player to block center (yaw 90, pitch 0)";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        KnockbackAnalyzer.getInstance().getTrackingManager().centerOnBlock();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return null;
    }
}
