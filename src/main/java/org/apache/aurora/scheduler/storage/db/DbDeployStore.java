package org.apache.aurora.scheduler.storage.db;

import java.util.Set;

import javax.inject.Inject;

import org.apache.aurora.scheduler.storage.DeployStore;
import org.apache.aurora.scheduler.storage.entities.IDeploy;

class DbDeployStore implements DeployStore.Mutable {

  private final DeployMapper deployMapper;
  private final JobKeyMapper jobKeyMapper;

  @Inject
  DbDeployStore(DeployMapper deployMapper, JobKeyMapper jobKeyMapper) {
    this.deployMapper = deployMapper;
    this.jobKeyMapper = jobKeyMapper;
  }

  @Override
  public void saveDeploy(IDeploy deploy) {
    jobKeyMapper.merge(deploy.getKey().newBuilder());
    deployMapper.merge(deploy.newBuilder());
  }

  @Override
  public Set<IDeploy> getDeploys() {
    return IDeploy.setFromBuilders(deployMapper.selectAll());
  }

  @Override
  public IDeploy getDeploy(String deployId) {
    return IDeploy.build(deployMapper.select(deployId));
  }
}
