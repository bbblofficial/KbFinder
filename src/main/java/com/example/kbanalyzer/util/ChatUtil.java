package com.example.kbanalyzer.util;

import com.example.kbanalyzer.KnockbackAnalyzer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public final class ChatUtil {

    private ChatUtil() {}

    public static void sendMessage(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        String colored = colorize(msg);
        mc.thePlayer.addChatMessage(new ChatComponentText(colored));
    }

    public static String colorize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&' && i + 1 < chars.length) {
                char code = Character.toLowerCase(chars[i + 1]);
                EnumChatFormatting fmt = EnumChatFormatting.getByChar(code);
                if (fmt != null) {
                    sb.append('\u00a7').append(code);
                    i++;
                    continue;
                }
            }
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    public static void log(String msg) {
        KnockbackAnalyzer.logger.info(msg);
    }
}
