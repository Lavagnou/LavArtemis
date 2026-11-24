"""Known product bugs, recognised by their symptom.  A scenario that meets one stops as an expected failure (xfail)
with the evidence, instead of failing for a reason already reported; once the bug is fixed the symptom is gone and
the scenario runs to its end.  Never used to skip an assertion: each check below is the bug's own signature."""
import pytest

TOOLBAR = ("PRODUCT BUG (LavArtemis-Qt): `toolBar` was deleted with the legacy UI (c37eb550) but StreamSegue.qml:131, "
           "QuitSegue.qml:38, CliPair.qml:39, CliStartStreamSegue.qml:38 and CliQuitStreamSegue.qml:23 still set "
           "`toolBar.visible`: ReferenceError, the rest of StackView.onActivated never runs, so no stream starts "
           "(stuck on « Démarrage de … ») and an art:// pairing link never pairs")

ADD_ALREADY_LISTED = ("PRODUCT BUG (LavArtemis-Qt): adding a new PC by address shows « Ce PC est déjà dans la liste » "
                      "and never opens the pairing dialog: ComputerManager emits computerAddCompleted before "
                      "computerStateChanged for a new host (computermanager.cpp ~1524-1530), so HostsView.addComplete "
                      "(HostsView.qml:228-235) looks for the new PC in a model that does not have it yet")

LIBRARY_ORDER = ("PRODUCT BUG (LavArtemis-Qt): the Playnite library on the home is sorted by name, not in the host's "
                 "order (most recently played first, NvComputer::sortApps): AppModel::updateAppList inserts every new "
                 "app at its alphabetical place (appmodel.cpp ~245, `existingApp.name.toLower() > newApp.name...`), "
                 "and initialize() goes through it too")


def toolbar_bug_seen(client_log):
    return "ReferenceError: toolBar is not defined" in client_log


def xfail_if_toolbar_bug(bench):
    log = "".join(open(p, encoding="utf-8", errors="replace").read() for p in bench.clients_logs if p)
    if toolbar_bug_seen(log):
        pytest.xfail(TOOLBAR)
