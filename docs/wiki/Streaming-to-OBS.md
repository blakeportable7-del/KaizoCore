# Streaming to OBS

KaizoCore sends the game's picture and sound, the tracker and your attempt counter to OBS on your PC, over a USB cable
or your Wi-Fi. No screen mirroring, no capture card. It works for any game you play, in a run or not.

## Set it up with a USB cable

The cable is the steadiest way: no Wi-Fi drops, and the address never changes.

You need USB debugging on the phone and adb on the PC. If you already use scrcpy, you have both: scrcpy comes with adb.
Otherwise turn on USB debugging in the phone's Developer options and get adb from Android's SDK Platform Tools.

1. Plug the phone into the PC and allow USB debugging if the phone asks.
2. On the PC, in a command window, run:

   ```
   adb forward tcp:8642 tcp:8642
   ```

   Run it again each time you plug the phone back in or restart the PC. It is the only command.
3. In the game, tap the top of the screen for the File bar, then **TOOLS**, **Stream**. It shows two links, Wi-Fi and USB cable. **Copy USB link** copies the
   cable's: `http://127.0.0.1:8642/?k=...`
4. Open that address in a browser on the PC. It is the setup guide, with a test of the picture. It says "You are on
   the USB cable" at the top.
5. **Download the OBS scene**. In OBS: Scene Collection, Import, pick the file. The scene "KaizoCore" has the game,
   the tracker, the attempts and the game over card, laid out at 1920 x 1080, and the run timer, the favorites and
   the run history, hidden. Every address in it starts `http://127.0.0.1:8642`, which is how OBS on this PC reaches the
   phone over the cable.

## Set it up over Wi-Fi

1. Put the phone and the PC on the same Wi-Fi.
2. In the game, File bar, **TOOLS**, **Stream**. **Copy Wi-Fi link** copies an address like `http://192.168.1.20:8642/?k=...`
3. Open that address in a browser on the PC, and download the OBS scene there, as above. Its addresses start with
   the phone's Wi-Fi address.

Use 5 GHz Wi-Fi and keep the phone near the router. The phone's Wi-Fi address can change; giving the phone a fixed
address in your router's settings (often called an address reservation) stops that.

## Both, and switching between them

Both links work at the same time, and the links, Copy buttons and the adb lines are also on **More**, **Stream**. The
scene uses the address you opened the guide at, so download it the way OBS will reach the phone. To move from Wi-Fi to
the cable or back, open the guide the new way and paste each new address into its source in OBS (right-click the
source, Properties). You do not need to import again.

Already have scenes of your own? The guide lists every address to add as a browser source.

## No scrcpy needed

The picture and sound in OBS come from KaizoCore itself, not from a copy of the phone's screen, so you do not need
scrcpy, a window capture or a capture card to stream. On the cable you need adb for the one forward line, and over
Wi-Fi nothing at all. If you used to stream a scrcpy window, take that source out of OBS and use **KaizoCore game**
instead: it is the game alone, lossless, without the phone's buttons, menus or notifications.

scrcpy is still handy as a way to play from the PC (below). Keep its window out of OBS.

## Play from your PC

The power button puts the phone to sleep, and KaizoCore pauses the game whenever it is not on screen. That is on
purpose. The picture in OBS freezes until you wake the phone.

To play with the phone's screen dark, use scrcpy as the controller instead. Its `--turn-screen-off` only darkens the
phone's display; the phone stays awake, and the game keeps running and streaming. Run this on the PC, over the cable or
wireless adb:

```
scrcpy --turn-screen-off --stay-awake --keyboard=uhid
```

- `--keyboard=uhid` makes your PC's keyboard reach the phone as a real keyboard, so KaizoCore's keys work.
  The first time, Android may want to know the keyboard's layout: pick yours under Physical keyboard in the phone's
  settings (Alt+K in the scrcpy window opens that page).
- `--stay-awake` keeps the phone awake while it charges, so keep it on the cable or a charger.
- Click the scrcpy window so it has the keyboard, then play with these keys (the defaults):

| Key | Button |
|---|---|
| Arrow keys | D-pad |
| X | A |
| Z | B |
| A | L |
| S | R |
| D | X |
| C | Y |
| Enter | Start |
| Right Shift | Select |

Change them in **More**, **Controls**.

- **DS games:** keep the scrcpy window where you can see it. Clicking in it taps the touch screen.
- **A controller** (Bluetooth or USB) works too, but then the phone's screen stays on unless scrcpy darkens it.
- The scrcpy window is only your controller. OBS gets the game from the stream.

## What goes out

- **The game**: lossless, at the game's own resolution, with its sound. For DS there is also a scene with the top
  screen only.
