Job Update Coordination
--------------

## Overview
This feature is introduced by [AURORA-690](https://issues.apache.org/jira/browse/AURORA-690) and
builds on top of the scheduler job update support implemented recently in
[AURORA-610](https://issues.apache.org/jira/browse/AURORA-610). To reiterate: some Aurora services
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

## Interface Changes
Add into `JobUpdateSettings`:
```
 /**
  * If set, requires external calls to heartbeatJobUpdate RPC at the specified rate for the update
  * to make progress. If no heartbeats received within specified interval the update will pause
  * and may be resumed by either resumeJobUpdate or heartbeatJobUpdate call.
  */
  9: i32 pauseIfNoHeartbeatsAfterMs
```

Expose a new `heartbeatJobUpdate` RPC:
```
 /**
  * Allows progress of the job update in case pauseIfNoHeartbeatsAfterMs is specified in
  * JobUpdateSettings. Resumes progress if the update was previously paused.
  */
  Response heartbeatJobUpdate(1: string updateId, 2: SessionKey session)
```

## Case Study
### 1. Successful coordinated update
* User posts a coordinated job update request
* Scheduler starts heartbeat countdown using specified heartbeat rate
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with OK
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with OK
* Scheduler finishes the update
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with STOP. External service stops heartbeats.

### 2. Update paused due to health problems
* User posts a coordinated job update request
* Scheduler starts heartbeat countdown using specified heartbeat rate
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with OK
* External service detects service health problems and stops heartbeats
* Heartbeat timeout occurs. Scheduler pauses the update.

### 3. Scheduler failover while update is in progress
* User posts a coordinated job update request
* Scheduler starts heartbeat countdown using specified heartbeat rate
* Scheduler restarts and automatically moves coordinated update into paused state
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resumes update,
resets countdown and responds with OK
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with OK
* Scheduler finishes the update
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with STOP. External service stops heartbeats.

### 4. Network partition between scheduler and external monitoring service
* Similar to case 2 above. The update is paused.

### 5. Update paused by user
* User posts a coordinated job update request
* Scheduler starts heartbeat countdown using specified heartbeat rate
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with OK
* Update is paused by user
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
  responds with STOP

### 6. Update is not active (FAILED/ERROR/ABORTED)
* User posts a coordinated job update request
* Scheduler starts heartbeat countdown using specified heartbeat rate
* Update enters terminal state due to failure or external user action (e.g. `abortJobUpdate`)
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler responds with STOP
* External service stops heartbeats.

### 7. Unknown update ID
* User posts a coordinated job update request
* Scheduler starts heartbeat countdown using specified heartbeat rate
* Update enters terminal state due to failure or external user action (e.g. `abortJobUpdate`)
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler responds with STOP
* External service stops heartbeats.

## Limitations
Current design assumes reasonably reliable network packet delivery where a delay on delivery &lt;&lt;
average heartbeat rate.