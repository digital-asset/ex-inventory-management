/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.daml.product.ims.bitcoin;

public class Transactions {
  private Iterable<TxRef> txRefs;
  private long balance;
  private long numTx;

  public Transactions(Iterable<TxRef> txRefs, long balance, long numTx) {
    this.txRefs = txRefs;
    this.balance = balance;
    this.numTx = numTx;
  }

  public Iterable<TxRef> getTxRefs() {
    return txRefs;
  }

  public long getBalance() {
    return balance;
  }

  public long getNumTx() {
    return numTx;
  }
}
