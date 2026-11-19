package com.tiny.arcade;

public final class ChessTheme {
    public static final int COUNT = 10;

    private static final String[] NAMES = {
        "Classic", "Walnut", "Slate", "Emerald", "Ocean",
        "Rosewood", "Sandstone", "Midnight", "Cherry", "Mono"
    };

    // light, dark, frame, frame line, coordinates, grain on light, grain on dark
    private static final int[][] COLORS = {
        {0xFFE6CFAF, 0xFF7D5333, 0xFFCBA678, 0xFF3B2414, 0xFF2B180C, 0x149C7444, 0x1C3B2210},
        {0xFFD8B98E, 0xFF6B4A2F, 0xFF8B6440, 0xFF2A1A0F, 0xFFF2E3C8, 0x16925F38, 0x1E2F1B0D},
        {0xFFC9D1D9, 0xFF5F6B7A, 0xFF2F3742, 0xFF11151A, 0xFFD8DEE8, 0x14FFFFFF, 0x1C000000},
        {0xFFD9E8D0, 0xFF4F7A5A, 0xFF2E4E3A, 0xFF14251A, 0xFFE9F3E4, 0x1482B36A, 0x1C17331F},
        {0xFFCFE1EA, 0xFF4E7488, 0xFF2C4A5A, 0xFF13232E, 0xFFE3F1F7, 0x1482B9D3, 0x1C152A36},
        {0xFFE8C9C1, 0xFF8B4F4F, 0xFF5E3232, 0xFF2A1212, 0xFFF6E4DF, 0x14B06B5E, 0x1C3A1717},
        {0xFFE7D8B8, 0xFFA8845C, 0xFF7A5A3A, 0xFF332414, 0xFFF5EBDD, 0x14B89458, 0x1C4A321B},
        {0xFF8C97A8, 0xFF2E3642, 0xFF1A1F27, 0xFF0B0E12, 0xFFC7D0DD, 0x14FFFFFF, 0x22000000},
        {0xFFEBC9B0, 0xFF9A4A3A, 0xFF6B2E24, 0xFF2B100B, 0xFFF8E3D8, 0x14C46B52, 0x1C3A120C},
        {0xFFD8D8D8, 0xFF6A6A6A, 0xFF3A3A3A, 0xFF111111, 0xFFF0F0F0, 0x14FFFFFF, 0x1C000000}
    };

    private ChessTheme() {
    }

    public static String name(int theme) {
        return NAMES[clamp(theme)];
    }

    public static int light(int theme) { return COLORS[clamp(theme)][0]; }
    public static int dark(int theme) { return COLORS[clamp(theme)][1]; }
    public static int frame(int theme) { return COLORS[clamp(theme)][2]; }
    public static int frameLine(int theme) { return COLORS[clamp(theme)][3]; }
    public static int coord(int theme) { return COLORS[clamp(theme)][4]; }
    public static int grainLight(int theme) { return COLORS[clamp(theme)][5]; }
    public static int grainDark(int theme) { return COLORS[clamp(theme)][6]; }

    private static int clamp(int theme) {
        return theme < 0 || theme >= COUNT ? 0 : theme;
    }
}
