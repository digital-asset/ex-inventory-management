/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.daml.product.ims.bitcoin;

import bitcoin.types.BitcoinAddress;
import bitcoin.types.RawTx;
import com.daml.product.ims.util.Utility;
import fr.acinq.bitcoin.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import transfer.transfer.UTXOData;

public class BTCUtility {
  private static final long TX_SEQUENCE = 0xFFFFFFFFL;

  public static Map<BitcoinAddress, Crypto.PrivateKey> loadPrivateKeysFromFile(String keysFile)
      throws IOException {
    final byte prefixByte = Base58.Prefix$.MODULE$.SecretKeyTestnet();
    final byte addressByte = Base58.Prefix$.MODULE$.PubkeyAddressTestnet();
    return Files.readAllLines(Paths.get(keysFile)).stream()
        .map(key -> Crypto.PrivateKey$.MODULE$.fromBase58(key, prefixByte))
        .collect(
            Collectors.toMap(
                privateKey ->
                    new BitcoinAddress(
                        Base58Check.encode(addressByte, privateKey.publicKey().hash160().data())),
                privateKey -> privateKey));
  }

  public static RawTx createTransactionData(
      Collection<UTXOData> txInputs,
      BitcoinAddress destinationAddress,
      Satoshi amount,
      Satoshi fee,
      Map<BitcoinAddress, Crypto.PrivateKey> addressToPrivateKey,
      BitcoinAddress changeAddress) {

    // sending $

    final long sendingSatoshi =
        txInputs.stream().mapToLong(utxoData -> utxoData.value.unpack).sum();
    SendingData sendingData = getSendingData(txInputs, addressToPrivateKey);
    List<TxIn> txIns = sendingData.txIns;
    List<SignData> signData = sendingData.signData;

    // receiving $

    BinaryData pubKeyScriptBinData = toScript(destinationAddress);
    List<TxOut> txOut = new ArrayList<>();
    txOut.add(new TxOut(amount, pubKeyScriptBinData));

    // change Address

    final long change = sendingSatoshi - amount.amount() - fee.amount();
    if (change > 0) {
      BinaryData changeScriptBinData = toScript(changeAddress);
      txOut.add(new TxOut(new Satoshi(change), changeScriptBinData));
    }

    // transaction and signing

    Transaction unsignedTransaction = new Transaction(1L, toScalaSeq(txIns), toScalaSeq(txOut), 0L);
    Transaction signedTransaction = Transaction.sign(unsignedTransaction, toScalaSeq(signData));
    return new RawTx(signedTransaction.bin().toString());
  }

  private static class SendingData {
    public SendingData(List<TxIn> txIns, List<SignData> signData) {
      this.txIns = txIns;
      this.signData = signData;
    }

    List<TxIn> txIns;
    List<SignData> signData;
  }

  private static SendingData getSendingData(
      Collection<UTXOData> txInputs, Map<BitcoinAddress, Crypto.PrivateKey> addressToPrivateKey) {
    List<TxIn> txIns = new ArrayList<>();
    List<SignData> signData = new ArrayList<>();
    for (UTXOData utxoData : txInputs) {
      BinaryData txHashBinData = BinaryData.apply(Utility.unpack(utxoData.txHash));
      BinaryData txHashBinDataReversed = BinaryData.apply(txHashBinData.data().reverse());
      OutPoint outPoint = new OutPoint(txHashBinDataReversed, utxoData.outputIdx);
      BinaryData prevOutputScriptBinData = BinaryData.apply(Utility.unpack(utxoData.sigScript));
      txIns.add(new TxIn(outPoint, prevOutputScriptBinData, TX_SEQUENCE, ScriptWitness.empty()));
      signData.add(
          new SignData(prevOutputScriptBinData, addressToPrivateKey.get(utxoData.address)));
    }
    return new SendingData(txIns, signData);
  }

  private static BinaryData toScript(BitcoinAddress address) {
    BinaryData addressBinaryData = Base58Check.decode(Utility.unpack(address))._2;
    return Script.write(Script.pay2pkh(addressBinaryData));
  }

  private static <T> Seq<T> toScalaSeq(List<T> list) {
    return JavaConverters.asScalaIteratorConverter(list.iterator()).asScala().toSeq();
  }
}
