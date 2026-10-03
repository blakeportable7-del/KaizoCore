# Streaming to OBS

KaizoCore sends the game's picture and sound, the tracker and your attempt counter to OBS on your PC, over your own
Wi-Fi. No cable, no screen mirroring, no capture card. It works for any game you play, in a run or not.

## Set it up

1. Put the phone and the PC on the same Wi-Fi.
2. In the game, **File**, **Stream**. The app shows an address like `http://192.168.1.20:8642/?k=...`, and **Copy
   link** copies it.
3. Open that address in a browser on the PC. It is the setup guide, with a test of the picture.
4. **Download the OBS scene**. In OBS: Scene Collection, Import, pick the file. The scene "KaizoCore" has the game,
   the tracker and the attempts, laid out at 1920 x 1080.

Already have scenes of your own? The guide lists the three addresses to add as browser sources.

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

The address carries a code (the `k=` part), so another device on the network cannot just guess its way in.

## While you stream

- While the stream is on, a notification says so: "KaizoCore is streaming". If KaizoCore goes to the background, the
  game pauses and so does the picture, and the notification pops up to bring you back with one tap. Android 13 and
  newer ask once to allow it.
- Swiping KaizoCore away in Recents ends the stream, and the notification goes with it.
- **Clean view** hides everything on the phone but the game.
- Your tracker background image and the Cam bubble are not sent. Fast forward speeds up the stream's sound too.