- **The tracker**: the same tracker as on the phone, and never more. An opponent's abilities, types and HP show as the
  phone shows them, and the randomized data stays locked until the run is over.
- **Attempts**: during a Kaizo IronMON run, and blank otherwise. A Nuzlocke counts no attempt.
- **Favorites**: each favorite Pokémon you set for the game, one picture each, as the tracker shows it, numbered as
  their boxes are (`/favorite/1` to `/favorite/9`, each a browser source). The imported scene has all nine, hidden:
  click the eye beside one to show it. They follow your favorites as soon as you change them, with the game open or
  not, and an empty box shows nothing. The picture alone is `/favorite/1.png` and so on, a see-through PNG. The idea is
  UTDZac's Favorites As Sources extension for the PC tracker.
- **Game over card** (`/gameover`): see-through while you play. When a Kaizo IronMON run ends it comes in with what
  ended the run (the Pokemon and trainer, as the game over box says it), the attempt, your badges, the time played,
  how it stands against your best run, the box's line, and the run's GachaMon card if one was made (its facts and the
  Pokemon's picture). It goes when the next run starts or Retry the battle undoes the loss; `&hold=20` makes it go
  after 20 seconds.
- **Run timer and splits** (`/timer`): the time played this attempt, the same time the run history keeps. It counts
  while KaizoCore is in front with the run's game open, and stops in the background, with the screen off or while a
  new run is made; fast forward counts as real time. Each badge gets a split, the time you earned it, and once a run
  with splits has been played on the same game and settings file, how far ahead or behind your best of those runs you
  are. `&splits=0` shows the time alone.
- **Run history** (`/history`): your past attempts on the game and settings file you are playing, for viewers: runs
  and wins, your best run, the Pokemon and trainers that end runs most, and the latest attempts. The imported scene has
  it hidden over the tracker. `&rows=20` shows more runs; without `&bg=none` it has a background of its own.
- **Looks**: add `&theme=clean` (see-through, for over the game) or `&theme=hud` (a ship's heads-up display) to the
  tracker, attempts, game over or timer address. `&demo=1` (`&demo=battle` on the tracker) shows each one with no game
  running, for placing it.

All of this goes from the phone to your PC only, over the cable or your own network. Nothing about the stream goes to
the internet, and none of it is sent anywhere unless STREAM is on. The address carries a code (the `k=` part), so
another device on the network cannot just guess its way in. Keep the addresses off your stream.

## While you stream

- While the stream is on, a notification says so: "KaizoCore is streaming". If KaizoCore goes to the background, the
  game pauses and so does the picture, and the notification pops up to bring you back with one tap. Android 13 and
  newer ask once to allow it.
- Swiping KaizoCore away in Recents ends the stream, and the notification goes with it.
- **Clean view** hides everything on the phone but the game.
- Your tracker background image and the Cam bubble are not sent. Fast forward speeds up the stream's sound too.
- Plug the phone in: streaming uses power and warms the phone. Turn on Do Not Disturb.

## More, Stream

Things on the phone that are set once, not mid-game, on their own page: **More**, **Stream**.

- **Links for your PC.** The Wi-Fi link and the USB cable link, each with Copy, and the adb lines to copy. With scrcpy
  open, a line copied on the phone can be pasted on the PC.
- **OBS switches scenes by itself.** In OBS 28 or newer, Tools, WebSocket Server Settings, tick Enable WebSocket
  server; Show Connect Info gives the address, port and password to type in. On Wi-Fi, type the PC's address. On the
  USB cable, run `adb reverse tcp:4455 tcp:4455` on the PC (again each time you plug the phone in) and type
  `127.0.0.1`: OBS's server is on the PC, and that line lets the phone reach it through the cable. Test connection
  reads your scene list, then pick a game scene, a battle scene and a game over scene. A battle has to last a moment
  before OBS switches, a lost Kaizo IronMON run goes to the game over scene at once, and leaving Play goes back to the
  game scene. It only switches while OBS shows one of those three, so a Starting soon scene is left alone. It can also
  save OBS's replay buffer when a run ends. The password stays on the phone, locked by its own key store, and is left
  out of backups.
- **Twitch chat commands.** Connect Twitch shows a code for twitch.tv/activate. Then viewers can type `!pokemon`,
  `!moves`, `!attempts`, `!gachamon`, `!progress`, `!heals`, `!about` or `!help` in your chat, and the phone answers as
  you, with only what your tracker shows. Each command has a switch. This one does use the internet: the phone talks
  to Twitch, over Wi-Fi or mobile data. Signing in gives KaizoCore two permissions on your account, reading your
  channel's chat and sending chat messages as you. The sign-in stays on the phone, locked the same way, and Sign out
  removes it here and at Twitch.
