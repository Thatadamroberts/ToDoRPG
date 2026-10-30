package com.todorpg.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.todorpg.app.data.HeroClass
import com.todorpg.app.data.MonsterKind

/**
 * 16x16 pixel-art sprites as character grids. '.' is transparent; every other
 * character is looked up in a palette, so the same grid can be recoloured.
 */
object Sprites {
    private val base = mapOf(
        'K' to Color(0xFF101018),
        'W' to Color(0xFFF8F8F8),
        'S' to Color(0xFFF8C098),
        'E' to Color(0xFF101018),
        'Y' to Color(0xFFF0C838),
        'G' to Color(0xFFA0A8B8),
        'R' to Color(0xFFD82828),
        'H' to Color(0xFF603820),
    )

    private val knight = listOf(
        "......KKKK......",
        ".....KGGGGK.....",
        "....KGWGGGGK..K.",
        "....KGGGGGGK.KWK",
        "....KKKKKKKK.KWK",
        "....KSEKKESK.KWK",
        ".....KSSSSK..KWK",
        "....KAAAAAAKKYYK",
        "...KAAWAAWAAKSK.",
        "..KBKAAAAAAKKK..",
        "..KBKAAYYAAK....",
        "..KBKKAAAAKK....",
        "..KBBKAKKAK.....",
        "....KGGKKGGK....",
        "...KGGGKKGGGK...",
        "...KKKKK.KKKK...",
    )
    private val monk = listOf(
        "................",
        ".....KKKKKK.....",
        "....KHHHHHHK....",
        "....KRRRRRRKRK..",
        "....KSSSSSSKRRK.",
        "....KSEKKESK.K..",
        ".....KSSSSK.....",
        "...KKAAAAAAKK...",
        "..KSKAAAAAAKSK..",
        "..KSKBAAAABKSK..",
        ".KSSKAAYYAAKSSK.",
        ".KSSKBBBBBBKSSK.",
        "..KK.KBBKBBK.KK.",
        ".....KBBKBBK....",
        "....KSSK.KSSK...",
        "....KKKK.KKKK...",
    )
    private val ranger = listOf(
        "................",
        ".....KKKKKK.....",
        "....KAAAAAAK....",
        "...KAAAAAAAAK...",
        "...KAKKKKKKAK...",
        "...KAKSEESKAK...",
        "...KAKSSSSKAK...",
        "...KKBBBBBBKK...",
        "..KSKBBBBBBKSK..",
        "..KSKBBBBBBKSKG.",
        "..KSKBBYYBBKKGK.",
        "...KKBBBBBBKSK..",
        "....KBBKKBBKK...",
        "....KBBKKBBK....",
        "....KHHKKHHK....",
        "...KKKKK.KKKK...",
    )
    private val mage = listOf(
        ".........KK.....",
        "........KYYK....",
        ".......KYYK.....",
        "......KYYYK.....",
        ".....KYYYYYK....",
        "....KYYYYYYYK...",
        "..KKYYYYYYYYYKK.",
        ".KYYYYYYYYYYYYYK",
        "..KKKKKKKKKKKKK.",
        "....KKEKKKEKK...",
        "...KAAKKKKKAAK..",
        "..KSKAAAAAAKSK..",
        "..KKKAAAAAAKKKB.",
        "...KAAAAAAAAKBK.",
        "...KAAAAAAAAKB..",
        "...KKKKKKKKKKK..",
    )
    private val cleric = listOf(
        "......KKKK......",
        ".....KWWWWK.....",
        "....KWWWWWWK....",
        "...KWWKKKKWWK...",
        "...KWKSEESKWK...",
        "...KWKSSSSKWK...",
        "...KWWKKKKWWK.Y.",
        "...KWWWWWWWWKKYK",
        "..KSKWWWWWWKSKG.",
        "..KSKWWWWWWK.KG.",
        "...KWWWWWWWWK.G.",
        "...KWRWWWWRWK.G.",
        "..KWRRRWWRRRWKG.",
        "..KRRWRRRRWRRK..",
        "..KWWWWWWWWWWK..",
        "..KKKKKKKKKKKK..",
    )

