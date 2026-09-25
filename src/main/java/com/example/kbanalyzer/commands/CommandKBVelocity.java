package com.example.kbanalyzer.commands;

import com.example.kbanalyzer.KnockbackAnalyzer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;

import java.util.List;

public class CommandKBVelocity extends CommandBase {

    @Override
    public String getCommandName() {
        return "kbgetvelocity";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/kbgetvelocity - Continuously display player velocity until /kbcancel";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        KnockbackAnalyzer.getInstance().getTrackingManager().startVelocityRecording();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return null;
    }
}
