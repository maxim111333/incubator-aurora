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
* Block the update progress if no `heartbeatJobUpdate` calls arrived within the last user-defined
time interval:
  * Store the last heartbeat timestamp in volatile memory only
  * Block progress if no heartbeat timestamp is available (covers initial creation and scheduler
restart cases)
  * Resume progress when a missing or delayed heartbeat arrives
  * Do not change update status on a missing heartbeat
* Require external monitoring service to call `pauseJobUpdate` to explicitly pause job update.
A paused job update may still be resumed via `resumeJobUpdate` RPC.

## Interface Changes
Add into `JobUpdateSettings`:
```
 /**
  * If set, requires external calls to heartbeatJobUpdate RPC at the specified rate for the update
  * to make progress. If no heartbeats received within specified interval the update will block
  * its progress and will get unblocked by a new heartbeatJobUpdate call.
  */
  9: i32 pauseIfNoHeartbeatsAfterMs
```

Expose a new `heartbeatJobUpdate` RPC:
```
 /**
  * Allows progress of the job update in case pauseIfNoHeartbeatsAfterMs is specified in
  * JobUpdateSettings. Resumes progress if the update was previously blocked.
  */
  Response heartbeatJobUpdate(1: string updateId, 2: SessionKey session)
```

## Case Study
### 1. Successful coordinated update
* User posts a coordinated job update request
* Scheduler creates a job update and waits for a heartbeat to start acting on it
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler unblocks update,
starts countdown using specified heartbeat rate and responds with OK
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with OK
* Scheduler finishes the update
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with STOP. External service stops heartbeats.

### 2. Update paused by user or external service
* User posts a coordinated job update request
* Scheduler creates a job update and waits for a heartbeat to start acting on it
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler unblocks update,
starts countdown using specified heartbeat rate and responds with OK
* External service detects service health problems and calls `pauseJobUpdate`. Alternatively, user
calls `pauseJobUpdate`
* Scheduler moves update into paused state
* In case a `heartbeatJobUpdate` RPC is called with the matching update ID, scheduler responds with
PAUSED. External service has a choice to either continue or stop heartbeats.

### 3. Update resumed by user or external service
* External service decides to resume job update after a health problem is resolved and calls
`resumeJobUpdate`. Alternatively, user calls `resumeJobUpdate`
* Scheduler resumes the update and waits for a heartbeat to start acting on it.

### 4. Missed heartbeat (network partition)
* User posts a coordinated job update request
* Scheduler creates a job update and waits for a heartbeat to start acting on it
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler unblocks update,
starts countdown using specified heartbeat rate and responds with OK
* Heartbeat timeout expires (e.g.: network partition), scheduler blocks update progress
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler unblocks update,
resets countdown using specified heartbeat rate and responds with OK
* Scheduler finishes the update
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with STOP. External service stops heartbeats.

### 5. Scheduler failover while update is in progress
* User posts a coordinated job update request
* Scheduler creates a job update and waits for a heartbeat to start acting on it
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler unblocks update,
starts countdown using specified heartbeat rate and responds with OK
* Scheduler restarts and waits for a heartbeat to start acting on it
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler unblocks update,
resets countdown and responds with OK
* Scheduler finishes the update
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler resets countdown and
responds with STOP. External service stops heartbeats.

### 6. Update is not active (FAILED/ERROR/ABORTED)
* User posts a coordinated job update request
* Scheduler creates a job update and waits for a heartbeat to start acting on it
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler unblocks update,
starts countdown using specified heartbeat rate and responds with OK
* Update enters terminal state due to failure or external user action (e.g. `abortJobUpdate`)
* A `heartbeatJobUpdate` RPC is called with the matching update ID. Scheduler responds with STOP
* External service stops heartbeats.

### 7. Unknown update ID
* A `heartbeatJobUpdate` RPC is called with the unknown update ID. Scheduler responds with ERROR
* External service stops heartbeats.
