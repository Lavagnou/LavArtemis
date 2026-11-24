"""One-off generator for the committed fixtures (kept for regeneration). Not used at run time."""
import json, pathlib, uuid

ROOT = pathlib.Path(__file__).parent / "fixtures"
NS = uuid.UUID("5d0c1c00-0000-4000-8000-000000000000")


def gid(name):
    return str(uuid.uuid5(NS, name)).upper()


def game(name, source, activity, fav=False):
    return {"id": gid(name), "name": name, "source": source, "platform": "PC (Windows)",
            "favorite": fav, "lastActivity": activity, "playtime": 3600, "cover": gid(name) + ".png"}


GAMES = {
    "atlas-test": [game("Nebula Drift", "Steam", "2026-11-20T21:00:00Z", True),
                   game("Cobalt Quarry", "GOG", "2026-11-18T19:30:00Z"),
                   game("Tidewater Tactics", "Epic", "2026-11-10T08:15:00Z"),
                   game("Paper Lantern", "Steam", None)],
    "cynisca-test": [game("Ember Courier", "Steam", "2026-11-22T10:00:00Z"),
                     game("Glass Orchard", "Xbox", "2026-11-01T12:00:00Z")],
    "offline-test": [], "old-version-test": [], "this-pc-test": [game("Nebula Drift", "Steam", "2026-11-20T21:00:00Z")],
}
PCS = {
    "atlas-test": {"name": "Test-Atlas", "port": 40989, "mac": "02:00:00:00:a7:01", "playnite_running": True},
    "cynisca-test": {"name": "Test-Cynisca", "port": 41989, "mac": "02:00:00:00:a7:02", "playnite_running": False},
    "old-version-test": {"name": "Test-Ancien", "port": 42989, "mac": "02:00:00:00:a7:03",
                         "appversion": "2.1.0.0", "playnite_running": False},
    "this-pc-test": {"name": "Test-CePc", "port": 43989, "mac": "02:00:00:00:a7:04",
                     "playnite_running": True, "this_pc": True},
    "offline-test": {"name": "Test-Eteint", "port": 44500, "playnite_running": False, "instance": False},
}
for fx, pc in PCS.items():
    d = ROOT / fx
    (d / "playnite").mkdir(parents=True, exist_ok=True)
    (d / "pc.json").write_text(json.dumps(pc, indent=2) + "\n")
    apps = {"env": {}, "apps": [{"name": "Bureau", "cmd": ""}]}
    (d / "apps.json").write_text(json.dumps(apps, indent=2) + "\n")
    lib = {"version": 1, "exportedAt": "2026-11-24T09:00:00Z", "games": GAMES[fx]}
    (d / "playnite" / "library.json").write_text(json.dumps(lib, indent=2, ensure_ascii=False) + "\n")
