Aurora Job SLA
--------------

- [Overview](#overview)
- [Metric Details](#metric-details)
  - [Platform Uptime](#platform-uptime)
  - [Job Uptime](#job-uptime)
  - [Median Time To Assigned (MTTA)](#median-time-to-assigned-\(mtta\))
  - [Median Time To Running (MTTR)](#median-time-to-running-\(mttr\))
- [Limitations](#limitations)

## Overview

The primary goal of the feature is collection and monitoring of Aurora job SLA (Service Level
Agreements) metrics that defining a contractual relationship between the Aurora/Mesos platform
and hosted services.

The Aurora SLA feature currently supports stat collection only for service (non-cron)
production jobs (`"production = True"` in your `.aurora` config).

The feature is implemented as a background worker thread that periodically computes job
instance counters from the existing scheduler `TaskEvent` history. The individual instance core
metrics are refreshed every minute (configurable via `sla_stat_refresh_interval`). The core instance
counters are subsequently aggregated by relevant grouping types before exporting to scheduler
`/vars` endpoint (when using `vagrant` that would be `http://192.168.33.7:8081/vars`)

## Metric Details

### Platform Uptime

*Aggregate amount of time a job spends in a non-runnable state due to platform unavailability
or scheduling delays.*

**Collection scope:**

* Per job - `sla_<job_key>_platform_uptime_percent`
* Per cluster - `sla_cluster_platform_uptime_percent`

**Units:** percent

To accurately calculate Platform Uptime, we must separate platform incurred downtime from user
actions that put a service instance in a non-operational state. It is simpler to isolate
user-incurred downtime and treat all other downtime as platform incurred.

Currently, a user can cause existing service (task) downtime in only two ways: via `killTasks`
or `restartShards` RPCs. For both, their affected tasks leave an audit state transition trail
relevant to uptime calculations. By applying a special "SLA meaning" to exposed task state
transition records, we can build a deterministic downtime trace for every given service instance.

A task going through a state transition carries one of three possible SLA meanings
(see `SlaAlgorithm.java` for sla-to-task-state mapping):

* Task is UP: starts a period where the task is considered to be up and running from the Aurora
  platform standpoint.

* Task is DOWN: starts a period where the task cannot reach the UP state for some
  non-user-related reason. Counts towards instance downtime.

* Task is REMOVED from SLA: starts a period where the task is not expected to be UP due to
  user initiated action or failure. We ignore this period for the uptime calculation purposes.

This metric is recalculated over the last sampling period (last minute) to account for
any UP/DOWN/REMOVED events. It ignores any UP/DOWN events not immediately adjacent to the
sampling interval as well as adjacent REMOVED events.

### Job Uptime

*Percentage of the job instances considered to be in RUNNING state for the specified duration
relative to request time. This is purely application side metric that is considering aggregate
uptime of all RUNNING instances. Any user- or platform initiated restarts directly affect
this metric.*

**Collection scope:** We currently expose job uptime values at 5 pre-defined
percentiles (50th,75th,90th,95th and 99th):

* `sla_<job_key>_job_uptime_50_00_sec`
* `sla_<job_key>_job_uptime_75_00_sec`
* `sla_<job_key>_job_uptime_90_00_sec`
* `sla_<job_key>_job_uptime_95_00_sec`
* `sla_<job_key>_job_uptime_99_00_sec`

**Units:** seconds
You can also get customized real-time stats from aurora client. See `aurora sla -h` for
more details.

### Median Time To Assigned (MTTA)

*Average time a job spends waiting for its tasks to get host-assigned. A combined metric that
helps tracking job scheduling performance dependency on the requested resources (user scope)
as well as the internal scheduler bin-packing algorithm efficiency (platform scope).*

**Collection scope:**

* Per job - `sla_<job_key>_mtta_ms`
* Per cluster - `sla_cluster_mtta_ms`
* Per instance size (small, medium, large, x-large, xx-large)
  * By CPU:
    * `sla_cpu_small_mtta_ms`
    * `sla_cpu_medium_mtta_ms`
    * `sla_cpu_large_mtta_ms`
    * `sla_cpu_xlarge_mtta_ms`
    * `sla_cpu_xxlarge_mtta_ms`
  * By RAM:
    * `sla_ram_small_mtta_ms`
    * `sla_ram_medium_mtta_ms`
    * `sla_ram_large_mtta_ms`
    * `sla_ram_xlarge_mtta_ms`
    * `sla_ram_xxlarge_mtta_ms`
  * By DISK:
    * `sla_disk_small_mtta_ms`
    * `sla_disk_medium_mtta_ms`
    * `sla_disk_large_mtta_ms`
    * `sla_disk_xlarge_mtta_ms`
    * `sla_disk_xxlarge_mtta_ms`

**Units:** milliseconds

The MTTA only considers instances that have already reached ASSIGNED state and ignores those
that are still PENDING. This ensures straggler instances (e.g. with unreasonable resource
constraints) do not affect metric curves.

### Median Time To Running (MTTR)

*Average time a job waits for its tasks to reach RUNNING state. This is a comprehensive metric
reflecting on the overall time it takes for the Aurora/Mesos to start executing user content.*

**Collection scope:**

* Per job - `sla_<job_key>_mttr_ms`
* Per cluster - `sla_cluster_mttr_ms`
* Per instance size (small, medium, large, x-large, xx-large)
  * By CPU:
    * `sla_cpu_small_mttr_ms`
    * `sla_cpu_medium_mttr_ms`
    * `sla_cpu_large_mttr_ms`
    * `sla_cpu_xlarge_mttr_ms`
    * `sla_cpu_xxlarge_mttr_ms`
  * By RAM:
    * `sla_ram_small_mttr_ms`
    * `sla_ram_medium_mttr_ms`
    * `sla_ram_large_mttr_ms`
    * `sla_ram_xlarge_mttr_ms`
    * `sla_ram_xxlarge_mttr_ms`
  * By DISK:
    * `sla_disk_small_mttr_ms`
    * `sla_disk_medium_mttr_ms`
    * `sla_disk_large_mttr_ms`
    * `sla_disk_xlarge_mttr_ms`
    * `sla_disk_xxlarge_mttr_ms`

**Units:** milliseconds

The MTTR only considers instances in RUNNING state. This ensures straggler instances (e.g. with
unreasonable resource constraints) do not affect metric curves.

## Limitations

* The availability of Aurora SLA metrics is bound by the scheduler availability.

* All metrics are calculated at a pre-defined interval (currently set at 1 minute).
  Scheduler restarts may result in missed collections.