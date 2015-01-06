package org.apache.aurora.scheduler.autoscaler;

import java.util.Map;
import java.util.logging.Logger;

import com.google.common.collect.Maps;

import org.apache.aurora.scheduler.storage.entities.IJobAutoScaleConfig;
import org.apache.aurora.scheduler.storage.entities.IJobConfiguration;
import org.apache.aurora.scheduler.storage.entities.IJobKey;

import static java.util.Objects.requireNonNull;

/**
 * Implements job autoscaling depending on the provided input metric.
 */
public class Autoscaler {

  private static final Logger LOG = Logger.getLogger(Autoscaler.class.getName());

  public static class AutoscalerException extends Exception {
    AutoscalerException(String message) {
      super(message);
    }
  }

  // TODO(maxim): this is a hack to get a hold of autoscaler settings. If update settings remain in
  // in JobConfiguration need to figure out a better way to store update settings. Perhaps, having
  // a dedicated API to store/update autoscaler settings.
  private final Map<IJobKey, IJobConfiguration> jobConfigs = Maps.newConcurrentMap();

  /**
   * Fake in-memory replacement for a "AutoscalerStore".
   *
   * @param jobConfiguration
   */
  public void addJobConfig(IJobConfiguration jobConfiguration) {
    if (jobConfiguration.isSetAutoScaleConfig()) {
      jobConfigs.put(jobConfiguration.getKey(), jobConfiguration);
    }
  }

  /**
   * Fake in-memory replacement for a "AutoscalerStore".
   *
   * @param jobKey
   * @return
   */
  public IJobConfiguration getJobConfig(IJobKey jobKey) {
    return jobConfigs.get(jobKey);
  }

  public int getInstanceCount(IJobKey jobKey, double currentMetric, int currentTasks)
      throws AutoscalerException{

    IJobConfiguration jobConfiguration = jobConfigs.get(jobKey);
    requireNonNull(jobConfiguration);

    IJobAutoScaleConfig autoScaleConfig = jobConfiguration.getAutoScaleConfig();
    Controller controller = new Controller.Builder()
        .setTarget(autoScaleConfig.getTargetUtilizationMetric())
        .setTolerance(autoScaleConfig.getTolerancePercent() / 100)
        .setMaxIncrementInstances(autoScaleConfig.getMaxInstanceIncrement())
        .setMaxDecrementInstances(autoScaleConfig.getMaxInstanceDecrement())
        .build();

    int countDelta = controller.calculate(currentMetric, currentTasks);
    LOG.info("Current metric: " + currentMetric);
    LOG.info("Count delta: " + countDelta);

    int targetTaskCount = currentTasks + countDelta;
    if (targetTaskCount < autoScaleConfig.getMinTotalInstances()) {
      throw new AutoscalerException(String.format(
          "Unable to reduce total instance count to %d as it would violate job configured " +
          "minTotalInstances of %d", targetTaskCount, autoScaleConfig.getMinTotalInstances()));
    } else if (targetTaskCount > autoScaleConfig.getMaxTotalInstances()) {
      throw new AutoscalerException(String.format(
          "Unable to increase total instance count to %d as it would violate job configured " +
          "maxTotalInstances of %d", targetTaskCount, autoScaleConfig.getMaxTotalInstances()));
    } else {
      return targetTaskCount;
    }
  }
}
