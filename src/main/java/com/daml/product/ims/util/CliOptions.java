/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.daml.product.ims.util;

import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

// Valid use case for a DataClass.
@SuppressWarnings("PMD.DataClass")
public class CliOptions {
  @Option(name = "-s", usage = "Sandbox host", metaVar = "SANDBOX_HOST")
  private String sandboxHost = "localhost";

  @Option(name = "-p", usage = "Sandbox port", metaVar = "SANDBOX_PORT")
  private int sandboxPort = 6865;

  @Option(name = "-b", usage = "Bitcoin host", metaVar = "BITCOIN_HOST")
  private String bitcoinHost = "localhost";

  @Option(name = "-bp", usage = "Bitcoin port", metaVar = "BITCOIN_PORT")
  private int bitcoinPort = 19091;

  @Option(name = "-busername", usage = "Bitcoin user name", metaVar = "BITCOIN_USER_NAME")
  private String bitcoinUserName = "admin1";

  @Option(name = "-bpassword", usage = "Bitcoin password", metaVar = "BITCOIN_PASSWORD")
  private String bitcoinPassword = "123";

  @Option(name = "-keyfile", usage = "Key file")
  private String keyFile = "src/main/resources/secretKeyStore.regtest.txt";

  @Argument private List<String> arguments = new ArrayList<String>();

  public String getSandboxHost() {
    return sandboxHost;
  }

  public int getSandboxPort() {
    return sandboxPort;
  }

  public String getKeyFile() {
    return keyFile;
  }

  public String getBitcoinUrl() {
    return String.format("http://%s:%s", bitcoinHost, bitcoinPort);
  }

  public String getBitcoinUserName() {
    return bitcoinUserName;
  }

  public String getBitcoinPassword() {
    return bitcoinPassword;
  }

  public static CliOptions parseArgs(String[] args) {
    CliOptions options = new CliOptions();
    CmdLineParser parser = new CmdLineParser(options);
    try {
      parser.parseArgument(args);

      if (options.arguments.size() > 0) throw new RuntimeException("Only one mode is supported");
    } catch (CmdLineException e) {
      System.err.println("Invalid command line options");
      parser.printUsage(System.err);
      System.exit(1);
    }
    return options;
  }
}
