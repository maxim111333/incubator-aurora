package org.apache.aurora.scheduler.storage.db;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.aurora.gen.Deploy;
import org.apache.aurora.gen.JobKey;
import org.apache.ibatis.annotations.Param;

public interface DeployMapper {

  long merge(
      @Param("deployId") long deployId,
      @Param("key") JobKey key,
      @Param("jobConfig") String jobConfig,
      @Param("status") int status,
      @Param("insertedTimestampMs") long insertedTimestampMs,
      @Param("completedTimestampMs") long completedTimestampMs);

  @Nullable
  Deploy select(long deployId);

  List<Deploy> selectAll();
}
