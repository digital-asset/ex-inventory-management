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
import com.digitalasset.refapps.ims.util.CommandsAndPendingSetBuilder;
import com.google.common.collect.Sets;
import io.reactivex.Flowable;
import java.util.*;
import java.util.function.Function;
import transfer.request.UncheckedTransferRequest;

public class TransferRequestValidatorBot {
  public final TransactionFilter transactionFilter;
  private final CommandsAndPendingSetBuilder commandBuilder;

  public TransferRequestValidatorBot(
      CommandsAndPendingSetBuilder.Factory commandBuilderFactory, String partyName) {
    String workflowId = "WORKFLOW-" + partyName + "-CheckLimitsBot-" + UUID.randomUUID().toString();
    commandBuilder = commandBuilderFactory.create(partyName, workflowId);

    Filter messageFilter =
        new InclusiveFilter(Sets.newHashSet(UncheckedTransferRequest.TEMPLATE_ID));

    this.transactionFilter = new FiltersByParty(Collections.singletonMap(partyName, messageFilter));
  }

  public Flowable<CommandsAndPendingSet> process(
      LedgerViewFlowable.LedgerView<Template> ledgerView) {
    CommandsAndPendingSetBuilder.Builder builder = commandBuilder.newBuilder();

    Map<String, UncheckedTransferRequest> transferRequestMap =
        filterTemplates(
            UncheckedTransferRequest.class,
            ledgerView.getContracts(UncheckedTransferRequest.TEMPLATE_ID));

    transferRequestMap.keySet().stream()
        .map(UncheckedTransferRequest.ContractId::new)
        .map(UncheckedTransferRequest.ContractId::exerciseValidateRequest)
        .forEach(builder::addCommand);

    return builder.buildFlowable();
  }

  public static Function<CreatedContract, Template> getContractInfo =
      TemplateUtils.contractTransformer(UncheckedTransferRequest.class);
}
