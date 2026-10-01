-- kcbot: the Kaizo IronMON rules on top of PokeBot's engine, for Gen 1 (see KCBOT.md).
-- Loaded by this folder's main.lua right after PokeBot's own modules. The rules are the app's own text,
-- app/src/main/assets/rulesets/RBY/kaizo.md; each override below names the one it keeps.

local Battle = require "action.battle"
local Textbox = require "action.textbox"
local Walk = require "action.walk"
local Combat = require "ai.combat"
local Control = require "ai.control"
local Data = require "data.data"
local Movelist = require "data.movelist"
local Memory = require "util.memory"
local Menu = require "util.menu"
local Player = require "util.player"
local Strategies = require("ai."..Data.gameName..".strategies")

local status = function() return Strategies.status end

-- "No Killing Wild Pokémon" and "One Pokémon at a time": run from every wild battle, never catch.
Control.shouldFight = function() return false end
Control.shouldCatch = function() return false end

-- "No Healing items outside of Battle" (Blake, 2026-09-30: "You can use items but only in battle"). Battle.automate
-- drinks a potion when the next hit would kill and the potion saves it; the route has no potion stops outside battle.
Control.canRecover = function() return true end
Strategies.functions.potion = function() return true end

-- A run never resets the console: the lead fainting ends it, and the app's game-over popup starts the next seed.
-- Anything else PokeBot would reset for (stuck, an error in a strategy) ends the run too, with its reason.
local function over(reason)
	error("KCBOT_RUN_OVER "..tostring(reason or ""), 0)
end
-- The seed has just booted, so PokeBot's power cycle before a run is not needed either.
Strategies.reboot = function() end
Strategies.death = function() over("death") end
Strategies.reset = function(reason, explanation) over(tostring(reason).." "..tostring(explanation or "")) end
Strategies.hardReset = function(reason, message) over(tostring(reason).." "..tostring(message or "")) end

-- ---- Moves --------------------------------------------------------------------------------------------------------

-- Banned in Gen 1 Kaizo, by the game's move number: healing and draining moves (Absorb, Mega Drain, Leech Seed,
-- Recover, Soft-Boiled, Dream Eater, Leech Life, Rest), Spore, Wrap, Bind, Fire Spin and Clamp ("Rules updates for
-- Kaizo & up"), and the HM moves (Ultimate's "No HM Moves in Battle": Cut, Fly, Surf, Strength, Flash).
local BANNED = {
	[71]=true, [72]=true, [73]=true, [105]=true, [135]=true, [138]=true, [141]=true, [156]=true,
	[147]=true,
	[20]=true, [35]=true, [83]=true, [128]=true,
	[15]=true, [19]=true, [57]=true, [70]=true, [148]=true,
}
-- Not a rule, but a run of one Pokémon is over when it faints: Self-Destruct and Explosion never.
local SELF_KO = {[120]=true, [153]=true}
local OAKS_LAB = 40

local choosing = false
local function banned(id)
	if SELF_KO[id] then
		return true
	end
	-- "If your starter pokemon has a banned move, you may use it in the lab fight only."
	return BANNED[id] and Memory.value("game", "map") ~= OAKS_LAB
end

-- While PokeBot picks our move, a banned move reads as one with no PP left, so it is never picked; a move that
-- deals the user's level (Seismic Toss, Night Shade) or a fixed 40 (Dragon Rage) counts as what it does.
local getMove = Movelist.get
Movelist.get = function(id)
	local move = getMove(id)
	if not choosing or not move then
		return move
	end
	local copy = {}
	for k, v in pairs(move) do
		copy[k] = v
	end
	if banned(id) then
		copy.banned = true
		copy.pp = nil -- an earlier read left the PP on the shared table; this copy has none
		return setmetatable(copy, {
			__index = function(_, k) if k == "pp" then return 0 end end,
			__newindex = function(t, k, v) if k ~= "pp" then rawset(t, k, v) end end,
		})
	end
	if id == 69 or id == 101 then
		copy.fixed = Memory.value("battle", "our_level")
	elseif id == 82 then
		copy.fixed = 40
	end
	return copy
end

local bestMove = Combat.bestMove
Combat.bestMove = function(...)
	choosing = true
	local ok, move, turns, enemyTurns = pcall(bestMove, ...)
	choosing = false
	if not ok then
		error(move, 0)
	end
	if move or not Battle.isActive() or not Battle.opponentAlive() then
		return move, turns, enemyTurns
	end
	-- Nothing legal has PP. With every move out of PP the game uses Struggle on its own, which is allowed; with
	-- only a banned move left there is no legal way on, and the run ends here.
	local ours = Combat.activePokemon()
	for __, m in pairs(ours.moves) do
		if m.pp and bit.band(m.pp, 0x3F) > 0 then
			over("no legal move")
		end
	end
	return {midx=1, name="Struggle", accuracy=100}
end

-- ---- Evolution ----------------------------------------------------------------------------------------------------

-- Ultimate's "Let your Friends Grow": an evolution may not be stopped. Gen 1 stops one when B is pressed during its
-- animation, and PokeBot clears text with A and B in turn, so B is held back from the moment the game starts an
-- evolution (wEvolutionOccurred set, wEvoNewSpecies the new form) until the lead is the new form.
local function evolving()
	return Memory.raw(0x1121) == 1 and Memory.raw(0x116B) ~= Memory.raw(0x0EEA)
end
local setButtons = joypad.set
joypad.set = function(buttons, ...)
	if buttons and buttons.B and evolving() then
		buttons.B = nil
	end
	return setButtons(buttons, ...)
end

-- ---- Strategies the route uses ------------------------------------------------------------------------------------

-- A trainer fight: the best legal move until it ends. With `wait`, a fight that has not started after that many
-- frames counts as done (the trainer did not see us, or was already beaten); a fight that starts later is still
-- fought, by main.lua's own battle handling.
Strategies.functions.ironmonFight = function(data)
	local s = status()
	if not s.kcStart then
		s.kcStart = emu.framecount()
	end
	if Strategies.trainerBattle() then
		Battle.automate()
	elseif s.foughtTrainer then
		return true
	elseif data.wait and emu.framecount() - s.kcStart > data.wait then
		return true
	end
end

-- A Pokémon Center outside a dungeon: in through the door at (x, y) of `map`, heal at the counter, out, and on to
-- `finishX`. PokeBot's own centers count Horn Attack PP and put Pikachu in the PC, so this one is plain.
Strategies.functions.ironmonCenter = function(data)
	local s = status()
	local px, py = Player.position()
	local dx, dy = px, py
	if Memory.value("game", "map") == data.map then
		if not s.healed then
			if px ~= data.x then
				dx = data.x
			else
				dy = data.y
			end
		else
			if not data.finishX or px == data.finishX then
				return true
			end
			dx = data.finishX
		end
	else
		-- Inside: the nurse is behind the counter, spoken to from (3, 3) facing up; the door is at the bottom.
		if s.healed then
			if Textbox.handle() then
				dy = 8
			end
		elseif px ~= 3 then
			if Menu.close() then
				dx = 3
			end
		elseif py > 3 then
			dy = 3
		else
			if Strategies.functions.dialogue({dir="Up"}) then
				s.healed = true
			end
			return false
		end
	end
	Walk.step(dx, dy)
end

-- The route has run out: the run is still alive, and the runner moves on to the next seed.
Strategies.functions.routeEnd = function()
	error("KCBOT_ROUTE_END", 0)
end
