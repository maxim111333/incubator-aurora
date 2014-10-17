# Recovering from a scheduler backup

- [Overview](#overview)
- [Preparation](#preparation)
- [Assess replicated log damage](#assess-replicated-log-damage)
- [Restore from backup](#restore-from-backup)
- [Cleanup](#cleanup)

**Be sure to read the entire page before attempting to restore from a backup, as it may have
unintended consequences.**

## Overview

The restoration procedure replaces the existing (possibly corrupted) Mesos replicated log with an
earlier, backed up, version and requires full scheduler outage. Once completed, the scheduler state
resets to what it was when the backup was created. This means any jobs/tasks created or updated
after the backup are unknown to the scheduler and will be killed shortly after the cluster restarts.
All other tasks continue operating as normal.

Usually, it is a bad idea to attempt to restore an update that is not extremely recent (i.e. older
than a few hours). This is because the scheduler will expect the cluster to look exactly as the
backup does, so any tasks that have been rescheduled since the backup was taken will be killed.

## Preparation

Follow these steps to prepare the cluster for restoring from backup:

* Stop all scheduler instances

* Consider blocking external traffic on a port defined in `-http_port` for all schedulers to
prevent users from interacting with the scheduler during the restoration process. This will help
troubleshooting by reducing the scheduler log noise and prevent users from making changes that will
be erased after the backup snapshot is restored

* Update scheduler configuration:
  * `-max_registration_delay` - set to sufficiently long interval to prevent registration timeout.
    E.g: `-max_registration_delay=360min`
  * Make sure `-gc_executor_path` option is not set to prevent accidental task GC. This is
    important as scheduler will attempt to reconcile the cluster state and will kill all tasks when
    restarted with an empty replicated log

  * Set `-mesos_master_address` to a non-existent zk address.
    E.g.: `-mesos_master_address=zk://localhost:2181`

* Restart all schedulers

## Assess replicated log damage

Now that the scheduler is ready to be restored from backup, determine what type of recovery is
required. Depending on the incurred damage, the replicated log may have to be re-initialized from
empty before proceeding with restore. The checklist below helps identifying the course of actions
to come next:

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

If any of the above bullets check out or if unsure about the replicated log integrity (see below)
re-initialize the replicated log:

* Stop schedulers
* Delete all files under `-native_log_file_path` on all schedulers
* Initialize replica's log file: `mesos-log initialize <-native_log_file_path>`
* Restart schedulers

NOTE: it is generally safer to assume the log is compromised and re-initialize logs when performing
a disaster recovery restore. The only case when it is acceptable to skip this section would be
restoring in a healthy cluster (e.g.: during a training exercise).

## Restore from backup

At this point the scheduler is ready to rehydrate from the backup snapshot:

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

