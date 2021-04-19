/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.daml.product.ims.bitcoin;

import bitcoin.types.BitcoinAddress;
import bitcoin.types.Satoshi;
import bitcoin.types.ScriptPubKey;
import bitcoin.types.TxHash;
import java.time.Instant;
import java.util.Optional;
import transfer.transfer.UTXOData;

public class TxRef {
  private String address;
  private String txHash;
  private long outputIdx;
  private long blockHeight;
  private long value;
  private Optional<Instant> confirmed;
  private String scriptPubKey;

  public TxRef(
      String address,
      String txHash,
      long outputIdx,
      long blockHeight,
      long value,
      Optional<Instant> confirmed,
      String scriptPubKey) {
    this.address = address;
    this.txHash = txHash;
    this.outputIdx = outputIdx;
    this.blockHeight = blockHeight;
    this.value = value;
    this.confirmed = confirmed;
    this.scriptPubKey = scriptPubKey;
  }

  public String getTxHash() {
    return txHash;
  }

  public long getOutputIdx() {
    return outputIdx;
  }

  public long getValue() {
    return value;
  }

  public UTXOData toUtxoData() {
    return new UTXOData(
        new BitcoinAddress(address),
        new TxHash(txHash),
        blockHeight,
        outputIdx,
        new Satoshi(value),
        confirmed,
        new ScriptPubKey(scriptPubKey));
  }
}
