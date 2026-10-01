"""Calc Atk's own math, run on many inputs, as a fixture for the native port (2026-09-29).

    python tools/parity/calcatk_fixture.py <CalcAtk-IronmonExtension folder> <out.tsv>

Runs self.calcLowHighStat() from UTDZac's CalcAtk.lua (v1.2, MIT) through lupa, with the
extension's screen buttons stubbed to plain values, over a seeded set of random inputs, and
writes each input with the low and high attacking stat the extension returns. CalcAtkTest holds
tracker-gba's CalcAtk to every row, so the port matches the extension's floating point and
floor() order exactly rather than a restatement of the formula.

Line format: level, damage, defense, power, effectiveness, other, stab, crit, weather, burned,
screen, low, high (TAB separated).
"""
import sys, re, random, pathlib
from lupa import LuaRuntime

src = (pathlib.Path(sys.argv[1]) / "CalcAtk.lua").read_text(encoding="utf-8")
out = pathlib.Path(sys.argv[2])
start = src.index("function self.calcLowHighStat()")
end = src.index("-- Other internal stuff, not involved with the calculations")
body = src[start:end]

lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute("""
MIN_STAT = 0
MAX_STAT = 999
CalcAtkScreen = { Buttons = {
  ValuePokemonLevel = {}, ValueDamageTaken = {}, ValuePokemonDefense = {}, ValueMovePower = {},
  ValueMoveEffectiveness = {}, ValueOtherMultiplier = {}, CheckboxStab = {}, CheckboxCrit = {},
  CheckboxWeather = {}, CheckboxBurn = {}, CheckboxScreenReflect = {},
  LabelConfidence = { checkAccuracyOfCalc = function() end },
} }
self = {}
""")
# The extension's locals MIN_STAT/MAX_STAT are globals here; the body is unchanged otherwise.
lua.execute(body)
calc = lua.eval("self.calcLowHighStat")
B = lua.eval("CalcAtkScreen.Buttons")

rng = random.Random(20260929)
TAB, NL = chr(9), chr(10)
rows = []
for _ in range(4000):
    level = rng.randint(1, 100)
    defense = rng.randint(1, 400)
    power = rng.choice([10, 15, 20, 25, 30, 35, 40, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 110, 120, 140, 150])
    eff = rng.choice([0.25, 0.5, 1, 1, 1, 2, 4])
    other = rng.choice([1, 1, 1, 1.5, 0.5])
    stab, crit = rng.random() < 0.4, rng.random() < 0.1
    weather = rng.choice([0, 0, 0, 1, 2])
    burned, screen = rng.random() < 0.1, rng.random() < 0.1
    damage = rng.randint(1, 250)
    B.ValuePokemonLevel.value = level
    B.ValueDamageTaken.value = damage
    B.ValuePokemonDefense.value = defense
    B.ValueMovePower.value = power
    B.ValueMoveEffectiveness.value = eff
    B.ValueOtherMultiplier.value = other
    B.CheckboxStab.toggleState = stab
    B.CheckboxCrit.toggleState = crit
    B.CheckboxWeather.state = weather
    B.CheckboxBurn.toggleState = burned
    B.CheckboxScreenReflect.toggleState = screen
    lo, hi = calc()
    rows.append([level, damage, defense, power, eff, other, int(stab), int(crit), weather, int(burned), int(screen), int(lo), int(hi)])

with open(out, "w", encoding="utf-8", newline=NL) as f:
    f.write("# level" + TAB + "damage" + TAB + "defense" + TAB + "power" + TAB + "effectiveness" + TAB + "other" + TAB
            + "stab" + TAB + "crit" + TAB + "weather" + TAB + "burned" + TAB + "screen" + TAB + "low" + TAB
            + "high  (CalcAtk.lua v1.2 calcLowHighStat via tools/parity/calcatk_fixture.py)" + NL)
    for r in rows:
        f.write(TAB.join(str(v) for v in r) + NL)
hits = sum(1 for r in rows if r[11] <= r[12])
print("rows", len(rows), "with an estimate", hits)
