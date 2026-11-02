# WynnTitles

![banner](https://github.com/user-attachments/assets/3ced8e93-39c7-4d10-87cf-8905e9de5206)

WynnTitles lets you earn title words from Wynncraft achievements and wear them above your name, then style them in a shop with colors, animations and Wynncraft fonts. Everyone else with the mod sees it. Made for my modpack [World of Wynncraft](https://modrinth.com/modpack/world-of-wynncraft)

![title-reel](https://github.com/user-attachments/assets/6a428802-6dea-4497-9fd7-067141ea2530)

## What's Up

- Earn title words from **160 achievements**: each achievement rewards title words and achievement points!!!
- Wear a **prefix and a suffix** above your name, and other WynnTitles players see it on your nametag
- Spend your achievement points in the **style shop**: Try every style on your title before you buy it (this is so cool)
- The big achievements come with their own **native style** that you can't buy, only earn
- Configurable settings via Mod Menu or config file

## See It

### Your Title

![nametag](https://github.com/user-attachments/assets/ce0f9831-afc9-4e5e-8fa9-dc031d6081de)

Prefix, suffix and style, right above your head!!

### Animations

![shop-animations](https://github.com/user-attachments/assets/bb64f573-a1a1-4aba-8851-99379263a307)

All 12 animations you can currently buy in the shop.

![native-animations](https://github.com/user-attachments/assets/fa37f466-655a-43ef-885e-32ae767a36d3)

And some of the ones you can't. These only come on the titles that earn them!!

### Fonts

![fonts](https://github.com/user-attachments/assets/423b71a1-e8f9-42b6-9160-689adcd76c5d)

Ten Wynncraft fonts, from the normal one to Wynnic, High Gavellian, Old Fruman and the banners.

### The Screens

![tab-tour](https://github.com/user-attachments/assets/87036067-b4f2-40a1-a6c4-a6315b210c26)

Titles, Styles, Shop and Achievements.

![pick-a-title](https://github.com/user-attachments/assets/6a3878c6-8baf-4222-9aca-e8c1c6e8098e)

Pick a prefix and a suffix then hit Equip.

![style-a-title](https://github.com/user-attachments/assets/7480be2f-0b30-45ca-b5b7-6a81577305ee)

Try on the animated styles you own, then Wear the one you like.

![try-on-in-the-shop](https://github.com/user-attachments/assets/6205e9f5-4864-4e7c-b244-7317761a4a84)

The shop lets you try before you buy.

![unlock-toast](https://github.com/user-attachments/assets/ea6be1fb-c98c-4ea0-bfff-00091b07e288)

Ding!! Achievement unlocked.

## Usage

- Make sure you have the latest Fabric API and Wynntils installed alongside WynnTitles
- Press `` ` `` or run `/titles` to open WynnTitles. You can rebind the key in the controls menu under WynnTitles
- **Titles** is where you pick your prefix and suffix, then hit Equip
- **Styles** is where you color and animate them with what you own
- **Shop** is where you spend your points. Buying asks you once first and purchases are final, so try things on before you buy
- **Achievements** shows your progress, the title words each one gives you and how rare it is
- Type `/titles help` for a list of all commands

## How It Works

- Achievements are detected with Wynntils data, on Wynncraft only, and sent to the WynnTitles server
- Titles are cosmetic so claims are trusted. Cheating gets you a cool name above your head and nothing else, and I have moderation tools for the people who try it
- Logging in uses the same Mojang check a Minecraft server does when you join it. Your access token only ever goes to Mojang
- The server keeps your UUID, username, unlocked achievements (with what the mod reported, like the quest name or your class and level), purchases and login times
- To show titles it asks the server about the players near you
- The mod doesn't include anything from the Wynncraft resource pack, it just points at the items and fonts you already have loaded
- The full tour is in [docs/ARCHITECTURE.md](https://github.com/DrBiznes/WynnTitles/blob/main/docs/ARCHITECTURE.md)

## Run Your Own Server

Got a guild or a group of friends? You can run a private WynnTitles server with your own achievements, shop and moderation dashboard. Everyone installs the normal mod and points it at your server. Here's how: [Running your own WynnTitles server](https://github.com/DrBiznes/WynnTitles/blob/main/deploy/README.md)

## Go Ham

- I don't know nothing about java so if you wanna fork this and fix this up go ham.
- Feel free to use this in your modpack
- It needs JDK 21 for the mod and Go 1.26 for the server

```
cd mod && ./gradlew build    # builds the mod and runs the unit tests
cd server && go test ./...   # server tests
scripts/smoke-test.sh        # everything offline, including an in-game test
```

- The smoke test starts a throwaway server, boots a real client with Wynntils and saves screenshots to `mod/build/run/clientGameTest/screenshots`
- Running it locally and the API are in [docs/ARCHITECTURE.md](https://github.com/DrBiznes/WynnTitles/blob/main/docs/ARCHITECTURE.md), and the rules for adding achievements are in [docs/AGENTS.md](https://github.com/DrBiznes/WynnTitles/blob/main/docs/AGENTS.md)
- Not made by or affiliated with Wynncraft or Wynntils

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Thanks to the Wynntils team, this mod would know nothing about what you've done without them
- Thanks to the Wynncraft team for creating an awesome MMORPG experience in Minecraft
- Thanks to shedaniel for Cloth Config
- Thanks to igbarvonsquid!!!

## Feedback, Support and My Mods
Got an idea for a cool achievement title, a native style, or a new animation? Hop into the [Discord](https://discord.gg/jqFF64rXZZ) to share your idea!!
If you run into bugs, crashes, or weird behavior, feel free to report them either on [GitHub Issues](https://github.com/DrBiznes/WynnTitles/issues) or in the Discord support channel!
### Check out all my projects!
>   [World of Wynncraft Modpack](https://modrinth.com/modpack/world-of-wynncraft)

>   [WynnTitles](https://modrinth.com/mod/wynntitles)

>   [WynnWanderer](https://modrinth.com/mod/wynnwanderer)

>   [WynnVista](https://modrinth.com/mod/wynnvista)

>   [Wynn Weapon Bigger](https://modrinth.com/mod/wynnweaponbigger)

>   [Nimble ReWynnded](https://modrinth.com/mod/nimble-rewynnded)

>   [Class Keybind Profiles](https://modrinth.com/mod/class-keybind-profiles)

>   [WynnBubbles](https://modrinth.com/mod/wynnbubbles)

>   [WynnLODGrabber](https://modrinth.com/mod/wynnlodgrabber)
