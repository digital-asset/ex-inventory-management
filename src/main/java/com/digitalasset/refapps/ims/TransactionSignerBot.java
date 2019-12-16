/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.refapps.ims;

import static com.digitalasset.refapps.ims.util.TemplateManager.filterTemplates;

import actors.signingparty.SigningPartyRole;
import bitcoin.types.BitcoinAddress;
import bitcoin.types.RawTx;
import com.daml.ledger.javaapi.data.*;
import com.daml.ledger.rxjava.components.LedgerViewFlowable;
import com.daml.ledger.rxjava.components.helpers.CommandsAndPendingSet;
import com.daml.ledger.rxjava.components.helpers.CreatedContract;
import com.daml.ledger.rxjava.components.helpers.TemplateUtils;
import com.digitalasset.refapps.ims.bitcoin.BTCUtility;
import com.digitalasset.refapps.ims.util.CommandsAndPendingSetBuilder;
import com.google.common.collect.Sets;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.Satoshi;
import io.reactivex.Flowable;
import java.util.*;
import java.util.function.Function;
import transfer.address.OwnedAddress;
import transfer.transfer.NewTransfer;

public class TransactionSignerBot {
  public final TransactionFilter transactionFilter;
  private final CommandsAndPendingSetBuilder commandBuilder;

  private Map<BitcoinAddress, Crypto.PrivateKey> addressToPrivateKeyMap;

  public TransactionSignerBot(
      CommandsAndPendingSetBuilder.Factory commandBuilderFactory,
      String partyName,
      Map<BitcoinAddress, Crypto.PrivateKey> privateKeyMap) {

    String workflowId =
        "WORKFLOW-" + partyName + "-TransactionCreatorBot-" + UUID.randomUUID().toString();
    commandBuilder = commandBuilderFactory.create(partyName, workflowId);

    Filter messageFilter =
        new InclusiveFilter(
            Sets.newHashSet(
                SigningPartyRole.TEMPLATE_ID, OwnedAddress.TEMPLATE_ID, NewTransfer.TEMPLATE_ID));

    this.transactionFilter = new FiltersByParty(Collections.singletonMap(partyName, messageFilter));
    this.addressToPrivateKeyMap = privateKeyMap;
  }

  public Flowable<CommandsAndPendingSet> process(
      LedgerViewFlowable.LedgerView<Template> ledgerView) {
    CommandsAndPendingSetBuilder.Builder builder = commandBuilder.newBuilder();

    Map<String, NewTransfer> transferMap =
        filterTemplates(NewTransfer.class, ledgerView.getContracts(NewTransfer.TEMPLATE_ID));
    Map<String, OwnedAddress> ownedAddressMap =
        filterTemplates(OwnedAddress.class, ledgerView.getContracts(OwnedAddress.TEMPLATE_ID));

    for (Map.Entry<String, NewTransfer> entry : transferMap.entrySet()) {
      NewTransfer.ContractId newTransferCid = new NewTransfer.ContractId(entry.getKey());
      NewTransfer newTransfer = entry.getValue();

      // find a change address
      BitcoinAddress ownedAddressForChange =
          ownedAddressMap.entrySet().stream()
              .filter(ownedEntry -> ownedEntry.getValue().balance.unpack == 0)
              .findFirst()
              .get()
              .getValue()
              .address;

      RawTx rawTx =
          BTCUtility.createTransactionData(
              newTransfer.txInputs,
              newTransfer.transferDetails.address,
              new Satoshi(newTransfer.transferDetails.amount.unpack),
              new Satoshi(newTransfer.fee.unpack),
              addressToPrivateKeyMap,
              ownedAddressForChange);

      builder.addCommand(newTransferCid.exerciseSignTransfer(rawTx));
    }

    return builder.buildFlowable();
  }

  public static Function<CreatedContract, Template> getContractInfo =
      TemplateUtils.contractTransformer(
          NewTransfer.class, SigningPartyRole.class, OwnedAddress.class);
}
