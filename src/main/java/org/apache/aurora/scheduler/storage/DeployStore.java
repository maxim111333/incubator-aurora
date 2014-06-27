package org.apache.aurora.scheduler.storage;

import java.util.Set;

import org.apache.aurora.scheduler.storage.entities.IDeploy;

public interface DeployStore {

  Set<IDeploy> getDeploys();

  IDeploy getDeploy(String deployId);

  interface Mutable extends DeployStore {

    void saveDeploy(IDeploy deploy);
  }
}
