/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

namespace java org.apache.aurora.gen.storage
namespace py gen.apache.aurora.storage

include "api.thrift"

// Thrift object definitions for messages used for mesos storage.

// Ops that are direct representations of the data needed to perform local storage mutations.
struct SaveFrameworkId {
  1: string id
}

struct SaveAcceptedJob {
  1: string managerId
  2: api.JobConfiguration jobConfig
}

struct SaveLock {
  1: api.Lock lock
}

struct RemoveLock {
  1: api.LockKey lockKey
}

struct RemoveJob {
  2: api.JobKey jobKey
}

struct SaveTasks {
  1: set<api.ScheduledTask> tasks
}

struct RewriteTask {
  1: string taskId
  2: api.TaskConfig task
}

struct RemoveTasks {
  1: set<string> taskIds
}

struct SaveQuota {
  1: string role
  2: api.ResourceAggregate quota
}

struct RemoveQuota {
  1: string role
}

struct SaveDeploy {
  1: api.Deploy deploy
}

struct SaveHostAttributes {
  1: api.HostAttributes hostAttributes
}

union Op {
  1: SaveFrameworkId saveFrameworkId
  2: SaveAcceptedJob saveAcceptedJob
  5: RemoveJob removeJob
  6: SaveTasks saveTasks
  7: RemoveTasks removeTasks
  8: SaveQuota saveQuota
  9: RemoveQuota removeQuota
  10: SaveHostAttributes saveHostAttributes
  11: RewriteTask rewriteTask
  12: SaveLock saveLock
  13: RemoveLock removeLock
  14: SaveDeploy saveDeploy
}

// The current schema version ID.  This should be incremented each time the
// schema is changed, and support code for schema migrations should be added.
const i32 CURRENT_SCHEMA_VERSION = 1

// Represents a series of local storage mutations that should be applied in a single atomic
// transaction.
struct Transaction {
  1: list<Op> ops
  2: i32 schemaVersion
}

struct StoredJob {
  1: string jobManagerId
  3: api.JobConfiguration jobConfiguration
}

struct SchedulerMetadata {
  1: string frameworkId
  // The SHA of the repo.
  2: string revision
  // The tag of the repo.
  3: string tag
  // The timestamp of the build.
  4: string timestamp
  // The user who built the scheduler
  5: string user
  // The machine that built the scheduler
  6: string machine
  7: api.APIVersion version
}

struct QuotaConfiguration {
  1: string role
  2: api.ResourceAggregate quota
}

// Represents a complete snapshot of local storage data suitable for restoring the local storage
// system to its state at the time the snapshot was taken.
struct Snapshot {

  // The timestamp when the snapshot was made in milliseconds since the epoch.
  1: i64 timestamp

  3: set<api.HostAttributes> hostAttributes
  4: set<api.ScheduledTask> tasks
  5: set<StoredJob> jobs
  6: SchedulerMetadata schedulerMetadata
  8: set<QuotaConfiguration> quotaConfigurations
  9: set<api.Lock> locks
  10: set<api.Deploy> deploys
}

// A message header that calls out the number of expected FrameChunks to follow to form a complete
// message.
struct FrameHeader {

  // The number of FrameChunks following this FrameHeader required to reconstitute its message.
  1: i32 chunkCount

  // The MD5 checksum over the binary blob that was chunked across chunkCount chunks to decompose
  // the message.
  2: binary checksum
}

// A chunk of binary data that can be assembled with others to reconstitute a fully framed message.
struct FrameChunk {
  2: binary data
}

// Frames form a series of LogEntries that can be re-assembled into a basic log entry type like a
// Snapshot.  The Frame protocol is that a single FrameHeader is followed by one or more FrameChunks
// that can be re-assembled to obtain the binary content of a basic log entry type.
//
// In the process of reading a Frame, invalid data should always be logged and skipped as it may
// represent a failed higher level transaction where a FrameHeader successfully appends but not all
// the chunks required to complete the full message frame successfully commit.  For example: if a
// Snaphsot is framed, it might break down into 1 FrameHeader followed by 5 FrameChunks.  It could
// be that the FrameHeader and 2 chunks get written successfully, but the 3rd and subsequent chunks
// fail to append.  In this case, the storage mechanism would throw to indicate a failed transaction
// at write-time leaving a partially framed message in the log stream that should be skipped over at
// read-time.
union Frame {
  1: FrameHeader header
  2: FrameChunk chunk
}

// A scheduler storage write-ahead log entry consisting of no-ops to skip over or else snapshots or
// transactions to apply.  Any entry type can also be chopped up into frames if the entry is too big
// for whatever reason.
union LogEntry {
  1: Snapshot snapshot
  2: Transaction transaction

  // The value should be ignored - both true and false signal an equivalent no operation marker.
  3: bool noop;

  4: Frame frame

  // A LogEntry that is first serialized in the thrift binary format,
  // then compressed using the "deflate" compression format.
  // Deflated entries are expected to be un-framed.  They may be pieced together by multiple frames,
  // but the contents of the deflated entry should not be a Frame.
  5: binary deflatedEntry
}

