package com.example.kbanalyzer.util;

import com.example.kbanalyzer.KnockbackAnalyzer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

public final class ChatUtil {

    private ChatUtil() {}

    public static void sendMessage(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        mc.thePlayer.addChatMessage(new ChatComponentText(colorize(msg)));
    }

    public static String colorize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&' && i + 1 < chars.length) {
                char code = Character.toLowerCase(chars[i + 1]);
                if (isValidColorCode(code)) {
                    sb.append('\u00a7').append(code);
                    i++;
                    continue;
                }
            }
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    private static boolean isValidColorCode(char c) {
        if (c >= '0' && c <= '9') return true;
        if (c >= 'a' && c <= 'f') return true;
        if (c >= 'k' && c <= 'o') return true;
        return c == 'r';
    }

    public static void log(String msg) {
        KnockbackAnalyzer.logger.info(msg);
    }
}