/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.daml.product.ims.bitcoin;

public class TransactionResponse {
  public Status getStatus() {
    return status;
  }

  public String getResponseMessage() {
    return responseMessage;
  }

  public enum Status {
    TRANSMITTED,
    FAILEDTOTRANSMIT
  };

  private Status status;
  private String responseMessage;

  public TransactionResponse(Status status, String responseMessage) {
    this.status = status;
    this.responseMessage = responseMessage;
  }
}
