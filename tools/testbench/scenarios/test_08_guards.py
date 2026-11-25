"""Scenario 8: the test client never talks to a real host.  Adding 192.168.1.231 (a real PC of the LAN),
127.0.0.1:45989 (the real LavApollo service of this PC) and 127.0.0.1:47989 (the default GameStream port) is refused
before any request, with the test build's message; an art:// pairing link to a real address (command line, the
CLI pairing path) is refused too, at once and with the same message.  The client's log shows the refusals and not a
single request (no test host runs here: no request may leave at all)."""
import time

import helpers

REFUSED = ["192.168.1.231", "127.0.0.1:45989", "127.0.0.1:47989"]
MESSAGE = "Le client de test ne parle qu'aux hôtes de test (127.0.0.1, ports 40000-44999)."


def request_lines(log):
    """Every request the client started (NvHTTP logs each one).  No test host runs in this scenario, so there must
    be none at all, to any address or port."""
    return [line for line in log.splitlines() if "Executing request" in line or "openConnection - URL" in line]


def test_guards_real_addresses_refused(bench):
    c = bench.start_client()
    for i, address in enumerate(REFUSED, 1):
        helpers.add_by_address(c, address)
        helpers.wait_text(c, MESSAGE)
        helpers.wait_text(c, "Oups")
        bench.shot(f"0{i}-refused-{address.replace(':', '-')}")
        c.click("OK")
        helpers.wait_text(c, "Oups", gone=True)
        assert c.state()["computers"] == [], f"{address} must not be added"
    time.sleep(1.0)
    log = c.log_text()
    for address in REFUSED:
        assert f'manual add of "{address}" refused' in log, f"no refusal line for {address}"
    assert not request_lines(log), request_lines(log)

    # A link handled by the command line (CliPair: ComputerManager::addNewHostManually, then NvHTTP)
    link = "art://192.168.1.231:47989?pin=1234&passphrase=testbench"
    # restart_client stops the first client before the second starts (never two on the command pipe)
    c = bench.restart_client(args=[link])
    # The refusal ends the CLI pairing with its message, well before the 10 s search timeout (it used to spin on
    # « Connexion au PC... » until then, and end on a generic « Failed to connect »)
    helpers.wait_text(c, MESSAGE, timeout=8)
    elapsed = time.time() - c.started_at
    bench.shot("04-art-link-refused")
    bench.note(f"art:// link refused after {elapsed:.1f}s (client start included)")
    assert elapsed < 8, elapsed
    log = c.log_text()
    assert not request_lines(log), request_lines(log)
    # Refused at the first layer: the pairing command adds the PC through the same guard as Mes PC
    assert 'manual add of "192.168.1.231:47989" refused' in log, "no manual-add refusal logged for the link"
    assert c.state()["computers"] == []
