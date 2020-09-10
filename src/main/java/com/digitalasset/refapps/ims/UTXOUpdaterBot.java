/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.refapps.ims;

import static com.digitalasset.refapps.ims.util.TemplateManager.filterTemplates;

import actors.operator.OnboardEntityMaster;
import actors.operator.UTXOUpdateRequest;
import bitcoin.types.Satoshi;
import com.daml.ledger.javaapi.data.*;
import com.daml.ledger.rxjava.components.LedgerViewFlowable;
import com.daml.ledger.rxjava.components.helpers.CommandsAndPendingSet;
import com.daml.ledger.rxjava.components.helpers.CreatedContract;
import com.daml.ledger.rxjava.components.helpers.TemplateUtils;
import com.digitalasset.refapps.ims.bitcoin.BTCService;
import com.digitalasset.refapps.ims.bitcoin.TxRef;
import com.digitalasset.refapps.ims.util.CommandsAndPendingSetBuilder;
import com.digitalasset.refapps.ims.util.Utility;
import com.google.common.collect.Sets;
import io.reactivex.Flowable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.pcollections.PMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import transfer.address.OwnedAddress;
import transfer.transfer.UTXO;
import transfer.transfer.UTXOData;

public class UTXOUpdaterBot {
  public final TransactionFilter transactionFilter;
  private final CommandsAndPendingSetBuilder commandBuilder;
  private static final Logger logger = LoggerFactory.getLogger(UTXOUpdaterBot.class);
  private BTCService btcService;

  public UTXOUpdaterBot(
      CommandsAndPendingSetBuilder.Factory commandBuilderFactory,
      String partyName,
      BTCService btcService) {
    this.btcService = btcService;

    String workflowId = "WORKFLOW-" + partyName + "-UTXOUpdaterBot-" + UUID.randomUUID().toString();
    commandBuilder = commandBuilderFactory.create(partyName, workflowId);

    Filter messageFilter =
        new InclusiveFilter(
            Sets.newHashSet(
                OnboardEntityMaster.TEMPLATE_ID,
                OwnedAddress.TEMPLATE_ID,
                UTXOUpdateRequest.TEMPLATE_ID,
                UTXO.TEMPLATE_ID));

    this.transactionFilter = new FiltersByParty(Collections.singletonMap(partyName, messageFilter));
  }

  public Flowable<CommandsAndPendingSet> process(
      LedgerViewFlowable.LedgerView<Template> ledgerView) {
    CommandsAndPendingSetBuilder.Builder builder = commandBuilder.newBuilder();

    Map<String, UTXOUpdateRequest> requestMap =
        filterTemplates(
            UTXOUpdateRequest.class, ledgerView.getContracts(UTXOUpdateRequest.TEMPLATE_ID));
    if (requestMap.isEmpty()) {
      return Flowable.empty();
    }
    Map<String, OnboardEntityMaster> operatorMap =
        filterTemplates(
            OnboardEntityMaster.class, ledgerView.getContracts(OnboardEntityMaster.TEMPLATE_ID));
    Map<String, OwnedAddress> ownedAddressMap =
        filterTemplates(OwnedAddress.class, ledgerView.getContracts(OwnedAddress.TEMPLATE_ID));
    Map<String, UTXO> utxoMap =
        filterTemplates(UTXO.class, ledgerView.getContracts(UTXO.TEMPLATE_ID));

    Set<UTXOData> oldTxHashes = getExistingHashes(utxoMap);
    Set<UTXOData> newTxHashes = new HashSet<>();
    OnboardEntityMaster.ContractId operatorCid =
        new OnboardEntityMaster.ContractId(operatorMap.entrySet().iterator().next().getKey());

    for (PMap.Entry<String, OwnedAddress> kv : ownedAddressMap.entrySet()) {
      OwnedAddress ownedAddress = kv.getValue();
      OwnedAddress.ContractId ownedAddressCid = new OwnedAddress.ContractId(kv.getKey());

      btcService
          .getTxDetailsForAddress(Utility.unpack(ownedAddress.address))
          .ifPresent(
              transactions -> {
                for (TxRef txRef : transactions.getTxRefs()) {
                  UTXOData utxoData = txRef.toUtxoData();
                  newTxHashes.add(utxoData);

                  if (oldTxHashes.contains(utxoData)) {
                    logger.info("{} already exists", format(utxoData));
                  } else {
                    logger.info("Found new {}", format(utxoData));
                    Command command = operatorCid.exerciseRegisterUTXO(utxoData);
                    builder.addCommand(command);
                  }
                }
                long balance = transactions.getBalance();
                long numTx = transactions.getNumTx();
                Command updateAddressBalance =
                    ownedAddressCid.exerciseUpdateBalance(new Satoshi(balance), numTx);
                builder.addCommand(updateAddressBalance);
              });
    }

    // remove utxos that have been spent
    oldTxHashes.removeAll(newTxHashes);

    utxoMap.entrySet().stream()
        .filter(e -> oldTxHashes.contains(e.getValue().utxoData))
        .map(e -> new UTXO.ContractId(e.getKey()).exerciseSpend())
        .forEach(builder::addCommand);

    requestMap.entrySet().stream()
        .map(e -> new UTXOUpdateRequest.ContractId(e.getKey()).exerciseAckUTXOUpdateRequest())
        .forEach(builder::addCommand);

    return builder.buildFlowable();
  }

  public static Function<CreatedContract, Template> getContractInfo =
      TemplateUtils.contractTransformer(
          OnboardEntityMaster.class, OwnedAddress.class, UTXOUpdateRequest.class, UTXO.class);

  private static String format(UTXOData utxoData) {
    return String.format(
        "UTXO with index %d of Tx %s (%d %s)",
        utxoData.outputIdx, utxoData.txHash.unpack, utxoData.value.unpack, utxoData.address.unpack);
  }

  private static Set<UTXOData> getExistingHashes(Map<String, UTXO> utxoMap) {
    Set<UTXOData> existingTxHashes =
        utxoMap.values().stream().map(utxo -> utxo.utxoData).collect(Collectors.toSet());
    logger.info("Existing Hashes: ");
    existingTxHashes.forEach(h -> logger.info(format(h)));
    return existingTxHashes;
  }
}
