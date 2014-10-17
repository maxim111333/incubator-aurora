# Storage Configuration And Maintenance

- [Mesos log configuration flags](#mesos-log-configuration-flags)
  - [-native_log_quorum_size](#-native_log_quorum_size)
  - [-native_log_file_path](#-native_log_file_path)
  - [-native_log_zk_group_path](#-native_log_zk_group_path)
- [Backup configuration flags](#backup-configuration-flags)
  - [-backup_interval](#-backup_interval)
  - [-backup_dir](#-backup_dir)
  - [-max_saved_backups](#-max_saved_backups)
- [Restoring from backup](#restoring-from-backup)
For a high level overview of the Aurora storage architecture refer to [this document](storage.md).

## Mesos log configuration flags

### -native_log_quorum_size
Defines the Mesos replicated log quorum size. See
[this document](deploying-aurora-scheduler.md#replicated-log-configuration) on how to choose the
right value.

### -native_log_file_path
Location of the Mesos replicated log files. Consider allocating a dedicated drive (preferably SSD)
for Mesos log files to ensure optimal storage performance.

### -native_log_zk_group_path
Zookeeper path used for native log quorum discovery.

See [code](../src/main/java/org/apache/aurora/scheduler/log/mesos/MesosLogStreamModule.java) for
other available Mesos replicated log configuration options and default values.

## Backup configuration flags

### -backup_interval
Scheduler writes snapshot backup files on a periodic basis to facilitate disaster recovery. The
default interval value is 1 hour.

### -backup_dir
Location for the backup files.

### -max_saved_backups
Max number of backups to retain before deleting them oldest first.

## Restoring from backup
See [this document](storage_recovery.md) for the recovery procedure.
