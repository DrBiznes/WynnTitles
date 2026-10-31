# Running your own WynnTitles server

A guild, or any group of friends, can run a private WynnTitles server: your own
achievements, your own title words and shop, and your own moderation dashboard.
Everyone in the group installs the normal mod and points it at your server.

You need a machine that stays on, Docker with Compose, and a domain name or
tunnel so players can reach it over HTTPS. A small VPS or a home server is
plenty, since the container is limited to 256 MB of memory and one CPU. Setup
takes about 30 minutes.

## What a private server is, and isn't

- **It's separate.** Accounts, unlocks, points and titles live only on your
  server. Players on it see each other's titles, not the ones from the public
  server, and progress doesn't carry over in either direction.
- **It isn't invite-only.** There is no player allowlist. Anyone with the mod who
  has your URL can log in with a real Minecraft account, so share it only with
  your group.
- **Claims are trusted.** The mod reports what it detected and the server records
  it. A determined cheater can fake claims, and the dashboard is how you handle
  that. Titles are cosmetic, so this is by design.
- **You hold your players' data.** That's their UUID, username, unlocks with the
  evidence the mod reported, purchases and login times. Players' Minecraft access
  tokens never reach your server; they go only to Mojang.

## 1. Set up the files

Put [compose.yaml](compose.yaml) and [.env.example](.env.example) in a folder on
your server, then:

```sh
cp .env.example .env
mkdir data
sudo chown 568:568 data
openssl rand -hex 32
```

Paste the output of `openssl` into `WT_ADMIN_PASSWORD` in `.env`, and pick a
`WT_ADMIN_USERNAME`. The name is recorded in the audit history. Never commit
`.env`.

The container runs as user `568`, which owns only the `data` folder. To use a
different user, set `WT_UID` and `WT_GID` in `.env` and give that user the folder.

## 2. Start it

```sh
docker compose up -d
docker compose logs
```

The logs should show two `listening` lines, for `:8080` and `:8081`, and no
offline-auth warning. Check it from the same machine:

```sh
curl http://127.0.0.1:8080/healthz
```

Both ports listen on `127.0.0.1` only, so nothing is public yet.

## 3. Put HTTPS in front of the API

Only port `8080` ever goes public. **Never expose `8081`**, which is the
dashboard. Use whichever of these suits you.

**Caddy** on a machine with a domain. It gets certificates by itself. Point your
domain at the server, open ports 80 and 443, and use this `Caddyfile`:

```
titles.example.com {
	reverse_proxy 127.0.0.1:8080
}
```

**Cloudflare Tunnel** for a home server, with no ports opened on your router.
Run `cloudflared` on the same machine and add a public hostname with the service
`http://127.0.0.1:8080`. Don't put Cloudflare Access or a browser challenge on
that hostname: the mod isn't a browser, so it can't pass them.

Then check `https://titles.example.com/healthz` from a browser, and that
`https://titles.example.com/player/anything` shows 404. The dashboard doesn't
exist on the public side.

Don't run this on plain HTTP over the internet. Session tokens would travel
unencrypted.

**Add a login rate limit** if your URL might get around. A flood of logins can
block real ones for a couple of minutes. On Cloudflare, add a rate limiting rule
for paths starting with `/v1/auth/`, counted by IP, at about 20 requests per 10
seconds. A normal login uses 2 requests.

## 4. Point the mod at your server

