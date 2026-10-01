-- kcbot: PokeBot's Yellow route, walked by the Kaizo IronMON rules (see ../../KCBOT.md).
-- Same coordinates as the speedrun route (the maps are not randomized); the speedrun's catches, shopping,
-- stat checks and potions outside battle are gone, each trainer fight is the generic "ironmonFight", and it
-- talks to Mom and heals at the Viridian and Pewter Pokémon Centers ("ironmonCenter").
-- It ends after Brock: most Kaizo runs are over long before, and the soak starts the next seed either way.
local YellowPaths = {

	-- Ash's room
	{38, {3,6}, {5,6}, {5,1}, {7,1}},
	-- Ash's house: "Don't be Rude: You have to talk to MOM when you start your journey." She sits at (5,4), facing the
	-- table on her left, so she is spoken to from her right.
	{39, {7,1}, {7,4}, {6,4}, {s="interact",dir="Left"}, {6,6}, {3,6}, {3,8}},
	-- Into the Wild
	{0, {5,6}, {10,6}, {10,0}},
	-- The lab: the rival takes a ball, Oak hands over the (randomized) starter, the lab fight
	{40, {5,3}, {c="a",a="Pallet Rival"}, {5,4}, {7,4}, {s="take",dir="Up"}, {5,3}, {s="dialogue",dir="Up",decline=true}, {s="acquire",poke="starter"}, {5,6}, {s="ironmonFight",wait=900}, {5,12}},
	-- Out of the lab
	{0, {12,12}, {c="a",a="Pallet Town"}, {9,12}, {9,2}, {10,2}, {10,-1}},
	-- Route 1
	{12, {10,35}, {10,30}, {8,30}, {8,24}, {12,24}, {12,20}, {9,20}, {9,14}, {14,14}, {s="dodgePalletBoy"}, {14,2}, {11,2}, {11,-1}},
	-- To the Mart (the clerk hands over Oak's parcel)
	{1, {21,35}, {21,30}, {19,30}, {19,20}, {29,20}, {29,19}},
	-- Viridian Mart
	{42, {2,5}, {2,6}, {3,6}, {3,8}},
	-- Back south
	{1, {29,20}, {29,21}, {26,21}, {26,30}, {20,30}, {20,36}},
	-- Route 1, south
	{12, {10, 0}, {10,3}, {8,3}, {8,18}, {9,18}, {9,21}, {12,21}, {12,24}, {10,24}, {10,36}},
	-- To Oak's lab
	{0, {10,0}, {10,7}, {9,7}, {9,12}, {12,12}, {12,11}},
	-- Parcel delivery
	{40, {5,11}, {5,3}, {4,3}, {4,1}, {5,1}, {s="interact",dir="Down"}, {4,1}, {4,12}},
	-- Leaving home
	{0, {12,12}, {9,12}, {9,2}, {10,2}, {10,-1}},
	-- Route 1 again
	{12, {10,35}, {10,30}, {8,30}, {8,24}, {12,24}, {12,20}, {9,20}, {9,14}, {14,14}, {s="dodgePalletBoy"}, {14,2}, {11,2}, {11,-1}},
	-- Through Viridian to the north, healing at the Pokémon Center (door at 23,25) on the way: Route 1's wild battles
	-- are run from, and a failed escape costs HP the forest's trainers would collect
	{1, {21,35}, {21,30}, {19,30}, {19,26}, {s="ironmonCenter",map=1,x=23,y=25,finishX=19}, {19,20}, {19,9}, {19,2}, {17,2}, {17,-1}},
	-- Route 2
	{13, {7,71}, {c="a",a="Route 2"}, {7,57}, {4,57}, {4,52}, {8,47}, {8,45}, {8,44}, {3,44}, {3,43}},
	-- Forest entrance
	{50, {4,7}, {c="a",a="Viridian Forest"}, {4,1}, {5,1}, {5,0}},
	-- Viridian Forest: three Bug Catchers
	{51, {16,47}, {16,44}, {18,44}, {18,42}, {26,42}, {27,34}, {27,33}, {s="ironmonFight",wait=240}, {27,32}, {31,32}, {31,18}, {25,18}, {25,12}, {26,12}, {26,9}, {17,9}, {17,16}, {15,16}, {13,16}, {s="interact",dir="Down"}, {s="ironmonFight",wait=240}, {13,3}, {6,3}, {6,22}, {2,22}, {2,19}, {s="interact",dir="Up"}, {s="ironmonFight",wait=240}, {1,19}, {1,16}, {1,-1}},
	-- Forest exit
	{47, {4,7}, {4,1}, {5,1}, {5,0}},
	-- Road to Pewter City
	{13, {3,11}, {3,8}, {8,8}, {8,-1}},
	-- Pewter City: the Pokémon Center, then the gym
	{2, {18,35}, {c="a",a="Pewter City"}, {18,26}, {s="ironmonCenter",map=2,x=13,y=25,finishX=18}, {18,22}, {19,22}, {19,13}, {10,13}, {10,18}, {16,18}, {16,17}},
	-- Brock's gym: the Jr. Trainer, then Brock
	{54, {4,13}, {c="a",a="Brock's Gym"}, {4,9}, {4,8}, {3,8}, {3,7}, {s="interact",dir="Up"}, {s="ironmonFight",wait=240}, {4,7}, {4,4}, {4,2}, {s="interact",dir="Up"}, {s="ironmonFight",wait=240}, {s="routeEnd"}, {4,14}},
}

return YellowPaths
