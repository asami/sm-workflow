# Confirmed terminated bounded validation continuation

The accepted 0.1.0 source retained an incomplete typed validation receipt at Wait
but had no ordinary continuation from that checkpoint. CNCF already distinguishes
confirmed terminal output-limit results from TerminationUnconfirmed; no process
poller or new execution interface is necessary.

The 0.1.1-SNAPSHOT source candidate adds common bounded Task/Fix continuation on
a later Advance. Existing immutable checkpoints retain the original capture;
the active prefix keeps successful checks while selecting the affected check
again through ordinary typed intent. Candidate freshness, evidence, review worker
exclusions, repair observations and counters stay under their existing owners.
The current invocation cannot recurse from a new incomplete result into retry.

Implementation and fixture specifications are source work only. No validation,
review, commit, publication or real consumer acceptance is claimed here. The
parent owns those gates and the real consumer's corrected 64/8 MiB capture
settings. Old 0.1.0 artifacts and publication catalog history remain intact.
