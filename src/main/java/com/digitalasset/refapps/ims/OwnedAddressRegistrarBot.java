/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.refapps.ims;

import static com.digitalasset.refapps.ims.util.TemplateManager.filterTemplates;

import actors.signingparty.SigningPartyRole;
import bitcoin.types.BitcoinAddress;
import com.daml.ledger.javaapi.data.*;
import com.daml.ledger.rxjava.components.LedgerViewFlowable;
import com.daml.ledger.rxjava.components.helpers.CommandsAndPendingSet;
import com.daml.ledger.rxjava.components.helpers.CreatedContract;
import com.daml.ledger.rxjava.components.helpers.TemplateUtils;
import com.digitalasset.refapps.ims.util.CommandsAndPendingSetBuilder;
import com.google.common.collect.Sets;
import fr.acinq.bitcoin.Crypto;
import io.reactivex.Flowable;
import java.util.*;
import java.util.function.Function;
import org.pcollections.PMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import transfer.address.OwnedAddress;

public class OwnedAddressRegistrarBot {
  public final TransactionFilter transactionFilter;
  private final CommandsAndPendingSetBuilder commandBuilder;
  private static final Logger logger = LoggerFactory.getLogger(OwnedAddressRegistrarBot.class);

  private Map<BitcoinAddress, Crypto.PrivateKey> addressToPrivateKeyMap;
  private boolean isFirstProcess;

  public OwnedAddressRegistrarBot(
      CommandsAndPendingSetBuilder.Factory commandBuilderFactory,
      String partyName,
      Map<BitcoinAddress, Crypto.PrivateKey> privateKeyMap) {

    String workflowId =
        "WORKFLOW-" + partyName + "-OwnedAddressRegistrarBot-" + UUID.randomUUID().toString();
    commandBuilder = commandBuilderFactory.create(partyName, workflowId);

    Filter messageFilter = new InclusiveFilter(Sets.newHashSet(SigningPartyRole.TEMPLATE_ID));

    this.transactionFilter = new FiltersByParty(Collections.singletonMap(partyName, messageFilter));
    this.addressToPrivateKeyMap = privateKeyMap;
    this.isFirstProcess = true;
  }

  public Flowable<CommandsAndPendingSet> process(
      LedgerViewFlowable.LedgerView<Template> ledgerView) {
    CommandsAndPendingSetBuilder.Builder builder = commandBuilder.newBuilder();

    // create own public addressByte contracts
    if (this.isFirstProcess) {
      Map<String, SigningPartyRole> signingPartyRoleMap =
          filterTemplates(
              SigningPartyRole.class, ledgerView.getContracts(SigningPartyRole.TEMPLATE_ID));
      logger.debug(String.format("found %d signingPartyContracts", signingPartyRoleMap.size()));
      PMap.Entry<String, SigningPartyRole> kv = signingPartyRoleMap.entrySet().iterator().next();
      SigningPartyRole.ContractId signingPartyCid = new SigningPartyRole.ContractId(kv.getKey());
      for (BitcoinAddress address : this.addressToPrivateKeyMap.keySet()) {
        Command command = signingPartyCid.exerciseRegisterOwnedAddress(address);
        builder.addCommand(command);
      }
      this.isFirstProcess = false;
    }

    return builder.buildFlowable();
  }

  public static Function<CreatedContract, Template> getContractInfo =
      TemplateUtils.contractTransformer(SigningPartyRole.class, OwnedAddress.class);
}
