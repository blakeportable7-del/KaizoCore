package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The DS tracker's move type symbols, drawn beside a move's name (Blake, 2026-10-02, showing the Platinum tracker:
 * "I like the symbols"). Transcribed from the NDS Ironmon Tracker's IconDrawer.lua (Brian0255, GPL-3.0, credited in
 * NOTICE): its seventeen *_FILLED icons, the ones it draws with "Color move type icons" on. Each row is a string of
 * palette numbers, 0 for no pixel and n for the icon's n-th colour, in its own colours as the DS tracker has them.
 * The DS games have no Fairy type, so the DS tracker draws none; KaizoCore adds two of its own in the same manner (dark
 * outline, light top-left, darker bottom-right): Fairy, a four-point sparkle in Fairy pink, for the Nat. Dex, MaxDex and
 * Heart & Soul moves (2026-10-06, Moonblast's row had none), and "???" (Gen 3's Mystery type, Curse's), a question mark
 * in its teal. Only an unknown type is left without one.
 */
internal object TypeSymbols {
    class Symbol(val rows: List<String>, val palette: List<Long>) {
        val width: Int get() = rows.first().length
        val height: Int get() = rows.size
    }

    /** The widest and the tallest symbol, 9 by 9: the square each move row holds for one, so the names stay in a column. */
    const val SLOT = 9

    /** A type's symbol by its name in any case ("FIRE", "Fire"), or null. Gen 3's Mystery type goes by "???" too. */
    fun of(typeName: String?): Symbol? = typeName?.let { BY_TYPE[it.trim().uppercase().let { n -> if (n == "???") "MYSTERY" else n }] }

    val BY_TYPE: Map<String, Symbol> = mapOf(
        "NORMAL" to Symbol(
            listOf("00111100", "01223310", "12255331", "12500631", "13500641", "13366441", "01334410", "00111100"),
            listOf(0xFF90916AL, 0xFFFFFFBCL, 0xFFEAEAADL, 0xFFD3D39CL, 0xFFACAD80L, 0xFF90916BL),
        ),
        "FIGHTING" to Symbol(
            listOf("0111110", "1213141", "1213141", "1213141", "0111111", "0001551", "0000110"),
            listOf(0xFFB82A23L, 0xFFFFC1C1L, 0xFFFFA9A8L, 0xFFFF8C8CL, 0xFFFF8787L),
        ),
        "FLYING" to Symbol(
            listOf("00111110", "01223331", "12331111", "13313341", "13334110", "14111000", "11000000", "10000000"),
            listOf(0xFF6C78CCL, 0xFFE0E4FFL, 0xFFD1D7FFL, 0xFFBFC7FFL),
        ),
        "POISON" to Symbol(
            listOf("01100000", "13420000", "14420010", "02200132", "00000020", "00110000", "01342000", "00220000"),
            listOf(0xFFC850CEL, 0xFF8B368EL, 0xFFF875FFL, 0xFFB247B7L),
        ),
        "GROUND" to Symbol(
            listOf("00000000", "00011000", "00123100", "00121100", "01114310", "01244310", "12444431", "11111111"),
            listOf(0xFFBC9D4FL, 0xFFFFEFCCL, 0xFFEAC975L, 0xFFFFE191L),
        ),
        "ROCK" to Symbol(
            listOf("00111000", "01233100", "01233110", "12331341", "12313341", "12313341", "01313410", "00111100"),
            listOf(0xFF89782DL, 0xFFC1A93FL, 0xFFB5A03BL, 0xFFA59136L),
        ),
        "BUG" to Symbol(
            listOf("00100100", "00011000", "10133101", "01233410", "01233410", "01233410", "00133100", "01011010"),
            listOf(0xFF98A81FL, 0xFFF1FF8EL, 0xFFEBFF56L, 0xFFDBEA52L),
        ),
        "GHOST" to Symbol(
            listOf("00111000", "01345100", "12141610", "12345610", "12345610", "12345610", "12141610", "01010100"),
            listOf(0xFF6F5797L, 0xFFD9C6FFL, 0xFFD2BAFFL, 0xFFC3A3FFL, 0xFFB296EAL, 0xFFA48AD8L),
        ),
        "STEEL" to Symbol(
            listOf("01111100", "13322210", "13111210", "12212410", "12214410", "01244100", "00111000"),
            listOf(0xFF9A9AADL, 0xFFE6E6F7L, 0xFFF4F4FFL, 0xFFD7D7E2L),
        ),
        "FIRE" to Symbol(
            listOf("00010000", "00110100", "01210110", "12321221", "12342321", "12444321", "01344310", "00111100"),
            listOf(0xFFCC4851L, 0xFFEA6B44L, 0xFFEEA160L, 0xFFF8F290L),
        ),
        "WATER" to Symbol(
            listOf("000100000", "001210000", "001210000", "014321000", "014321000", "013321000", "013321000", "001110000"),
            listOf(0xFF5379EDL, 0xFF56B4EAL, 0xFF8CD4FFL, 0xFFC6EAFFL),
        ),
        "GRASS" to Symbol(
            listOf("00010000", "00141000", "01345100", "12345610", "12345610", "12345610", "01141100", "00010000"),
            listOf(0xFF5F913EL, 0xFFCEFFADL, 0xFFC6F9A4L, 0xFFB9EA9AL, 0xFFADDB90L, 0xFFA3CE88L),
        ),
        "ELECTRIC" to Symbol(
            listOf("00444400", "04123400", "41224444", "41222234", "44444234", "00042234", "00422340", "00444400"),
            listOf(0xFFFCE682L, 0xFFFBDD59L, 0xFFFACC15L, 0xFFE19328L),
        ),
        "PSYCHIC" to Symbol(
            listOf("00111100", "01332210", "13311221", "13121121", "12122241", "12211110", "01222410", "00111100"),
            listOf(0xFFEF5890L, 0xFFFFCCDCL, 0xFFFFD6E3L, 0xFFFFA3C1L),
        ),
        "ICE" to Symbol(
            listOf("00011000", "01133110", "01311210", "13122141", "13122141", "01211410", "01144110", "00011000"),
            listOf(0xFF87B7B5L, 0xFFE5FFFCL, 0xFFF7FFFEL, 0xFFD3EAE7L),
        ),
        "DRAGON" to Symbol(
            listOf("10100101", "11100111", "12111131", "12444431", "01444410", "01244310", "00144100", "00011000"),
            listOf(0xFF6A2AFDL, 0xFFC3A9F9L, 0xFF986FF2L, 0xFFB493FBL, 0xFFE20053L),
        ),
        "DARK" to Symbol(
            listOf("00000000", "01100110", "12100161", "12311561", "12344561", "12344561", "01344510", "00111100", "00000000"),
            listOf(0xFF6E5848L, 0xFFD3A88BL, 0xFFC19A7FL, 0xFFB59077L, 0xFFA5836DL, 0xFF997964L),
        ),
        // KaizoCore's own, after the DS set's manner (no DS game has these types).
        "FAIRY" to Symbol(
            listOf("000010000", "000121000", "000121000", "011223110", "122233341", "011334110", "000141000", "000141000", "000010000"),
            listOf(0xFFC9578DL, 0xFFFFE6F0L, 0xFFFFC2DAL, 0xFFF39BC0L),
        ),
        "MYSTERY" to Symbol(
            listOf("0111110", "1223321", "1211121", "0001321", "0013210", "0012100", "0001000", "0012100", "0011100"),
            listOf(0xFF3D6B5CL, 0xFFC4EBDDL, 0xFF8CC7B2L),
        ),
    )
}

/**
 * [typeName]'s symbol at one reference pixel a dot, centred in a square [TypeSymbols.SLOT] units a side; an empty slot for
 * a type with none, so every name in the table starts at the same x.
 */
@Composable
internal fun TypeSymbol(typeName: String?) {
    val s = TypeSymbols.of(typeName)
    val slot = Modifier.width(TypeSymbols.SLOT.rp).height(TypeSymbols.SLOT.rp)
    if (s == null) { androidx.compose.foundation.layout.Spacer(slot); return }
    Canvas(slot.semantics { contentDescription = typeName.orEmpty() }) {
        val px = size.height / TypeSymbols.SLOT
        // Offsets in whole units, on the icon's own grid.
        val top = ((TypeSymbols.SLOT - s.height) / 2) * px
        val left = ((TypeSymbols.SLOT - s.width) / 2) * px
        s.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                val n = c - '0'
                if (n > 0) drawRect(Color(s.palette[n - 1]), Offset(left + x * px, top + y * px), Size(px, px))
            }
        }
    }
}
