/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.refapps.ims;

import bitcoin.types.BitcoinAddress;
import com.daml.ledger.rxjava.DamlLedgerClient;
import com.daml.ledger.rxjava.components.Bot;
import com.digitalasset.refapps.ims.bitcoin.BTCService;
import com.digitalasset.refapps.ims.bitcoin.BTCUtility;
import com.digitalasset.refapps.ims.bitcoin.BitcoinClient;
import com.digitalasset.refapps.ims.util.CliOptions;
import com.digitalasset.refapps.ims.util.CommandsAndPendingSetBuilder;
import fr.acinq.bitcoin.Crypto;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  // application id used for sending commands
  public static final String APP_ID = "InventoryManagement";

  // constants for referring to the parties
  public static final String OPERATOR = "Operator";
  public static final String SIGNING_PARTY = "SigningParty";

  private static final AtomicReference<Clock> clock =
      new AtomicReference<>(Clock.fixed(Instant.ofEpochSecond(0), ZoneId.systemDefault()));

  public static void main(String[] args) throws InterruptedException, IOException {

    CliOptions cliOptions = CliOptions.parseArgs(args);

    DamlLedgerClient.Builder builder =
        DamlLedgerClient.newBuilder(cliOptions.getSandboxHost(), cliOptions.getSandboxPort());
    DamlLedgerClient client = builder.build();

    waitForSandbox(cliOptions.getSandboxHost(), cliOptions.getSandboxPort(), client);

    run(client, cliOptions);

    logger.info("Welcome to Inventory Management Application!");
    logger.info("Press Ctrl+C to shut down the program.");
    Thread.currentThread().join();
  }

  public static void run(DamlLedgerClient client, CliOptions cliOptions) throws IOException {
    // We create a Flowable<Instant> clockFlowable to set the time
    client
        .getTimeClient()
        .getTime()
        .doOnNext(ts -> logger.info("Received time change {}", ts))
        .doOnNext(ts -> clock.set(Clock.fixed(ts, ZoneId.systemDefault())))
        .subscribe();

    Duration mrt = Duration.ofSeconds(10);
    CommandsAndPendingSetBuilder.Factory commandBuilderFactory =
        CommandsAndPendingSetBuilder.factory(APP_ID, clock::get, mrt);

    // private keys for signing bots
    Map<BitcoinAddress, Crypto.PrivateKey> addressToPrivateKeyMap =
        BTCUtility.loadPrivateKeysFromFile(cliOptions.getKeyFile());

    TransactionSignerBot transactionSignerBot =
        new TransactionSignerBot(commandBuilderFactory, SIGNING_PARTY, addressToPrivateKeyMap);
    OwnedAddressRegistrarBot ownedAddressRegistrarBot =
        new OwnedAddressRegistrarBot(commandBuilderFactory, SIGNING_PARTY, addressToPrivateKeyMap);
    TransferRequestValidatorBot transferRequestValidatorBot =
        new TransferRequestValidatorBot(commandBuilderFactory, OPERATOR);

    SubmitForSigningBot submitForSigningBot =
        new SubmitForSigningBot(commandBuilderFactory, OPERATOR);

    logger.info("Using Bitcoin network at '{}'.", cliOptions.getBitcoinUrl());
    BitcoinClient bitcoinClient =
        new BitcoinClient(
            cliOptions.getBitcoinUrl(),
            cliOptions.getBitcoinUserName(),
            cliOptions.getBitcoinPassword());
    BTCService btcService = new BTCService(bitcoinClient);
    UTXOUpdaterBot utxoUpdaterBot = new UTXOUpdaterBot(commandBuilderFactory, OPERATOR, btcService);
    RawTxPusherBot rawTxPusherBot = new RawTxPusherBot(commandBuilderFactory, OPERATOR, btcService);

    Bot.wire(
        APP_ID,
        client,
        transactionSignerBot.transactionFilter,
        transactionSignerBot::process,
        TransactionSignerBot.getContractInfo);
    Bot.wire(
        APP_ID,
        client,
        ownedAddressRegistrarBot.transactionFilter,
        ownedAddressRegistrarBot::process,
        OwnedAddressRegistrarBot.getContractInfo);
    Bot.wire(
        APP_ID,
        client,
        transferRequestValidatorBot.transactionFilter,
        transferRequestValidatorBot::process,
        TransferRequestValidatorBot.getContractInfo);
    Bot.wire(
        APP_ID,
        client,
        submitForSigningBot.transactionFilter,
        submitForSigningBot::process,
        SubmitForSigningBot.getContractInfo);
    Bot.wire(
        APP_ID,
        client,
        utxoUpdaterBot.transactionFilter,
        utxoUpdaterBot::process,
        UTXOUpdaterBot.getContractInfo);
    Bot.wire(
        APP_ID,
        client,
        rawTxPusherBot.transactionFilter,
        rawTxPusherBot::process,
        RawTxPusherBot.getContractInfo);
  }

  public static void waitForSandbox(String host, int port, DamlLedgerClient client) {
    boolean connected = false;
    while (!connected) {
      try {
        client.connect();
        connected = true;
      } catch (Exception _ignored) {
        logger.info(String.format("Connecting to sandbox at %s:%s", host, port));
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
      }
    }
  }
}
