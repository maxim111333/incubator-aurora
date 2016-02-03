package org.apache.aurora.scheduler.storage.log;

import org.apache.aurora.gen.InstanceTaskConfig;
import org.apache.aurora.gen.JobUpdate;
import org.apache.aurora.gen.TaskConfig;
import org.apache.aurora.scheduler.storage.entities.ITaskConfig;

public final class ThriftBackfill {
  static JobUpdate backFillJobUpdate(JobUpdate update) {
    backFillTaskConfig(update.getInstructions().getDesiredState().getTask());
    for (InstanceTaskConfig instanceConfig : update.getInstructions().getInitialState()) {
      backFillTaskConfig(instanceConfig.getTask());
    }

    return update;
  }

  public static ITaskConfig backFillTaskConfig(ITaskConfig task) {
    if (!task.isSetEnvironment() || !task.isSetJobName()) {
      return ITaskConfig.build(backFillTaskConfig(task.newBuilder()));
    }
    return task;
  }

  private static TaskConfig backFillTaskConfig(TaskConfig task) {
    task.setJobName(task.getJob().getName()).setEnvironment(task.getJob().getEnvironment());
    return task;
  }
}
