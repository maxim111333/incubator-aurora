package org.apache.aurora.scheduler.storage.log;

import org.apache.aurora.gen.InstanceTaskConfig;
import org.apache.aurora.gen.JobUpdate;
import org.apache.aurora.gen.TaskConfig;

final class ThriftBackfill {
  static JobUpdate backFillJobUpdate(JobUpdate update) {
    backFillTaskConfig(update.getInstructions().getDesiredState().getTask());
    for (InstanceTaskConfig instanceConfig : update.getInstructions().getInitialState()) {
      backFillTaskConfig(instanceConfig.getTask());
    }

    return update;
  }

  private static void backFillTaskConfig(TaskConfig task) {
    task.setJobName(task.getJob().getName()).setEnvironment(task.getJob().getEnvironment());
  }
}
