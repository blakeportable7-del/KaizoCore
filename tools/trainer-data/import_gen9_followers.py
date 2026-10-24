"""Cut the Generation 9 Resource Pack's follower sheets into walkingpals-expo.

The originals stay in tools/trainer-data/sources/gen9pack (sha256 in sources.tsv). A doubled
sheet is taken at its own pixels; Falinks is not an exact doubling, so it keeps the pixels
it was drawn at. convert_walking_pals_nat.py does this again on a full run.
"""
import hashlib
import pathlib
import shutil
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import convert_walking_pals_nat as cw

PACK = pathlib.Path(r"C:\Users\bepor\walkingpals-cache\gen9pack\followers\Graphics\Characters\Followers")
POST = "https://eeveeexpo.com/resources/1101/"
TERMS = "Credit the artists. Generation 9 Resource Pack v3.3.8 by Caruban"
GEN5 = ("MissingLukey, help-14, Kymoyonian, cSc-A7X, 2and2makes5, Pokegirl4ever, Fernandojl, "
        "Silver-Skies, TyranitarDark, Getsuei-H, Kid1513, Milomilotic11, Kyt666, kdiamo11, "
        "Chocosrawlooid, Syledude, Gallanty, Gizamimi-Pichu, Zyon17, LarryTurbo, spritesstealer")
GEN7 = "Larry Turbo, princess-phoenix"
GEN8 = "SageDeoxys, Wolfang62, LarryTurbo, tammyclaydon"

# pack file, source file, nat dex id, name, national key, artists
ROWS = [
    ("SIMISEAR.png", "simisear.png", 539, "Simisear", "514", GEN5),
    ("SIMIPOUR.png", "simipour.png", 541, "Simipour", "516", GEN5),
    ("TRANQUILL.png", "tranquill.png", 545, "Tranquill", "520", GEN5),
    ("BLITZLE.png", "blitzle.png", 547, "Blitzle", "522", GEN5),
    ("ZEBSTRIKA.png", "zebstrika.png", 548, "Zebstrika", "523", GEN5),
    ("THROH.png", "throh.png", 563, "Throh", "538", GEN5),
    ("CRUSTLE.png", "crustle.png", 583, "Crustle", "558", GEN5),
    ("TIRTOUGA.png", "tirtouga.png", 589, "Tirtouga", "564", GEN5),
    ("CARRACOSTA.png", "carracosta.png", 590, "Carracosta", "565", GEN5),
    ("AMOONGUSS.png", "amoonguss.png", 616, "Amoonguss", "591", GEN5),
    ("FRILLISH.png", "frillish.png", 617, "Frillish", "592", GEN5),
    ("SHELMET.png", "shelmet.png", 641, "Shelmet", "616", GEN5),
    ("BOUFFALANT.png", "bouffalant.png", 651, "Bouffalant", "626", GEN5),
    ("TRUMBEAK.png", "trumbeak.png", 757, "Trumbeak", "732", GEN7),
    ("GUMSHOOS.png", "gumshoos.png", 760, "Gumshoos", "735", GEN7),
    ("SHIINOTIC.png", "shiinotic.png", 781, "Shiinotic", "756", GEN7),
    ("ORANGURU.png", "oranguru.png", 790, "Oranguru", "765", GEN7),
    ("ROLYCOLY.png", "rolycoly.png", 862, "Rolycoly", "837", GEN8),
    ("CARKOL.png", "carkol.png", 863, "Carkol", "838", GEN8),
    ("COALOSSAL.png", "coalossal.png", 864, "Coalossal", "839", GEN8),
    ("MRRIME.png", "mr-rime.png", 891, "Mr. Rime", "866", GEN8),
    ("FALINKS.png", "falinks.png", 895, "Falinks", "870", GEN8),
    ("CUFANT.png", "cufant.png", 903, "Cufant", "878", GEN8),
    ("ZARUDE.png", "zarude.png", 918, "Zarude", "893", GEN8),
    ("MASCHIFF.png", "maschiff.png", 967, "Maschiff", "942", "Carmanekko"),
    ("IRONJUGULIS.png", "iron-jugulis.png", 1018, "Iron Jugulis", "993",
     "TooManyLuigis, Mortedesu, Billla, Wolfang62"),
]


def main():
    cw.EXPO_DIR.mkdir(parents=True, exist_ok=True)
    lines = [
        "# Follower sheets from the Generation 9 Resource Pack v3.3.8 (eeveeexpo.com/resources/1101).",
        "# Downloaded 2026-10-08. Used only where PMD Sprite Collab has no sheet of its own.",
        "# The pack asks that the artists be credited. Caruban compiles the pack.",
        "# file\tnatdex\tname\tkey\ttitle\tpost\tposted\tfetched\tsha256\tartists\tterms",
    ]
    for src, dest, natdex, name, key, artists in ROWS:
        data = (PACK / src).read_bytes()
        (cw.EXPO_DIR / dest).write_bytes(data)
        digest = hashlib.sha256(data).hexdigest()
        title = name + " Overworld Sprite"
        lines.append("\t".join([dest, str(natdex), name, key, title, POST, "2026-10-02", "2026-10-08", digest, artists, TERMS]))
    (cw.EXPO_DIR / "sources.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
    sources = cw.darkus_sources(cw.EXPO_DIR)
    assets = cw.REPO / "app/src/main/assets" / cw.EXPO_SET
    table, credits, nbytes = cw.convert_expo(sources, {s.key for s in sources}, assets)
    esrc = "the Generation 9 Resource Pack v3.3.8 (eeveeexpo.com/resources/1101), tools/trainer-data/sources/gen9pack"
    cw.write_tsv(assets / (cw.EXPO_SET + ".tsv"), ["key", "animation", "w", "h", "x", "y", "durations"], [
        "Walking Pals from " + esrc + ",",
        "only where PMD Sprite Collab has no sheet of its own (natdex-map.tsv). Keyed as walkingpals-nat is.",
        "Written by tools/trainer-data/convert_walking_pals_nat.py: a doubled sheet at its own pixels, Falinks at the",
        "pixels it was drawn; the eight facings, each diagonal as the side view; walk the four frames from the first",
        "step, idle the two grounded poses. No sleep or faint: the app shows idle.",
        "Credit the artists (credits.tsv). x,y: offset from a 32x32 icon's corner; durations in game frames (60 a second).",
    ], table)
    cw.write_tsv(assets / "credits.tsv", ["key", "sprite", "post", "artists", "terms"], [
        "Who drew each sheet in this folder, from " + esrc + "/sources.tsv.",
        "The pack's page asks that the artists be credited. Caruban compiles the pack.",
    ], credits)
    cw.write_tsv(assets / "shiny-colors.tsv", ["key", "animations", "colors"], [
        "No shinies ship in this folder. A shiny walks in plain colors. The same columns as walkingpals-nat/shiny-colors.tsv.",
    ], [])
    cw.write_tsv(assets / "shiny.tsv", ["key", "animation", "w", "h", "x", "y", "durations"], [
        "No shinies ship in this folder (see shiny-colors.tsv). The same columns as walkingpals-expo.tsv.",
    ], [])
    print("wrote %d sheets, %.2f MB" % (len(table), nbytes / 1e6))


if __name__ == "__main__":
    main()
