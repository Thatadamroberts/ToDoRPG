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
import androidx.compose.ui.graphics.lerp
import com.todorpg.app.data.GearSlot
import com.todorpg.app.data.HeroClass
import com.todorpg.app.data.MonsterKind
import com.todorpg.app.data.Rarity

/**
 * A set of animation frames (character grids) sharing one palette. '.' is
 * transparent; every other character is looked up in the palette.
 */
class SpriteSheet(val frames: List<List<String>>, val palette: Map<Char, Color>, val blinkColor: Color? = null) {
    val cols: Int = frames.first().maxOf { it.length }
    val rows: Int = frames.first().size
}

/**
 * 16x16 pixel-art sprites. Idle animation frames are derived from a single
 * drawing where possible: the "breathe" frame sinks the upper body by a pixel.
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
    private val bard = listOf(
        "..........RR....",
        "......KKKKRK....",
        ".....KAAAAAK....",
        "....KAAAAAAAK...",
        "...KKKKKKKKKKK..",
        "....KSEKKESK....",
        ".....KSSSSK.....",
        "....KBBBBBBK....",
        "...KBBYBBYBBK...",
        "..KSKBBBBKKKK...",
        "..KSKBBBKHHHHK..",
        "...KKBBBKHKKHK..",
        "....KBBBKHHHHK..",
        "....KAAKKKKKK...",
        "....KHHKKHHK....",
        "...KKKKK.KKKK...",
    )
    private val samurai = listOf(
        "......KKKK......",
        ".....KRRRRK.....",
        "...KKRYRRYRKK...",
        "..KRRRRRRRRRRK..",
        "....KSSSSSSK....",
        "....KSEKKESK..K.",
        ".....KSSSSK..KGK",
        "....KAAAAAAKKGK.",
        "...KAAWAAWAKGK..",
        "..KSKAAAAAAKK...",
        "..KSKBBBBBBYK...",
        "...KKBBBBBBKSK..",
        "....KBBBBBBKK...",
        "....KBBBKBBBK...",
        "....KHHK.KHHK...",
        "...KKKKK.KKKKK..",
    )
    private val druid = listOf(
        "..K..........K..",
        ".KHK.KKKKKK.KHK.",
        "..KHKAAAAAAKHK..",
        "...KAAAAAAAAK...",
        "...KAKKKKKKAK...",
        "...KAKSEESKAK.K.",
        "...KAKSSSSKAKKHK",
        "...KAABBBBAAKKHK",
        "..KAAABBBBAAAKHK",
        "..KSKAABBAAKSKHK",
        "..KSKAAAAAAKKKHK",
        "...KAAAAAAAAK.HK",
        "...KAAAAAAAAK.HK",
        "...KAAAAAAAAK.HK",
        "...KAAKKKKAAK.KK",
        "...KKKK..KKKK...",
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
    private val bat2 = listOf(
        "................",
        "................",
        "................",
        "................",
        "......K..K......",
        "......KKKK......",
        ".KKK.KAAAAK.KKK.",
        "KAAAKKRAARKKAAAK",
        "KABAAAAAAAAAABAK",
        "KABBAAWKKWAABBAK",
        ".KBBBKAAAAKBBBK.",
        "..KKK.KBBK.KKK..",
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
    private val golem = listOf(
        "....KKKKKKKK....",
        "...KAAAAAAAAK...",
        "...KABAAAABAK...",
        "...KARRKKRRAK...",
        "...KAAAAAAAAK...",
        ".KKKKAKKKKAKKKK.",
        "KAABKAAAAAAKBAAK",
        "KAABAAAYYAAABAAK",
        "KAAKAAYRRYAAKAAK",
        "KBBKAAAYYAAAKBBK",
        "KAAKKAAAAAAKKAAK",
        ".KK.KAAAAAAK.KK.",
        "....KAABBAAK....",
        "...KAAAKKAAAK...",
        "..KBBBBKKBBBBK..",
        "..KKKKKK.KKKKK..",
    )
    private val demon = listOf(
        ".K............K.",
        "KWK..........KWK",
        "KWWK.KKKKKK.KWWK",
        ".KWKKAAAAAAKKWK.",
        "..KKAAAAAAAAKK..",
        "KK.KAYKAAKYAK.KK",
        "KBKKAAAAAAAAKKBK",
        "KBBBKAWKKWAKBBBK",
        "KBBBBKAAAAKBBBBK",
        ".KBBKAAAAAAKBBK.",
        "..KKAAAKKAAAKK..",
        "...KAAAAAAAAK...",
        "....KAAKKAAK....",
        "...KAAK..KAAK...",
        "...KYYK..KYYK...",
        "...KKKK..KKKK...",
    )

    private val gearWeapon = listOf("......KW", ".....KWK", "....KWK.", "...KWK..", "K.KWK...", ".KYK....", "KKYK....", "KK......")
    private val gearArmor = listOf("KK....KK", "KAKKKKAK", "KAAAAAAK", ".KAAAAK.", ".KAYYAK.", ".KAAAAK.", ".KAAAAK.", "..KKKK..")
    private val gearAccessory = listOf("...KK...", "..KYYK..", ".KYKKYK.", "KYK..KYK", "KYK..KYK", ".KYKKYK.", "..KYYK..", "...KK...")

    /** Upper body sinks one pixel: rows 0..7 shift down, overwriting row 8. */
    private fun breathe(grid: List<String>): List<String> {
        val blank = ".".repeat(grid.first().length)
        return listOf(blank) + grid.subList(0, 8) + grid.subList(9, grid.size)
    }

    private fun pal(a: Long, b: Long, extra: Map<Char, Color> = emptyMap()) =
        base + mapOf('A' to Color(a), 'B' to Color(b)) + extra

    private val heroSheets: Map<HeroClass, SpriteSheet> = HeroClass.entries.associateWith { c ->
        val (grid, palette) = when (c) {
            HeroClass.KNIGHT -> knight to pal(0xFF4068C8, 0xFFC82830)
            HeroClass.MONK -> monk to pal(0xFFE87828, 0xFFA04818, mapOf('H' to Color(0xFF281810)))
            HeroClass.RANGER -> ranger to pal(0xFF309848, 0xFF685030, mapOf('H' to Color(0xFF402818)))
            HeroClass.MAGE -> mage to pal(0xFF3850B0, 0xFF784828, mapOf('E' to Color(0xFFFFE650)))
            HeroClass.CLERIC -> cleric to pal(0xFFF8F8F8, 0xFFF8F8F8)
            HeroClass.BARD -> bard to pal(0xFF30A098, 0xFFC84078, mapOf('H' to Color(0xFF986028)))
            HeroClass.SAMURAI -> samurai to pal(0xFF383848, 0xFF782830, mapOf('H' to Color(0xFF282838)))
            HeroClass.DRUID -> druid to pal(0xFF408038, 0xFFC8A858, mapOf('H' to Color(0xFF886030)))
        }
        val blink = if (c == HeroClass.MAGE) Color(0xFF101018) else palette.getValue('S')
        SpriteSheet(listOf(grid, breathe(grid)), palette, blinkColor = blink)
    }

    fun hero(c: HeroClass): SpriteSheet = heroSheets.getValue(c)

    /** Palettes per tier so deeper floors get recoloured monsters. */
    private val monsterPalettes: Map<MonsterKind, List<Pair<Long, Long>>> = mapOf(
        MonsterKind.SLIME to listOf(0xFF50A0F0 to 0xFF2860C0, 0xFFE05050 to 0xFFA02828, 0xFF9050D0 to 0xFF603090, 0xFF303030 to 0xFF101010),
        MonsterKind.BAT to listOf(0xFF8048A0 to 0xFF502870, 0xFF408048 to 0xFF205028, 0xFFC03030 to 0xFF801818, 0xFF404040 to 0xFF202020),
        MonsterKind.GOBLIN to listOf(0xFF78B040 to 0xFF885830, 0xFFC08040 to 0xFF404890, 0xFF7070C0 to 0xFF603020, 0xFF606060 to 0xFF802020),
        MonsterKind.SKELETON to listOf(0xFFF8F8F8 to 0xFFF8F8F8, 0xFFF8E8A0 to 0xFFF8E8A0, 0xFFA0E0F8 to 0xFFA0E0F8, 0xFFE0A0F8 to 0xFFE0A0F8),
        MonsterKind.DRAGON to listOf(0xFFC83830 to 0xFFF09838, 0xFF308850 to 0xFF90D050, 0xFF3048B8 to 0xFF60B0F0, 0xFF282828 to 0xFF9038C0),
        MonsterKind.GOLEM to listOf(0xFF888070 to 0xFF585048, 0xFF6878A0 to 0xFF404860, 0xFFA07848 to 0xFF684828, 0xFF505868 to 0xFF303440),
        MonsterKind.DEMON to listOf(0xFFA02840 to 0xFF482060, 0xFF283890 to 0xFF701830, 0xFF306030 to 0xFF202020, 0xFF602070 to 0xFFB04020),
    )

    fun monster(kind: MonsterKind, tier: Int): SpriteSheet {
        val frames = when (kind) {
            MonsterKind.SLIME -> listOf(slime)
            MonsterKind.BAT -> listOf(bat, bat2)
            MonsterKind.GOBLIN -> listOf(goblin, breathe(goblin))
            MonsterKind.SKELETON -> listOf(skeleton, breathe(skeleton))
            MonsterKind.DRAGON -> listOf(dragon, breathe(dragon))
            MonsterKind.GOLEM -> listOf(golem, breathe(golem))
            MonsterKind.DEMON -> listOf(demon, breathe(demon))
        }
        val palettes = monsterPalettes.getValue(kind)
        val (a, b) = palettes[tier.coerceIn(0, palettes.lastIndex)]
        val extra = if (kind == MonsterKind.SKELETON) mapOf('W' to Color(a)) else emptyMap()
        return SpriteSheet(frames, pal(a, b, extra))
    }

    fun gear(slot: GearSlot, rarity: Rarity): SpriteSheet {
        val grid = when (slot) {
            GearSlot.WEAPON -> gearWeapon
            GearSlot.ARMOR -> gearArmor
            GearSlot.ACCESSORY -> gearAccessory
        }
        val c = Color(rarity.color)
        return SpriteSheet(listOf(grid), base + mapOf('W' to c, 'A' to c, 'Y' to if (slot == GearSlot.ACCESSORY) c else base.getValue('Y')))
    }
}

