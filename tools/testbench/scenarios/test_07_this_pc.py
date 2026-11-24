"""Scenario 7: a PC that is this very PC (LavApollo announces LavTestHostThisPc, test_host_this_pc): launching from
it is refused with « Impossible de streamer … depuis lui-même », and no session starts on either side."""
import time

import helpers


def test_this_pc_launch_refused(bench):
    me, = bench.up("this-pc-test")
    c = bench.start_client()
    comp = helpers.add_and_pair(c, me)
    comp = helpers.wait_computer(c, me.name, lambda x: x["online"] and x["apps"])
    assert comp["thisPc"] is True and comp["testHost"] is True
    helpers.wait_page(c, "HomeView")
    helpers.wait_text(c, "Bureau")
    bench.shot("01-home")

    c.click("Bureau")
    msg = helpers.wait_any_text(c, ["depuis lui-même"])
    assert msg == (f"Impossible de streamer {me.name} depuis lui-même : LavApollo tourne sur ce PC. "
                   "Lance le stream depuis un autre appareil."), msg
    bench.shot("02-refused")
    time.sleep(2.0)
    st = c.state()
    assert not st["session"]["active"] and st["page"] == "HomeView", st
    log = c.log_text()
    assert "/launch?" not in log and "/resume?" not in log, "no launch request may leave"
    host_log = (me.dir / "sunshine.log").read_text(encoding="utf-8", errors="replace")
    assert "New streaming session" not in host_log
