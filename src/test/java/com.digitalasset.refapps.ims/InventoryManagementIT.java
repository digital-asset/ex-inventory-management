/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.refapps.ims;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import actors.compliance.ComplianceRole;
import actors.deskhead.DeskHeadRole;
import actors.operator.OnboardEntityMaster;
import actors.trader.TraderRole;
import bitcoin.types.BitcoinAddress;
import bitcoin.types.Satoshi;
import com.daml.ledger.javaapi.data.Identifier;
import com.daml.ledger.javaapi.data.Party;
import com.digitalasset.refapps.ims.bitcoin.BitcoinClient;
import com.digitalasset.refapps.ims.bitcoin.BitcoinNetwork;
import com.digitalasset.refapps.ims.util.CliOptions;
import com.digitalasset.testing.junit4.Sandbox;
import com.digitalasset.testing.ledger.DefaultLedgerAdapter;
import com.digitalasset.testing.utils.ContractWithId;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.*;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import transfer.address.AddressReputation;
import transfer.address.DestinationAddress;
import transfer.address.OwnedAddress;
import transfer.request.FailedAddressCheckTransferRequest;
import transfer.request.FailedLimitsCheckTransferRequest;
import transfer.request.InsufficientTransferRequest;
import transfer.transfer.SignedTransfer;
import transfer.transfer.TransmittedTransfer;
import transfer.transfer.UTXO;

public class InventoryManagementIT {
  private static final Logger logger = LoggerFactory.getLogger(InventoryManagementIT.class);
  private static final Path RELATIVE_DAR_PATH = Paths.get("./target/inventory-management.dar");
  private static final String TEST_MODULE = "Test.Test";
  private static final String TEST_SCENARIO = "onboarding";

  private static final Party OPERATOR_PARTY = new Party("Operator");
  private static final Party SIGNINGPARTY_PARTY = new Party("SigningParty");
  private static final Party TRADER_PARTY = new Party("Trader1");
  private static final Party COMPLIANCEOFFICER_PARTY = new Party("ComplianceOfficer");
  private static final Party DESKHEAD_PARTY = new Party("DeskHead");

