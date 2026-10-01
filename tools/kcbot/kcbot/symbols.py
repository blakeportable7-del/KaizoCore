"""The games' memory maps, from pret's disassemblies (the `symbols` branch of each repository: pokered, pokeyellow,
pokecrystal, pokeruby, pokeemerald, pokefirered). They name every variable in the games' RAM; they hold nothing
from the games themselves. Kept in C:/Users/bepor/bot-ref/symbols/.

A Game Boy line reads `bank:address name` (`01:d4d9 wPartyCount`), a GBA line `address type size name`
(`02024284 g 00000258 gPlayerParty`).
"""
import os

SYMBOLS_DIR = os.environ.get('KCBOT_SYMBOLS', 'C:/Users/bepor/bot-ref/symbols')

FILES = {
    'red': 'pokered.sym', 'blue': 'pokeblue.sym', 'yellow': 'pokeyellow.sym',
    'crystal': 'pokecrystal.sym',
    'ruby': 'pokeruby.sym', 'sapphire': 'pokesapphire.sym', 'emerald': 'pokeemerald.sym',
    'firered': 'pokefirered.sym', 'leafgreen': 'pokeleafgreen.sym',
    'firered-v11': 'pokefirered_rev1.sym', 'leafgreen-v11': 'pokeleafgreen_rev1.sym',
}

SYSTEM_RAM = 0x02000000     # where the app's port puts the core's system RAM


class Symbols:
    def __init__(self, game):
        self.game = game
        self.gb = game in ('red', 'blue', 'yellow', 'crystal')
        self.table = {}
        with open(os.path.join(SYMBOLS_DIR, FILES[game]), encoding='utf-8', errors='replace') as f:
            for line in f:
                parts = line.split()
                if not parts or parts[0].startswith(';'):
                    continue
                if self.gb and len(parts) >= 2 and ':' in parts[0]:
                    bank, addr = parts[0].split(':')
                    self.table[parts[1]] = (int(bank, 16), int(addr, 16), None)
                elif not self.gb and len(parts) >= 4:
                    self.table[parts[3]] = (0, int(parts[0], 16), int(parts[2], 16))

    def __contains__(self, name):
        return name in self.table

    def size(self, name):
        return self.table[name][2]

    def port_address(self, name):
        """The address to read `name` at through the app's port."""
        bank, addr, _ = self.table[name]
        if not self.gb:
            return addr                                 # GBA bus addresses go through the core's memory map
        if 0xC000 <= addr < 0xD000:
            return SYSTEM_RAM + (addr - 0xC000)          # WRAM bank 0
        if 0xD000 <= addr < 0xE000:
            return SYSTEM_RAM + 0x1000 * max(bank, 1) + (addr - 0xD000)   # WRAM bank n (1 on a Game Boy game)
        raise ValueError(f'{name} at {bank:02x}:{addr:04x} is not in WRAM')
