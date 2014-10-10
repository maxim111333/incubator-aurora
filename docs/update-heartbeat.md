Job Update Coordination
--------------

## Overview
This feature builds on top of the scheduler job update support implemented recently in
[AURORA-610](https://issues.apache.org/jira/browse/AURORA-610) and is introduced by
[AURORA-690](https://issues.apache.org/jira/browse/AURORA-690). To reiterate: some Aurora services
may benefit from having more control over the server updater process by explicitly acknowledging
the job update progress. This may be helpful for mission-critical service updates where explicit
job health monitoring is required during the entire job update lifecycle.

## Summary
* Allow users to specify if their job update needs to rely on periodic heartbeat RPC calls
acknowledging the service/update health.
* Support user-defined heartbeat rate.
* Expose a new `heartbeatJobUpdate` RPC to let external monitoring services control update progress.
* Make scheduler pause a job update in case of a missed heartbeat call.
* Allow resuming of the paused-due-to-missed-heartbeat update via a `resumeJobUpdate` call.
* Allow resuming of the paused-due-to-missed-heartbeat update via a fresh `heartbeatJobUpdate` call.
* Support pause reason in scheduler to differentiate between user-issued pause and pause due to
timeout to disallow resuming on `heartbeatJobUpdate` in case of a job previously paused by user.

## Case Study
### 1. Successful coordinated update
* User posts a coordinated job update request
* Scheduler starts heartbeat countdown using specified heartbeat rate
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with OK update status
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with OK update status
* Scheduler finishes the update
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with STOP update status. External service stops heartbeats.

### 2. Update paused due to health problems
* User posts a coordinated job update request
* Scheduler starts heartbeat countdown using specified heartbeat rate
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with OK update status
* External service detects service health problems and stops heartbeats
* Heartbeat timeout occurs. Scheduler pauses the update.

### 3. Scheduler failover while update is in progress
* User posts a coordinated job update request
* Scheduler starts heartbeat countdown using specified heartbeat rate
* Scheduler restarts and automatically moves coordinated update into paused state
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resumes update,
resets countdown and responds with OK update status
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with OK update status
* Scheduler finishes the update
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with STOP update status. External service stops heartbeats.

### 4. Network partition between scheduler and external monitoring service
* Similar to case 2 above. The update is paused.

### 5. Update paused by user
* User posts a coordinated job update request
* Scheduler starts heartbeat countdown using specified heartbeat rate
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with OK update status
* Update is paused by user
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
  responds with STOP update status

## Limitations
Current design assumes reasonably reliable network packet delivery where a delay on delivery &lt;&lt;
average heartbeat rate.