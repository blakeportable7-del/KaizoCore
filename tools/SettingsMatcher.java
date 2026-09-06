import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Settings-file matching, and its validation against real files.
 *
 * Brief section 5: match on game tag + NatDex + ruleset, NEVER on a bare name like
 * "RSE Kaizo.rnqs". Loading a vanilla .rnqs onto a NatDex ROM crashes the intro, so
 * this is a correctness gate, not a convenience.
 *
 * Real filenames on this machine carry a version token the brief did not predict
 * ("RSE NatDex v1.2 Kaizo.rnqs"), and the NDS files use a different convention
 * entirely ("HeartGoldSoulSilver_SuperKaizo.rnqs"). Both are handled by normalising
 * away every non-alphanumeric character before matching.
 *
 *     javac -d out SettingsMatcher.java && java -cp out SettingsMatcher <dir>
 */
public class SettingsMatcher {

    /** Longest-first. "superkaizo" must be tested before "kaizo" or it never matches. */
    static final String[] RULESETS = {
        "survivalrevival", "ironmonjourney", "kaizodoubles", "superkaizo",
        "chaoskaizo", "evokaizo", "standard", "survival", "ultimate", "kaizo",
    };

    /** Longest-first. "blackwhite2" must be tested before "blackwhite". */
    static final String[][] GAME_TAGS = {
        {"diamondpearlplatinum", "DPPt"}, {"heartgoldsoulsilver", "HGSS"},
        {"blackwhite2", "B2W2"}, {"blackwhite", "BW"},
        {"frlg", "FRLG"}, {"rse", "RSE"},
    };

    record Parsed(String fileName, String gameTag, String ruleset, boolean natDex) {
        boolean complete() { return gameTag != null && ruleset != null; }
        public String toString() {
            return String.format("%-6s %-15s %s", gameTag, ruleset, natDex ? "NatDex" : "vanilla");
        }
    }

    static String normalise(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    static Parsed parse(String fileName) {
        String n = normalise(fileName.replaceAll("(?i)\\.rnqs$", ""));

        String game = null;
        for (String[] pair : GAME_TAGS) if (n.contains(pair[0])) { game = pair[1]; break; }

        String rules = null;
        for (String r : RULESETS) if (n.contains(r)) { rules = r; break; }

        return new Parsed(fileName, game, rules, n.contains("natdex"));
    }

    /**
     * All distinct files matching a profile. Returning a list rather than a single
     * result is deliberate: an ambiguous match must be visible, not silently resolved.
     */
    static List<String> select(List<Parsed> all, String gameTag, String ruleset, boolean natDex) {
        return all.stream()
            .filter(p -> gameTag.equals(p.gameTag))
            .filter(p -> normalise(ruleset).equals(p.ruleset))
            .filter(p -> p.natDex == natDex)
            .map(Parsed::fileName).distinct().sorted().collect(Collectors.toList());
    }

    // ------------------------------------------------------------- validation

    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : "C:\\PokemonIronmon");

        List<Parsed> all;
        try (Stream<Path> w = Files.walk(root)) {
            all = w.filter(p -> p.toString().toLowerCase().endsWith(".rnqs"))
                   .map(p -> parse(p.getFileName().toString()))
                   .collect(Collectors.toList());
        }
        List<String> distinct = all.stream().map(Parsed::fileName).distinct().sorted().toList();
        System.out.printf("scanned %d .rnqs files (%d distinct names) under %s%n%n",
                          all.size(), distinct.size(), root);

        // Every real file must parse completely. An unparsed file is a silent mismatch.
        List<String> unparsed = all.stream().filter(p -> !p.complete())
                                   .map(Parsed::fileName).distinct().sorted().toList();
        check("every real .rnqs parses to a game tag and a ruleset", unparsed.isEmpty(),
              "unparsed: " + unparsed);

        System.out.println("  breakdown:");
        all.stream().map(Parsed::toString).distinct().sorted()
           .forEach(s -> System.out.println("    " + s));
        System.out.println();

        // The four v1 profiles resolve to exactly one file each.
        one(all, "RSE",  "Kaizo", true,  "RSE NatDex v1.2 Kaizo.rnqs");
        one(all, "FRLG", "Kaizo", true,  "FRLG NatDex v1.2 Kaizo.rnqs");
        one(all, "RSE",  "Kaizo", false, "RSE Kaizo.rnqs");
        one(all, "FRLG", "Kaizo", false, "FRLG Kaizo.rnqs");

        // Super Kaizo must never satisfy a request for Kaizo.
        one(all, "RSE", "Super Kaizo", true, "RSE NatDex v1.2 Super Kaizo.rnqs");

        // The rule that stops a crashed intro: no vanilla file may serve a NatDex profile.
        boolean leak = all.stream().filter(p -> !p.natDex)
            .anyMatch(p -> select(all, p.gameTag == null ? "?" : p.gameTag, "Kaizo", true)
                              .contains(p.fileName()));
        check("no vanilla settings file can ever be selected for a NatDex profile", !leak,
              "a vanilla file leaked into a NatDex selection");

        // Platinum, reserved for v2, is already present and must not answer a GBA request.
        check("Platinum settings exist and are tagged DPPt, not RSE/FRLG",
              all.stream().anyMatch(p -> "DPPt".equals(p.gameTag))
                  && select(all, "RSE", "Kaizo", false).stream().noneMatch(f -> f.contains("Platinum")),
              "Platinum tagging is wrong");

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    static void one(List<Parsed> all, String game, String rules, boolean natDex, String want) {
        List<String> hits = select(all, game, rules, natDex);
        String label = String.format("%s %s %s resolves to exactly \"%s\"",
                                     game, natDex ? "NatDex" : "vanilla", rules, want);
        check(label, hits.size() == 1 && hits.get(0).equals(want), "got " + hits);
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("  PASS  " + name); }
        else    { failed++; System.out.println("  FAIL  " + name + "  --  " + detail); }
    }
}
