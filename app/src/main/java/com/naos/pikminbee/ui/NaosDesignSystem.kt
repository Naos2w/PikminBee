package com.naos.pikminbee.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Product-specific use of the already approved Naos Universal UI/UX Design
 * System, NOT a second design system. Semantic values match the canonical
 * references/themes.json and design-tokens.css exactly.
 */
enum class NaosThemeFamily(val label: String) {
    SAGE("鼠尾草"), CLAY("暖陶土"), GRAPHITE("柔霧石墨"),
    SAND("燕麥亞麻"), MINERAL("礦石霧藍"), MAUVE("灰紫柔霧")
}

enum class NaosGlassMaterial(val label: String) {
    REGULAR("Regular"), CLEAR("Clear"), BLUR("Blur")
}

data class NaosPalette(
    val bg: Color, val surface: Color, val ink: Color, val muted: Color,
    val accent: Color, val onAccent: Color, val stroke: Color,
    val soft: Color, val tone: Color,
)

object NaosDesignSystem {
    const val FAST_MS = 160
    const val NORMAL_MS = 280
    const val PANEL_MS = 420
    const val STAGGER_MS = 50

    /** Preview-only optical parameters, not native Android system API values. */
    const val GLASS_BLUR_PX = 11f
    const val GLASS_REFRACTION_PX = 14f
    const val GLASS_TINT = 0.13f

    fun palette(theme: NaosThemeFamily, dark: Boolean): NaosPalette = when (theme) {
        NaosThemeFamily.SAGE -> if (dark)
            make(0x171D19,0x242E26,0xEDF3ED,0xA7B6A7,0xB1C5AE,0x18261B,0x38463B,0x324133,0x405645)
        else make(0xF4F6F1,0xFFFFFF,0x26332A,0x738073,0x5E735F,0xFFFFFF,0xDFE6DA,0xE8EDE5,0xCFDCCC)
        NaosThemeFamily.CLAY -> if (dark)
            make(0x211D1B,0x302824,0xF6F0EC,0xB8A69D,0xD9A594,0x321B16,0x4A3B35,0x46312B,0x72554B)
        else make(0xF7F5F2,0xFFFFFF,0x2B2926,0x857B75,0x965E4F,0xFFFFFF,0xEAE5DF,0xF2EAE4,0xDFC9BC)
        NaosThemeFamily.GRAPHITE -> if (dark)
            make(0x161B1D,0x242C2E,0xEDF2F1,0xA7B5B5,0xB6CAC7,0x1A2A29,0x3D4B4C,0x324141,0x4A5E5D)
        else make(0xF3F5F4,0xFFFFFF,0x252F30,0x788486,0x536A6C,0xFFFFFF,0xDCE2E2,0xE7ECEB,0xCFD8D7)
        NaosThemeFamily.SAND -> if (dark)
            make(0x201E1A,0x302D26,0xF8F3E9,0xB9AF9D,0xD9C4A2,0x302715,0x4B4538,0x40392D,0x736148)
        else make(0xF6F4EE,0xFFFEFB,0x332F27,0x898274,0x806B4D,0xFFFFFF,0xE7E1D5,0xF0EBDD,0xD8CBB4)
        NaosThemeFamily.MINERAL -> if (dark)
            make(0x161D21,0x253238,0xEEF4F5,0xA7BAC0,0xB3CCD4,0x1B3037,0x3C5057,0x32464E,0x4C6872)
        else make(0xF1F5F6,0xFFFFFF,0x243338,0x778990,0x4D737E,0xFFFFFF,0xDBE4E7,0xE5EDF0,0xC3D4D8)
        NaosThemeFamily.MAUVE -> if (dark)
            make(0x211D22,0x322A31,0xF5F0F4,0xB9AAB7,0xD1B8CB,0x322135,0x4C3F49,0x443441,0x735F70)
        else make(0xF7F4F6,0xFFFFFF,0x342E34,0x8C7F8A,0x81697B,0xFFFFFF,0xE7DFE5,0xEFE8ED,0xDACAD5)
    }

    private fun make(bg: Long, surface: Long, ink: Long, muted: Long,
        accent: Long, onAccent: Long, stroke: Long, soft: Long, tone: Long) =
        NaosPalette(Color(0xFF000000L or bg), Color(0xFF000000L or surface),
            Color(0xFF000000L or ink), Color(0xFF000000L or muted),
            Color(0xFF000000L or accent), Color(0xFF000000L or onAccent),
            Color(0xFF000000L or stroke), Color(0xFF000000L or soft),
            Color(0xFF000000L or tone))
}

data class NaosVisualState(
    val family: NaosThemeFamily = NaosThemeFamily.SAGE,
    val material: NaosGlassMaterial = NaosGlassMaterial.REGULAR,
    val dark: Boolean = false,
) {
    val palette: NaosPalette get() = NaosDesignSystem.palette(family, dark)
}
val LocalNaosVisualState = staticCompositionLocalOf { NaosVisualState() }
