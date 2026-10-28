# Modrinth listing

Copy these fields into the Modrinth project page. Keep this file in sync with
the live listing so the disclosure stays accurate (Modrinth rules 1.11, 5.9).

## Name

WynnTitles

## Summary

Earn title words from Wynncraft achievements and wear them above your name.

## Categories / environment

- Categories: Social, Utility, Decoration
- Client: required. Server: unsupported.
- Loader: Fabric. Game version: 1.21.11
- License: MIT
- Dependencies: Wynntils (required), owo-lib (required), Fabric API (required), Mod Menu (optional). Cloth Config is embedded.

## Description

WynnTitles adds achievements to Wynncraft. Each achievement unlocks title words, and you combine a prefix and a suffix into a title that shows above your name for every other WynnTitles player.

**Features**

- 30 achievements at launch for combat levels, profession levels, quests, places, items and statistics. New achievements arrive from the server without a mod update.
- Titles show on the Wynntils nametag stack, above player names.
- Achievement and title screens: press `` ` `` or type `/wt`.
- Unlock toasts and chat announcements, both optional.
- Options in Mod Menu: hide other players' titles, turn off announcements, or point the mod at your own server.

**Requirements**

WynnTitles is a Wynntils addon. It needs Wynntils, owo-lib and Fabric API, and titles appear only when Wynntils' custom nametags are on.

**Network and privacy**

WynnTitles talks to the WynnTitles server at `wynntitles.jamino.me`, and only while you are connected to Wynncraft. It sends nothing in singleplayer or on other servers unless you open its screen or run `/wt` there. Here is everything it sends and why:

- **Login.** When you join Wynncraft, the mod proves that you own your Minecraft account with the standard Mojang session check, the one every multiplayer server uses. Your access token goes only to Mojang's session server, never to WynnTitles. WynnTitles receives your username and UUID.
- **Achievements.** When you earn an achievement, the mod sends the achievement ID along with the Wynntils data that triggered it (for example your combat level or the quest name).
- **Titles.** To show titles, the mod sends the UUIDs of players whose nametags are on your screen and gets their titles back.
- **Version.** Every request includes the mod version.

The server stores your UUID, username, unlocked achievements with their trigger data, and your equipped title. It does not collect chat, inventories, or anything else. You can point the mod at your own server under Mod Menu → WynnTitles → Advanced. To have your data deleted, contact the author.

WynnTitles is a fan project and is not affiliated with or endorsed by Wynncraft or Wynntils. It changes only cosmetic nametags and gives no gameplay advantage.

**Source and license**

MIT licensed. Source: <add repository link>.
