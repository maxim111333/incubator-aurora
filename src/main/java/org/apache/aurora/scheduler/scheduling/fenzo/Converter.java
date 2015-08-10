package org.apache.aurora.scheduler.scheduling.fenzo;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.netflix.fenzo.ConstraintEvaluator;
import com.netflix.fenzo.TaskRequest;
import com.netflix.fenzo.VMTaskFitnessCalculator;
import com.netflix.fenzo.VirtualMachineLease;
import com.netflix.fenzo.plugins.VMLeaseObject;

import org.apache.aurora.scheduler.HostOffer;
import org.apache.aurora.scheduler.base.JobKeys;
import org.apache.aurora.scheduler.storage.entities.ITaskConfig;

public final class Converter {

  public static VirtualMachineLease getVMLease(HostOffer offer) {
    return new VMLeaseObject(offer.getOffer());
  }

  public static TaskRequest getTaskRequest(ITaskConfig task, String taskId) {

    return new TaskRequest() {
      @Override
      public String getId() {
        return taskId;
      }

      @Override
      public String taskGroupName() {
        return JobKeys.canonicalString(task.getJob());
      }

      @Override
      public double getCPUs() {
        return task.getNumCpus();
      }

      @Override
      public double getMemory() {
        return task.getRamMb();
      }

      @Override
      public double getNetworkMbps() {
        return 0;
      }

      @Override
      public double getDisk() {
        return task.getDiskMb();
      }

      @Override
      public int getPorts() {
        return task.getRequestedPorts().size();
      }

      @Override
      public List<? extends ConstraintEvaluator> getHardConstraints() {
        return ImmutableList.of();
      }

      @Override
      public List<? extends VMTaskFitnessCalculator> getSoftConstraints() {
        return ImmutableList.of();
      }
    };
  }
}
