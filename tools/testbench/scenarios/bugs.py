"""Known product bugs, recognised by their symptom.  A scenario that meets one stops as an expected failure (xfail)
with the evidence, instead of failing for a reason already reported; once the bug is fixed the symptom is gone,
its entry goes from here and the scenario must pass.  Never used to skip an assertion: each check is the bug's own
signature.

None open.  Fixed in LavArtemis-Qt (2026-11-25), their scenarios now assert the right behaviour:
- toolBar left in the stream and CLI segues (no stream, no art:// pairing): cf898563 -- scenarios 1c, 5, 6, 8;
- add by address said « Ce PC est déjà dans la liste », no pairing dialog: 56c91db6 -- scenarios 1a, 1b;
- Playnite library sorted by name instead of the host's order: 23f65be2 -- scenario 2;
- « Quitter la session » closed the client: 4c260672 -- scenario 5.
"""
