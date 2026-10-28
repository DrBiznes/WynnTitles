#!/usr/bin/env bash
# Runs every test offline: Go unit tests, then a throwaway server with offline
# auth, the mod's JUnit tests against it, and the in-game client smoke test.
#
#   scripts/smoke-test.sh            # everything
#   scripts/smoke-test.sh --no-game  # skip the Minecraft client test
#   WT_WYNN_PACK=<cached pack> scripts/smoke-test.sh  # also check icons and
#                                    # screenshot the screens with the real pack
#
# Screenshots from the client test: mod/build/run/clientGameTest/screenshots
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
port="${WT_TEST_PORT:-18765}"
url="http://127.0.0.1:${port}"
tmp="$(mktemp -d)"
server_pid=""

cleanup() {
	if [[ -n "$server_pid" ]]; then
		kill "$server_pid" 2>/dev/null || true
	fi
	rm -rf "$tmp"
}
trap cleanup EXIT

echo "==> Go tests"
(cd "$root/server" && go vet ./... && go test ./...)

echo "==> Starting test server on $url"
(cd "$root/server" && go build -o "$tmp/wynntitles" ./cmd/wynntitles)
WT_OFFLINE_AUTH=true WT_STATS_TTL=0 WT_DB="$tmp/test.db" WT_ADDR="127.0.0.1:${port}" "$tmp/wynntitles" >"$tmp/server.log" 2>&1 &
server_pid=$!
for _ in $(seq 1 50); do
	curl -sf "$url/healthz" >/dev/null && break
	sleep 0.1
done
curl -sf "$url/healthz" >/dev/null || { cat "$tmp/server.log"; echo "server did not start"; exit 1; }

tasks=(test)
if [[ "${1:-}" != "--no-game" ]]; then
	tasks+=(runClientGameTest)
fi

props=(-Dwynntitles.testServer="$url")
if [[ -n "${WT_WYNN_PACK:-}" ]]; then
	props+=(-Dwynntitles.wynnPack="$WT_WYNN_PACK")
fi

echo "==> Mod tests: ${tasks[*]}"
if ! (cd "$root/mod" && ./gradlew "${tasks[@]}" "${props[@]}" --console=plain); then
	echo "---- server log ----"
	cat "$tmp/server.log"
	exit 1
fi

echo "==> All smoke tests passed"
if [[ "${1:-}" != "--no-game" ]]; then
	echo "Screenshots: $root/mod/build/run/clientGameTest/screenshots"
fi
