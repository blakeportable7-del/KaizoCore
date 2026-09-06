/**
 * Dumps species and move names out of a Gen 4 .nds ROM using the randomizer's own
 * NDS code, so the names come from the game rather than from a list I typed.
 *
 * Doubles as proof the ZX engine can load Platinum at all, which is what the
 * Platinum randomize path depends on.
 *
 *   javac -cp engine-zx.jar -d out tools/DumpGen4Names.java
 *   java  -cp engine-zx.jar;out DumpGen4Names <rom.nds> <outDir>
 */
import com.dabomstew.pkrandomzx.pokemon.Pokemon;
import com.dabomstew.pkrandomzx.romhandlers.Gen4RomHandler;
import com.dabomstew.pkrandomzx.romhandlers.RomHandler;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

public class DumpGen4Names {
    public static void main(String[] args) throws Exception {
        String rom = args[0];
        Path outDir = Paths.get(args[1]);

        RomHandler.Factory factory = new Gen4RomHandler.Factory();
        if (!factory.isLoadable(rom)) {
            System.out.println("NOT LOADABLE by Gen4RomHandler: " + rom);
            System.exit(2);
        }
        RomHandler handler = factory.create(new Random());
        handler.loadRom(rom);
        System.out.println("loaded: " + handler.getROMName());

        List<Pokemon> pokes = handler.getPokemon();     // index 0 is null
        try (PrintWriter w = new PrintWriter(
                outDir.resolve("species.tsv").toFile(), StandardCharsets.UTF_8)) {
            int count = 0;
            for (int i = 1; i < pokes.size(); i++) {
                Pokemon p = pokes.get(i);
                if (p == null) continue;
                w.println(p.number + "\t" + p.name);
                count++;
            }
            System.out.println("species written: " + count);
        }

        // Ability and item NAMES are fixed properties of the game, not of a seed:
        // randomization changes which ability a species has, never what ability 22
        // is called. Safe to bundle; the per-species assignment comes from the
        // sidecar the engine writes next to each randomized ROM.
        try (PrintWriter w = new PrintWriter(
                outDir.resolve("abilities.tsv").toFile(), StandardCharsets.UTF_8)) {
            int count = 0;
            for (int i = 1; i <= handler.highestAbilityIndex(); i++) {
                String n = handler.abilityName(i);
                if (n != null && !n.isBlank()) { w.println(i + "\t" + n); count++; }
            }
            System.out.println("abilities written: " + count);
        }

        String[] items = handler.getItemNames();
        try (PrintWriter w = new PrintWriter(
                outDir.resolve("items.tsv").toFile(), StandardCharsets.UTF_8)) {
            int count = 0;
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null && !items[i].isBlank()) {
                    w.println(i + "\t" + items[i]); count++;
                }
            }
            System.out.println("items written: " + count);
        }

        List<com.dabomstew.pkrandomzx.pokemon.Move> moves = handler.getMoves();
        try (PrintWriter w = new PrintWriter(
                outDir.resolve("moves.tsv").toFile(), StandardCharsets.UTF_8)) {
            int count = 0;
            for (int i = 1; i < moves.size(); i++) {
                com.dabomstew.pkrandomzx.pokemon.Move m = moves.get(i);
                if (m == null) continue;
                // number, name, power, accuracy, type, pp, category
                // Gen 4 has a REAL physical/special split stored per move, so the
                // category is read here rather than derived from the type the way
                // Gen 3 forces. PP is the base value; PP Ups are per-Pokemon.
                w.println(m.number + "\t" + m.name + "\t" + m.power + "\t"
                        + (int) m.hitratio + "\t" + (m.type == null ? "" : m.type.name())
                        + "\t" + m.pp
                        + "\t" + (m.category == null ? "" : m.category.name()));
                count++;
            }
            System.out.println("moves written: " + count);
        }
    }
}
