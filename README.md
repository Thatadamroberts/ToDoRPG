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

Hero names and category names can be changed from the Party screen.

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
- **Missed dailies**: when a new day starts, the monster hits the hero of each daily you missed
  yesterday (the penalty covers at most 3 days, so a holiday won't wipe you out). A hero at 0 HP is KO'd: they
  can't fight and earn half EXP until revived. Levelling up fully heals and revives a hero.
  The party also recovers 10% HP each night.
- **Shop**: Potions, Elixirs and Phoenix Feathers, plus weapon upgrades (+4 ATK each).
- Completing or deleting a quest can be undone from the banner for a few seconds.

Progress is saved on the device in `files/save.json`.

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
