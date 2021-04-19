/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.daml.product.ims.bitcoin;

import java.util.Optional;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Wrapped BitcoinClient. BitcoinClient has access to a running Bitcoin network.
public class BTCService {
  private static final Logger logger = LoggerFactory.getLogger(BTCService.class);

  private BitcoinClient bitcoinClient;

  public BTCService(BitcoinClient bitcoinClient) {
    this.bitcoinClient = bitcoinClient;
  }

  public TransactionResponse pushTransaction(String rawTx) {
    JSONObject response = (JSONObject) bitcoinClient.sendRawTransaction(rawTx);
    JSONObject error = (JSONObject) response.get("error");
    if (error != null) {
      String errorMessage = (String) error.get("message");
      logger.warn(
          "Something went wrong while pushing TXs! HttpResponseCode: "
              + ". Response Message: "
              + errorMessage);
      return new TransactionResponse(TransactionResponse.Status.FAILEDTOTRANSMIT, errorMessage);
    } else {
      String txHash = (String) response.get("result");
      logger.debug("The transaction hash is " + txHash);
      String messageStringBuilder = "Transmitted Tx Hash: " + txHash + "\n";
      return new TransactionResponse(TransactionResponse.Status.TRANSMITTED, messageStringBuilder);
    }
  }

  public Optional<Transactions> getTxDetailsForAddress(String address) {
    Iterable<TxRef> txRefs = bitcoinClient.listUTXOs(1, address);
    long balance = 0;
    long numTx = 0;
    for (TxRef x : txRefs) {
      balance += x.getValue();
      numTx++;
    }
    return Optional.of(new Transactions(txRefs, balance, numTx));
  }
}
