-- KaizoCore for BizHawk: Heart & Soul sessions.
-- Copyright (C) 2026 Blake Porterfield. Part of KaizoCore.
-- This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
-- License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
-- version. It is distributed WITHOUT ANY WARRANTY; see LICENSE.txt in this folder or <https://www.gnu.org/licenses/>.
-- It contains no code from besteon's Ironmon-Tracker; it follows that tracker's New Run file names and button combo.
--
-- Load this from BizHawk's Lua Console (Script > Open Script) for a Heart & Soul session, in place of
-- Ironmon-Tracker.lua. It needs KaizoCore PC running (KaizoCore-PC.jar), in the same folder as this file.
--
-- What it does:
--   * checks the loaded game is a KaizoCore Heart & Soul build (header "POKEMON HNS", and the hash KaizoCore PC
--     knows for the run it made);
--   * hands the game's RAM to KaizoCore PC by plain files a few times a second, at the tracker's pace;
--   * New Run: the same button combo as besteon's Ironmon-Tracker (A, B, Start unless you changed it there), which
--     opens the next run KaizoCore PC has already made, and counts the attempt;
--   * the tracker panel beside the game, clicks and notes, the game over card (Retry the battle loads the state the
--     battle began from, as besteon's does; Save this attempt writes a save state into the run's folder);
--   * the log viewer drawn over the game area when the card's Inspect the log is clicked, closed with its X, turned
--     with the mouse wheel, searched through a text box (as besteon's LogOverlay);
--   * the favorites on the start screen and the notes, typed on the panel's own keyboard with the mouse (KaizoCore PC
--     draws it; nothing typed reaches the game); the same favorites for every run, and KaizoCore PC's own window has a
--     Favorites button too;
--   * Play as your Pokemon: the player walks as the lead Pokemon (or one picked in the gear, or your own picture),
--     drawn over the game where the game puts the player, the trainer hidden (Sprite Is Me by UTDZac, MIT);
--   * New Run from KaizoCore PC's window, which also works with no game open in BizHawk yet (BizHawk reads no buttons
--     then, so the combo cannot be pressed): the very first run needs no game opened by hand.
-- It never stops the emulator when KaizoCore PC is not running: it says so in BizHawk's message area and waits.
--
-- No launch flags, no sockets: it only reads and writes files in the "session" folder beside this script.

local KC = {
	version = "0.8.0",
	frame = 0,
	gen = 0,            -- KaizoCore PC's number for the game it is tracking; 0 = not tracking
	romHash = nil,
	isHnS = false,
	jar = nil,          -- last jar.txt, parsed
	jarAlive = false,
	jarStart = nil,
	seq = math.floor((os.time() % 100000) * 1000),
	comboAllowedAt = 60, -- besteon waits a second after a load before the combo counts (Input.initialize)
	confirmUntil = 0, -- a second hold of the New Run combo before this frame ends the attempt
	nextSnapAt = 0,
	snaps = 0,
	lastHelloAt = -1000,
	warnedAt = -100000,
	binarySafe = nil,
}

local DEFAULT_COMBO = "Start, A, B"
local SRC = debug.getinfo(1, "S").source
local HERE = ((SRC:sub(1, 1) == "@") and SRC:sub(2) or SRC):match("(.*[/\\])") or ""
local SESSION = (KAIZOCORE_SESSION or (HERE .. "session")) .. "/"
local TEST = KAIZOCORE_TEST -- set only by the automated checks in bizhawk/test/

local function now() return os.clock() end

