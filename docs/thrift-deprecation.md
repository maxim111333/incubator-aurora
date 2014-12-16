# Thrift API Changes

## Overview
Aurora uses [Apache Thrift](https://thrift.apache.org/) for representing structured data in
client/server RPC protocol as well as for internal data storage. While Thrift is capable to
correctly handle additions and renames of the existing members, field removals must be done
carefully to ensure backwards compatibility and provide predictable deprecation cycle. This
document describes general guidelines for making thrift schema changes to the existing fields in
[api.thrift](../src/main/thrift/org/apache/aurora/gen/api.thrift).

## Checklist
Every existing thrift schema modification is unique in its requirements and must be analyzed
carefully to identify its scope and expected consequences. The following checklist may help in that
analysis:
* Is this a new field/struct? If yes, go ahead.
* Is this a pure field/struct rename without any type/structure change? If yes, go ahead and rename.
* Anything else, read further to make sure your change is properly planned.

## Deprecation cycle
Any time a breaking change (e.g.: field replacement or removal) is required, the following cycle
must be followed:

### vCurrent
Change is applied in a way that does not break scheduler/client with this version to
communicate with scheduler/client from vCurrent-1.
* Add a field double to the old one and implement a dual read/write anywhere it's used
* Check [storage.thrift](../src/main/thrift/org/apache/aurora/gen/storage.thrift) to see if the
affected struct is stored in Aurora scheduler storage. If so, you most likely need to backfill
existing data to ensure both fields are populated eagerly on startup
See [StorageBackfill.java](../src/main/java/org/apache/aurora/scheduler/storage/StorageBackfill.java)
* Add a deprecation jira ticket into the vCurrent+1 release candidate
* Add a TODO next to the deprecated field with that jira ticket

### vCurrent+1:
Finalize the change by removing the deprecated fields from the thrift schema.
* Drop any dual read/write routines added in the previous version
* Remove the deprecated thrift field.

## Testing
It's always advisable to test your changes in the local vagrant environment to build more
confidence that you change is backwards compatible. It's easy to simulate different
client/scheduler versions by playing with `aurorabuild` command. See [this document](vagrant.md)
for more.