  private static final CliOptions cliOptions = new CliOptions();
  private static Sandbox sandbox =
      Sandbox.builder()
          .dar(RELATIVE_DAR_PATH)
          .module(TEST_MODULE)
          .scenario(TEST_SCENARIO)
          .parties(
              OPERATOR_PARTY.getValue(),
              SIGNINGPARTY_PARTY.getValue(),
              TRADER_PARTY.getValue(),
              COMPLIANCEOFFICER_PARTY.getValue(),
              DESKHEAD_PARTY.getValue())
          .setupAppCallback(
              client -> {
                try {
                  Main.run(client, cliOptions);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              })
          .build();

  @ClassRule public static ExternalResource compile = sandbox.getClassRule();
  @Rule public ExternalResource sandboxRule = sandbox.getRule();

  private final BitcoinNetwork bitcoinNetwork = new BitcoinNetwork();
  BitcoinClient bitcoinClient =
      new BitcoinClient(
          cliOptions.getBitcoinUrl(),
          cliOptions.getBitcoinUserName(),
          cliOptions.getBitcoinPassword());

  private TraderRole.ContractId traderRoleCid;
  private OnboardEntityMaster.ContractId operatorRoleCid;
  private ComplianceRole.ContractId complianceRoleCid;
  private DestinationAddress goodAddress;
  private DestinationAddress badAddress;
  private DefaultLedgerAdapter ledgerAdapter;
  private String changeAddress = "mvQ2E19nuUNEXkCBEv1qcpTNpGVmnC4uwM"; // target of remaining UTXO-s
  private static final int numberOfOwnedAddresses = 14;
  private long transferAmount = 1_000_000L;
  private long transactionFee = 20_000L;
  private long initialMiningReward = 5_000_000_000L; // of the first 149 regtest blocks
  private long originalOwnedBalance;
  // TODO(demian) extract change address (from RawTX or put it into TransactionDetails),
  // TODO(demian) extract transactionFee

  @Before
  public void setUp() throws Exception {
    bitcoinNetwork.startClean();
    initLedger();
  }

  @After
  public void tearDown() throws InterruptedException {
    bitcoinNetwork.stop();
  }

  private Map<String, Long> requestUTXOUpdate(DefaultLedgerAdapter ledgerAdapter) throws Exception {
    ledgerAdapter.exerciseChoice(OPERATOR_PARTY, operatorRoleCid.exerciseRequestUTXOUpdate());
    // populating UTXOs takes some time depending on the blockchain mode
    waitFor(4000, ledgerAdapter, OPERATOR_PARTY, UTXO.TEMPLATE_ID, UTXO.ContractId::new);
    return fetchOwnedBalance(numberOfOwnedAddresses);
  }

  private void initLedger() throws Exception {
    ledgerAdapter = sandbox.getLedgerAdapter();

    // fetch an operator role
    operatorRoleCid =
        ledgerAdapter.getCreatedContractId(
            OPERATOR_PARTY, OnboardEntityMaster.TEMPLATE_ID, OnboardEntityMaster.ContractId::new);

    fetchOwnedBalance(numberOfOwnedAddresses);

    // fetch a trade role
    traderRoleCid =
        ledgerAdapter.getCreatedContractId(
            TRADER_PARTY, TraderRole.TEMPLATE_ID, TraderRole.ContractId::new);

    // fetch a compliance role
    complianceRoleCid =
        ledgerAdapter.getCreatedContractId(
            COMPLIANCEOFFICER_PARTY, ComplianceRole.TEMPLATE_ID, ComplianceRole.ContractId::new);

    // fetch good and bad addresses
    ContractWithId<DestinationAddress.ContractId> goodAddressWithId =
        ledgerAdapter.getMatchedContract(
            TRADER_PARTY, DestinationAddress.TEMPLATE_ID, DestinationAddress.ContractId::new);
    goodAddress = DestinationAddress.fromValue(goodAddressWithId.record);
    assertThat(goodAddress.reputation, is(AddressReputation.GOOD));

    DestinationAddress address;
    do {
      ContractWithId<DestinationAddress.ContractId> addressWithId =
          ledgerAdapter.getMatchedContract(
              TRADER_PARTY, DestinationAddress.TEMPLATE_ID, DestinationAddress.ContractId::new);
      address = DestinationAddress.fromValue(addressWithId.record);
    } while (address.reputation.equals(AddressReputation.GOOD));
    badAddress = address;

    originalOwnedBalance = totalBalance(requestUTXOUpdate(ledgerAdapter));
  }

  private Map<String, Long> fetchOwnedBalance(int numberOfOwnedAddresses) {
    return IntStream.range(0, numberOfOwnedAddresses)
        .mapToObj(
            x ->
                waitForAddress(
                    4000,
                    ledgerAdapter,
                    OPERATOR_PARTY,
                    OwnedAddress.TEMPLATE_ID,
                    OwnedAddress.ContractId::new))
        .collect(Collectors.toMap(x -> x.address.unpack, x -> x.balance.unpack));
  }

  private long totalBalance(Map<String, Long> balances) {
    return balances.values().stream().reduce(0L, Long::sum);
  }

  private Map<String, Long> assertUpdatedOwnBalanceEquals(long expected) throws Exception {
    Map<String, Long> currentOwnedBalances = requestUTXOUpdate(ledgerAdapter);
    long actual = totalBalance(currentOwnedBalances);
    assertEquals(expected, actual);
    return currentOwnedBalances;
  }

  @Test
  public void testProperPayment() throws Exception {

    BitcoinAddress externalAddress = new BitcoinAddress("mj1tM31mDkW5ip2h6JWA2D9LWoqiuhw2qd");
    // private key: cTGmnUQpzz7SFysPospTHiKWdbyoqWEwjKerFzKQTSeJTPPurPSf
    ledgerAdapter.exerciseChoice(
        COMPLIANCEOFFICER_PARTY,
        complianceRoleCid.exerciseRegisterNewAddress(
            externalAddress, AddressReputation.GOOD, "Our business partner"));

    // externalAddress is on the ledger
    Assert.assertTrue(
        ledgerAdapter.observeMatchingContracts(
            OPERATOR_PARTY,
            DestinationAddress.TEMPLATE_ID,
            DestinationAddress::fromValue,
            false, // checkFirstOnly=false
            da -> da.address.equals(externalAddress)));

    ledgerAdapter.exerciseChoice(
        TRADER_PARTY,
        traderRoleCid.exerciseRequestTransfer(new Satoshi(transferAmount), externalAddress));
    SignedTransfer.ContractId signedTransferCid =
        ledgerAdapter.getCreatedContractId(
            TRADER_PARTY, SignedTransfer.TEMPLATE_ID, SignedTransfer.ContractId::new);

    ledgerAdapter.exerciseChoice(TRADER_PARTY, signedTransferCid.exerciseConfirm());
    ledgerAdapter.getCreatedContractId(
        TRADER_PARTY, TransmittedTransfer.TEMPLATE_ID, TransmittedTransfer.ContractId::new);

    // current balance excludes the mining pool (transactions with 0 confirmations)
    // involved UTXO-s also ignored by BTC client to prevent double spending
    assertUpdatedOwnBalanceEquals(originalOwnedBalance - initialMiningReward);

    bitcoinClient.generate(10, goodAddress.address.unpack);

    long expectedOwnedBalance =
        originalOwnedBalance
            - transactionFee // TX fee will be available only after 100 confirmation
            - transferAmount;
    assertUpdatedOwnBalanceEquals(expectedOwnedBalance);

    // external addresses are not tracked by the ledger
    bitcoinClient.importAddress(externalAddress.unpack); // "Address not found in wallet" otherwise
    long destinationBalance = bitcoinClient.getReceivedByAddress(1, externalAddress.unpack);
    assertEquals(transferAmount, destinationBalance);
  }

  @Test
  public void testProperPaymentWithinOwnedWallet() throws Exception {
    ledgerAdapter.exerciseChoice(
        TRADER_PARTY,
        traderRoleCid.exerciseRequestTransfer(
            new Satoshi(transferAmount),
            goodAddress.address)); // target address is an owned address
    SignedTransfer.ContractId signedTransferCid =
        ledgerAdapter.getCreatedContractId(
            TRADER_PARTY, SignedTransfer.TEMPLATE_ID, SignedTransfer.ContractId::new);

    ledgerAdapter.exerciseChoice(TRADER_PARTY, signedTransferCid.exerciseConfirm());
    ledgerAdapter.getCreatedContractId(
        TRADER_PARTY, TransmittedTransfer.TEMPLATE_ID, TransmittedTransfer.ContractId::new);

    // current balance excludes the mining pool (transactions with 0 confirmations)
    // involved UTXO-s also ignored by BTC client to prevent double spending
    assertUpdatedOwnBalanceEquals(originalOwnedBalance - initialMiningReward);

    bitcoinClient.generate(10, goodAddress.address.unpack);

    long expectedOwnedBalance =
        originalOwnedBalance
            - transactionFee; // TX fee will be available only after 100 confirmation
    Map<String, Long> currentOwnedBalances = assertUpdatedOwnBalanceEquals(expectedOwnedBalance);

    long destinationBalance = currentOwnedBalances.get(goodAddress.address.unpack);
    assertEquals(transferAmount, destinationBalance);

    long expectedChangeAddressBalance = (initialMiningReward - transferAmount - transactionFee);
    // assertEquals(expectedChangeAddressBalance, (long) currentOwnedBalances.get(changeAddress));
  }

  @Test
  public void testDailyLimitExcess() throws InvalidProtocolBufferException {
    ledgerAdapter.exerciseChoice(
        TRADER_PARTY,
        traderRoleCid.exerciseRequestTransfer(new Satoshi(100_000_001L), goodAddress.address));

    FailedLimitsCheckTransferRequest.ContractId failedTransferRequestCid =
        ledgerAdapter.getCreatedContractId(
            TRADER_PARTY,
            FailedLimitsCheckTransferRequest.TEMPLATE_ID,
            FailedLimitsCheckTransferRequest.ContractId::new);

    // fetch a desk head role
    DeskHeadRole.ContractId deskHeadRoleCid =
        ledgerAdapter.getCreatedContractId(
            DESKHEAD_PARTY, DeskHeadRole.TEMPLATE_ID, DeskHeadRole.ContractId::new);

    //  bypass limit check
    ledgerAdapter.exerciseChoice(
        DESKHEAD_PARTY,
        deskHeadRoleCid.exerciseApproveRequestOverLimits(
            failedTransferRequestCid, "dailyLimitExcessReason", Collections.emptyList()));

    SignedTransfer.ContractId signedTransferCid2 =
        ledgerAdapter.getCreatedContractId(
            TRADER_PARTY, SignedTransfer.TEMPLATE_ID, SignedTransfer.ContractId::new);

    // btc wallet check
    ledgerAdapter.exerciseChoice(TRADER_PARTY, signedTransferCid2.exerciseConfirm());
    ledgerAdapter.getCreatedContractId(
        TRADER_PARTY, TransmittedTransfer.TEMPLATE_ID, TransmittedTransfer.ContractId::new);
  }

  @Test
  public void testBalanceExcess() throws Exception {
    long hugeAmountToTransfer = 700_000_000_000L;

    Map<String, Long> currentOwnedBalances = requestUTXOUpdate(ledgerAdapter);
    long actualOwnedBalance = totalBalance(currentOwnedBalances);
    Assert.assertTrue(hugeAmountToTransfer > actualOwnedBalance);

    ledgerAdapter.exerciseChoice(
        TRADER_PARTY,
        traderRoleCid.exerciseRequestTransfer(
            new Satoshi(hugeAmountToTransfer), goodAddress.address));

    FailedLimitsCheckTransferRequest.ContractId failedTransferRequestCid =
        ledgerAdapter.getCreatedContractId(
            TRADER_PARTY,
            FailedLimitsCheckTransferRequest.TEMPLATE_ID,
            FailedLimitsCheckTransferRequest.ContractId::new);

    // fetch a desk head role
    DeskHeadRole.ContractId deskHeadRoleCid =
        ledgerAdapter.getCreatedContractId(
            DESKHEAD_PARTY, DeskHeadRole.TEMPLATE_ID, DeskHeadRole.ContractId::new);

    //  bypass limit check
    ledgerAdapter.exerciseChoice(
        DESKHEAD_PARTY,
        deskHeadRoleCid.exerciseApproveRequestOverLimits(
            failedTransferRequestCid, "dailyLimitExcessReason", Collections.emptyList()));

    ledgerAdapter.getCreatedContractId(
        OPERATOR_PARTY,
        InsufficientTransferRequest.TEMPLATE_ID,
        InsufficientTransferRequest.ContractId::new);
  }

  @Test
  public void testBadAddress() throws InvalidProtocolBufferException {
    // exceeding daily limit
    ledgerAdapter.exerciseChoice(
        TRADER_PARTY,
        traderRoleCid.exerciseRequestTransfer(new Satoshi(1_000_000L), badAddress.address));

    FailedAddressCheckTransferRequest.ContractId failedTransferRequestCid =
        ledgerAdapter.getCreatedContractId(
            TRADER_PARTY,
            FailedAddressCheckTransferRequest.TEMPLATE_ID,
            FailedAddressCheckTransferRequest.ContractId::new);

    // bypass address check
    ledgerAdapter.exerciseChoice(
        COMPLIANCEOFFICER_PARTY,
        complianceRoleCid.exerciseApproveRequestToBadAddress(
            failedTransferRequestCid, "badAddressReason", Collections.emptyList()));

    SignedTransfer.ContractId signedTransferCid3 =
        ledgerAdapter.getCreatedContractId(
            TRADER_PARTY, SignedTransfer.TEMPLATE_ID, SignedTransfer.ContractId::new);

    // btc wallet check
    ledgerAdapter.exerciseChoice(TRADER_PARTY, signedTransferCid3.exerciseConfirm());
    ledgerAdapter.getCreatedContractId(
        TRADER_PARTY, TransmittedTransfer.TEMPLATE_ID, TransmittedTransfer.ContractId::new);
  }

  private <T> T waitFor(
      long timeoutMillis,
      DefaultLedgerAdapter ledgerAdapter,
      Party party,
      Identifier templateId,
      Function<String, T> idFactory) {
    long start = System.currentTimeMillis();
    while (true) {
      try {
        return ledgerAdapter.getCreatedContractId(party, templateId, idFactory);
      } catch (Exception e) {
        if (e instanceof TimeoutException) {
          long elapsed = System.currentTimeMillis() - start;
          if (elapsed > timeoutMillis) {
            throw e;
          }
          logger.info("Timeout observed but waiting more");
        } else throw e;
      }
    }
  }

  private OwnedAddress waitForAddress(
      long timeoutMillis,
      DefaultLedgerAdapter ledgerAdapter,
      Party party,
      Identifier templateId,
      Function<String, OwnedAddress.ContractId> idFactory) {
    long start = System.currentTimeMillis();
    while (true) {
      try {
        return OwnedAddress.fromValue(
            ledgerAdapter.getMatchedContract(party, templateId, idFactory).record);
      } catch (Exception e) {
        if (e instanceof TimeoutException) {
          long elapsed = System.currentTimeMillis() - start;
          if (elapsed > timeoutMillis) {
            throw e;
          }
          logger.info("Timeout observed but waiting more");
        } else throw e;
      }
    }
  }
}
