"""What a Gen 3 game (Ruby, Sapphire, Emerald, FireRed, LeafGreen) is doing, read through the app's test port.

Layouts from pret's pokeemerald and pokefirered (struct Pokemon, struct BattlePokemon, struct SaveBlock1); the
addresses from their symbol files (kcbot/symbols.py). Read-only: nothing here writes to the game.
"""
import struct

from .symbols import Symbols

PARTY_MON_SIZE = 100
BATTLE_MON_SIZE = 0x58

# The four 12-byte substructures of a Pokémon's secure data are stored in one of 24 orders, picked by personality
# % 24; each letter is the position of Growth, Attacks, EVs, Misc.
ORDERS = ['GAEM', 'GAME', 'GEAM', 'GEMA', 'GMAE', 'GMEA', 'AGEM', 'AGME', 'AEGM', 'AEMG', 'AMGE', 'AMEG',
          'EGAM', 'EGMA', 'EAGM', 'EAMG', 'EMGA', 'EMAG', 'MGAE', 'MGEA', 'MAGE', 'MAEG', 'MEGA', 'MEAG']


class Gen3:
    def __init__(self, port, game):
        self.port = port
        self.sym = Symbols(game)
        self.by_address = {}
        for name, (_, addr, size) in self.sym.table.items():
            self.by_address.setdefault(addr, name)

    def read(self, name, offset=0, size=None):
        size = size or self.sym.size(name)
        return self.port.mem(self.sym.port_address(name) + offset, size)

    def u32(self, name, offset=0):
        return struct.unpack('<I', self.read(name, offset, 4))[0]

    # ---- what is on screen --------------------------------------------------------------------------------

    def callback2(self):
        """The name of gMain.callback2, the game's current main loop (CB2_Overworld, BattleMainCB2, ...)."""
        ptr = self.u32('gMain', 4) & ~1        # a Thumb function's address has its low bit set
        return self.by_address.get(ptr, hex(ptr))

    def state(self):
        cb = self.callback2().upper()
        if cb == 'CB2_OVERWORLD':
            return 'overworld'
        if cb == 'BATTLEMAINCB2':
            return 'battle'
        if cb in ('CB2_INITBATTLE', 'CB2_HANDLESTARTBATTLE', 'CB2_OVERWORLDBASIC'):
            return 'battle-starting'
        if cb in ('CB2_ENDWILDBATTLE',):
            return 'battle-ending'
        if cb in ('CB2_LOADMAP', 'CB2_LOADMAP2', 'CB2_DOCHANGEMAP'):
            return 'changing-map'
        if cb in ('CB2_STARTERCHOOSE', 'CB2_CHOOSESTARTER'):
            return 'choose-starter'
        if cb in ('CB2_TITLESCREENRUN', 'CB2_INITTITLESCREEN', 'CB2_INTRO', 'CB2_SETUPINTRO', 'MAINCB2', 'MAINCB2_INTRO',
                  'CB2_INITCOPYRIGHTSCREENAFTERBOOTUP', 'CB2_WAITFADEBEFORESETUPINTRO', 'CB2_INITMAINMENU'):
            return 'title'
        if cb == 'CB2_MAINMENU':
            return 'main-menu'
        if cb == 'CB2_WHITEOUT':
            return 'whiteout'
        return cb.lower()

    # ---- where the player is ------------------------------------------------------------------------------

    def position(self):
        """(map group, map number, x, y) from SaveBlock1."""
        base = self.u32('gSaveBlock1Ptr')
        b = self.port.mem(base, 8)
        x, y = struct.unpack_from('<hh', b, 0)
        return b[4], b[5], x, y

    # ---- the party ----------------------------------------------------------------------------------------

    def party(self):
        """The player's team: dicts of species (national dex order as the game stores it), level, hp, max_hp."""
        count = self.read('gPlayerPartyCount', 0, 1)[0]
        raw = self.read('gPlayerParty', 0, PARTY_MON_SIZE * 6)
        return [self._mon(raw[i * PARTY_MON_SIZE:(i + 1) * PARTY_MON_SIZE]) for i in range(min(count, 6))]

    @staticmethod
    def _mon(b):
        personality, ot_id = struct.unpack_from('<II', b, 0)
        key = personality ^ ot_id
        data = bytearray(b[0x20:0x50])
        for i in range(0, 48, 4):
            word = struct.unpack_from('<I', data, i)[0] ^ key
            struct.pack_into('<I', data, i, word)
        order = ORDERS[personality % 24]
        growth = data[order.index('G') * 12:order.index('G') * 12 + 12]
        species = struct.unpack_from('<H', growth, 0)[0]
        level = b[0x54]
        hp, max_hp = struct.unpack_from('<HH', b, 0x56)
        return {'species': species, 'level': level, 'hp': hp, 'max_hp': max_hp}

    # ---- the battle ---------------------------------------------------------------------------------------

    def battle_mons(self):
        """The four battlers (player's, enemy's, and their partners in doubles): species, level, hp, max_hp."""
        raw = self.read('gBattleMons', 0, BATTLE_MON_SIZE * 4)
        out = []
        for i in range(4):
            m = raw[i * BATTLE_MON_SIZE:(i + 1) * BATTLE_MON_SIZE]
            species = struct.unpack_from('<H', m, 0x00)[0]
            hp = struct.unpack_from('<H', m, 0x28)[0]
            level = m[0x2A]
            max_hp = struct.unpack_from('<H', m, 0x2C)[0]
            out.append({'species': species, 'level': level, 'hp': hp, 'max_hp': max_hp})
        return out

    def battle_outcome(self):
        """0 in battle, 1 won, 2 lost, 3 drew, 4 ran, 7 caught (pokefirered's B_OUTCOME_*)."""
        return self.read('gBattleOutcome', 0, 1)[0]
