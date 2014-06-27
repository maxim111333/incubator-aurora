package org.apache.aurora.scheduler.state;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import org.apache.aurora.gen.Deploy;
import org.apache.aurora.gen.DeployStatus;
import org.apache.aurora.scheduler.base.JobKeys;
import org.apache.aurora.scheduler.storage.Storage.MutateWork;
import org.apache.aurora.scheduler.configuration.SanitizedConfiguration;
import org.apache.aurora.scheduler.storage.Storage;
import org.apache.aurora.scheduler.storage.entities.IDeploy;
import org.apache.aurora.scheduler.storage.entities.IJobKey;

import com.twitter.common.util.Clock;

public interface Deployer {

  void startDeploy(SanitizedConfiguration jobConfiguration);

  class DeployerImpl implements Deployer {

    private final Storage storage;
    private final ExecutorService executor;
    private final Clock clock;
    private final UUIDGenerator uuidGenerator;

    @Inject
    DeployerImpl(
        Storage storage,
        ExecutorService executor,
        Clock clock,
        UUIDGenerator uuidGenerator) {

      this.storage = storage;
      this.executor = executor;
      this.clock = clock;
      this.uuidGenerator = uuidGenerator;
    }

    @Override
    public void startDeploy(final SanitizedConfiguration sanitizedConfig) {
      final Deploy deploy = createDeploy(sanitizedConfig);
      saveDeploy(deploy);

      executor.submit(new Runnable() {
        @Override
        public void run() {
          int result = invokeAuroraClient(sanitizedConfig.getJobConfig().getKey());

          saveDeploy(deploy
              .setCompletedTimestampMs(clock.nowMillis())
              .setStatus(result == 0 ? DeployStatus.SUCCEEDED : DeployStatus.FAILED));
        }
      });
    }

    private void saveDeploy(final Deploy deploy) {
      storage.write(new MutateWork.NoResult.Quiet() {
        @Override
        protected void execute(Storage.MutableStoreProvider storeProvider) {
          storeProvider.getDeployStore().saveDeploy(IDeploy.build(deploy));
        }
      });
    }

    private Deploy createDeploy(final SanitizedConfiguration sanitizedConfig) {
      return new Deploy()
          .setDeployId(uuidGenerator.createNew().toString())
          .setKey(sanitizedConfig.getJobConfig().getKey().newBuilder())
          .setJobConfig(sanitizedConfig.getJobConfig().newBuilder().toString())
          .setStatus(DeployStatus.IN_PROGRESS)
          .setInsertedTimestampMs(clock.nowMillis());
    }

    private int invokeAuroraClient(IJobKey jobKey) {
      ProcessBuilder builder = new ProcessBuilder(
          "aurora",
          "deployment",
          "release",
          JobKeys.canonicalString(jobKey));

//      try {
//        Process process = builder.start();
//        return process.waitFor();
//      } catch (IOException e) {
//        e.printStackTrace();
//      } catch (InterruptedException e) {
//        e.printStackTrace();
//      }

      return -1;
    }
  }
}
