/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.refapps.ims;

import static com.digitalasset.refapps.ims.util.TemplateManager.filterTemplates;

import com.daml.ledger.javaapi.data.*;
import com.daml.ledger.rxjava.components.LedgerViewFlowable;
import com.daml.ledger.rxjava.components.helpers.CommandsAndPendingSet;
import com.daml.ledger.rxjava.components.helpers.CreatedContract;
import com.daml.ledger.rxjava.components.helpers.TemplateUtils;
import com.digitalasset.refapps.ims.bitcoin.BTCService;
import com.digitalasset.refapps.ims.bitcoin.TransactionResponse;
import com.digitalasset.refapps.ims.util.CommandsAndPendingSetBuilder;
import com.digitalasset.refapps.ims.util.Utility;
import com.google.common.collect.Sets;
import io.reactivex.Flowable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import transfer.transfer.PendingTransfer;

public class RawTxPusherBot {
  public final TransactionFilter transactionFilter;
  private final CommandsAndPendingSetBuilder commandBuilder;
  private BTCService btcService;

  public RawTxPusherBot(
      CommandsAndPendingSetBuilder.Factory commandBuilderFactory,
      String partyName,
      BTCService btcService) {
    this.btcService = btcService;
    String workflowId = "WORKFLOW-" + partyName + "-RawTxPusherBot-" + UUID.randomUUID().toString();
    commandBuilder = commandBuilderFactory.create(partyName, workflowId);
    Filter messageFilter = new InclusiveFilter(Sets.newHashSet(PendingTransfer.TEMPLATE_ID));
    this.transactionFilter = new FiltersByParty(Collections.singletonMap(partyName, messageFilter));
  }

  public Flowable<CommandsAndPendingSet> process(
      LedgerViewFlowable.LedgerView<Template> ledgerView) {
    CommandsAndPendingSetBuilder.Builder builder = commandBuilder.newBuilder();

    Map<String, PendingTransfer> PendingTransferMap =
        filterTemplates(
            PendingTransfer.class, ledgerView.getContracts(PendingTransfer.TEMPLATE_ID));

    List<Map.Entry<String, PendingTransfer>> toSendTxs =
        PendingTransferMap.entrySet().stream().collect(Collectors.toList());

    for (Map.Entry<String, PendingTransfer> tx : toSendTxs) {
      PendingTransfer.ContractId pendingTransferCid = new PendingTransfer.ContractId(tx.getKey());
      PendingTransfer signedTx = tx.getValue();
      TransactionResponse response;
      response = btcService.pushTransaction(Utility.unpack(signedTx.rawTx));
      Command command =
          response.getStatus() == TransactionResponse.Status.FAILEDTOTRANSMIT
              ? pendingTransferCid.exerciseFail(response.getResponseMessage())
              : pendingTransferCid.exerciseTransmit(response.getResponseMessage());
      builder.addCommand(command);
    }

    return builder.buildFlowable();
  }

  public static Function<CreatedContract, Template> getContractInfo =
      TemplateUtils.contractTransformer(PendingTransfer.class);
}
