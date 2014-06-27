package org.apache.aurora.scheduler.storage.db;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.aurora.gen.Deploy;

public interface DeployMapper {

  void merge(Deploy deploy);

  @Nullable
  Deploy select(String deployId);

  List<Deploy> selectAll();
}
