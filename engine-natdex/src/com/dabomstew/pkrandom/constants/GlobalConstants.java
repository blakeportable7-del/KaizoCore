package com.dabomstew.pkrandom.constants;

/*----------------------------------------------------------------------------*/
/*--  GlobalConstants.java - constants that are relevant for multiple games --*/
/*--                         in the Pokemon series                          --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import com.dabomstew.pkrandom.pokemon.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GlobalConstants {

    public static final boolean[] bannedRandomMoves = new boolean[2000], bannedForDamagingMove = new boolean[2000];
    static {
        bannedRandomMoves[Moves.struggle] = true; //  self explanatory
        bannedRandomMoves[Moves.roost] = true;
        bannedRandomMoves[Moves.gravity] = true;
        bannedRandomMoves[Moves.miracleEye] = true;
        bannedRandomMoves[Moves.wakeUpSlap] = true;
        bannedRandomMoves[Moves.hammerArm] = true;
        bannedRandomMoves[Moves.gyroBall] = true;
        bannedRandomMoves[Moves.healingWish] = true;
        bannedRandomMoves[Moves.brine] = true;
        bannedRandomMoves[Moves.naturalGift] = true;
        bannedRandomMoves[Moves.feint] = true;
        bannedRandomMoves[Moves.pluck] = true;
        bannedRandomMoves[Moves.tailwind] = true;
        bannedRandomMoves[Moves.acupressure] = true;
        bannedRandomMoves[Moves.metalBurst] = true;
        bannedRandomMoves[Moves.uTurn] = true;
        bannedRandomMoves[Moves.closeCombat] = true;
        bannedRandomMoves[Moves.payback] = true;
        bannedRandomMoves[Moves.assurance] = true;
        bannedRandomMoves[Moves.embargo] = true;
        bannedRandomMoves[Moves.fling] = true;
        bannedRandomMoves[Moves.psychoShift] = true;
        bannedRandomMoves[Moves.trumpCard] = true;
        bannedRandomMoves[Moves.healBlock] = true;
        bannedRandomMoves[Moves.wringOut] = true;
        bannedRandomMoves[Moves.powerTrick] = true;
        bannedRandomMoves[Moves.gastroAcid] = true;
        bannedRandomMoves[Moves.luckyChant] = true;
        bannedRandomMoves[Moves.meFirst] = true;
        bannedRandomMoves[Moves.copycat] = true;
        bannedRandomMoves[Moves.powerSwap] = true;
        bannedRandomMoves[Moves.guardSwap] = true;
        bannedRandomMoves[Moves.punishment] = true;
        bannedRandomMoves[Moves.lastResort] = true;
        bannedRandomMoves[Moves.worrySeed] = true;
        bannedRandomMoves[Moves.suckerPunch] = true;
        bannedRandomMoves[Moves.toxicSpikes] = true;
        bannedRandomMoves[Moves.heartSwap] = true;
        bannedRandomMoves[Moves.aquaRing] = true;
        bannedRandomMoves[Moves.magnetRise] = true;
        bannedRandomMoves[Moves.flareBlitz] = true;
        bannedRandomMoves[Moves.forcePalm] = true;
        bannedRandomMoves[Moves.auraSphere] = true;
        bannedRandomMoves[Moves.rockPolish] = true;
        bannedRandomMoves[Moves.poisonJab] = true;
        bannedRandomMoves[Moves.darkPulse] = true;
        bannedRandomMoves[Moves.nightSlash] = true;
        bannedRandomMoves[Moves.aquaTail] = true;
        bannedRandomMoves[Moves.seedBomb] = true;
        bannedRandomMoves[Moves.xScissor] = true;
        bannedRandomMoves[Moves.bugBuzz] = true;
        bannedRandomMoves[Moves.dragonPulse] = true;
        bannedRandomMoves[Moves.dragonRush] = true;
        bannedRandomMoves[Moves.powerGem] = true;
        bannedRandomMoves[Moves.drainPunch] = true;
        bannedRandomMoves[Moves.vacuumWave] = true;
        bannedRandomMoves[Moves.focusBlast] = true;
        bannedRandomMoves[Moves.energyBall] = true;
        bannedRandomMoves[Moves.braveBird] = true;
        bannedRandomMoves[Moves.earthPower] = true;
        bannedRandomMoves[Moves.switcheroo] = true;
        bannedRandomMoves[Moves.gigaImpact] = true;
        bannedRandomMoves[Moves.nastyPlot] = true;
        bannedRandomMoves[Moves.bulletPunch] = true;
        bannedRandomMoves[Moves.avalanche] = true;
        bannedRandomMoves[Moves.iceShard] = true;
        bannedRandomMoves[Moves.shadowClaw] = true;
        bannedRandomMoves[Moves.thunderFang] = true;
        bannedRandomMoves[Moves.iceFang] = true;
        bannedRandomMoves[Moves.fireFang] = true;
        bannedRandomMoves[Moves.mudBomb] = true;
        bannedRandomMoves[Moves.psychoCut] = true;
        bannedRandomMoves[Moves.zenHeadbutt] = true;
        bannedRandomMoves[Moves.mirrorShot] = true;
        bannedRandomMoves[Moves.flashCannon] = true;
        bannedRandomMoves[Moves.rockClimb] = true;
        bannedRandomMoves[Moves.defog] = true;
        bannedRandomMoves[Moves.trickRoom] = true;
        bannedRandomMoves[Moves.dracoMeteor] = true;
        bannedRandomMoves[Moves.discharge] = true;
        bannedRandomMoves[Moves.lavaPlume] = true;
        bannedRandomMoves[Moves.powerWhip] = true;
        bannedRandomMoves[Moves.rockWrecker] = true;
        bannedRandomMoves[Moves.crossPoison] = true;
        bannedRandomMoves[Moves.gunkShot] = true;
        bannedRandomMoves[Moves.ironHead] = true;
        bannedRandomMoves[Moves.magnetBomb] = true;
        bannedRandomMoves[Moves.stoneEdge] = true;
        bannedRandomMoves[Moves.captivate] = true;
        bannedRandomMoves[Moves.stealthRock] = true;
        bannedRandomMoves[Moves.grassKnot] = true;
        bannedRandomMoves[Moves.chatter] = true;
        bannedRandomMoves[Moves.judgment] = true;
        bannedRandomMoves[Moves.bugBite] = true;
        bannedRandomMoves[Moves.chargeBeam] = true;
        bannedRandomMoves[Moves.woodHammer] = true;
        bannedRandomMoves[Moves.aquaJet] = true;
        bannedRandomMoves[Moves.attackOrder] = true;
        bannedRandomMoves[Moves.defendOrder] = true;
        bannedRandomMoves[Moves.healOrder] = true;
        bannedRandomMoves[Moves.headSmash] = true;
        bannedRandomMoves[Moves.doubleHit] = true;
        bannedRandomMoves[Moves.roarOfTime] = true;
        bannedRandomMoves[Moves.spacialRend] = true;
        bannedRandomMoves[Moves.lunarDance] = true;
        bannedRandomMoves[Moves.crushGrip] = true;
        bannedRandomMoves[Moves.magmaStorm] = true;
        bannedRandomMoves[Moves.darkVoid] = true;
        bannedRandomMoves[Moves.seedFlare] = true;
        bannedRandomMoves[Moves.ominousWind] = true;
        bannedRandomMoves[Moves.shadowForce] = true;
        bannedRandomMoves[Moves.honeClaws] = true;
        bannedRandomMoves[Moves.wideGuard] = true;
        bannedRandomMoves[Moves.guardSplit] = true;
        bannedRandomMoves[Moves.powerSplit] = true;
        bannedRandomMoves[Moves.wonderRoom] = true;
        bannedRandomMoves[Moves.psyshock] = true;
        bannedRandomMoves[Moves.venoshock] = true;
        bannedRandomMoves[Moves.autotomize] = true;
        bannedRandomMoves[Moves.ragePowder] = true;
        bannedRandomMoves[Moves.telekinesis] = true;
        bannedRandomMoves[Moves.magicRoom] = true;
        bannedRandomMoves[Moves.smackDown] = true;
        bannedRandomMoves[Moves.stormThrow] = true;
        bannedRandomMoves[Moves.flameBurst] = true;
        bannedRandomMoves[Moves.sludgeWave] = true;
        bannedRandomMoves[Moves.quiverDance] = true;
        bannedRandomMoves[Moves.heavySlam] = true;
        bannedRandomMoves[Moves.synchronoise] = true;
        bannedRandomMoves[Moves.electroBall] = true;
        bannedRandomMoves[Moves.soak] = true;
        bannedRandomMoves[Moves.flameCharge] = true;
        bannedRandomMoves[Moves.coil] = true;
        bannedRandomMoves[Moves.lowSweep] = true;
        bannedRandomMoves[Moves.acidSpray] = true;
        bannedRandomMoves[Moves.foulPlay] = true;
        bannedRandomMoves[Moves.simpleBeam] = true;
        bannedRandomMoves[Moves.entrainment] = true;
        bannedRandomMoves[Moves.afterYou] = true;
        bannedRandomMoves[Moves.round] = true;
        bannedRandomMoves[Moves.echoedVoice] = true;
        bannedRandomMoves[Moves.chipAway] = true;
        bannedRandomMoves[Moves.clearSmog] = true;
        bannedRandomMoves[Moves.storedPower] = true;
        bannedRandomMoves[Moves.quickGuard] = true;
        bannedRandomMoves[Moves.allySwitch] = true;
        bannedRandomMoves[Moves.scald] = true;
        bannedRandomMoves[Moves.shellSmash] = true;
        bannedRandomMoves[Moves.healPulse] = true;
        bannedRandomMoves[Moves.hex] = true;
        bannedRandomMoves[Moves.skyDrop] = true;
        bannedRandomMoves[Moves.shiftGear] = true;
        bannedRandomMoves[Moves.circleThrow] = true;
        bannedRandomMoves[Moves.incinerate] = true;
        bannedRandomMoves[Moves.quash] = true;
        bannedRandomMoves[Moves.acrobatics] = true;
        bannedRandomMoves[Moves.reflectType] = true;
        bannedRandomMoves[Moves.retaliate] = true;
        bannedRandomMoves[Moves.finalGambit] = true;
        bannedRandomMoves[Moves.bestow] = true;
        bannedRandomMoves[Moves.inferno] = true;
        bannedRandomMoves[Moves.waterPledge] = true;
        bannedRandomMoves[Moves.firePledge] = true;
        bannedRandomMoves[Moves.grassPledge] = true;
        bannedRandomMoves[Moves.voltSwitch] = true;
        bannedRandomMoves[Moves.bulldoze] = true;
        bannedRandomMoves[Moves.frostBreath] = true;
        bannedRandomMoves[Moves.dragonTail] = true;
        bannedRandomMoves[Moves.workUp] = true;
        bannedRandomMoves[Moves.electroweb] = true;
        bannedRandomMoves[Moves.wildCharge] = true;
        bannedRandomMoves[Moves.drillRun] = true;
        bannedRandomMoves[Moves.dualChop] = true;
        bannedRandomMoves[Moves.heartStamp] = true;
        bannedRandomMoves[Moves.hornLeech] = true;
        bannedRandomMoves[Moves.sacredSword] = true;
        bannedRandomMoves[Moves.razorShell] = true;
        bannedRandomMoves[Moves.heatCrash] = true;
        bannedRandomMoves[Moves.leafTornado] = true;
        bannedRandomMoves[Moves.steamroller] = true;
        bannedRandomMoves[Moves.cottonGuard] = true;
        bannedRandomMoves[Moves.nightDaze] = true;
        bannedRandomMoves[Moves.psystrike] = true;
        bannedRandomMoves[Moves.tailSlap] = true;
        bannedRandomMoves[Moves.hurricane] = true;
        bannedRandomMoves[Moves.headCharge] = true;
        bannedRandomMoves[Moves.gearGrind] = true;
        bannedRandomMoves[Moves.searingShot] = true;
        bannedRandomMoves[Moves.technoBlast] = true;
        bannedRandomMoves[Moves.relicSong] = true;
        bannedRandomMoves[Moves.secretSword] = true;
        bannedRandomMoves[Moves.glaciate] = true;
        bannedRandomMoves[Moves.boltStrike] = true;
        bannedRandomMoves[Moves.blueFlare] = true;
        bannedRandomMoves[Moves.fieryDance] = true;
        bannedRandomMoves[Moves.freezeShock] = true;
        bannedRandomMoves[Moves.iceBurn] = true;
        bannedRandomMoves[Moves.snarl] = true;
        bannedRandomMoves[Moves.icicleCrash] = true;
        bannedRandomMoves[Moves.vCreate] = true;
        bannedRandomMoves[Moves.fusionFlare] = true;
        bannedRandomMoves[Moves.fusionBolt] = true;
        bannedRandomMoves[Moves.flyingPress] = true;
        bannedRandomMoves[Moves.matBlock] = true;
        bannedRandomMoves[Moves.belch] = true;
        bannedRandomMoves[Moves.rototiller] = true;
        bannedRandomMoves[Moves.stickyWeb] = true;
        bannedRandomMoves[Moves.fellStinger] = true;
        bannedRandomMoves[Moves.phantomForce] = true;
        bannedRandomMoves[Moves.trickOrTreat] = true;
        bannedRandomMoves[Moves.nobleRoar] = true;
        bannedRandomMoves[Moves.ionDeluge] = true;
        bannedRandomMoves[Moves.parabolicCharge] = true;
        bannedRandomMoves[Moves.forestsCurse] = true;
        bannedRandomMoves[Moves.petalBlizzard] = true;
        bannedRandomMoves[Moves.freezeDry] = true;
        bannedRandomMoves[Moves.partingShot] = true;
        bannedRandomMoves[Moves.topsyTurvy] = true;
        bannedRandomMoves[Moves.craftyShield] = true;
        bannedRandomMoves[Moves.flowerShield] = true;
        bannedRandomMoves[Moves.grassyTerrain] = true;
        bannedRandomMoves[Moves.mistyTerrain] = true;
        bannedRandomMoves[Moves.electrify] = true;
        bannedRandomMoves[Moves.boomburst] = true;
        bannedRandomMoves[Moves.fairyLock] = true;
        bannedRandomMoves[Moves.kingsShield] = true;
        bannedRandomMoves[Moves.confide] = true;
        bannedRandomMoves[Moves.diamondStorm] = true;
        bannedRandomMoves[Moves.steamEruption] = true;
        bannedRandomMoves[Moves.hyperspaceHole] = true;
        bannedRandomMoves[Moves.waterShuriken] = true;
        bannedRandomMoves[Moves.mysticalFire] = true;
        bannedRandomMoves[Moves.spikyShield] = true;
        bannedRandomMoves[Moves.aromaticMist] = true;
        bannedRandomMoves[Moves.eerieImpulse] = true;
        bannedRandomMoves[Moves.venomDrench] = true;
        bannedRandomMoves[Moves.powder] = true;
        bannedRandomMoves[Moves.geomancy] = true;
        bannedRandomMoves[Moves.magneticFlux] = true;
        bannedRandomMoves[Moves.happyHour] = true;
        bannedRandomMoves[Moves.electricTerrain] = true;
        bannedRandomMoves[Moves.celebrate] = true;
        bannedRandomMoves[Moves.holdHands] = true;
        bannedRandomMoves[Moves.babyDollEyes] = true;
        bannedRandomMoves[Moves.nuzzle] = true;
        bannedRandomMoves[Moves.holdBack] = true;
        bannedRandomMoves[Moves.infestation] = true;
        bannedRandomMoves[Moves.powerUpPunch] = true;
        bannedRandomMoves[Moves.oblivionWing] = true;
        bannedRandomMoves[Moves.thousandArrows] = true;
        bannedRandomMoves[Moves.thousandWaves] = true;
        bannedRandomMoves[Moves.landsWrath] = true;
        bannedRandomMoves[Moves.lightOfRuin] = true;
        bannedRandomMoves[Moves.originPulse] = true;
        bannedRandomMoves[Moves.precipiceBlades] = true;
        bannedRandomMoves[Moves.dragonAscent] = true;
        bannedRandomMoves[Moves.hyperspaceFury] = true;
        bannedRandomMoves[Moves.shoreUp] = true;
        bannedRandomMoves[Moves.firstImpression] = true;
        bannedRandomMoves[Moves.banefulBunker] = true;
        bannedRandomMoves[Moves.spiritShackle] = true;
        bannedRandomMoves[Moves.darkestLariat] = true;
        bannedRandomMoves[Moves.sparklingAria] = true;
        bannedRandomMoves[Moves.iceHammer] = true;
        bannedRandomMoves[Moves.floralHealing] = true;
        bannedRandomMoves[Moves.highHorsepower] = true;
        bannedRandomMoves[Moves.strengthSap] = true;
        bannedRandomMoves[Moves.solarBlade] = true;
        bannedRandomMoves[Moves.leafage] = true;
        bannedRandomMoves[Moves.spotlight] = true;
        bannedRandomMoves[Moves.toxicThread] = true;
        bannedRandomMoves[Moves.laserFocus] = true;
        bannedRandomMoves[Moves.gearUp] = true;
        bannedRandomMoves[Moves.throatChop] = true;
        bannedRandomMoves[Moves.pollenPuff] = true;
        bannedRandomMoves[Moves.anchorShot] = true;
        bannedRandomMoves[Moves.psychicTerrain] = true;
        bannedRandomMoves[Moves.lunge] = true;
        bannedRandomMoves[Moves.fireLash] = true;
        bannedRandomMoves[Moves.powerTrip] = true;
        bannedRandomMoves[Moves.burnUp] = true;
        bannedRandomMoves[Moves.speedSwap] = true;
        bannedRandomMoves[Moves.smartStrike] = true;
        bannedRandomMoves[Moves.purify] = true;
        bannedRandomMoves[Moves.revelationDance] = true;
        bannedRandomMoves[Moves.coreEnforcer] = true;
        bannedRandomMoves[Moves.tropKick] = true;
        bannedRandomMoves[Moves.instruct] = true;
        bannedRandomMoves[Moves.beakBlast] = true;
        bannedRandomMoves[Moves.clangingScales] = true;
        bannedRandomMoves[Moves.dragonHammer] = true;
        bannedRandomMoves[Moves.brutalSwing] = true;
        bannedRandomMoves[Moves.auroraVeil] = true;
        bannedRandomMoves[Moves.shellTrap] = true;
        bannedRandomMoves[Moves.fleurCannon] = true;
        bannedRandomMoves[Moves.psychicFangs] = true;
        bannedRandomMoves[Moves.stompingTantrum] = true;
        bannedRandomMoves[Moves.shadowBone] = true;
        bannedRandomMoves[Moves.accelerock] = true;
        bannedRandomMoves[Moves.liquidation] = true;
        bannedRandomMoves[Moves.prismaticLaser] = true;
        bannedRandomMoves[Moves.spectralThief] = true;
        bannedRandomMoves[Moves.sunsteelStrike] = true;
        bannedRandomMoves[Moves.moongeistBeam] = true;
        bannedRandomMoves[Moves.tearfulLook] = true;
        bannedRandomMoves[Moves.zingZap] = true;
        bannedRandomMoves[Moves.naturesMadness] = true;
        bannedRandomMoves[Moves.multiAttack] = true;
        bannedRandomMoves[Moves.mindBlown] = true;
        bannedRandomMoves[Moves.plasmaFists] = true;
        bannedRandomMoves[Moves.photonGeyser] = true;
        bannedRandomMoves[Moves.zippyZap] = true;
        bannedRandomMoves[Moves.splishySplash] = true;
        bannedRandomMoves[Moves.floatyFall] = true;
        bannedRandomMoves[Moves.pikaPapow] = true;
        bannedRandomMoves[Moves.bouncyBubble] = true;
        bannedRandomMoves[Moves.buzzyBuzz] = true;
        bannedRandomMoves[Moves.sizzlySlide] = true;
        bannedRandomMoves[Moves.glitzyGlow] = true;
        bannedRandomMoves[Moves.baddyBad] = true;
        bannedRandomMoves[Moves.sappySeed] = true;
        bannedRandomMoves[Moves.freezyFrost] = true;
        bannedRandomMoves[Moves.sparklySwirl] = true;
        bannedRandomMoves[Moves.veeveeVolley] = true;
        bannedRandomMoves[Moves.doubleIronBash] = true;
        bannedRandomMoves[Moves.dynamaxCannon] = true;
        bannedRandomMoves[Moves.snipeShot] = true;
        bannedRandomMoves[Moves.jawLock] = true;
        bannedRandomMoves[Moves.stuffCheeks] = true;
        bannedRandomMoves[Moves.noRetreat] = true;
        bannedRandomMoves[Moves.tarShot] = true;
        bannedRandomMoves[Moves.magicPowder] = true;
        bannedRandomMoves[Moves.dragonDarts] = true;
        bannedRandomMoves[Moves.teatime] = true;
        bannedRandomMoves[Moves.octolock] = true;
        bannedRandomMoves[Moves.boltBeak] = true;
        bannedRandomMoves[Moves.fishiousRend] = true;
        bannedRandomMoves[Moves.courtChange] = true;
        bannedRandomMoves[Moves.clangorousSoul] = true;
        bannedRandomMoves[Moves.bodyPress] = true;
        bannedRandomMoves[Moves.decorate] = true;
        bannedRandomMoves[Moves.drumBeating] = true;
        bannedRandomMoves[Moves.snapTrap] = true;
        bannedRandomMoves[Moves.pyroBall] = true;
        bannedRandomMoves[Moves.behemothBlade] = true;
        bannedRandomMoves[Moves.behemothBash] = true;
        bannedRandomMoves[Moves.auraWheel] = true;
        bannedRandomMoves[Moves.breakingSwipe] = true;
        bannedRandomMoves[Moves.branchPoke] = true;
        bannedRandomMoves[Moves.overdrive] = true;
        bannedRandomMoves[Moves.appleAcid] = true;
        bannedRandomMoves[Moves.gravApple] = true;
        bannedRandomMoves[Moves.spiritBreak] = true;
        bannedRandomMoves[Moves.strangeSteam] = true;
        bannedRandomMoves[Moves.lifeDew] = true;
        bannedRandomMoves[Moves.obstruct] = true;
        bannedRandomMoves[Moves.falseSurrender] = true;
        bannedRandomMoves[Moves.meteorAssault] = true;
        bannedRandomMoves[Moves.eternabeam] = true;
        bannedRandomMoves[Moves.steelBeam] = true;
        bannedRandomMoves[Moves.expandingForce] = true;
        bannedRandomMoves[Moves.steelRoller] = true;
        bannedRandomMoves[Moves.scaleShot] = true;
        bannedRandomMoves[Moves.meteorBeam] = true;
        bannedRandomMoves[Moves.shellSideArm] = true;
        bannedRandomMoves[Moves.mistyExplosion] = true;
        bannedRandomMoves[Moves.grassyGlide] = true;
        bannedRandomMoves[Moves.risingVoltage] = true;
        bannedRandomMoves[Moves.terrainPulse] = true;
        bannedRandomMoves[Moves.skitterSmack] = true;
        bannedRandomMoves[Moves.burningJealousy] = true;
        bannedRandomMoves[Moves.lashOut] = true;
        bannedRandomMoves[Moves.poltergeist] = true;
        bannedRandomMoves[Moves.corrosiveGas] = true;
        bannedRandomMoves[Moves.coaching] = true;
        bannedRandomMoves[Moves.flipTurn] = true;
        bannedRandomMoves[Moves.tripleAxel] = true;
        bannedRandomMoves[Moves.dualWingbeat] = true;
        bannedRandomMoves[Moves.scorchingSands] = true;
        bannedRandomMoves[Moves.jungleHealing] = true;
        bannedRandomMoves[Moves.wickedBlow] = true;
        bannedRandomMoves[Moves.surgingStrikes] = true;
        bannedRandomMoves[Moves.thunderCage] = true;
        bannedRandomMoves[Moves.dragonEnergy] = true;
        bannedRandomMoves[Moves.freezingGlare] = true;
        bannedRandomMoves[Moves.fieryWrath] = true;
        bannedRandomMoves[Moves.thunderousKick] = true;
        bannedRandomMoves[Moves.glacialLance] = true;
        bannedRandomMoves[Moves.astralBarrage] = true;
        bannedRandomMoves[Moves.eerieSpell] = true;
        bannedRandomMoves[Moves.direClaw] = true;
        bannedRandomMoves[Moves.psyshieldBash] = true;
        bannedRandomMoves[Moves.powerShift] = true;
        bannedRandomMoves[Moves.stoneAxe] = true;
        bannedRandomMoves[Moves.springtideStorm] = true;
        bannedRandomMoves[Moves.mysticalPower] = true;
        bannedRandomMoves[Moves.ragingFury] = true;
        bannedRandomMoves[Moves.waveCrash] = true;
        bannedRandomMoves[Moves.chloroblast] = true;
        bannedRandomMoves[Moves.mountainGale] = true;
        bannedRandomMoves[Moves.victoryDance] = true;
        bannedRandomMoves[Moves.headlongRush] = true;
        bannedRandomMoves[Moves.barbBarrage] = true;
        bannedRandomMoves[Moves.esperWing] = true;
        bannedRandomMoves[Moves.bitterMalice] = true;
        bannedRandomMoves[Moves.shelter] = true;
        bannedRandomMoves[Moves.tripleArrows] = true;
        bannedRandomMoves[Moves.infernalParade] = true;
        bannedRandomMoves[Moves.ceaselessEdge] = true;
        bannedRandomMoves[Moves.bleakwindStorm] = true;
        bannedRandomMoves[Moves.wildboltStorm] = true;
        bannedRandomMoves[Moves.sandsearStorm] = true;
        bannedRandomMoves[Moves.lunarBlessing] = true;
        bannedRandomMoves[Moves.takeHeart] = true;
        bannedRandomMoves[Moves.teraBlast] = true;
        bannedRandomMoves[Moves.silkTrap] = true;
        bannedRandomMoves[Moves.axeKick] = true;
        bannedRandomMoves[Moves.lastRespects] = true;
        bannedRandomMoves[Moves.luminaCrash] = true;
        bannedRandomMoves[Moves.orderUp] = true;
        bannedRandomMoves[Moves.jetPunch] = true;
        bannedRandomMoves[Moves.spicyExtract] = true;
        bannedRandomMoves[Moves.spinOut] = true;
        bannedRandomMoves[Moves.populationBomb] = true;
        bannedRandomMoves[Moves.iceSpinner] = true;
        bannedRandomMoves[Moves.glaiveRush] = true;
        bannedRandomMoves[Moves.revivalBlessing] = true;
        bannedRandomMoves[Moves.saltCure] = true;
        bannedRandomMoves[Moves.tripleDive] = true;
        bannedRandomMoves[Moves.mortalSpin] = true;
        bannedRandomMoves[Moves.doodle] = true;
        bannedRandomMoves[Moves.filletAway] = true;
        bannedRandomMoves[Moves.kowtowCleave] = true;
        bannedRandomMoves[Moves.flowerTrick] = true;
        bannedRandomMoves[Moves.torchSong] = true;
        bannedRandomMoves[Moves.aquaStep] = true;
        bannedRandomMoves[Moves.ragingBull] = true;
        bannedRandomMoves[Moves.makeItRain] = true;
        bannedRandomMoves[Moves.ruination] = true;
        bannedRandomMoves[Moves.collisionCourse] = true;
        bannedRandomMoves[Moves.electroDrift] = true;
        bannedRandomMoves[Moves.shedTail] = true;
        bannedRandomMoves[Moves.chillyReception] = true;
        bannedRandomMoves[Moves.tidyUp] = true;
        bannedRandomMoves[Moves.snowscape] = true;
        bannedRandomMoves[Moves.pounce] = true;
        bannedRandomMoves[Moves.trailblaze] = true;
        bannedRandomMoves[Moves.chillingWater] = true;
        bannedRandomMoves[Moves.hyperDrill] = true;
        bannedRandomMoves[Moves.twinBeam] = true;
        bannedRandomMoves[Moves.rageFist] = true;
        bannedRandomMoves[Moves.armorCannon] = true;
        bannedRandomMoves[Moves.bitterBlade] = true;
        bannedRandomMoves[Moves.doubleShock] = true;
        bannedRandomMoves[Moves.gigatonHammer] = true;
        bannedRandomMoves[Moves.comeuppance] = true;
        bannedRandomMoves[Moves.aquaCutter] = true;
        bannedRandomMoves[Moves.blazingTorque] = true;
        bannedRandomMoves[Moves.wickedTorque] = true;
        bannedRandomMoves[Moves.noxiousTorque] = true;
        bannedRandomMoves[Moves.combatTorque] = true;
        bannedRandomMoves[Moves.magicalTorque] = true;
        bannedRandomMoves[Moves.psyblade] = true;
        bannedRandomMoves[Moves.hydroSteam] = true;
        bannedRandomMoves[Moves.bloodMoon] = true;
        bannedRandomMoves[Moves.matchaGotcha] = true;
        bannedRandomMoves[Moves.syrupBomb] = true;
        bannedRandomMoves[Moves.ivyCudgel] = true;
        bannedRandomMoves[Moves.electroShot] = true;
        bannedRandomMoves[Moves.teraStarstorm] = true;
        bannedRandomMoves[Moves.fickleBeam] = true;
        bannedRandomMoves[Moves.burningBulwark] = true;
        bannedRandomMoves[Moves.thunderclap] = true;
        bannedRandomMoves[Moves.mightyCleave] = true;
        bannedRandomMoves[Moves.tachyonCutter] = true;
        bannedRandomMoves[Moves.hardPress] = true;
        bannedRandomMoves[Moves.dragonCheer] = true;
        bannedRandomMoves[Moves.alluringVoice] = true;
        bannedRandomMoves[Moves.temperFlare] = true;
        bannedRandomMoves[Moves.supercellSlam] = true;
        bannedRandomMoves[Moves.psychicNoise] = true;
        bannedRandomMoves[Moves.upperHand] = true;
        bannedRandomMoves[Moves.malignantChain] = true;

        bannedForDamagingMove[Moves.selfDestruct] = true;
        bannedForDamagingMove[Moves.dreamEater] = true;
        bannedForDamagingMove[Moves.explosion] = true;
        bannedForDamagingMove[Moves.snore] = true;
        bannedForDamagingMove[Moves.falseSwipe] = true;
        bannedForDamagingMove[Moves.futureSight] = true;
        bannedForDamagingMove[Moves.fakeOut] = true;
        bannedForDamagingMove[Moves.focusPunch] = true;
        bannedForDamagingMove[Moves.doomDesire] = true;
        bannedForDamagingMove[Moves.feint] = true;
        bannedForDamagingMove[Moves.lastResort] = true;
        bannedForDamagingMove[Moves.suckerPunch] = true;
        bannedForDamagingMove[Moves.constrict] = true; // overly weak
        bannedForDamagingMove[Moves.rage] = true; // lock-in in gen1
        bannedForDamagingMove[Moves.rollout] = true; // lock-in
        bannedForDamagingMove[Moves.iceBall] = true; // Rollout clone
        bannedForDamagingMove[Moves.synchronoise] = true; // hard to use
        bannedForDamagingMove[Moves.shellTrap] = true; // hard to use
        bannedForDamagingMove[Moves.foulPlay] = true; // doesn't depend on your own attacking stat
        bannedForDamagingMove[Moves.spitUp] = true; // hard to use

        // make sure these cant roll
        bannedForDamagingMove[Moves.sonicBoom] = true;
        bannedForDamagingMove[Moves.dragonRage] = true;
        bannedForDamagingMove[Moves.hornDrill] = true;
        bannedForDamagingMove[Moves.guillotine] = true;
        bannedForDamagingMove[Moves.fissure] = true;
        bannedForDamagingMove[Moves.sheerCold] = true;

    }

    /* @formatter:off */
    public static final List<Integer> normalMultihitMoves = Arrays.asList(
            Moves.armThrust, Moves.barrage, Moves.boneRush, Moves.bulletSeed, Moves.cometPunch, Moves.doubleSlap,
            Moves.furyAttack, Moves.furySwipes, Moves.icicleSpear, Moves.pinMissile, Moves.rockBlast, Moves.spikeCannon,
            Moves.tailSlap, Moves.waterShuriken);

    public static final List<Integer> doubleHitMoves = Arrays.asList(
            Moves.bonemerang, Moves.doubleHit, Moves.doubleIronBash, Moves.doubleKick, Moves.dragonDarts,
            Moves.dualChop, Moves.gearGrind, Moves.twineedle);

    public static final List<Integer> varyingPowerZMoves = Arrays.asList(
            );

    public static final List<Integer> fixedPowerZMoves = Arrays.asList(
            );

    public static final List<Integer> zMoves = Stream.concat(fixedPowerZMoves.stream(),
            varyingPowerZMoves.stream()).collect(Collectors.toList());

    public static Map<Integer,StatChange> getStatChanges(int generation) {
        Map<Integer,StatChange> map = new TreeMap<>();

        switch(generation) {
            case 6:
                map.put(Species.butterfree,new StatChange(Stat.SPATK.val,90));
                map.put(Species.beedrill,new StatChange(Stat.ATK.val,90));
                map.put(Species.pidgeot,new StatChange(Stat.SPEED.val,101));
                map.put(Species.pikachu,new StatChange(Stat.DEF.val | Stat.SPDEF.val,40, 50));
                map.put(Species.raichu,new StatChange(Stat.SPEED.val,110));
                map.put(Species.nidoqueen,new StatChange(Stat.ATK.val,92));
                map.put(Species.nidoking,new StatChange(Stat.ATK.val,102));
                map.put(Species.clefable,new StatChange(Stat.SPATK.val,95));
                map.put(Species.wigglytuff,new StatChange(Stat.SPATK.val,85));
                map.put(Species.vileplume,new StatChange(Stat.SPATK.val,110));
                map.put(Species.poliwrath,new StatChange(Stat.ATK.val,95));
                map.put(Species.alakazam,new StatChange(Stat.SPDEF.val,95));
                map.put(Species.victreebel,new StatChange(Stat.SPDEF.val,70));
                map.put(Species.golem,new StatChange(Stat.ATK.val,120));
                map.put(Species.ampharos,new StatChange(Stat.DEF.val,85));
                map.put(Species.bellossom,new StatChange(Stat.DEF.val,95));
                map.put(Species.azumarill,new StatChange(Stat.SPATK.val,60));
                map.put(Species.jumpluff,new StatChange(Stat.SPDEF.val,95));
                map.put(Species.beautifly,new StatChange(Stat.SPATK.val,100));
                map.put(Species.exploud,new StatChange(Stat.SPDEF.val,73));
                map.put(Species.staraptor,new StatChange(Stat.SPDEF.val,60));
                map.put(Species.roserade,new StatChange(Stat.DEF.val,65));
                map.put(Species.stoutland,new StatChange(Stat.ATK.val,110));
                map.put(Species.unfezant,new StatChange(Stat.ATK.val,115));
                map.put(Species.gigalith,new StatChange(Stat.SPDEF.val,80));
                map.put(Species.seismitoad,new StatChange(Stat.ATK.val,95));
                map.put(Species.leavanny,new StatChange(Stat.SPDEF.val,80));
                map.put(Species.scolipede,new StatChange(Stat.ATK.val,100));
                map.put(Species.krookodile,new StatChange(Stat.DEF.val,80));
                break;
            case 7:
                map.put(Species.arbok,new StatChange(Stat.ATK.val,95));
                map.put(Species.dugtrio,new StatChange(Stat.ATK.val,100));
                map.put(Species.farfetchd,new StatChange(Stat.ATK.val,90));
                map.put(Species.dodrio,new StatChange(Stat.SPEED.val,110));
                map.put(Species.electrode,new StatChange(Stat.SPEED.val,150));
                map.put(Species.exeggutor,new StatChange(Stat.SPDEF.val,75));
                map.put(Species.noctowl,new StatChange(Stat.SPATK.val,86));
                map.put(Species.ariados,new StatChange(Stat.SPDEF.val,70));
                map.put(Species.qwilfish,new StatChange(Stat.DEF.val,85));
                map.put(Species.magcargo,new StatChange(Stat.HP.val | Stat.SPATK.val,60,90));
                map.put(Species.corsola,new StatChange(Stat.HP.val | Stat.DEF.val | Stat.SPDEF.val,65,95,95));
                map.put(Species.mantine,new StatChange(Stat.HP.val,85));
                map.put(Species.swellow,new StatChange(Stat.SPATK.val,75));
                map.put(Species.pelipper,new StatChange(Stat.SPATK.val,95));
                map.put(Species.masquerain,new StatChange(Stat.SPATK.val | Stat.SPEED.val,100,80));
                map.put(Species.delcatty,new StatChange(Stat.SPEED.val,90));
                map.put(Species.volbeat,new StatChange(Stat.DEF.val | Stat.SPDEF.val,75,85));
                map.put(Species.illumise,new StatChange(Stat.DEF.val | Stat.SPDEF.val,75,85));
                map.put(Species.lunatone,new StatChange(Stat.HP.val,90));
                map.put(Species.solrock,new StatChange(Stat.HP.val,90));
                map.put(Species.chimecho,new StatChange(Stat.HP.val | Stat.DEF.val | Stat.SPDEF.val,75,80,90));
                map.put(Species.woobat,new StatChange(Stat.HP.val,65));
                map.put(Species.crustle,new StatChange(Stat.ATK.val,105));
                map.put(Species.beartic,new StatChange(Stat.ATK.val,130));
                map.put(Species.cryogonal,new StatChange(Stat.HP.val | Stat.DEF.val,80,50));
                break;
            case 8:
                map.put(Species.aegislash,new StatChange(Stat.DEF.val | Stat.SPDEF.val,140,140));
                break;
            case 9:
                map.put(Species.cresselia,new StatChange(Stat.DEF.val | Stat.SPDEF.val, 110,120));
                map.put(Species.zacian,new StatChange(Stat.ATK.val, 120));
                map.put(Species.zamazenta,new StatChange(Stat.ATK.val, 120));
                break;
        }
        return map;
    }

    /* @formatter:on */

    public static final List<Integer> xItems = Arrays.asList(Items.guardSpec, Items.direHit, Items.xAttack,
            Items.xDefense, Items.xSpeed, Items.xAccuracy, Items.xSpAtk, Items.xSpDef);

    public static final List<Integer> battleTrappingAbilities = Arrays.asList(Abilities.shadowTag, Abilities.magnetPull,
            Abilities.arenaTrap);

    public static final List<Integer> negativeAbilities = Arrays.asList(
            Abilities.defeatist, Abilities.slowStart, Abilities.truant, Abilities.klutz, Abilities.stall
    );

    public static final List<Integer> badAbilities = Arrays.asList(
            Abilities.minus, Abilities.plus, Abilities.anticipation, Abilities.forewarn, Abilities.frisk,
            Abilities.honeyGather, Abilities.auraBreak, Abilities.receiver, Abilities.powerOfAlchemy
    );

    public static final List<Integer> doubleBattleAbilities = Arrays.asList(
            Abilities.friendGuard, Abilities.healer, Abilities.telepathy, Abilities.symbiosis,
            Abilities.battery
    );

    public static final List<Integer> duplicateAbilities = Arrays.asList(
            Abilities.vitalSpirit, Abilities.whiteSmoke, Abilities.purePower, Abilities.shellArmor, Abilities.airLock,
            Abilities.solidRock, Abilities.ironBarbs, Abilities.turboblaze, Abilities.teravolt, Abilities.emergencyExit,
            Abilities.dazzling, Abilities.tanglingHair, Abilities.powerOfAlchemy, Abilities.fullMetalBody,
            Abilities.shadowShield, Abilities.prismArmor, Abilities.libero, Abilities.stalwart
    );

    public static final List<Integer> noPowerNonStatusMoves = Arrays.asList(
            Moves.guillotine, Moves.hornDrill, Moves.sonicBoom, Moves.lowKick, Moves.counter, Moves.seismicToss,
            Moves.dragonRage, Moves.fissure, Moves.nightShade, Moves.bide, Moves.psywave, Moves.superFang,
            Moves.flail, Moves.revenge, Moves.returnTheMoveNotTheKeyword, Moves.present, Moves.frustration,
            Moves.magnitude, Moves.mirrorCoat, Moves.beatUp, Moves.spitUp, Moves.sheerCold
    );

    public static final List<Integer> cannotBeObsoletedMoves = Arrays.asList(
            Moves.returnTheMoveNotTheKeyword, Moves.frustration, Moves.endeavor, Moves.flail, Moves.reversal,
            Moves.hiddenPower, Moves.storedPower, Moves.smellingSalts, Moves.fling, Moves.powerTrip, Moves.counter,
            Moves.mirrorCoat, Moves.superFang
    );

    public static final List<Integer> cannotObsoleteMoves = Arrays.asList(
            Moves.gearUp, Moves.magneticFlux, Moves.focusPunch, Moves.explosion, Moves.selfDestruct, Moves.geomancy,
            Moves.venomDrench
    );

    public static final List<Integer> doubleBattleMoves = Arrays.asList(
            Moves.followMe, Moves.helpingHand, Moves.ragePowder, Moves.afterYou, Moves.allySwitch, Moves.healPulse,
            Moves.quash, Moves.ionDeluge, Moves.matBlock, Moves.aromaticMist, Moves.electrify, Moves.instruct,
            Moves.spotlight, Moves.decorate, Moves.lifeDew, Moves.coaching
    );

    public static final List<Integer> uselessMoves = Arrays.asList(
            Moves.splash, Moves.celebrate, Moves.holdHands, Moves.teleport,
            Moves.reflectType       // the AI does not know how to use this move properly
    );

    public static final List<Integer> requiresOtherMove = Arrays.asList(
            Moves.spitUp, Moves.swallow, Moves.dreamEater, Moves.nightmare
    );

    public static final int MIN_DAMAGING_MOVE_POWER = 50;

    public static final int HIGHEST_POKEMON_GEN = 9;

    // Eevee has 8 potential evolutions
    public static final int LARGEST_NUMBER_OF_SPLIT_EVOS = 8;
}
