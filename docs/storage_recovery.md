# Storage recovery from backup

- [Overview](#overview)
- [Preparation](#preparation)
- [Assess the damage](#assess-the-damage)
- [Restore from backup](#restore-from-backup)
- [Cleanup](#cleanup)

**Do NOT attempt this on a production cluster unless absolutely necessary! If you don't prepare
correctly for this operation, Aurora cluster could suffer irrecoverable data loss.**

## Overview

The restoration procedure replaces the existing (possibly corrupted) Mesos replicated log with an
earlier, backed up, version and requires full scheduler outage. Once completed, the scheduler state
resets to what it was when the backup was created. This means any jobs/tasks created or updated
after the backup are unknown to the scheduler and will be killed shortly after the cluster restarts.
All other tasks continue operating as normal.

## Preparation

* Stop all scheduler instances and disable any runtime monitoring if used (e.g. upstart)

* Consider blocking external traffic on a port defined in `-http_port` for all schedulers to
prevent concurrent access

* Update scheduler configuration:
  * `-max_registration_delay` - set to sufficiently long interval to prevent registration timeout.
    E.g: `-max_registration_delay=360min`
  * Make sure `-gc_executor_path` option is not set to prevent accidental task GC
  * Set `-mesos_master_address` to a non-existent zk address.
    E.g.: `-mesos_master_address=zk://localhost:2181`

* Restart all schedulers

## Assess the damage

Depending on the incurred damage, the replicated log may have to be re-initialized from empty before
proceeding with restore. The checklist below helps identifying the course of actions to come next:
* Scheduler keeps failing over periodically with a change in leadership
* Scheduler logs show unusual replicated log messages similar to the this:

<pre>
I0313 23:14:39.313841 31780 replica.cpp:633] Replica in RECOVERING status received a broadcasted recover request
I0313 23:14:39.313902 31782 recover.cpp:220] Received a recover response from a replica in RECOVERING status
I0313 23:14:39.314538 31780 recover.cpp:220] Received a recover response from a replica in EMPTY status
I0313 23:14:39.314671 31778 recover.cpp:220] Received a recover response from a replica in VOTING status
</pre>
* The scheduler log file defined by `-native_log_file_path` is missing or known to be corrupted
on any of the replicas

If any of the above bullets check out or if unsure about the replicated log integrity re-initialize
the replicated log:

* Stop schedulers
* Initialize replica's log file: `mesos-log initialize <-native_log_file_path>`
* Restart schedulers

## Restore from backup

* Identify the leading scheduler by:
  * running `aurora_admin get_scheduler <cluster>` - if scheduler is responsive
  * examining scheduler logs
  * or examining Zookeeper registration under the path defined by `-zk_endpoints`
    and `-serverset_path`

* Locate the desired backup file, copy it to the leading scheduler and stage recovery by running
the following command on a leader
`aurora_admin scheduler_stage_recovery <cluster> scheduler-backup-<yyyy-MM-dd-HH-mm>`

* At this point, the recovery snapshot is staged and available for manual verification/modification
via `aurora_admin scheduler_print_recovery_tasks` and `scheduler_delete_recovery_tasks` commands.
See `aurora_admin help <command>` for usage details.

* Commit recovery. This instructs the scheduler to overwrite the existing replicated log with the
provided backup snapshot and initiate a mandatory failover
`aurora_admin scheduler_commit_recovery <cluster>`

## Cleanup
Undo any modification done during [Preparation](#preparation) sequence.