Each player needs the mod, Wynntils, Fabric API and, for the settings screen,
[Mod Menu](https://modrinth.com/mod/modmenu). Then:

1. Open **Mod Menu → WynnTitles → Advanced**.
2. Set **Server URL** to `https://titles.example.com` and save.

Without Mod Menu, set `serverUrl` in `config/wynntitles.json` and restart the
game. Changing the URL logs the player out of the old server. In game, run
`/titles status` to see which server the mod is using, and `/titles login` to
retry a login.

## 5. Use the dashboard

The dashboard is a separate listener for moderators. Reach it through an SSH
tunnel from your own computer:

```sh
ssh -L 8081:127.0.0.1:8081 you@your-server
```

Then open `http://127.0.0.1:8081` and sign in with the `.env` username and
password. From there you can:

- Search players, and see each one's title, points, styles and every claim with
  the evidence the mod reported. That evidence is context, not proof.
- **Revoke or restore** a claim. A revoked claim can't be claimed again from the
  mod.
- **Reset purchases** for one player or for everyone, which refunds every price
  paid.
- Grant achievements and styles to one developer account, set with `WT_DEV_UUID`.
  Log in once, copy your UUID from the dashboard, add it to `.env` and run
  `docker compose up -d`.

Every change writes an audit entry. Players see changes after `/titles reload`
or their next launch.

## 6. Make your own achievements

The catalog is built into the server, so custom content means building your own
image. Fork this repository and edit:

- [`server/catalog/achievements.json`](../server/catalog/achievements.json) for
  achievements and title words
- [`server/catalog/shop.json`](../server/catalog/shop.json) for shop items and
  prices

The server checks both at startup and refuses to run if something is invalid.
Run the checks before you deploy, including the price and uniqueness rules, which
are tuned for the official catalog and may need adjusting for yours:

```sh
cd server && go test ./catalog/...
```

Players download the catalog from your server, so they need no mod update, as
long as you use trigger types the mod already knows. A new trigger type needs a
mod change too, and every player would have to run your build. See
[docs/AGENTS.md](../docs/AGENTS.md) for the catalog rules.

Build the image and use it instead of the public one by changing `image:` in
`compose.yaml`:

```sh
docker build -f server/Dockerfile -t my-guild/wynntitles server
```

If you fork on GitHub, the included workflow also publishes your image to your
own GitHub packages on every push to `main`.

Be careful changing things after people have started playing. Renaming a title
word unequips it, removing an achievement removes its points, and lowering a
price is unfair to anyone who paid the old one. Purchases keep the price paid, so
raising points is the safe way to make the shop cheaper.

## Updates

```sh
docker compose pull
docker compose up -d
```

Back up first. Database changes run by themselves at startup and can't be undone
by going back to an older image. To stay on a known version, set `image:` to a
`sha-<commit>` tag rather than `latest`.

Keep the server updated before the mod. A newer mod may need endpoints an older
server doesn't have.

## Backups

Everything lives in `data`. Stop the server to back it up, because recent writes
can still be in the database's `-wal` file:

```sh
docker compose stop
tar czf wynntitles-backup.tar.gz data
docker compose start
```

Keep a copy somewhere else, along with your `.env`.

## Settings

Set these under `environment:` in `compose.yaml`.

| Setting | Default | Meaning |
|---|---|---|
| `WT_ADDR` | `:8080` | Public API listen address |
| `WT_DB` | `/data/wynntitles.db` | Database path |
| `WT_ADMIN_ADDR` | unset | Dashboard listen address; unset turns it off |
| `WT_ADMIN_USERNAME` | | Dashboard login name |
| `WT_ADMIN_PASSWORD` | | Dashboard password, at least 24 characters |
| `WT_ADMIN_PASSWORD_FILE` | | Read the password from a file instead |
| `WT_ADMIN_ALLOWED_CIDRS` | loopback | Source networks allowed to open the dashboard |
| `WT_DEV_UUID` | unset | The only account that can receive grants |
| `WT_SESSION_TTL` | `720h` | How long a login lasts |
| `WT_STATS_TTL` | `1m` | How long achievement rarity is cached |

`WT_OFFLINE_AUTH` lets anyone log in as anyone. It's for local testing only, so
never set it on a real server.

## Troubleshooting

| Symptom | Check |
|---|---|
| Compose says `Set a random password` | `WT_ADMIN_PASSWORD` in `.env` is blank. |
| Container restarts with `dashboard requires` | The password is under 24 characters, or the username is blank. |
| `permission denied` opening the database | `data` must belong to `WT_UID`:`WT_GID`. Don't use `chmod 777`. |
| Container restarts naming an achievement | Your catalog edit is invalid. The log says what's wrong. |
| Dashboard says "restricted" | Your source network isn't in `WT_ADMIN_ALLOWED_CIDRS`. |
| "Reload the dashboard and try again" | The server restarted, which changes its form tokens. |
| No developer controls | `WT_DEV_UUID` isn't your UUID, or you didn't run `docker compose up -d` after setting it. |
| Mod says it could not connect | Check `/healthz` over HTTPS, and that no browser challenge is on the hostname. The mod retries a few times by itself. |
| Login fails | The player needs a real, not offline, Minecraft account. |

A [dashboard preview](dashboard-preview.jpg) shows what the moderation console
looks like.
