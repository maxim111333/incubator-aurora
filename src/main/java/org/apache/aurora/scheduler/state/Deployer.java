package org.apache.aurora.scheduler.state;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.apache.aurora.gen.Deploy;
import org.apache.aurora.gen.DeployStatus;
import org.apache.aurora.scheduler.base.JobKeys;
import org.apache.aurora.scheduler.storage.Storage.MutateWork;
import org.apache.aurora.scheduler.configuration.SanitizedConfiguration;
import org.apache.aurora.scheduler.storage.Storage;
import org.apache.aurora.scheduler.storage.entities.IDeploy;
import org.apache.aurora.scheduler.storage.entities.IJobKey;

import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.util.Clock;

public interface Deployer {

  String startDeploy(SanitizedConfiguration jobConfiguration);

  class DeployerImpl implements Deployer {

    @CmdLine(name = "aurora_client_path", help = "Aurora client path relative to home directory.")
    public static final Arg<String> AURORA_PATH = Arg.create("/aurora");

    @CmdLine(name = "tunnel_host", help = "Tunnel host for the aurora client.")
    public static final Arg<String> TUNNEL_HOST = Arg.create(null);

    @CmdLine(name = "deploy_cluster", help = "Name of the cluster to deploy.")
    public static final Arg<String> CLUSTER_NAME = Arg.create("devcluster");

    private static final Logger LOG = Logger.getLogger(DeployerImpl.class.getName());

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
    public String startDeploy(final SanitizedConfiguration sanitizedConfig) {
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

      return deploy.deployId;
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
          System.getenv("HOME") + AURORA_PATH.get(),
          "deployment",
          TUNNEL_HOST.get() == null ? "" : "--tunnel_host=" + TUNNEL_HOST.get(),
          "release",
          CLUSTER_NAME.get() + "/" + JobKeys.canonicalString(jobKey)).redirectErrorStream(true);

      try {
        Process process = builder.start();

        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) {
          LOG.info("*********: " + line);
        }

        return process.waitFor();
      } catch (IOException | InterruptedException e) {
        LOG.severe("Error while running Deployer: " + e);
        return -1;
      }
    }
  }
}
