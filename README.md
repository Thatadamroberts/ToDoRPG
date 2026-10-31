# ToDo RPG

An Android app that turns your to-do lists into a pixel-art RPG.

Each to-do category belongs to a party member. Finishing a task in that category
levels up that hero and earns them a turn in battle.

| Category | Hero   | Class style                         |
|----------|--------|-------------------------------------|
| Work     | Arthur | Knight: steady, strong hits         |
| Health   | Bo     | Monk: fast flurry of blows          |
| Home     | Wren   | Ranger: high critical chance        |
| Learning | Vivi   | Mage: big magic damage              |
| Social   | Rosa   | Cleric: weak hits, heals allies     |

Hero names and category names can be changed from the Party screen. You can recruit up to
8 heroes in total (each new hero is a new category), including three extra classes:
**Bard** (weak hits, next ally hits +50%), **Samurai** (heavy blows, good crits) and
**Druid** (light hits, heals the whole party).

## How it plays

- **Daily quests** repeat every day. Completing one gives EXP and **gold**. Doing it on
  consecutive days builds a streak: more EXP, plus a 25 G bonus every 7 days.
- **One-off quests** give **bonus EXP** (2.5x a daily) and disappear the day after you
  finish them.
- Difficulty (Easy / Normal / Hard) multiplies the rewards. Hard quests give 2 battle turns.
- **Battle**: every completed quest gives that hero a turn (orange pips). Spend turns on the
  Battle screen to fight the current monster. Defeating it gives gold and EXP to the whole party
  and moves you to the next floor. Every 5th floor is a boss. Deeper floors have recoloured,
  tougher monsters.
- **Battle commands**: tap a hero, then choose **Attack**, their class **Skill** (costs MP),
  **Guard** (halves the next hit, restores 3 MP) or **Item** (free). Each hero's attack has an
  element, and every monster has two weaknesses. Hitting one deals 1.5x damage, reveals it, and fills
  the **BREAK** gauge; a broken monster is stunned and takes extra damage for 3 turns.
  Monsters show their next move above their head (who they'll hit, and in how many turns) and
  strike back when the count reaches zero. MP comes back from finishing quests (+2) and resting.
- **Missed dailies**: when a new day starts, the monster hits the hero of each daily you missed
  yesterday (the penalty covers at most 3 days, so a holiday won't wipe you out). A hero at 0 HP is KO'd: they
  can't fight and earn half EXP until revived. Levelling up fully heals and revives a hero.
  The party also recovers 10% HP each night.
- **Gear**: monsters drop weapons, armour and accessories in four rarities (Common, Rare,
  Epic, Legendary). Bosses always drop Rare or better. Equip gear from a hero's page on the
  Party screen. Armour's DEF reduces damage from missed dailies. Sell spare gear from the bag
  in the Shop.
- **Weekly raid**: every Monday a raid boss appears, sealed in chains. Each hero with quests
  must finish 3 of them that week to break their seal. Once every seal is broken, spend turns on
  the raid boss for an Epic-or-better drop and a big gold reward.
- **Shop**: Potions, Elixirs and Phoenix Feathers, plus Dojo training (+4 ATK each).
- **Growth**: every level gives a skill point for the hero's skill tree (Might, Vitality, Focus,
  Precision, Fortune, then Mastery). At level 15 a hero can be promoted to an advanced class
  (Knight → Paladin, Mage → Archmage, …) with better stats, stronger skills and gold trim.
- **Pets**: you start with an egg, and every 7 days of a daily streak lays another. Eggs hatch
  after 10 quests into a Chick, Pup, Cat, Turtle or Fairy, each with a passive bonus that grows
  as you complete quests together. Pick which one comes along on the Party screen.
- **Journal** (📖): 24 achievements that pay out gold, and a bestiary that fills in each
  monster's weaknesses and resistances as you discover them.
- **Widget**: add "ToDo RPG quests" to your home screen to see today's dailies and tick them off.
- **Backup** (⚙ settings): export your save to a file (Google Drive works from the picker) and
  import it again later.
- **Daily reminder** (⚙ settings): a notification at your chosen time if any dailies are left.
- Completing or deleting a quest can be undone from the banner for a few seconds.

Progress is saved on the device in `files/save.json`.

## Art and sound

- Sprites are 16×16 pixel art defined as character grids in `ui/components/Sprites.kt`, and
  animated in code (idle breathing, blinking, attack effects, death dissolve).
- Music and sound effects are original chiptune, rendered by `tools/make_audio.py`
  (`python3 tools/make_audio.py`, needs numpy and ffmpeg). The camp theme plays on menus and a
  faster theme plays in battle.
- Fonts: [Press Start 2P](https://fonts.google.com/specimen/Press+Start+2P) and
  [Pixelify Sans](https://fonts.google.com/specimen/Pixelify+Sans), both under the SIL Open Font
  License (see `licenses/`).

## Updating without losing progress

Android only installs an update over the old app if both are signed with the same key. To make
every GitHub build use one key, add two repository secrets (Settings → Secrets and variables →
Actions): `SIGNING_KEYSTORE_B64` (the keystore file, base64-encoded) and `SIGNING_PASSWORD`.
Without them, builds fall back to a random debug key and you'd need to uninstall first.
Each build's version number comes from the CI run number, so newer builds always count as upgrades.

## Building

Open the project in Android Studio (Ladybug or newer) and run the `app` configuration, or:

```
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. Every push to GitHub also builds the APK in
GitHub Actions. Download it from the run's **Artifacts** section and install it on your phone
(you may need to allow installing from unknown sources).

Game rules live in `app/src/main/java/com/todorpg/app/game/GameEngine.kt` and balance numbers in
`Rules` inside `data/Models.kt`. Both are covered by unit tests in `app/src/test`.
