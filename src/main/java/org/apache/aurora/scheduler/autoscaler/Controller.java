package org.apache.aurora.scheduler.autoscaler;

import java.util.logging.Logger;

/**
 * Autoscaler controller.
 * Currently supports only proportional part of the classic PID controller.
 */
class Controller {
  private static final Logger LOG = Logger.getLogger(Controller.class.getName());

  private final double pCoefficient;
  private final double target;
  private final int maxDecrement;
  private final int maxIncrement;
  private final double tolerance;

  static final class Builder {
    private double pCoefficient = 1.0;
    private double target = 0.0;
    private int maxDecrement = 1;
    private int maxIncrement = 1;
    private double tolerance = 0.01;

    Builder setProportionalCoefficient(double pCoefficient) {
      this.pCoefficient = pCoefficient;
      return this;
    }

    Builder setTarget(double target) {
      this.target = target;
      return this;
    }

    Builder setMaxIncrementInstances(int maxIncrement) {
      this.maxIncrement = maxIncrement;
      return this;
    }

    Builder setMaxDecrementInstances(int maxDecrement) {
      this.maxDecrement = -maxDecrement;
      return this;
    }

    Builder setTolerance(double tolerance) {
      this.tolerance = tolerance;
      return this;
    }

    Controller build() {
      return new Controller(pCoefficient, target, maxIncrement, maxDecrement, tolerance);
    }
  }

  private Controller(
      double pCoefficient,
      double target,
      int maxIncrement,
      int minDecrement,
      double tolerance) {

    this.pCoefficient = pCoefficient;
    this.target = target;
    this.maxIncrement = maxIncrement;
    this.maxDecrement = minDecrement;
    this.tolerance = tolerance;
  }

  int calculate(double currentInput, int currentInstances) {
    if (onTarget(currentInput)) {
      LOG.info(String.format(
          "Target: %f, current: %f, tolerance: %f", target, currentInput, tolerance));
      return 0;
    }

    LOG.info("Controller: " + this.toString());
    LOG.info("Current input: " + currentInput);
    LOG.info("Current instances: " + currentInstances);

    double delta = currentInput / target;
    double deltaInst = pCoefficient * delta * currentInstances;

    int targetInstances = delta > 0 ? (int) Math.ceil(deltaInst) : (int) Math.floor(deltaInst);
    int deltaInstances = targetInstances - currentInstances;

    if (deltaInstances < maxDecrement) {
      return maxDecrement;
    } else if (deltaInstances > maxIncrement) {
      return maxIncrement;
    } else {
      return deltaInstances;
    }
  }

  boolean onTarget(double currentInput) {
    return Math.abs(target - currentInput) <= tolerance * target;
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("pCoefficient", pCoefficient)
        .add("target", target)
        .add("maxIncrement", maxIncrement)
        .add("maxDecrement", maxDecrement)
        .add("tolerance", tolerance)
        .toString();
  }
}
