"""Scenario 8: the test client never talks to a real host.  Adding 192.168.1.231 (a real PC of the LAN),
127.0.0.1:45989 (the real LavApollo service of this PC) and 127.0.0.1:47989 (the default GameStream port) is refused
before any request, with the test build's message; an art:// pairing link to a real address (command line, the
CLI pairing path) is refused too.  The client's log shows the refusals and not a single request to those addresses."""
import re
import time

import bugs
import helpers

REFUSED = ["192.168.1.231", "127.0.0.1:45989", "127.0.0.1:47989"]
MESSAGE = "Le client de test ne parle qu'aux hôtes de test (127.0.0.1, ports 40000-44999)."


def requests_to(log, address):
    """Log lines of a request that left (or would have left) for this address."""
    host = address.split(":")[0]
    port = address.split(":")[1] if ":" in address else None
    out = []
    for line in log.splitlines():
        if ("Executing request" in line or "openConnection - URL" in line) and host in line:
            if port is None or re.search(rf"{re.escape(host)}:{port}\b", line):
                out.append(line)
    return out


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
        assert not requests_to(log, address), requests_to(log, address)

    # A link handled by the command line (CliPair: ComputerManager::addNewHostManually, then NvHTTP)
    link = "art://192.168.1.231:47989?pin=1234&passphrase=testbench"
    c = bench.start_client(reset=False, args=[link])
    deadline = time.time() + 20
    log = ""
    while time.time() < deadline:
        log = c.log_text()
        refused = "refused (loopback and ports 40000-44999 only)" in log and "192.168.1.231:47989" in log
        if refused or bugs.toolbar_bug_seen(log):
            break
        time.sleep(0.3)
    bench.shot("04-art-link-refused")
    assert not requests_to(log, "192.168.1.231"), requests_to(log, "192.168.1.231")
    if bugs.toolbar_bug_seen(log):
        # the CLI pairing page never starts (known product bug): nothing is attempted, so nothing can leave
        bench.note("art:// link: CliPair never ran (toolBar bug); no request in the log")
        assert c.state()["computers"] == []
        return
    # Refused at the first layer (the pairing command adds the PC through the same guard) or, failing that, by
    # NvHTTP before any request
    assert ('manual add of "192.168.1.231:47989" refused' in log or
            re.search(r'Test build: request "\w+" to "192\.168\.1\.231:47989" refused', log)), \
        "no refusal logged for the link"
    assert c.state()["computers"] == []
