/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.daml.product.ims.bitcoin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class BitcoinClient {

  private static final BigDecimal BTC_SATOSHI = BigDecimal.valueOf(100_000_000L);
  private String bitcoinUrl;
  private String authInfo;

  public BitcoinClient(String bitcoinUrl, String userName, String password) {
    this.bitcoinUrl = bitcoinUrl;
    this.authInfo = createAuthInfo(userName, password);
  }

  private String createAuthInfo(String userName, String password) {
    return String.format(
        "Basic %s",
        Base64.getEncoder()
            .encodeToString(
                String.format("%s:%s", userName, password).getBytes(StandardCharsets.UTF_8)));
  }

  // https://bitcoin.org/en/glossary/watch-only-address
  // imported address does not influence wallet balance
  public void importAddress(String address) {
    queryResult("importaddress", address, "", true, false);
  }

  public long getReceivedByAddress(int minConfirmation, String address) {
    double response = queryResult("getreceivedbyaddress", address, minConfirmation);
    return btcToSatoshi(BigDecimal.valueOf(response));
  }

  public void generate(int blockNumber, String address) {
    queryResult("generatetoaddress", blockNumber, address);
  }

  public Iterable<TxRef> listUTXOs(int minConfirmation, String address) {
    JSONArray result =
        queryResult(
            "listunspent",
            minConfirmation,
            999999,
            Collections.singletonList(
                address)); // TODO(demian) maxConfirmation should depend on block height to skip
    // processed UTXOs
    Stream<JSONObject> transactions = result.stream();
    return transactions.map(x -> toTxRef(address, x, minConfirmation)).collect(Collectors.toList());
  }

  private TxRef toTxRef(String address, JSONObject x, int minConfirmation) {
    long outputIdx = (Long) x.get("vout");
    long value = btcToSatoshi(BigDecimal.valueOf((Double) x.get("amount")));
    String scriptPubKey = (String) x.get("scriptPubKey");
    String txid = (String) x.get("txid");

    JSONObject transactionDetail = queryResult("gettransaction", txid);
    String blockHash = (String) transactionDetail.get("blockhash");
    JSONObject block = queryResult("getblockheader", blockHash);

    long blockHeight = (Long) block.get("height");

    String confirmationBlockHash = queryResult("getblockhash", blockHeight + minConfirmation);
    Optional<Instant> confirmed;
    if (confirmationBlockHash == null) {
      confirmed = Optional.empty();
    } else {
      JSONObject confirmationBlock = queryResult("getblockheader", confirmationBlockHash);
      confirmed = Optional.of(Instant.ofEpochMilli((Long) confirmationBlock.get("time")));
    }

    return new TxRef(address, txid, outputIdx, blockHeight, value, confirmed, scriptPubKey);
  }

  private long btcToSatoshi(BigDecimal amount) {
    return amount.multiply(BTC_SATOSHI).longValueExact();
  }

  public Object sendRawTransaction(String rawTx) {
    return query("sendrawtransaction", rawTx);
  }

  private <T> T queryResult(String method, Object... params) {
    return (T) query(method, params).get("result");
  }

  private JSONObject query(String method, Object... params) {
    HttpResponse response;
    try {
      Request request =
          Request.Post(bitcoinUrl)
              .addHeader("Authorization", authInfo)
              .addHeader("Content-Type", "text/plain")
              .bodyString(createJsonBody(method, params), ContentType.APPLICATION_JSON);
      response = request.execute().returnResponse();
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage(), e.getCause());
    }
    return (JSONObject) parse(response);
  }

  private Object parse(HttpResponse httpResponse) {
    try {
      return new JSONParser().parse(new InputStreamReader(httpResponse.getEntity().getContent()));
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e.getCause());
    }
  }

  private String createJsonBody(String method, Object... params) {
    Map<String, Object> parts =
        new HashMap<String, Object>() {
          {
            this.put("jsonrpc", "1.0");
            this.put("id", "CNS-RefApp");
            this.put("method", method);
            this.put("params", Arrays.asList(params));
          }
        };
    return JSONObject.toJSONString(parts);
  }
}