    private val slime = listOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        ".....KKKKKK.....",
        "....KAWWAAAK....",
        "...KAWAAAAAAK...",
        "..KAAAAAAAAAAK..",
        "..KAAKKAAKKAAK..",
        ".KAAAKWAAKWAAAK.",
        ".KAAAAAAAAAAAAK.",
        ".KAAABAAAABAAAK.",
        ".KBAAABBBBAAABK.",
        "..KBBBBBBBBBBK..",
        "...KKKKKKKKKK...",
    )
    private val bat = listOf(
        "................",
        "................",
        ".KK..........KK.",
        ".KAK..K..K..KAK.",
        ".KAAK.KKKK.KAAK.",
        "KAAAAKAAAAKAAAAK",
        "KAAAAAAAAAAAAAAK",
        "KABAAKRAARKAABAK",
        "KABBAAAAAAAABBAK",
        ".KBKBAWKKWABKBK.",
        ".KK.KBAAAABK.KK.",
        "......KBBK......",
        ".......KK.......",
        "................",
        "................",
        "................",
    )
    private val goblin = listOf(
        "................",
        "................",
        "..K..KKKKKK..K..",
        ".KAKKAAAAAAKKAK.",
        ".KAAAAAAAAAAAAK.",
        "..KAKRKAAKRKAK..",
        "...KAAAAAAAAK...",
        "...KAWKWKWKAK...",
        "....KAAAAAAK....",
        "...KBBBBBBBBK...",
        "..KAKBBBBBBKAKG.",
        "..KAKBBBBBBKAKGK",
        "...KKBBYYBBKKGK.",
        "....KBBKKBBKKK..",
        "....KAAKKAAK....",
        "...KKKKK.KKKK...",
    )
    private val skeleton = listOf(
        "......KKKK......",
        ".....KWWWWK.....",
        "....KWWWWWWK....",
        "....KWKKWKKK....",
        "....KWRKWRKK....",
        "....KWWWKWWK....",
        ".....KWKWKWK....",
        "......KKKKK.....",
        "....KKWWWWKK....",
        "...KWKKWWKKWK...",
        "..KWK.KWWK.KWK..",
        "..KK.KWKKWK.KK..",
        "......KWWK......",
        ".....KWKKWK.....",
        "....KWK..KWK....",
        "....KKK..KKK....",
    )
    private val dragon = listOf(
        "..........KK....",
        ".........KAAK...",
        "..KK....KAAWAK..",
        ".KBBK..KAAAAAKKK",
        "KBBBBK.KAARAAAAK",
        "KBBBBBKKAAAAKKK.",
        ".KBBBBBKAAAAK...",
        "..KBBBAAAAAK....",
        "...KAAAAAAAK....",
        "..KAAWWWWAAAK...",
        ".KAAWWWWWWAAAK..",
        ".KAAWWWWWWAAAKK.",
        "..KAAWWWWAAAKAK.",
        "...KAAKKAAAK.KAK",
        "...KYK..KYK...KK",
        "...KKK..KKK.....",
    )

    private fun pal(a: Long, b: Long, extra: Map<Char, Color> = emptyMap()) =
        base + mapOf('A' to Color(a), 'B' to Color(b)) + extra

    fun hero(c: HeroClass): Pair<List<String>, Map<Char, Color>> = when (c) {
        HeroClass.KNIGHT -> knight to pal(0xFF4068C8, 0xFFC82830)
        HeroClass.MONK -> monk to pal(0xFFE87828, 0xFFA04818, mapOf('H' to Color(0xFF281810)))
        HeroClass.RANGER -> ranger to pal(0xFF309848, 0xFF685030, mapOf('H' to Color(0xFF402818)))
        HeroClass.MAGE -> mage to pal(0xFF3850B0, 0xFF784828, mapOf('E' to Color(0xFFFFE650)))
        HeroClass.CLERIC -> cleric to pal(0xFFF8F8F8, 0xFFF8F8F8)
    }

    /** Palettes per tier so deeper floors get recoloured monsters. */
    private val monsterPalettes: Map<MonsterKind, List<Pair<Long, Long>>> = mapOf(
        MonsterKind.SLIME to listOf(0xFF50A0F0 to 0xFF2860C0, 0xFFE05050 to 0xFFA02828, 0xFF9050D0 to 0xFF603090, 0xFF303030 to 0xFF101010),
        MonsterKind.BAT to listOf(0xFF8048A0 to 0xFF502870, 0xFF408048 to 0xFF205028, 0xFFC03030 to 0xFF801818, 0xFF404040 to 0xFF202020),
        MonsterKind.GOBLIN to listOf(0xFF78B040 to 0xFF885830, 0xFFC08040 to 0xFF404890, 0xFF7070C0 to 0xFF603020, 0xFF606060 to 0xFF802020),
        MonsterKind.SKELETON to listOf(0xFFF8F8F8 to 0xFFF8F8F8, 0xFFF8E8A0 to 0xFFF8E8A0, 0xFFA0E0F8 to 0xFFA0E0F8, 0xFFE0A0F8 to 0xFFE0A0F8),
        MonsterKind.DRAGON to listOf(0xFFC83830 to 0xFFF09838, 0xFF308850 to 0xFF90D050, 0xFF3048B8 to 0xFF60B0F0, 0xFF282828 to 0xFF9038C0),
    )

    fun monster(kind: MonsterKind, tier: Int): Pair<List<String>, Map<Char, Color>> {
        val grid = when (kind) {
            MonsterKind.SLIME -> slime
            MonsterKind.BAT -> bat
            MonsterKind.GOBLIN -> goblin
            MonsterKind.SKELETON -> skeleton
            MonsterKind.DRAGON -> dragon
        }
        val palettes = monsterPalettes.getValue(kind)
        val (a, b) = palettes[tier.coerceIn(0, palettes.lastIndex)]
        val extra = if (kind == MonsterKind.SKELETON) mapOf('W' to Color(a)) else emptyMap()
        return grid to pal(a, b, extra)
    }
}

/** Draws a character-grid sprite with crisp square pixels. */
@Composable
fun PixelSprite(
    sprite: Pair<List<String>, Map<Char, Color>>,
    modifier: Modifier = Modifier,
    grayscale: Boolean = false,
    flash: Boolean = false,
    flipX: Boolean = false,
) {
    val (grid, palette) = sprite
    val pixels = remember(grid, palette, grayscale, flash) {
        buildList<Triple<Int, Int, Color>> {
            grid.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch ->
                    val c = palette[ch] ?: return@forEachIndexed
                    val color = when {
                        flash -> Color.White
                        grayscale -> {
                            val l = c.red * 0.3f + c.green * 0.59f + c.blue * 0.11f
                            Color(l * 0.7f, l * 0.7f, l * 0.75f, c.alpha)
                        }
                        else -> c
                    }
                    add(Triple(x, y, color))
                }
            }
        }
    }
    val cols = grid.maxOf { it.length }
    val rows = grid.size
    Canvas(
        modifier
            .aspectRatio(cols.toFloat() / rows)
            .graphicsLayer { if (flipX) scaleX = -1f }
    ) {
        val px = size.width / cols
        val py = size.height / rows
        for ((x, y, color) in pixels) {
            // Slight overdraw avoids hairline gaps between pixels.
            drawRect(color, Offset(x * px, y * py), Size(px + 0.5f, py + 0.5f))
        }
    }
}
