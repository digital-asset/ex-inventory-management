/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.refapps.ims.bitcoin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;

public class BitcoinNetwork {

  private Process bitcoind;
  private ProcessBuilder daemon;
  private final ProcessBuilder setup;
  private Path dataDir;

  public BitcoinNetwork() {
    Path configFile = Paths.get("src/main/resources/bitcoin-standalone.conf").toAbsolutePath();
    dataDir = Paths.get("/tmp/bitcoin");
    daemon =
        new ProcessBuilder()
            .command("bitcoind", "-datadir=" + dataDir.toString(), "-conf=" + configFile.toString())
            .redirectOutput(new File("bitcoin-network.log"))
            .redirectError(new File("bitcoin-network.log"));
    setup =
        new ProcessBuilder()
            .command("./scripts/setup-bitcoin.sh")
            .redirectOutput(new File("bitcoin-network.log"))
            .redirectError(new File("bitcoin-network.log"));
  }

  public void startClean() throws IOException, InterruptedException {
    FileUtils.deleteDirectory(dataDir.toFile());
    Files.createDirectory(dataDir);
    start();
  }

  private void start() throws IOException, InterruptedException {
    bitcoind = daemon.start();
    Process setupProcess = setup.start();
    setupProcess.waitFor();
  }

  public void stop() throws InterruptedException {
    if (bitcoind != null) {
      bitcoind.destroy();
      bitcoind.waitFor();
    }
    bitcoind = null;
  }
}
