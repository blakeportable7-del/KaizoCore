-- kcbot: PokeBot playing the story for the wiki's screenshots (run_pokebot.py). A speedrun resets the console the
-- moment a run goes wrong; here the runner reloads the checkpoint it took when the player last arrived on a map, and
-- PokeBot carries on from there. Loaded by this folder's main.lua right after PokeBot's own modules.

local Data = require "data.data"
local Strategies = require("ai."..Data.gameName..".strategies")

local function died(reason)
	error("KCBOT_DIED "..tostring(reason or ""), 0)
end

-- Never the power button: a death, being stuck or any other reset goes back to the runner.
Strategies.reboot = function() end
Strategies.reset = function(reason, explanation)
	died(tostring(reason).." "..tostring(explanation or ""))
end
Strategies.hardReset = function(reason, message)
	if reason == "won" then
		error("KCBOT_WON "..tostring(message or ""), 0)
	end
	died(tostring(reason).." "..tostring(message or ""))
end
