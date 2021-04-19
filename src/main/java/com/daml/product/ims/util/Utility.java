/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.daml.product.ims.util;

import bitcoin.types.BitcoinAddress;
import bitcoin.types.RawTx;
import bitcoin.types.ScriptPubKey;
import bitcoin.types.TxHash;
import com.daml.ledger.javaapi.data.Value;

public class Utility {

  private static String asString(Value packedValue) {
    return packedValue.asRecord().get().getFields().get(0).getValue().asText().get().getValue();
  }

  public static String unpack(BitcoinAddress address) {
    return asString(address.toValue());
  }

  public static String unpack(RawTx rawTx) {
    return asString(rawTx.toValue());
  }

  public static String unpack(ScriptPubKey scriptPubKey) {
    return asString(scriptPubKey.toValue());
  }

  public static String unpack(TxHash txHash) {
    return asString(txHash.toValue());
  }
}
