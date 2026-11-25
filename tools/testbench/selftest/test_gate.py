"""Built-in scenario used only by LAV_TESTBENCH_SELFTEST=pass|fail|flaky (see README): proves the release gate's
exit codes without a GUI, a host or a build.  Never part of a normal run (it lives outside scenarios/)."""
import os


def test_selftest_gate():
    mode = os.environ.get("LAV_TESTBENCH_SELFTEST")
    attempt = int(os.environ.get("LAV_TESTBENCH_ATTEMPT", "1"))
    if mode == "fail":
        assert False, "selftest: deliberate failure"
    if mode == "flaky":
        assert attempt > 1, "selftest: deliberate failure on the first attempt only"
