/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.refapps.ims;

import static com.digitalasset.refapps.ims.util.TemplateManager.filterTemplates;

import actors.signingparty.SigningPartyRole;
import bitcoin.types.Satoshi;
import com.daml.ledger.javaapi.data.*;
import com.daml.ledger.rxjava.components.LedgerViewFlowable;
import com.daml.ledger.rxjava.components.helpers.CommandsAndPendingSet;
import com.daml.ledger.rxjava.components.helpers.CreatedContract;
import com.daml.ledger.rxjava.components.helpers.TemplateUtils;
import com.digitalasset.refapps.ims.util.CommandsAndPendingSetBuilder;
import com.google.common.collect.Sets;
import io.reactivex.Flowable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import transfer.request.ValidatedTransferRequest;
import transfer.transfer.UTXO;

public class SubmitForSigningBot {
  public final TransactionFilter transactionFilter;
  private final CommandsAndPendingSetBuilder commandBuilder;

  private static final long BTC_FEE = 20000;

  public SubmitForSigningBot(
      CommandsAndPendingSetBuilder.Factory commandBuilderFactory, String partyName) {
    String workflowId =
        "WORKFLOW-" + partyName + "-SubmitForSigningBot-" + UUID.randomUUID().toString();
    this.commandBuilder = commandBuilderFactory.create(partyName, workflowId);
    Filter messageFilter =
        new InclusiveFilter(
            Sets.newHashSet(
                ValidatedTransferRequest.TEMPLATE_ID,
                SigningPartyRole.TEMPLATE_ID,
                UTXO.TEMPLATE_ID));
    this.transactionFilter = new FiltersByParty(Collections.singletonMap(partyName, messageFilter));
  }

  public Flowable<CommandsAndPendingSet> process(
      LedgerViewFlowable.LedgerView<Template> ledgerView) {
    CommandsAndPendingSetBuilder.Builder builder = commandBuilder.newBuilder();

    Map<String, ValidatedTransferRequest> transferRequestMap =
        filterTemplates(
            ValidatedTransferRequest.class,
            ledgerView.getContracts(ValidatedTransferRequest.TEMPLATE_ID));

    Map<String, UTXO> utxoMap =
        filterTemplates(UTXO.class, ledgerView.getContracts(UTXO.TEMPLATE_ID));
    List<UTXO.ContractId> utxoCidsToUse =
        utxoMap.entrySet().stream()
            .map(entry -> new UTXO.ContractId(entry.getKey()))
            .collect(Collectors.toList());

    SigningPartyRole.ContractId signingPartyRoleCid = getSigningParty(ledgerView);

    for (Map.Entry<String, ValidatedTransferRequest> keyValue : transferRequestMap.entrySet()) {
      ValidatedTransferRequest.ContractId transferRequestCid =
          new ValidatedTransferRequest.ContractId(keyValue.getKey());
      Command requestCommand =
          transferRequestCid.exercisePrepareToTransfer(
              signingPartyRoleCid, new Satoshi(BTC_FEE), utxoCidsToUse);
      builder.addCommand(requestCommand);
    }

    return builder.buildFlowable();
  }

  public static Function<CreatedContract, Template> getContractInfo =
      TemplateUtils.contractTransformer(
          ValidatedTransferRequest.class, SigningPartyRole.class, UTXO.class);

  private static SigningPartyRole.ContractId getSigningParty(
      LedgerViewFlowable.LedgerView<Template> ledgerView) {
    Map<String, SigningPartyRole> signingPartyRoleMap =
        filterTemplates(
            SigningPartyRole.class, ledgerView.getContracts(SigningPartyRole.TEMPLATE_ID));
    String signingPartyRoleKey = signingPartyRoleMap.entrySet().iterator().next().getKey();
    return new SigningPartyRole.ContractId(signingPartyRoleKey);
  }
}
