package org.apache.aurora.scheduler.autoscaler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created with IntelliJ IDEA.
 * User: mkhutornenko
 * Date: 1/5/15
 * Time: 3:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class ControllerTest {

  @Test
  public void onTargetTest() {
    Controller controller = new Controller.Builder()
        .setTarget(80)
        .setTolerance(0.1).build();

    assertEquals(0, controller.calculate(73, 1000));
  }

  @Test
  public void scaleUpTest() {
    Controller controller = new Controller.Builder()
        .setTarget(70)
        .setMaxIncrementInstances(1000)
        .setTolerance(0.05).build();

    assertEquals(143, controller.calculate(80, 1000));
  }

  @Test
  public void scaleDownTest() {
    Controller controller = new Controller.Builder()
        .setTarget(70)
        .setMaxDecrementInstances(-1000)
        .setTolerance(0.05).build();

    assertEquals(-143, controller.calculate(60, 1000));
  }
}
