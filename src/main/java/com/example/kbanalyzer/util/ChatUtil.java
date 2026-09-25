package com.example.kbanalyzer.util;

import com.example.kbanalyzer.KnockbackAnalyzer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

public final class ChatUtil {

    private ChatUtil() {}

    public static void sendMessage(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        String colored = colorize(msg);
        mc.thePlayer.addChatMessage(new ChatComponentText(colored));
    }

    /**
     * Convert &-codes to Minecraft formatting codes (§).
     * Does NOT depend on EnumChatFormatting.getByChar() which is unavailable
     * in the stable_20 mappings used for 1.8.8/1.8.9.
     */
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
        // 0-9 = colors, a-f = colors, k-o = formatting, r = reset
        if (c >= '0' && c <= '9') return true;
        if (c >= 'a' && c <= 'f') return true;
        if (c >= 'k' && c <= 'o') return true;
        return c == 'r';
    }

    public static void log(String msg) {
        KnockbackAnalyzer.logger.info(msg);
    }
}