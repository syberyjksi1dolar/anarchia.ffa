package pl.anarchia.rangi;

import net.minecraft.ChatFormatting;

public enum Rank {
    VIP("VIP", ChatFormatting.YELLOW, 0xFFFF55),
    SVIP("SVIP", ChatFormatting.GREEN, 0x55FF55),
    MVIP("MVIP", ChatFormatting.GREEN, 0x90EE90),
    GVIP("GVIP", ChatFormatting.BLUE, 0x5555FF),
    DVIP("DVIP", ChatFormatting.DARK_GREEN, 0x00AA00),
    TWORCA("TWÓRCA", ChatFormatting.LIGHT_PURPLE, 0xAA00AA);

    private final String displayName;
    private final ChatFormatting teamColor;
    private final int rgb;

    Rank(String displayName, ChatFormatting teamColor, int rgb) {
        this.displayName = displayName;
        this.teamColor = teamColor;
        this.rgb = rgb;
    }

    public String displayName() {
        return displayName;
    }

    public ChatFormatting color() {
        return teamColor;
    }

    public int rgb() {
        return rgb;
    }

    public String teamId() {
        return "ar_" + name().toLowerCase();
    }
}
