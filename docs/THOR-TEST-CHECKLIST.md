# KaizoCore on the AYN Thor: two-screen DS test

Thank you for testing. This build puts a DS game's bottom screen on the Thor's small screen and the top screen on the
big one. It has only been tried on a computer that pretends to have a second screen, never on a real Thor, so whatever
you see is useful, good or bad.

## 1. Install

1. Copy the APK to the Thor and open it. Allow installs from that app if Android asks.
2. It installs over your KaizoCore and keeps your games, runs and saves. The next public release installs over it.
3. Check the version: More, then About. It should say `1.0.0-rc40-thor1`.

## 2. Turn it on

1. Start a DS game. Platinum first if you have it, then HeartGold and Black 2.
2. Tap the top part of the game picture to open the File bar, tap Tracker, then Tracker Setup.
3. Turn on **DS games: bottom screen on the second screen**.
4. Turn on **Second screen touch info (for testing)**. It prints a few lines on both screens: the second screen's size,
   how many pictures have been drawn there, and where your last touch landed.
5. If you do not see the first switch at all, stop here and tell us. It means Android does not offer the small screen
   to apps the way this build asks for it. Send a bug report (section 4) so we can see what the Thor lists.

## 3. Check each of these

Write down yes or no for each, and anything odd.

1. **Both screens show.** The big screen shows only the DS top screen. The small screen shows the DS bottom screen,
   upright, with thin black bands at the top and bottom. Is the picture sideways, stretched or cut off?
2. **The picture moves.** The "Frames drawn there" number keeps going up while you play.
3. **Touch works on the small screen.** Tap the menus that live on the bottom screen: the battle buttons (Fight, Bag,
   Pokémon, Run), the Poketch in Platinum, the menu in HeartGold, the C-Gear in Black 2. Do they react where you tap?
4. **The numbers match.** With touch info on, tap the middle of the small screen. It should say about `DS 128, 96`.
   The top left corner of the picture is about `DS 0, 0` and the bottom right about `DS 255, 191`. Tapping the black
   bands should say "outside the picture" and do nothing in the game.
5. **The buttons still work after a touch.** Tap the small screen, then walk with the stick or d-pad and press A.
   Does the game still answer the controller?
6. **Switch apps and come back.** Press Home, open another app, then go back to KaizoCore. Does the bottom screen come
   back on the small screen? While you are away, the small screen should not keep showing the game.
7. **Sleep and wake.** Put the Thor to sleep and wake it. Does the bottom screen come back?
8. **Turn the small screen off and on**, if the Thor lets you. With it off, both DS screens should come back to the big
   screen. With it on again, the bottom screen should move back to the small one.
9. **Fast forward**, if you use it. Does the game still speed up with the bottom screen on the small screen?
10. **Turn the option off.** Both screens should come back to the big screen, laid out the way you had them.

## 4. What to send

- A photo of both screens with touch info on (a phone photo is fine, screenshots may only catch the big screen).
- The touch info lines: the "Second screen" line, the "Frames drawn there" number and a "Touch" line after a tap in
  the middle.
- Your yes or no list from section 3.
- A bug report from the app: More, then About, then Report a bug. It includes the lines KaizoCore wrote about the
  screens it found.

Post it in the KaizoCore Discord. Thank you!