-- Every [n] frames at normal speed, as often in real time when BizHawk runs faster (besteon's clientFpsMultiplier):
-- at 4x or 16x the files and the RAM are looked at no more often a second than at 1x, so the game keeps its speed.
local function every(n, phase)
	local p = math.max(1, math.floor(n * (KC.speed or 1) + 0.5))
	return (KC.frame - (phase or 0)) % p == 0
end

-- BizHawk's mouse, read once a frame however many places ask for it.
local function frameMouse()
	if not KC.haveMouse then
		KC.haveMouse = true
		local ok, m = pcall(function()
			if TEST and TEST.mouse then return TEST.mouse(KC) end
			return input.getmouse()
		end)
		KC.mouse = (ok and type(m) == "table") and m or {}
	end
	return KC.mouse
end

-- The panel's keyboard (further down), used by the in-BizHawk card before it is defined.
local live, drawLive

local function readFile(path)
	local f = io.open(path, "rb")
	if not f then return nil end
	local s = f:read("*a")
	f:close()
	return s
end

local function parse(text)
	local t = {}
	for line in (text or ""):gmatch("[^\r\n]+") do
		local k, v = line:match("^([^=]+)=(.*)$")
		if k then t[k] = v end
	end
	return t
end

local function say(msg)
	console.log("KaizoCore: " .. msg)
	pcall(gui.addmessage, "KaizoCore: " .. msg)
	if TEST and TEST.onSay then TEST.onSay(msg) end
end

-- Writes to a temp name, then renames, so KaizoCore PC never reads half a file.
local function writeAtomic(name, ...)
	local tmp = SESSION .. name .. ".tmp"
	local f = io.open(tmp, "wb")
	if not f then return false end
	for _, part in ipairs({ ... }) do f:write(part) end
	f:close()
	os.remove(SESSION .. name)
	return os.rename(tmp, SESSION .. name) ~= nil
end

-- KaizoCore PC holds jar.lock for as long as it runs, and Windows refuses a read of it then. One byte read here, from a
-- file that does not change, is how the script knows KaizoCore PC is alive. Until 2026-10-09 jar.txt carried the time,
-- rewritten every second, and the first open of each new jar.txt waited on Windows Defender's scan, about 20 ms on
-- BizHawk's emulation thread: a hitch in the game every second.
local function jarLockHeld()
	local f = io.open(SESSION .. "jar.lock", "rb")
	if not f then return false end
	local size = f:seek("end")
	if (size or 0) < 1 then f:close(); return false end
	f:seek("set", 0)
	local b = f:read(1)
	f:close()
	return b == nil
end

local function readJar()
	local text = readFile(SESSION .. "jar.txt")
	if TEST and TEST.dropJarRead and TEST.dropJarRead(KC) then text = nil end
	local j = text and parse(text)
	if (j and j.lock == "1") or (not j and KC.jar and KC.jar.lock == "1") then
		if j then KC.jar = j end
		KC.jarAlive = jarLockHeld()
		if KC.jarAlive and KC.jar.start ~= KC.jarStart then
			KC.jarStart = KC.jar.start
			KC.gen = 0
		end
		return
	end
	if not (j and tonumber(j.alive)) then
		-- A read that failed (KaizoCore PC replacing the file that moment, or Windows holding it for a moment) is not a
		-- stopped KaizoCore PC: the last good heartbeat stands until it is more than 3 seconds old. Until 2026-10-07 one
		-- failed read said "KaizoCore PC stopped", dropped the panel for a beat and lost a click made in it.
		KC.jarAlive = KC.jar ~= nil and (tonumber(KC.jar.alive) or 0) >= os.time() - 3
		return
	end
	KC.jar = j
	KC.jarAlive = (tonumber(j.alive) or 0) >= os.time() - 3
	if KC.jarAlive and j.start ~= KC.jarStart then
		-- KaizoCore PC started (again): it tracks nothing until it is told what is loaded.
		KC.jarStart = j.start
		KC.gen = 0
	end
end

local function combo()
	return (KC.jar and KC.jar.combo and KC.jar.combo ~= "") and KC.jar.combo or DEFAULT_COMBO
end

-- One request at a time; waits for the answer (a few milliseconds when the next run is ready).
local function request(cmd, fields, timeoutSec)
	if not KC.jarAlive then return nil, "KaizoCore PC is not running" end
	KC.seq = KC.seq + 1
	local lines = { "seq=" .. KC.seq, "cmd=" .. cmd }
	for k, v in pairs(fields or {}) do lines[#lines + 1] = k .. "=" .. tostring(v) end
	os.remove(SESSION .. "jar.resp")
	if not writeAtomic("lua.req", table.concat(lines, "\n"), "\n") then
		return nil, "cannot write to " .. SESSION
	end
	local deadline = now() + (timeoutSec or 5)
	while now() < deadline do
		local text = readFile(SESSION .. "jar.resp")
		-- Spin for the first 200 ms (a ready run answers in a few), then sleep between looks.
		if not text and now() > deadline - (timeoutSec or 5) + 0.2 then pcall(client.sleep, 1) end
		if text then
			local r = parse(text)
			if tonumber(r.seq) == KC.seq then
				os.remove(SESSION .. "jar.resp")
				return r
			end
		end
	end
	os.remove(SESSION .. "lua.req")
	return nil, "KaizoCore PC did not answer"
end

-- The file BizHawk loaded: its most recent ROM ("*OpenRom*C:\..."); besteon's LuaImp lookup is gone in 2.11.
local function romPath()
	local ok, path = pcall(function() return client.getconfig().RecentRoms.MostRecent end)
	if ok and type(path) == "string" and path ~= "" then
		return (path:gsub("^%*OpenRom%*", ""))
	end
	return ""
end

-- The ROM addresses the script reads in each KaizoCore build, newest first (the RAM is the same in both): gTMHMItemMoveIds,
-- gMovesInfo, CB2_Overworld with its thumb bit, and the ability pop-up scripts (BattleScript_AbilityPopUpTarget up to
-- sZero). A build is known by TM01's record (item 582) at its own gTMHMItemMoveIds.
local BUILDS = {
	{ crc = "F4E28E3D", tmhm = 0x0078E894, moves = 0x007B57D0, overworld = 0x0819A7DD, popup = { 0x0829183D, 0x082918A3 } },
	{ crc = "AA092306", tmhm = 0x0078E874, moves = 0x007B57B0, overworld = 0x0819A7C1, popup = { 0x0829181D, 0x08291883 } },
	{ crc = "1C824440", tmhm = 0x0078E554, moves = 0x007B5490, overworld = 0x0819A74D, popup = { 0x082917AD, 0x08291813 } },
	{ crc = "CDC719C2", tmhm = 0x0078E5E4, moves = 0x007B5520, overworld = 0x0819A7DD, popup = { 0x0829183D, 0x082918A3 } },
	{ crc = "09405F53", tmhm = 0x0078F184, moves = 0x007B60E8, overworld = 0x0819B195, popup = { 0x0829240B, 0x08292471 } },
	{ crc = "00CCF118", tmhm = 0x0078F104, moves = 0x007B6068, overworld = 0x0819B115, popup = { 0x0829238B, 0x082923F1 } },
	{ crc = "C4684999", tmhm = 0x0078F0F4, moves = 0x007B6058, overworld = 0x0819B115, popup = { 0x0829238B, 0x082923F1 } },
	{ crc = "17C288C4", tmhm = 0x0078F0D4, moves = 0x007B6038, overworld = 0x0819B0ED, popup = { 0x08292363, 0x082923C9 } },
}

local function romBuild()
	for _, b in ipairs(BUILDS) do
		local ok, item = pcall(memory.read_u16_le, b.tmhm + 4, "ROM")
		if ok and item == 582 then return b end
	end
	return nil
end

-- The header's game title. Heart & Soul keeps Emerald's game code but its title reads "POKEMON HNS".
local function detect()
	KC.romHash = gameinfo.getromhash()
	local oks, sys = pcall(emu.getsystemid)
	KC.noGame = not oks or sys == nil or sys == "" or sys == "NULL"
	if TEST and TEST.noGame ~= nil then KC.noGame = TEST.noGame end
	if TEST and TEST.onDetect then TEST.onDetect(KC.romHash, KC.frame) end
	local ok, title = pcall(memory.read_bytes_as_binary_string, 0xA0, 12, "ROM")
	KC.isHnS = ok and title ~= nil and title:sub(1, 11) == "POKEMON HNS"
	KC.build = KC.isHnS and romBuild() or nil
	KC.gen = 0
	KC.comboAllowedAt = KC.frame + 60
	KC.styleOn = false
	KC.devPcDone = false
	if dropBattleState then dropBattleState() end
end

local function hello()
	KC.lastHelloAt = KC.frame
	if not KC.isHnS then return end
	local r, err = request("hello", { hash = KC.romHash, path = romPath(), name = gameinfo.getromname(), quiet = 1 }, 5)
	if not r then return end
	if r.ok == "1" then
		KC.gen = tonumber(r.gen) or 0
		KC.refusedHash = nil
	else
		-- Asked once per game: a game KaizoCore PC cannot track stays that way until another one loads. Until
		-- 2026-10-07 the script asked again every two seconds, said so each time and held BizHawk while it waited.
		KC.gen = 0
		KC.refusedHash = KC.romHash
		say((r.why or "this game cannot be tracked") .. " Press New Run (" .. combo() .. ") for a KaizoCore run.")
	end
end

-- Raw RAM bytes need a binary-safe string from BizHawk; checked once.
-- The RAM a Heart & Soul read touches (traced from GbaTracker.read, overworld and battle), not all 288 KB.
-- base 0 is a full GBA address. base 1 and 2 are offsets from gSaveBlock1Ptr and gSaveBlock2Ptr, which move.
-- The party is all six slots even when fewer are filled: the trace only walked the one Pokemon in the fixture.
-- Five reads, not twenty-five. Each BizHawk memory call stalls the emulator, so nearby bytes are taken together.
-- The first block includes gSpecialVar_Result (0x02001766), which the starter yes/no and its info card need.
local RAM_RANGES = {
	{0, 0x02000000, 0x1E20},
	{0, 0x02034760, 0x7B0},
	{0, 0x03001DAC, 0x2430},
	{1, 0, 7612},
	{2, 0, 176},
}
local SB1_PTR, SB2_PTR = 0x030041D8, 0x030041D4

local function readAbs(addr, len)
	local dom, off
	if addr >= 0x02000000 and addr + len <= 0x02040000 then dom, off = "EWRAM", addr - 0x02000000
	elseif addr >= 0x03000000 and addr + len <= 0x03008000 then dom, off = "IWRAM", addr - 0x03000000
	else return nil end
	local b = memory.read_bytes_as_binary_string(off, len, dom)
	if #b ~= len then return nil end
	return b
end

local function readU32(addr)
	local b = readAbs(addr, 4)
	if not b then return 0 end
	local a, c, d, e = string.byte(b, 1, 4)
	return a + c * 256 + d * 65536 + e * 16777216
end

local function le32(n)
	return string.char(n % 256, math.floor(n / 256) % 256, math.floor(n / 65536) % 256, math.floor(n / 16777216) % 256)
end

local function le16(n)
	return string.char(n % 256, math.floor(n / 256) % 256)
end

local function snapshot()
	local probe = readAbs(0x02000000, 4)
	if KC.binarySafe == nil then
		KC.binarySafe = probe ~= nil
		if not KC.binarySafe then say("this BizHawk returns RAM as text; tracking is off") end
	end
	if not KC.binarySafe then return false end
	-- No look at ram.bin first. Opening it is what makes Windows Defender scan it, and this open waited on that scan on
	-- BizHawk's thread (20 to 30 ms, 2026-10-09). KaizoCore PC takes ram.bin within a few milliseconds of its arrival;
	-- one still here is replaced by this newer snapshot, and a rename that fails is tried again 8 frames later.
	local sb1 = readU32(SB1_PTR)
	local sb2 = readU32(SB2_PTR)
	if sb1 < 0x02000000 or sb1 >= 0x02040000 then sb1 = 0 end
	if sb2 < 0x02000000 or sb2 >= 0x02040000 then sb2 = 0 end
	local parts = { "KCRAM ", tostring(KC.gen), "\n" }
	for _, r in ipairs(RAM_RANGES) do
		local addr = r[2]
		if r[1] == 1 then addr = sb1 ~= 0 and (sb1 + r[2]) or 0
		elseif r[1] == 2 then addr = sb2 ~= 0 and (sb2 + r[2]) or 0 end
		if addr ~= 0 then
			local b = readAbs(addr, r[3])
			if b then parts[#parts + 1] = le32(addr); parts[#parts + 1] = le16(r[3]); parts[#parts + 1] = b end
		end
	end
	parts[#parts + 1] = le32(0)
	local f = io.open(SESSION .. "ram.tmp", "wb")
	if not f then return false end
	f:write(table.concat(parts))
	f:close()
	os.remove(SESSION .. "ram.bin")
	if not os.rename(SESSION .. "ram.tmp", SESSION .. "ram.bin") then return false end
	KC.snaps = KC.snaps + 1
	return true
end

local function comboHeld(joy)
	if type(joy) ~= "table" then return false end
	local any = false
	for button in string.gmatch(combo(), "([^,%s]+)") do
		if not joy[button] then return false end
		any = true
	end
	return any
end

-- New Run: KaizoCore PC hands over the run it made ahead; BizHawk opens it.
local function newRun()
	local t0 = now()
	readJar()
	if not KC.jarAlive then
		say("KaizoCore PC is not running, so New Run cannot open the next game. Start KaizoCore PC.")
		KC.comboAllowedAt = KC.frame + 60
		return
	end
	local r, err = request("newrun", {}, 35)
	local t1 = now()
	if not r or r.ok ~= "1" then
		say("New Run failed: " .. ((r and r.why) or err or "no answer"))
		KC.comboAllowedAt = KC.frame + 60
		return
	end
	-- As besteon's Main.LoadRom: a backup state of the run being left, sound off while loading.
	if KC.isHnS and KC.jar and KC.jar.backup and KC.jar.backup ~= "" then
		pcall(savestate.save, KC.jar.backup, true)
	end
	local t2 = now()
	local sound = client.GetSoundOn()
	client.SetSoundOn(false)
	client.openrom(r.path)
	local t3 = now()
	emu.frameadvance()
	KC.frame = KC.frame + 1
	-- Read the new game's title and hash once it has run a frame, so neither can be the old game's.
	detect()
	KC.gen = tonumber(r.gen) or 0
	client.SetSoundOn(sound)
	local t4 = now()
	say(string.format("attempt %s", r.attempt or "?"))
	console.log(string.format("KaizoCore: New Run %.0f ms (ask %.0f, backup %.0f, open %.0f, first frame %.0f)",
		(t4 - t0) * 1000, (t1 - t0) * 1000, (t2 - t1) * 1000, (t3 - t2) * 1000, (t4 - t3) * 1000))
	if TEST and TEST.onNewRun then
		TEST.onNewRun({ total = t4 - t0, ask = t1 - t0, backup = t2 - t1, open = t3 - t2, first = t4 - t3,
			attempt = tonumber(r.attempt), path = r.path, isHnS = KC.isHnS, hash = KC.romHash })
	end
	-- BizHawk's hash of the game it opened (SHA-1) must be the run KaizoCore PC made; else ask it what this is.
	if not r.sha1 or r.sha1:upper() ~= tostring(KC.romHash):upper() then
		KC.gen = 0
		hello()
	end
	KC.lastHelloAt = KC.frame
end

-- The panel beside the game, the way besteon's tracker does it: BizHawk's screen widened by 150 game pixels on the
-- right (client.SetGameExtraPadding), and the mouse read as besteon's Input.lua reads it (input.getmouse in game
-- pixels, x 240 and on is the panel): a left click goes to KaizoCore PC as panel pixels, a right click opens the note
-- of the Pokemon the panel shows. The picture itself is drawn at the window's scale: the script tells KaizoCore PC the
-- scale (panel.want, from client.transformPoint, the padded area's size in client pixels over its 150), KaizoCore PC
-- draws a 150 by 160 times that picture, and the script draws it 1:1 on the "client" surface (window pixels, no
-- scaling), so every glyph stays a hard pixel edge at 2x, 3x or any window size.
local PANEL_X, PANEL_W, PANEL_H = 240, 150, 160
local panel = { seq = nil, file = nil, species = 0, note = "", scale = 1, padded = false, lastLeft = false, lastRight = false, form = nil,
	wantScale = nil, rect = nil, lastWheel = nil, typing = "0", logtype = "0", kind = "", typed = "", logq = "" }
-- The log viewer over the game area (overlay.txt): drawn at the game's 240 by 160 on the client surface while it is open.
local overlay = { seq = nil, file = nil, rect = nil }

-- BizHawk 2.11 keeps what was drawn on the client surface until it is cleared: a box drawn there for 120 frames was
-- still on screen 180 frames after the script stopped drawing it (2026-10-09). So the surface is cleared whenever what
-- is drawn on it changes (the log viewer or GachaMon card opened or closed, the repel's steps, a new size), and
-- everything is drawn again that same frame. Without it a closed GachaMon card stayed over the game.
local clientKey = nil
local function clientFresh(key)
	if key == clientKey then return end
	if clientKey ~= nil then pcall(gui.clearGraphics, "client") end
	clientKey = key
end

local function clientState(r)
	local g = overlay.rect
	return (overlay.file and "o" or "-") .. ":" .. tostring(panel.repel or 0) .. ":"
		.. (g and (g.x .. "," .. g.y .. "," .. g.w .. "," .. g.h) or "") .. ":" .. (r and (r.x .. "," .. r.w) or "")
end

local function unescape(s)
	return (s:gsub("\\(.)", function(c) if c == "n" then return "\n" elseif c == "r" then return "\r" else return c end end))
end

local function escape(s)
	return (s:gsub("\\", "\\\\"):gsub("\n", "\\n"):gsub("\r", "\\r"))
end

-- Retry the battle, besteon's way (GameOverScreen.createTempSaveState / loadTempSaveState): the state a battle began
-- from is kept in memory from the moment the panel says a battle is on (panel.txt battle=1) until the next battle, a
-- new game or Retry. lua.txt tells KaizoCore PC whether one is held, so the card offers Retry only then.
local battle = { on = false, state = nil, written = nil }

-- lua.txt also says the script is alive (its clock) and what is open (none, other, hns), about once a second, so
-- KaizoCore PC's window knows the script runs before any Heart & Soul game does and can offer New Run.
local function writeLuaState(force)
	local game = KC.noGame and "none" or (KC.isHnS and "hns" or "other")
	local text = "battlestate=" .. (battle.state and "1" or "0") .. "\ngame=" .. game .. "\n"
	if text ~= battle.written or force then
		battle.written = text
		writeAtomic("lua.txt", text, "alive=", tostring(os.time()), "\n")
	end
end

function dropBattleState()
	if battle.state then pcall(memorysavestate.removestate, battle.state) end
	battle.state = nil
	battle.on = false
	writeLuaState()
end

local function battleFlag(on)
	if on and not battle.on then
		if battle.state then pcall(memorysavestate.removestate, battle.state) end
		local ok, id = pcall(memorysavestate.savecorestate)
		battle.state = ok and id or nil
		writeLuaState()
	end
	battle.on = on
end

-- One thing KaizoCore PC asked BizHawk to do (jar.cmd): retry (load the battle's state), savestate (Save this attempt).
local function takeCmd()
	local text = readFile(SESSION .. "jar.cmd")
	if not text then return end
	os.remove(SESSION .. "jar.cmd")
	local c = parse(text)
	if c.cmd == "retry" then
		if battle.state then
			pcall(memorysavestate.loadcorestate, battle.state)
			say("back to the start of the battle")
		end
		dropBattleState()
	elseif c.cmd == "savestate" and c.path then
		local ok = pcall(savestate.save, c.path)
		pcall(client.saveram)
		say(ok and "attempt saved" or "could not save the state")
	elseif c.cmd == "search" then
		openSearch(unescape(c.text or ""))
	elseif c.cmd == "newrun" then
		-- KaizoCore PC's New Run button; one left from before this script started is not acted on.
		if os.time() - (tonumber(c.at) or 0) <= 10 then newRun() end
	elseif c.cmd == "continue" and c.path then
		-- The same seed again. The attempt count stays, and BizHawk loads the save that belongs to this file.
		if os.time() - (tonumber(c.at) or 0) <= 10 then
			pcall(client.saveram)
			local sound = client.GetSoundOn()
			client.SetSoundOn(false)
			client.openrom(c.path)
			emu.frameadvance()
			KC.frame = KC.frame + 1
			detect()
			hello()
			client.SetSoundOn(sound)
			say("continuing attempt " .. (c.attempt or "?"))
		end
	end
	if TEST and TEST.onCmd then TEST.onCmd(c) end
end

local function setPadding(on)
	if on == panel.padded then return end
	pcall(client.SetGameExtraPadding, 0, 0, on and PANEL_W or 0, 0)
	panel.padded = on
end

-- A new picture is drawn over the one before it for its first frames, and only the cache's oldest pictures are let go,
-- and only now and then: until 2026-10-07 every new picture emptied BizHawk's image cache first (gui.clearImageCache),
-- so the picture on screen was let go before the new one had been loaded, and any new picture BizHawk could not open
-- at once (Windows or a virus scanner still holding the new file) left the panel empty for that frame: a flash on most
-- clicks. Now a picture that cannot be drawn falls back to the last one that could, and the cache is emptied once every
-- CACHE_KEEP pictures, after the new one has been read whole. KC.drawFails and KC.blankFrames count what happened.
local CACHE_KEEP = 24

local function readable(path)
	local f = path and io.open(path, "rb")
	if not f then return false end
	local size = f:seek("end")
	f:close()
	return (size or 0) > 54
end

local function newPicture(holder, file)
	holder.prev = holder.good or holder.prev
	holder.file = file
	holder.underUntil = KC.frame + 2
	holder.pictures = (holder.pictures or 0) + 1
end

-- Draws [holder]'s picture at [r], the last good one under it for its first frames and in its place when it cannot be
-- drawn; a frame with neither is counted as blank.
local function drawPicture(holder, r)
	if holder.prev and holder.prev ~= holder.file and KC.frame <= (holder.underUntil or 0) then
		pcall(gui.drawImage, holder.prev, r.x, r.y, r.w, r.h, true)
	end
	-- The same file is already in BizHawk's cache. Do not open it on disk again every frame.
	if holder.file and holder.file == holder.good and pcall(gui.drawImage, holder.file, r.x, r.y, r.w, r.h, true) then
		return
	end
	if holder.file and readable(holder.file) and pcall(gui.drawImage, holder.file, r.x, r.y, r.w, r.h, true) then
		holder.good = holder.file
		-- The picture is on screen. Every CACHE_KEEP new pictures, drop the ones BizHawk is still holding,
		-- on the next frame, so this one is not thrown away before it has been seen.
		if holder.pictures and holder.pictures % 120 == 0 and holder.kept ~= holder.pictures then
			holder.kept = holder.pictures
			-- Besteon waits before gui.clearImageCache so the picture just drawn is not dropped first.
			holder.dropAt = KC.frame + 30
		end
		return
	end
	KC.drawFails = (KC.drawFails or 0) + 1
	if holder.good and holder.good ~= holder.file and pcall(gui.drawImage, holder.good, r.x, r.y, r.w, r.h, true) then return end
	if holder.prev and pcall(gui.drawImage, holder.prev, r.x, r.y, r.w, r.h, true) then return end
	KC.blankFrames = (KC.blankFrames or 0) + 1
	if KC.blankFrames % 100 == 1 then console.log("KaizoCore: a panel picture could not be drawn (" .. tostring(holder.file) .. ")") end
end

local gameRect

-- Drawing.drawRepelUsage: the spray-can icon on the game's top right, then a 4 by 21 bar of what is left.
local function drawRepel(panelRect)
	local steps = panel.repel or 0
	if steps <= 0 or not panelRect then return end
	local g = gameRect()
	local s = (g.w or 0) / PANEL_X
	if s <= 0 then return end
	local x = g.x + g.w - 24 * s
	local y = g.y
	pcall(gui.drawImage, HERE .. "repelUsage.png", x, y, 16 * s, 16 * s, true)
	local dur = panel.repelDur or 100
	if dur <= 0 then dur = 100 end
	local frac = steps / dur
	if frac > 1 then frac = 1 end
	local bh = 21 * s
	local filled = bh * frac
	local color = 0xFF18C020
	if frac <= 0.25 then color = 0xFFE02020 elseif frac <= 0.5 then color = 0xFFE0C020 end
	local bx = x + 18 * s
	local by = y + s
	pcall(gui.drawRectangle, bx, by, 4 * s, bh, 0xFF000000, 0x66000000)
	pcall(gui.drawRectangle, bx, by + bh - filled, 4 * s, filled, 0x00000000, color)
end

local function readPanel()
	local text = readFile(SESSION .. "panel.txt")
	-- A failed read is KaizoCore PC replacing the file that moment, not a panel gone: keep the last one. Until 2026-10-07
	-- it dropped the panel, so BizHawk took the padding away and put it back, and the whole screen flashed.
	if not text then return end
	local t = parse(text)
	if t.seq ~= panel.seq then
		panel.seq = t.seq
		newPicture(panel, t.file and (SESSION .. t.file) or nil)
		panel.species = tonumber(t.species) or 0
		panel.scale = tonumber(t.scale) or 1
		panel.note = unescape(t.note or "")
		panel.over = t.over == "1"
		panel.repel = tonumber(t.repel) or 0
		panel.repelDur = tonumber(t.repeldur) or 100
		if TEST and TEST.onPanel then TEST.onPanel(panel) end
	end
	panel.windowMode = t.window == "1"
	panel.typing = t.typing or "0"
	panel.logtype = t.logtype or "0"
	panel.kind = t.kind or ""
	panel.typed = t.typed or ""
	panel.logq = t.logq or ""
	battleFlag(t.battle == "1")
end

local function readOverlay()
	local text = readFile(SESSION .. "overlay.txt")
	if not text then
		-- No overlay.txt: the viewer is closed (KaizoCore PC removes it), unless the read failed while it was replaced.
		local still = io.open(SESSION .. "overlay.txt", "rb")
		if still then still:close() elseif overlay.file then overlay.missed = (overlay.missed or 0) + 1 end
		if (overlay.missed or 0) >= 3 then overlay.file = nil; overlay.good = nil; overlay.prev = nil; overlay.missed = 0 end
		return
	end
	overlay.missed = 0
	local t = parse(text)
	if t.seq ~= overlay.seq then
		overlay.seq = t.seq
		newPicture(overlay, t.file and (SESSION .. t.file) or nil)
		if TEST and TEST.onOverlay then TEST.onOverlay(overlay) end
	end
end

-- BizHawk's transformPoint is either a table {x, y} or two numbers. pcall keeps only the first
-- result unless both are named, so the two-number form was thrown away and every click missed the panel.
local function pointXY(first, second)
	if type(first) == "number" and type(second) == "number" then return first, second end
	if type(first) == "table" or type(first) == "userdata" then
		local x = tonumber(first.x or first.X or first[1])
		local y = tonumber(first.y or first.Y or first[2])
		if x and y then return x, y end
	end
	return nil
end

local function clientPoint(gx, gy)
	local ok, a, b = pcall(client.transformPoint, gx, gy)
	if not ok then return nil end
	return pointXY(a, b)
end

-- Where the game's 240 by 160 land in client (window) pixels, for the log viewer and the repel icon.
function gameRect()
	local ax, ay = clientPoint(0, 0)
	local bx, by = clientPoint(PANEL_X, PANEL_H)
	if ax and ay and bx and by and bx > ax and by > ay then return { x = ax, y = ay, w = bx - ax, h = by - ay } end
	local sw, sh = client.screenwidth(), client.screenheight()
	local s = sw / (PANEL_X + (panel.padded and PANEL_W or 0))
	return { x = 0, y = 0, w = math.floor(PANEL_X * s + 0.5), h = math.floor(PANEL_H * s + 0.5) }
end

-- Where the panel's 150 by 160 game pixels land in client (window) pixels: client.transformPoint, else the screen's
-- size over the padded width (390).
local function panelRect()
	local ax, ay = clientPoint(PANEL_X, 0)
	local bx, by = clientPoint(PANEL_X + PANEL_W, PANEL_H)
	if ax and ay and bx and by and bx > ax and by > ay then return { x = ax, y = ay, w = bx - ax, h = by - ay } end
	local sw, sh = client.screenwidth(), client.screenheight()
	local s = sw / (PANEL_X + PANEL_W)
	return { x = math.floor(PANEL_X * s + 0.5), y = 0, w = math.floor(PANEL_W * s + 0.5), h = math.floor(PANEL_H * s + 0.5) }
end

-- Mouse position as pixels of the 150 by 160 panel picture. getmouse is game pixels on an unscaled window and
-- client pixels when the window is scaled, so both are accepted. Nil when the pointer is not on the panel.
local function inRect(x, y, r)
	return r and r.w and r.w > 0 and r.h and r.h > 0 and x >= r.x and x < r.x + r.w and y >= r.y and y < r.y + r.h
end

-- The mouse as game pixels (0 to 240, 0 to 160) under the viewer over the game, or nil, and which reading it was.
-- getmouse is game pixels, but a scaled window has given client pixels; read both ways, the first click that fits
-- only one decides for the clicks after it, where both would fit.
local function gamePoint(m)
	if not m or m.X == nil or m.Y == nil then return nil end
	local gx, gy, cx, cy
	if m.X >= 0 and m.X < PANEL_X and m.Y >= 0 and m.Y < PANEL_H then gx, gy = m.X, m.Y end
	local r = overlay.rect
	if inRect(m.X, m.Y, r) and not (r.x == 0 and r.y == 0 and r.w == PANEL_X and r.h == PANEL_H) then
		cx = math.min(PANEL_X - 1, (m.X - r.x) * PANEL_X / r.w)
		cy = math.min(PANEL_H - 1, (m.Y - r.y) * PANEL_H / r.h)
	end
	if gx and cx then
		if KC.clientMouse then return cx, cy, "both" end
		return gx, gy, "both"
	end
	if cx then return cx, cy, "client" end
	if gx then return gx, gy, "game" end
	return nil
end

local function panelPixels(m)
	if m.X == nil or m.Y == nil then return nil end
	-- Emulator pixels first. getmouse is in that space even when the window is scaled, and the
	-- client rectangle (a bad transform) was swallowing every click before this check.
	if m.X >= PANEL_X and m.X < PANEL_X + PANEL_W and m.Y >= 0 and m.Y < PANEL_H then
		return m.X - PANEL_X, m.Y
	end
	local r = panel.rect
	if inRect(m.X, m.Y, r) then
		return (m.X - r.x) * PANEL_W / r.w, (m.Y - r.y) * PANEL_H / r.h
	end
	local ok, s = pcall(client.getwindowsize)
	if ok and type(s) == "number" and s > 1 then
		local x, y = m.X / s, m.Y / s
		if x >= PANEL_X and x < PANEL_X + PANEL_W and y >= 0 and y < PANEL_H then
			return x - PANEL_X, y
		end
	end
	return nil
end

-- Asks KaizoCore PC for a picture at the window's scale when the window changed (two decimals; it draws at the first).
local function wantScale(r)
	local s = math.floor(r.w / PANEL_W * 100 + 0.5) / 100
	if s ~= panel.wantScale then
		panel.wantScale = s
		writeAtomic("panel.want", "scale=" .. string.format("%.2f", s) .. "\n")
	end
end

-- One line of input for KaizoCore PC; skipped while the last one is not taken yet (it takes it within a few ms).
local function sendInput(lines)
	-- A click that arrives while the last one is still unread goes in input.req2, so a fast second click is not thrown away.
	local name = "input.req"
	local pending = io.open(SESSION .. name, "rb")
	if pending then
		pending:close()
		name = "input.req2"
		local second = io.open(SESSION .. name, "rb")
		if second then second:close(); return false end
	end
	KC.seq = KC.seq + 1
	return writeAtomic(name, "seq=" .. KC.seq .. "\n" .. table.concat(lines, "\n") .. "\n")
end

local function closeNote()
	if panel.form then pcall(forms.destroy, panel.form) end
	panel.form = nil
end

-- The log viewer's search (besteon's LogSearchScreen types on the panel; here a BizHawk text box, as the notes).
function openSearch(current)
	if panel.form then closeNote() end
	local ok, form = pcall(forms.newform, 320, 120, "KaizoCore log search", function() panel.form = nil end)
	if not ok or not form then return end
	panel.form = form
	local box = forms.textbox(form, current, 290, 24, nil, 10, 10, false, false)
	local function go(text)
		sendInput({ "cmd=search", "text=" .. escape(text) })
		closeNote()
	end
	forms.button(form, "Search", function() go(forms.gettext(box) or "") end, 10, 48, 90, 25)
	forms.button(form, "Clear", function() go("") end, 110, 48, 90, 25)
	forms.button(form, "Cancel", closeNote, 210, 48, 90, 25)
	if TEST and TEST.onSearchForm then TEST.onSearchForm(form, box, go) end
end

-- The nine favorites, drawn from sheets written once (favbanner.txt). The strip slides; each sheet walks.
-- Aggron is fav-game-306.png, the game's own follower (the walking sheet read as the mega). Never the mega's sheet.
local function readFavBanner()
	local text = readFile(SESSION .. "favbanner.txt")
	if text == nil then
		local f = io.open(SESSION .. "favbanner.txt", "rb")
		if f then f:close() else panel.fav = nil; panel.favText = nil end
		return
	end
	if text == panel.favText then return end
	panel.favText = text
	local t = parse(text)
	if t.on ~= "1" then panel.fav = nil; return end
	local mons = {}
	for line in text:gmatch("[^\n]+") do
		local body = line:match("^mon=(.*)$")
		if body then
			local file, name, w, h, x, y, durs = body:match("^([^|]+)|([^|]*)|(-?%d+)|(-?%d+)|(-?%d+)|(-?%d+)|([%d,]*)$")
			if file then
				local d, total = {}, 0
				for n in durs:gmatch("%d+") do local v = tonumber(n); d[#d + 1] = v; total = total + v end
				mons[#mons + 1] = { file = SESSION .. file, name = name, w = tonumber(w), h = tonumber(h), x = tonumber(x), y = tonumber(y), d = d, total = total }
			end
		end
	end
	panel.fav = {
		x = tonumber(t.x) or 2, y = tonumber(t.y) or 67, w = tonumber(t.w) or 136, h = tonumber(t.h) or 70,
		slot = tonumber(t.slot) or 64, mons = mons, shift = 0,
	}
end

local function favFrame(mon, frames)
	if mon.total <= 0 or #mon.d == 0 then return 0 end
	local f = frames % mon.total
	for i, n in ipairs(mon.d) do
		if f < n then return i - 1 end
		f = f - n
	end
	return 0
end

local function drawFavBanner(r)
	if every(8) or panel.fav == nil then readFavBanner() end
	local b = panel.fav
	if not b or #b.mons == 0 or not r then return end
	local sx, sy = r.w / PANEL_W, r.h / PANEL_H
	local span = #b.mons * b.slot
	-- Half the first speed Blake watched (3 px a frame, and a walk step every frame). 2026-10-09.
	b.shift = (b.shift + 1.5) % span
	local left, right = b.x, b.x + b.w
	for i, mon in ipairs(b.mons) do
		local function at(gx)
			local dx, dy = gx + 6 + mon.x, b.y + mon.y
			local dw, dh = mon.w, mon.h
			if dx + dw <= left or dx >= right then return end
			local sx0 = 0
			if dx < left then sx0 = left - dx; dx = left; dw = dw - sx0 end
			if dx + dw > right then dw = right - dx end
			if dw <= 0 or dh <= 0 then return end
			local frame = favFrame(mon, math.floor(KC.frame / 2))
			pcall(gui.drawImageRegion, mon.file, frame * mon.w + sx0, 0, dw, dh, r.x + dx * sx, r.y + dy * sy, dw * sx, dh * sy)
			if gx > left and gx < right - 24 then
				pcall(gui.drawText, r.x + gx * sx, r.y + (b.y + 40) * sy, mon.name, 0xFFFFFFFF, 0x00000000)
			end
		end
		local gx = (i - 1) * b.slot - b.shift
		at(gx)
		at(gx + span)
	end
end

-- The card in KaizoCore PC's own window (panel.txt says window=1): BizHawk shows the game alone, with the repel icon
-- and the log viewer over it, and draws no card. Its clicks, wheel and typing go to that window, not through here.
local function windowStep()
	setPadding(false)
	if not (KC.isHnS and KC.jarAlive and KC.gen > 0) then clientFresh("none"); return end
	-- The game's place in the window is only needed while something is drawn over it. transformPoint every quarter
	-- second with nothing there was work on every frame's neighbors for a rect nobody used.
	if ((panel.repel or 0) > 0 or overlay.file) and (every(15) or not overlay.rect) then
		overlay.rect = gameRect()
		-- The log viewer is drawn at the game's own scale (KaizoCore PC keeps it to 2 at most).
		wantScale({ w = overlay.rect.w * PANEL_W / PANEL_X })
	end
	clientFresh(clientState(nil))
	if (panel.repel or 0) > 0 or overlay.file then
		pcall(gui.use_surface, "client")
		drawRepel(overlay.rect)
		if overlay.file then drawPicture(overlay, overlay.rect) end
		pcall(gui.use_surface, "emucore")
	end
	if not overlay.file then panel.lastLeft = nil; panel.lastRight = nil; panel.lastWheel = nil; return end
	local m = frameMouse()
	local gx, gy, space = gamePoint(m)
	local inGame = gx ~= nil
	if (m.Left or m.Right) and inGame then
		pcall(joypad.set, { A = false, B = false, Select = false, Start = false, L = false, R = false, Up = false, Down = false, Left = false, Right = false })
	end
	if m.Left and not panel.lastLeft and inGame then
		if space == "client" then KC.clientMouse = true elseif space == "game" then KC.clientMouse = false end
		-- BizHawk makes a second click on its window fullscreen; a fast second click on the viewer turns it back.
		local t = now()
		if panel.clickAt and (t - panel.clickAt) < 0.45 and math.abs(m.X - (panel.clickX or -99)) <= 3 and math.abs(m.Y - (panel.clickY or -99)) <= 3 then
			pcall(client.togglefullscreen)
			panel.clickAt = nil
		else
			panel.clickAt, panel.clickX, panel.clickY = t, m.X, m.Y
		end
		sendInput({ "cmd=oclick", "x=" .. math.floor(gx), "y=" .. math.floor(gy), "raw=" .. m.X .. "," .. m.Y .. " " .. space })
	end
	-- A right click on the game closes the viewer or the GachaMon card, as Escape does.
	if m.Right and not panel.lastRight and inGame then sendInput({ "cmd=logclose" }) end
	panel.lastRight = m.Right
	-- A smooth wheel moves a little each frame: the change adds up until it is a notch (100), then goes.
	local wheel = tonumber(m.Wheel) or 0
	if panel.lastWheel then
		local delta = wheel - panel.lastWheel
		if delta >= 100 or delta <= -100 then sendInput({ "cmd=wheel", "delta=" .. delta }); panel.lastWheel = wheel end
	else panel.lastWheel = wheel end
	panel.lastLeft = m.Left
end

local function panelStep()
	-- The card file is read a few times a second, not every frame. Opening it 60 times a second was stalling the game.
	-- With the card in its own window the file only carries the repel and the battle state: ten times a second.
	if panel.windowMode then
		if every(6) then readPanel() end
	elseif every(3) or panel.file == nil then readPanel() end
	-- Every other frame while a card is open: the picture only changes when KaizoCore PC writes a new one.
	if every(overlay.file and 2 or 6) then readOverlay() end
	if panel.windowMode then return windowStep() end
	local show = KC.isHnS and KC.jarAlive and KC.gen > 0 and panel.file ~= nil
	-- The padding goes only after the panel has been away for 2 real seconds (clock time, so fast forward does not
	-- shorten it): a moment's gap no longer makes BizHawk resize the screen and back, which read as a flash.
	-- Once the panel is up in a Heart & Soul session, a missing picture must not take the padding away.
	-- That resize is the window jumping to full screen and back when clicks come in fast.
	if show then panel.goneSince = nil
	elseif not (KC.isHnS and KC.jarAlive) then
		if not panel.goneSince then panel.goneSince = now() end
	end
	local keep = show or (KC.isHnS and KC.jarAlive and panel.padded)
	setPadding(keep or (panel.padded and panel.goneSince ~= nil and now() - panel.goneSince < 2))
	if not show then return end
	if every(15) or not panel.rect then
		local n = panelRect()
		local keep = false
		if panel.rect and panel.rect.w and panel.rect.w > 0 and n.w and n.w > 0 then
			local ratio = n.w / panel.rect.w
			if ratio > 1.5 or ratio < 0.67 then
				-- A one-frame jump (fullscreen, then back) is ignored. The same new size on the next look is real.
				if panel.pendingW and math.abs(n.w - panel.pendingW) <= 2 then
					panel.pendingW = nil
				else
					panel.pendingW = n.w
					keep = true
				end
			else
				panel.pendingW = nil
			end
		end
		if not keep then panel.rect = n; wantScale(n) end
	end
	local r = panel.rect
	if overlay.file and (every(15) or not overlay.rect) then overlay.rect = gameRect() end
	clientFresh(clientState(r))
	-- The picture is 150 by 160 times the scale it was drawn at: 1:1 at that scale, scaled only until the next arrives.
	pcall(gui.use_surface, "client")
	drawPicture(panel, r)
	drawFavBanner(r)
	if panel.dropAt and KC.frame >= panel.dropAt then panel.dropAt = nil; pcall(gui.clearImageCache) end
	drawRepel(r)
	if overlay.file then drawPicture(overlay, overlay.rect) end
	drawLive(r)
	pcall(gui.use_surface, "emucore")
	local m = frameMouse()
	local px, py = panelPixels(m)
	local inPanel = px ~= nil
	local inGame = overlay.file ~= nil and not inPanel and m.X ~= nil and m.X >= 0 and m.X < PANEL_X and m.Y >= 0 and m.Y < PANEL_H
	-- A click on the panel (or on the log viewer over the game) never reaches the game: while a mouse button is held there
	-- every button is held off for the game. A BizHawk set up with a mouse button bound to a game button (Blake's had
	-- GBA Select on the left button, 2026-10-07) pressed it with every click on the tracker: in the lab that is the
	-- game's "A KEY ITEM can be registered to holding SELECT" message.
	if (m.Left or m.Right) and (inPanel or inGame) then
		pcall(joypad.set, { A = false, B = false, Select = false, Start = false, L = false, R = false, Up = false, Down = false, Left = false, Right = false })
	end
	if m.Left and not panel.lastLeft and inPanel and liveClick(px, py) then
		-- A key on the open keyboard: the letter is drawn here, this frame. The card is not repainted for it.
	elseif m.Left and not panel.lastLeft and inPanel then
		if live.on then live.adopt = true; live.adoptSeq = panel.seq end
		-- BizHawk turns a second click on its window into fullscreen, and another click shrinks it.
		-- The click already happened before this frame, so toggle it back off the tracker.
		local t = now()
		if panel.clickAt and (t - panel.clickAt) < 0.45 then
			pcall(client.togglefullscreen)
			panel.clickAt = nil
		else
			panel.clickAt = t
		end
		sendInput({ "cmd=click", "x=" .. math.floor(px + 0.5), "y=" .. math.floor(py + 0.5) })
	elseif m.Left and not panel.lastLeft and inGame then
		sendInput({ "cmd=oclick", "x=" .. m.X, "y=" .. m.Y })
	end
	-- A right click: the note on the Pokemon the panel shows, typed on the panel's own keyboard (KaizoCore PC draws it).
	if m.Right and not panel.lastRight and inPanel then
		sendInput({ "cmd=rnote" })
	end
	-- The wheel turns the log's pages while the log is open, and the favorites list while the mouse is on the tracker.
	-- Input.checkMouseWheel: the change since the last frame, 100 a notch.
	local wheel = tonumber(m.Wheel) or 0
	if panel.lastWheel then
		local delta = wheel - panel.lastWheel
		if delta >= 100 or delta <= -100 then
			if overlay.file then sendInput({ "cmd=wheel", "delta=" .. delta })
			elseif inPanel then sendInput({ "cmd=favwheel", "delta=" .. delta }) end
			panel.lastWheel = wheel
		end
	else panel.lastWheel = wheel end
	panel.lastLeft, panel.lastRight = m.Left, m.Right
end

-- Play as your Pokemon (Sprite Is Me by UTDZac, MIT; the phone's SpriteIsMe, whose emulator side is sprite_core.h): the
-- player walks as the lead Pokemon, a Pokemon picked in the gear, or the player's own picture or sheets. KaizoCore PC
-- decides who and hands over the sheets (playas.txt); this does the part that happens between two frames, as
-- sprite_core.h does it on the phone:
--   * only while the game's main callback is the overworld's, and only for a player sprite in use and not invisible;
--   * the box comes from the game's own sprite fields (UpdateOamCoords: x + x2 + the camera offset, the feet line), so
--     it follows ledges, surfing and the bike instead of assuming the middle of the screen;
--   * the trainer is hidden in the game's own OAM buffer (gMain.oamBuffer, which the game rebuilds every frame, so
--     nothing reaches a save or a save state), every entry whose tile is in the player's tile block, the reflection too;
--   * the frame is drawn at the box read one frame earlier, because the hide written now shows in the next picture;
--   * facing and stepping come from the game itself; asleep after 55 seconds with no button and no step, fainted at
--     0 HP, asleep while the lead is; a walk frame held long bobs a pixel (SpriteIsMeLogic);
--   * while the screen fades (a warp, a battle starting) the Pokemon is not drawn once the fade is past half, since a
--     picture drawn here cannot be faded with the game's colors (the phone fades it pixel by pixel).
-- Nothing is drawn and nothing written when playas.txt says on=0, or the player cannot be read.
local playas = { cfg = nil, text = nil, prev = nil, anim = nil, animStart = 0, facing = 1, lastActive = os.clock(), lastJoy = "" }
local PA_TILES = { [0] = { [0] = 1, 4, 16, 64 }, [1] = { [0] = 2, 4, 8, 32 }, [2] = { [0] = 2, 4, 8, 32 }, [3] = { [0] = 0, 0, 0, 0 } }

local function paDomain(addr)
	if addr >= 0x03000000 and addr < 0x03008000 then return addr - 0x03000000, "IWRAM" end
	if addr >= 0x02000000 and addr < 0x02040000 then return addr - 0x02000000, "EWRAM" end
	if addr >= 0x05000000 and addr < 0x05000400 then return addr - 0x05000000, "PALRAM" end
	return nil
end

local function paRead(addr, n)
	local o, d = paDomain(addr)
	if not o then return nil end
	local ok, s = pcall(memory.read_bytes_as_binary_string, o, n, d)
	if ok and s and #s == n then return s end
	return nil
end

local function le16(s, i) local a, b = s:byte(i, i + 1); return a + b * 256 end
local function s16(s, i) local v = le16(s, i); if v >= 0x8000 then v = v - 0x10000 end; return v end
local function s8(v) if v >= 0x80 then return v - 0x100 end; return v end

local function readPlayAs()
	local text = readFile(SESSION .. "playas.txt")
	if text == nil then
		-- No playas.txt: KaizoCore PC removed it (no game) or is replacing it; a failed read keeps what was there.
		local f = io.open(SESSION .. "playas.txt", "rb")
		if f then f:close() else playas.cfg = nil; playas.text = nil end
		return
	end
	if text == playas.text then return end
	playas.text = text
	local t = parse(text)
	if t.on ~= "1" then playas.cfg = nil; return end
	local c = { anims = {} }
	for _, k in ipairs({ "main", "oam", "cb2", "cb2basic", "avatar", "sprites", "offx", "offy", "unfaded", "faded", "objects", "hp", "asleep" }) do
		c[k] = tonumber(t[k]) or 0
	end
	c.source = t.source
	for _, a in ipairs({ "idle", "walk", "sleep", "faint" }) do
		local v = t[a]
		if v then
			local file, w, h, x, y, rows, durs = v:match("^([^,]+),(-?%d+),(-?%d+),(-?%d+),(-?%d+),(%d+),(.*)$")
			if file then
				local d = {}
				for n in durs:gmatch("%d+") do d[#d + 1] = tonumber(n) end
				local total = 0
				for _, n in ipairs(d) do total = total + n end
				c.anims[a] = { file = SESSION .. file, w = tonumber(w), h = tonumber(h), x = tonumber(x), y = tonumber(y), rows = tonumber(rows), d = d, total = total }
			end
		end
	end
	if t.picture then
		local file, w, h, ox, oy = t.picture:match("^([^,]+),(%d+),(%d+),(-?%d+),(-?%d+)$")
		if file then c.picture = { file = SESSION .. file, w = tonumber(w), h = tonumber(h), ox = tonumber(ox), oy = tonumber(oy) } end
	end
	playas.cfg = c
	if TEST and TEST.onPlayAs then TEST.onPlayAs(c) end
end

-- The player's sprite now: its box (one frame ahead of the picture on screen), its tiles, palette, facing and step.
local function paReadPlayer(c)
	local m = paRead(c.main + 4, 4)
	if not m then return nil end
	local cb2 = m:byte(1) + m:byte(2) * 256 + m:byte(3) * 65536 + m:byte(4) * 16777216
	if cb2 ~= c.cb2 and cb2 ~= c.cb2basic then return nil end
	local av = paRead(c.avatar, 8)
	if not av then return nil end
	local spriteId, oeId = av:byte(5), av:byte(6)
	if spriteId >= 64 then return nil end
	local sp = paRead(c.sprites + spriteId * 0x44, 0x44)
	if not sp then return nil end
	local flags = sp:byte(0x3F)
	if flags & 1 == 0 or flags & 4 ~= 0 then return nil end
	local attr0, attr1, attr2 = le16(sp, 1), le16(sp, 3), le16(sp, 5)
	local tiles = PA_TILES[(attr0 >> 14) & 3][(attr1 >> 14) & 3]
	local vecX, vecY = s8(sp:byte(0x29)), s8(sp:byte(0x2A))
	if tiles == 0 or vecX > 0 or vecX < -64 or vecY >= 0 or vecY < -64 then return nil end
	local offX, offY = 0, 0
	if flags & 2 ~= 0 then
		local ox, oy = paRead(c.offx, 2), paRead(c.offy, 2)
		if not ox or not oy then return nil end
		offX, offY = s16(ox, 1), s16(oy, 1)
	end
	local bx = s16(sp, 0x21) + s16(sp, 0x25) + offX - 16
	local by = s16(sp, 0x23) + s16(sp, 0x27) + offY - vecY - 32
	if bx < -128 or bx > 368 or by < -128 or by > 288 then return nil end
	local p = { x = bx, y = by, tileBase = attr2 & 0x3FF, tiles = tiles, pal = (attr2 >> 12) & 15, facing = 0 }
	p.moving = av:byte(3) == 2 or av:byte(4) == 1
	if oeId < 16 then
		local oe = paRead(c.objects + oeId * 0x24 + 0x18, 1)
		if oe then
			local dir = oe:byte(1) & 15
			if dir >= 1 and dir <= 4 then p.facing = dir end
		end
	end
	p.unfaded = paRead(c.unfaded + 0x200 + p.pal * 32 + 2, 30)
	return p
end

-- hideTileRange on gMain.oamBuffer: attr0 bits 8-9 set to 0b10 (OBJ off) on every live entry in the player's tiles.
local function paHide(c, p)
	local base = c.main + c.oam
	local oam = paRead(base, 1024)
	if not oam then return end
	local o, d = paDomain(base)
	for i = 0, 127 do
		local at = i * 8 + 1
		local attr0 = le16(oam, at)
		local tile = le16(oam, at + 4) & 0x3FF
		local off = (attr0 >> 8) & 3 == 2
		local dummy = attr0 == 0x00A0 and le16(oam, at + 2) == 0 and le16(oam, at + 4) == 0
		if not off and not dummy and tile >= p.tileBase and tile < p.tileBase + p.tiles then
			pcall(memory.write_u16_le, o + i * 8, (attr0 & ~0x0300) | 0x0200, d)
		end
	end
end

-- How far the game has faded the player's palette (0 none, 16 all the way), fitted as the game's BlendPalette towards
-- black or white; nil when the change is not a fade (weather tints the palette, for one).
local function paFade(p)
	if not p.unfaded then return 0 end
	-- The search is a few thousand Lua steps. Outdoors the palette is tinted every frame, so it is not a fade.
	-- Checking that eight times a second is enough for a door.
	if not every(8) and playas.fadeCache ~= nil then return playas.fadeCache end
	local shown = paRead(0x05000200 + p.pal * 32 + 2, 30)
	if not shown then playas.fadeCache = 0; return 0 end
	if shown == p.unfaded then playas.fadeCache = 0; return 0 end
	local best, bestCoeff = nil, 0
	for _, target in ipairs({ 0, 31 }) do
		for coeff = 1, 16 do
			local err = 0
			for i = 0, 14 do
				local u, s = le16(p.unfaded, i * 2 + 1), le16(shown, i * 2 + 1)
				for ch = 0, 2 do
					local uc, sc = (u >> (5 * ch)) & 31, (s >> (5 * ch)) & 31
					local v = uc + ((target - uc) * coeff) // 16
					err = err + (sc - v) * (sc - v)
				end
			end
			if not best or err < best then best, bestCoeff = err, coeff end
		end
	end
	local answer = bestCoeff
	if best > 15 * 3 * 8 then answer = nil end
	playas.fadeCache = answer
	return answer
end

local PA_IDLE_SECONDS = 55

local function paWanted(c, afk, moving)
	if afk then return "sleep" end
	if c.hp <= 0 then return "faint" end
	if c.asleep == 1 then return "sleep" end
	if moving then return "walk" end
	return "idle"
end

-- SheetSet.substitute: the nearest animation the set has.
local function paSubstitute(c, want)
	if c.anims[want] then return want end
	if want == "walk" and c.anims.idle then return "idle" end
	if want == "idle" and c.anims.walk then return "walk" end
	if c.anims.idle then return "idle" end
	if c.anims.walk then return "walk" end
	return nil
end

local function paDraw(c, p)
	local frames = emu.framecount()
	local afk = os.clock() - playas.lastActive >= PA_IDLE_SECONDS
	local bob = 0
	if c.picture then
		if p.moving and (frames // 8) % 2 == 1 then bob = -1 end
		local sx = (playas.facing == 3) and c.picture.w or 0
		pcall(gui.drawImageRegion, c.picture.file, sx, 0, c.picture.w, c.picture.h, p.x + c.picture.ox, p.y + c.picture.oy + bob)
		return
	end
	local a = paSubstitute(c, paWanted(c, afk, p.moving))
	if not a then return end
	local s = c.anims[a]
	if playas.anim ~= a or frames < playas.animStart then playas.anim = a; playas.animStart = frames end
	local t = frames - playas.animStart
	local index = 0
	if s.total > 0 and #s.d > 0 then
		if a == "faint" and t >= s.total then
			index = #s.d - 1
		else
			local f = t % s.total
			for i, n in ipairs(s.d) do
				if f < n then index = i - 1; break end
				f = f - n
			end
		end
	end
	local row = 0
	if (a == "idle" or a == "walk") and s.rows >= 8 then
		local fc = playas.facing
		row = (fc == 2 and 4) or (fc == 3 and 6) or (fc == 4 and 2) or 0
	end
	local held = #s.d < 2 or (s.d[index + 1] or 0) > 32
	if paWanted(c, afk, p.moving) == "walk" and held and (frames // 8) % 2 == 1 then bob = -1 end
	pcall(gui.drawImageRegion, s.file, s.w * index, s.h * row, s.w, s.h, p.x + s.x, p.y + s.y + bob)
end

local function playAsStep(joy)
	if every(30, 7) then readPlayAs() end
	local c = playas.cfg
	local plan = playas.prev
	playas.prev = nil
	if not c or not KC.isHnS then
		if playas.drew then pcall(gui.clearGraphics, "emucore"); playas.drew = false end
		return
	end
	-- The 55 seconds: a button, a step or a turn counts as the player being there.
	joy = joy or ((TEST and TEST.joypad) and TEST.joypad(KC) or joypad.get())
	local pressed = {}
	for k, v in pairs(joy or {}) do if v == true then pressed[#pressed + 1] = k end end
	local j = table.concat(pressed, ",")
	if j ~= "" and j ~= playas.lastJoy then playas.lastActive = os.clock() end
	if j ~= "" then playas.lastActive = os.clock() end
	playas.lastJoy = j
	local p = paReadPlayer(c)
	if p then
		if p.moving or (p.facing >= 1 and p.facing ~= playas.facing) then playas.lastActive = os.clock() end
		if p.facing >= 1 then playas.facing = p.facing end
		-- During a door fade the game's own sprite fades. Hiding it then leaves a blank indoors.
		local fadeNow = paFade(p)
		if fadeNow == nil or fadeNow < 8 then
			paHide(c, p)
			playas.prev = p
		end
	end
	local drew = false
	if plan then
		local fade = paFade(plan)
		if fade == nil or fade < 8 then
			pcall(gui.use_surface, "emucore")
			paDraw(c, plan)
			drew = true
		end
	end
	-- BizHawk keeps what was drawn on a surface until something is drawn on it again: the frame the Pokemon stops being
	-- drawn (a battle starts, a menu, a warp), the game's surface is emptied once, or the last frame stays over the battle.
	if playas.drew and not drew then pcall(gui.clearGraphics, "emucore") end
	playas.drew = drew
	if TEST and TEST.onPlayAsFrame then TEST.onPlayAsFrame(plan, p) end
end

local function cleanup()
	os.remove(SESSION .. "ram.tmp")
	os.remove(SESSION .. "lua.req.tmp")
	dropBattleState()
	os.remove(SESSION .. "lua.txt")
	closeNote()
	setPadding(false)
	overlay.file = nil
	pcall(gui.use_surface, "emucore")
	pcall(gui.clearGraphics, "client")
	if KC.jarAlive then
		-- Tell KaizoCore PC this game is no longer being played; no wait for the answer.
		KC.seq = KC.seq + 1
		writeAtomic("lua.req", "seq=" .. KC.seq .. "\ncmd=bye\n")
	end
end

-- A click on the tracker must not press the New Run combo or the GBA soft reset. Blank the buttons first.
local function blankIfClicking()
	-- The card in its own window: BizHawk's mouse matters only while something is open over the game.
	if panel.windowMode and not overlay.file then return false end
	local m = frameMouse()
	local inPanel = m.X ~= nil and m.X >= PANEL_X and m.X < PANEL_X + PANEL_W and m.Y >= 0 and m.Y < PANEL_H
	local inGame = overlay.file ~= nil and m.X ~= nil and m.X >= 0 and m.X < PANEL_X and m.Y >= 0 and m.Y < PANEL_H
	if panel.windowMode then inPanel = false; inGame = overlay.file ~= nil and gamePoint(m) ~= nil end
	if (m.Left or m.Right) and (inPanel or inGame) then
		pcall(joypad.set, { A = false, B = false, Select = false, Start = false, L = false, R = false, Up = false, Down = false, Left = false, Right = false })
		return true
	end
	return false
end

-- Letters are drawn here, the frame the key is pressed. KaizoCore PC paints the card only when the word is
-- saved, or (favorites, Always use, log search) a moment later so the suggestion list or the results can catch up.
-- A note has no list, so it is not sent until Save.
live = { on = false, text = "", kind = "", sent = nil, localAt = 0, adopt = false, adoptSeq = nil }
local logEsc = false
local prevType = {}
local KEY_ROWS = { "1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM" }

local function liveMax()
	if live.kind == "note" then return 500 end
	if live.kind == "log" then return 10 end
	return 20
end

local function liveAdd(ch)
	if ch == "back" then live.text = live.text:sub(1, math.max(#live.text - 1, 0))
	elseif ch == "clear" then live.text = ""
	elseif #live.text < liveMax() then live.text = live.text .. ch end
	live.localAt = now()
end

local function liveSync()
	local want = panel.typing == "1" or panel.logtype == "1"
	local kind = panel.typing == "1" and (panel.kind ~= "" and panel.kind or "note") or "log"
	if not want then live.on = false; live.text = ""; live.sent = nil; return end
	local incoming = unescape(panel.typing == "1" and panel.typed or panel.logq)
	if not live.on or live.kind ~= kind then
		live.on = true
		live.kind = kind
		live.text = incoming
		live.sent = incoming
		live.localAt = 0
		return
	end
	-- A slot or suggestion click asks for the next picture. When that picture's word is a different one, take it.
	-- Our own draft coming back is the same word we sent, so it does not replace letters typed since.
	if live.adopt and panel.seq ~= live.adoptSeq then
		live.adopt = false
		if incoming ~= live.text and incoming ~= live.sent then
			live.text = incoming
			live.sent = incoming
			live.localAt = 0
		end
	elseif incoming ~= live.text and incoming ~= live.sent and (live.localAt == 0 or now() - live.localAt > 0.35) then
		live.text = incoming
		live.sent = incoming
	end
end

local function liveFlush()
	if not live.on or live.kind == "note" then return end
	if live.localAt == 0 or now() - live.localAt < 0.12 then return end
	if live.text == live.sent then return end
	if sendInput({ "cmd=draft", "text=" .. escape(live.text) }) then live.sent = live.text end
end

local function liveCommit()
	if sendInput({ "cmd=commit", "text=" .. escape(live.text) }) then live.sent = live.text; live.localAt = 0 end
end

local function liveCancel()
	if sendInput({ "cmd=typecancel" }) then live.on = false; live.text = ""; live.localAt = 0 end
end

local function keyAt(px, py)
	local function inside(x, y, w, h) return px >= x and px < x + w and py >= y and py < y + h end
	local fieldY = live.kind == "favorites" and 51 or (live.kind == "always" and 20 or 80)
	if inside(115, fieldY, 12, 12) then return "back" end
	if inside(130, fieldY, 12, 12) then return "clear" end
	for row, keys in ipairs(KEY_ROWS) do
		local inset = 0
		if row >= 3 then inset = math.floor((row - 2) * (0.5 * 11) + 0.5) end
		for i = 1, #keys do
			local x = 11 + inset + (i - 1) * 13
			local y = 100 + (row - 1) * 13
			if inside(x, y, 11, 11) then return keys:sub(i, i) end
		end
	end
	-- The space sits just after M: row 4, inset 11, M is the 7th key, then one key-gap to the right.
	local mx, my = 11 + 11 + 6 * 13 + 13, 100 + 3 * 13
	if inside(mx, my, 24, 11) then return " " end
	return nil
end

-- A click on the open keyboard. True when this script handled it, so the card is not asked to repaint.
function liveClick(px, py)
	liveSync()
	if not live.on then return false end
	local function inside(x, y, w, h) return px >= x and px < x + w and py >= y and py < y + h end
	if live.kind == "note" and inside(8, 80, 40, 11) then liveCommit(); return true end
	if live.kind == "favorites" and inside(111, 2, 32, 11) then liveCommit(); return true end
	if (live.kind == "note" or live.kind == "always") and inside(127, 1, 18, 12) then liveCancel(); return true end
	local ch = keyAt(px, py)
	if not ch then return false end
	liveAdd(ch)
	return true
end

function drawLive(r)
	liveSync()
	if not live.on or not r then return end
	local sx, sy = r.w / PANEL_W, r.h / PANEL_H
	local function box(px, py, pw, ph, text)
		local x, y = r.x + px * sx, r.y + py * sy
		pcall(gui.drawRectangle, x, y, pw * sx, ph * sy, 0xFF000000, 0xFF101018)
		if text and text ~= "" then
			pcall(gui.drawText, x + 2, y + 1, text, 0xFFFFFFFF, 0xFF101018, "Arial", math.max(8, math.floor(8 * sy)))
		end
	end
	local shown = live.text .. "_"
	if live.kind == "note" then
		box(6, 16, 132, 62, nil)
		local y = 0
		local lines = {}
		local rest = shown
		while #rest > 0 do
			if #rest <= 33 then lines[#lines + 1] = rest; break end
			local cut = 33
			local sp = rest:sub(1, 33):match("^.*() ")
			if sp and sp > 1 then cut = sp - 1 end
			lines[#lines + 1] = rest:sub(1, cut)
			rest = rest:sub(cut + 1):gsub("^ +", "")
		end
		local first = math.max(1, #lines - 5)
		for i = first, #lines do
			pcall(gui.drawText, r.x + 8 * sx, r.y + (18 + y * 10) * sy, lines[i], 0xFFFFFFFF, 0x00000000, "Arial", math.max(8, math.floor(8 * sy)))
			y = y + 1
		end
	elseif live.kind == "favorites" then
		box(8, 51, 104, 12, shown)
	elseif live.kind == "always" then
		box(8, 20, 104, 12, shown)
	else
		box(8, 80, 104, 12, shown)
	end
end

local function pressedKeys()
	local ok, keys = pcall(function() return input.get() end)
	if not ok or type(keys) ~= "table" then keys = {} end
	local down = {}
	for k, v in pairs(keys) do
		if v == true or v == 1 then down[tostring(k)] = true
		elseif type(k) == "number" and type(v) == "string" then down[v] = true end
	end
	return down
end

local function typingKeys()
	-- The tracker's own window takes what is typed on the card; BizHawk's keys stay the game's.
	if panel.windowMode then live.on = false; prevType = {}; return end
	liveSync()
	if not live.on then prevType = {}; return end
	pcall(joypad.set, { A = false, B = false, Select = false, Start = false, L = false, R = false, Up = false, Down = false, Left = false, Right = false })
	local keys = pressedKeys()
	local function hit(name) return keys[name] and not prevType[name] end
	local shift = keys["ShiftKey"] or keys["LShiftKey"] or keys["RShiftKey"] or keys["Shift"]
	if hit("Back") or hit("Backspace") then liveAdd("back")
	elseif hit("Escape") then
		if live.kind == "log" or overlay.file then sendInput({ "cmd=logclose" }); live.on = false
		else liveCancel() end
	elseif hit("Enter") or hit("Return") then liveCommit()
	elseif hit("Space") then liveAdd(" ")
	else
		local sent = nil
		for i = 0, 25 do
			local name = string.char(string.byte("A") + i)
			if hit(name) or hit(string.lower(name)) then sent = (shift and name or string.lower(name)); break end
		end
		if not sent then
			for i = 0, 9 do
				if hit("D" .. i) or hit("Number" .. i) or hit(tostring(i)) then sent = tostring(i); break end
			end
		end
		if sent then liveAdd(sent) end
	end
	liveFlush()
	prevType = {}
	for k, v in pairs(keys) do if v then prevType[k] = true end end
end

-- A new game was Shift, so a knockout asks you to switch. Set never asks. The option lives in the save
-- (SaveBlock2 + 21, bit 1: 0 Shift, 1 Set). Written here so this attempt stops asking without a new seed.
-- The ability pop-up script is only on screen for a few frames. A RAM snapshot every quarter second misses it,
-- so the enemy card never learned Drizzle. While that script is the one running, note the battler and its live ability.
local lastPopup = ""
local function watchAbilityPopup()
	if not KC.isHnS or KC.noGame or not KC.build then return end
	local ok, s = pcall(memory.read_bytes_as_binary_string, 0x144, 4, "EWRAM")
	if not ok or not s or #s < 4 then return end
	local pc = (s:byte(1) + s:byte(2) * 256 + s:byte(3) * 65536 + s:byte(4) * 16777216) & 0xFFFFFFFE
	if pc < KC.build.popup[1] or pc >= KC.build.popup[2] then return end
	local okb, bb = pcall(memory.read_bytes_as_binary_string, 0x2FA, 1, "EWRAM")
	if not okb or not bb or #bb < 1 then return end
	local battler = bb:byte(1) % 4
	local okm, mon = pcall(memory.read_bytes_as_binary_string, 0x41C + battler * 136, 80, "EWRAM")
	if not okm or not mon or #mon < 80 then return end
	local species = mon:byte(1) + mon:byte(2) * 256
	local ability = mon:byte(33) + mon:byte(34) * 256
	if species == 0 or ability == 0 then return end
	-- Its personality value (BattlePokemon.personality, +76): the tracker counts the ability for this Pokemon only, and
	-- a second Pokemon of the same species showing the same ability is a new reveal.
	local pid = mon:byte(77) + mon:byte(78) * 256 + mon:byte(79) * 65536 + mon:byte(80) * 16777216
	local key = species .. ":" .. ability .. ":" .. pid
	if key == lastPopup then return end
	lastPopup = key
	writeAtomic("ability.req", "species=" .. species .. "\nability=" .. ability .. "\npid=" .. pid .. "\n")
end

local function forceSetStyle()
	if not KC.isHnS or KC.noGame or KC.styleOn then return end
	local ok, s = pcall(memory.read_bytes_as_binary_string, 0x41D4, 4, "IWRAM")
	if not ok or not s or #s < 4 then return end
	local ptr = s:byte(1) + s:byte(2) * 256 + s:byte(3) * 65536 + s:byte(4) * 16777216
	if ptr < 0x02000000 or ptr >= 0x02040000 then return end
	local off = ptr - 0x02000000 + 21
	local okb, b = pcall(memory.read_bytes_as_binary_string, off, 1, "EWRAM")
	if not okb or not b or #b < 1 then return end
	local v = b:byte(1)
	if (v & 2) == 0 then pcall(memory.write_u8, off, v | 2, "EWRAM") else KC.styleOn = true end
end

-- Blake's own testing only: with kaizocore-dev.txt beside this script, each attempt's item PC gets Heart & Soul's Rare
-- Candy Box (ITEM_INFINITE_RARE_CANDIES), 99 Lum Berries, a Leftovers and each of TM01 to TM50 whose move deals damage
-- in this seed (the randomizer changes what a TM teaches),
-- once the player is on the map: a new game or a continued one, before or after the first Pokemon. Only items the
-- PC does not hold yet go in, into empty slots. The release has no such file.
local DEV_ITEMS = io.open(HERE .. "kaizocore-dev.txt", "rb")
if DEV_ITEMS then DEV_ITEMS:close(); DEV_ITEMS = true else DEV_ITEMS = false end
-- A move is 68 bytes in gMovesInfo, byte 10 bits 5-6 its category (2 a status move); gMain.callback2 is in IWRAM.
local MOVE_SIZE, MAIN_CB2 = 68, 0x5BDC
-- Item ids in Heart & Soul 2.0.6 (include/constants/items.h): ITEM_TM01 is 582, ITEM_INFINITE_RARE_CANDIES 880.
local TM_FIRST, TM_LAST, RARE_CANDY_BOX = 582, 631, 880

local function devPcList()
	-- The box first, so a PC with few empty slots still gets it.
	local list = { { RARE_CANDY_BOX, 1 }, { 522, 99 }, { 472, 1 } }
	for tm = 1, 50 do
		local okm, move = pcall(memory.read_u16_le, KC.build.tmhm + 4 * tm + 2, "ROM")
		local okc, b = false, nil
		if okm and move and move > 0 then okc, b = pcall(memory.read_u8, KC.build.moves + move * MOVE_SIZE + 10, "ROM") end
		if okc and b and ((b >> 5) & 3) ~= 2 then list[#list + 1] = { 581 + tm, 1 } end
	end
	return list
end

local function devPcItems()
	if not DEV_ITEMS or KC.devPcDone or not KC.isHnS or KC.noGame or not KC.build then return end
	-- On the title screen and in the intro the save block is not the game's yet, and a new game clears the PC.
	local okm, cb2 = pcall(memory.read_u32_le, MAIN_CB2, "IWRAM")
	if not okm or cb2 ~= KC.build.overworld then return end
	local ok, s = pcall(memory.read_bytes_as_binary_string, 0x41D8, 4, "IWRAM")
	if not ok or not s or #s < 4 then return end
	local ptr = s:byte(1) + s:byte(2) * 256 + s:byte(3) * 65536 + s:byte(4) * 16777216
	if ptr < 0x02000000 or ptr >= 0x02040000 then return end
	local base = ptr - 0x02000000 + 0x49C
	local okp, pc = pcall(memory.read_bytes_as_binary_string, base, 200, "EWRAM")
	if not okp or not pc or #pc < 200 then return end
	local have, free = {}, {}
	for slot = 0, 49 do
		local item = pc:byte(slot * 4 + 1) + pc:byte(slot * 4 + 2) * 256
		if item == 0 then free[#free + 1] = slot else have[item] = true end
	end
	local added, tms = 0, 0
	for _, it in ipairs(devPcList()) do
		if not have[it[1]] and #free > 0 then
			local slot = table.remove(free, 1)
			pcall(memory.write_u16_le, base + slot * 4, it[1], "EWRAM")
			pcall(memory.write_u16_le, base + slot * 4 + 2, it[2], "EWRAM")
			added = added + 1
			if it[1] >= TM_FIRST and it[1] <= TM_LAST then tms = tms + 1 end
		end
	end
	KC.devPcDone = true
	if added > 0 then say("developer items put in the item PC: " .. added .. " items, " .. tms .. " of them damaging TMs") end
end

-- A part of the frame that fails is told on the console once every 600 frames, and the rest of the frame still runs.
local failedAt = {}
local function safely(label, fn)
	local ok, v = pcall(fn)
	if ok then return v end
	if not failedAt[label] or KC.frame - failedAt[label] >= 600 then
		failedAt[label] = KC.frame
		console.log("KaizoCore " .. label .. ": " .. tostring(v))
	end
end

local function step()
	KC.frame = KC.frame + 1
	KC.haveMouse = false
	if KC.frame % 30 == 0 then
		local ok, fps = pcall(client.get_approx_framerate)
		KC.speed = (ok and type(fps) == "number" and fps > 66) and math.min(32, fps / 60) or 1
	end
	if every(15) then
		local was = KC.jarAlive
		readJar()
		if KC.jarAlive and not was then say("connected to KaizoCore PC") end
		if not KC.jarAlive and was then
			KC.gen = 0
			say("KaizoCore PC stopped; tracking paused")
		end
	end
	if every(60) and gameinfo.getromhash() ~= KC.romHash then detect() end
	if every(60, 30) then writeLuaState(true) end
	if KC.jarAlive and KC.isHnS and KC.romHash ~= KC.refusedHash and KC.frame - KC.lastHelloAt > 120 * (KC.speed or 1) then
		local jg = KC.jar and tonumber(KC.jar.gen) or 0
		if KC.gen == 0 or jg ~= KC.gen then hello() end
	end
	if not KC.jarAlive and KC.isHnS and KC.frame - KC.warnedAt > 3600 then
		KC.warnedAt = KC.frame
		say("KaizoCore PC is not running: start KaizoCore PC to track and for New Run")
	end

	if every(30) then safely("style", forceSetStyle) end
	if every(60, 45) then safely("dev items", devPcItems) end
	-- The ability banner stays up for many frames. One memory read a frame was a stall each frame (BizHawk's reads
	-- wait on the emulator); every other frame still sees it.
	if every(2) then safely("ability", watchAbilityPopup) end
	safely("typing", typingKeys)
	if overlay.file and not live.on then
		local keys = pressedKeys()
		if keys["Escape"] and not logEsc then sendInput({ "cmd=logclose" }) end
		logEsc = keys["Escape"] == true
	else
		logEsc = false
	end
	local clicking = safely("click", blankIfClicking) or panel.typing == "1"
	local joy = safely("joypad", function() return (TEST and TEST.joypad) and TEST.joypad(KC) or joypad.get() end)
	-- A Heart & Soul attempt ends only on a second hold of the combo, within 3 seconds of the first. A click on the
	-- tracker never counts. The window's New Run button is a separate command and already asked.
	if not clicking and KC.frame >= KC.comboAllowedAt and comboHeld(joy) then
		if KC.isHnS and not KC.noGame then
			if KC.frame <= (KC.confirmUntil or 0) then
				KC.confirmUntil = 0
				newRun()
			else
				KC.confirmUntil = KC.frame + 180
				KC.comboAllowedAt = KC.frame + 45
				say("Release, then hold " .. combo() .. " again to end this attempt.")
			end
		else
			newRun()
		end
		return
	end
	-- besteon's "Toggle view" (Input.lua, Battle.togglePokemonViewed): Start, pressed in battle, swaps the panel
	-- between your Pokemon and the opponent.
	local startDown = joy and joy.Start == true
	if startDown and not KC.lastStart and battle.on and KC.jarAlive then sendInput({ "cmd=toggle" }) end
	KC.lastStart = startDown

	-- The next read in frames at the game's real speed: os.clock is BizHawk's CPU time, which runs ahead of the clock
	-- the faster the emulator works, so reads came closer together the faster the game ran.
	if KC.gen > 0 and KC.jarAlive and KC.frame >= KC.nextSnapAt then
		if snapshot() then
			local poll = tonumber(KC.jar and KC.jar.poll) or 700
			KC.nextSnapAt = KC.frame + math.max(1, math.floor(poll / 1000 * 60 * (KC.speed or 1) + 0.5))
		else
			KC.nextSnapAt = KC.frame + math.max(8, math.floor(8 * (KC.speed or 1) + 0.5))
		end
	end
	local okp, errp = pcall(panelStep)
	if not okp then console.log("KaizoCore panel: " .. tostring(errp)) end
	local oka, erra = pcall(playAsStep, joy)
	if not oka and KC.frame % 600 == 0 then console.log("KaizoCore play as: " .. tostring(erra)) end
	if TEST and TEST.onFrame then TEST.onFrame(KC) end
end

console.log("KaizoCore for BizHawk " .. KC.version .. ", session folder " .. SESSION)
event.onexit(cleanup, "KaizoCoreCleanup")
readJar()
detect()
if KC.noGame then
	say("no game is open. Press New Run in KaizoCore PC's window to open the next Heart & Soul run.")
elseif not KC.isHnS then
	say("this is not Heart & Soul. For other games load besteon's Ironmon-Tracker.lua; New Run here opens a Heart & Soul run.")
end
writeLuaState(true)
if KC.jarAlive then hello() end

-- New Run is looked for outside step, so nothing that fails in a frame can keep the next game from opening.
while true do
	safely("frame", step)
	if every(3, 1) then safely("cmd", takeCmd) end
	if TEST and TEST.done then break end
	emu.frameadvance()
end
cleanup()