private class Px(val x: Int, val y: Int, val color: Color, val noise: Float, val blinkable: Boolean)

/**
 * Draws an animated sprite with crisp square pixels. All the moving parts are
 * read in the draw phase, so animation redraws without recomposing.
 *
 * @param animate cycle through frames using the shared [LocalClock].
 * @param phase offset in ms so a row of heroes doesn't breathe in unison.
 * @param flash draw solid white (hit flash).
 * @param dissolve 0..1, pixels vanish in a scattered pattern while tinting red (death).
 */
@Composable
fun PixelSprite(
    sheet: SpriteSheet,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    frameMs: Int = 480,
    phase: Int = 0,
    grayscale: Boolean = false,
    flash: () -> Boolean = { false },
    dissolve: () -> Float = { 0f },
    flipX: Boolean = false,
) {
    val frames = remember(sheet, grayscale) {
        sheet.frames.map { grid ->
            buildList<Px> {
                grid.forEachIndexed { y, row ->
                    row.forEachIndexed { x, ch ->
                        val c = sheet.palette[ch] ?: return@forEachIndexed
                        val color = if (grayscale) {
                            val l = c.red * 0.3f + c.green * 0.59f + c.blue * 0.11f
                            Color(l * 0.7f, l * 0.7f, l * 0.75f, c.alpha)
                        } else c
                        // Cheap deterministic hash for the dissolve pattern.
                        val h = ((x * 73856093) xor (y * 19349663)) and 0xFFFF
                        add(Px(x, y, color, h / 65535f, ch == 'E'))
                    }
                }
            }
        }
    }
    val clock = LocalClock.current
    val blinkColor = sheet.blinkColor
    Canvas(
        modifier
            .aspectRatio(sheet.cols.toFloat() / sheet.rows)
            .graphicsLayer { if (flipX) scaleX = -1f }
    ) {
        val t = if (animate && !grayscale) clock.value + phase else 0L
        val frame = frames[((t / frameMs) % frames.size).toInt()]
        val blinking = blinkColor != null && animate && !grayscale && (t % 3700) < 140
        val white = flash()
        val d = dissolve()
        val px = size.width / sheet.cols
        val py = size.height / sheet.rows
        for (p in frame) {
            if (d > 0f && p.noise < d) continue
            val color = when {
                white -> Color.White
                blinking && p.blinkable -> blinkColor!!
                d > 0f -> lerp(p.color, Color(0xFFFF2040), d.coerceAtMost(0.8f))
                else -> p.color
            }
            // Slight overdraw avoids hairline gaps between pixels.
            drawRect(color, Offset(p.x * px, p.y * py), Size(px + 0.5f, py + 0.5f))
        }
    }
}
