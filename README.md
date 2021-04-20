# Reference Application: Inventory Management System

## Overview

Crypto Inventory Management System (IMS) has been designed to make it easy and safe for crypto funds to manage their crypto assets on public ledgers (such as the Bitcoin network). It is one component of a potential future crypto-trading ecosystem augmented with custom compliance rules.

### Features

- **Automated DAML workflow** It has multiple configurable checkpoints, including transfer limits, whitelisted addresses, and blacklisted addresses.
- **Segregation of Signing Party** The Signing Party is a designated role to keep private keys and sign transfers safely. No other party in the workflow has access to private keys. The only processes with access to private keys are bots acting on behalf of the Signing Party. In a production environment, they could run on a separate server with no direct access to the internet or even be delegated to an agent outside the trading house. Private keys are kept in the file system to keep it simple, however, they could also be guarded with more sophisticated mechanisms. Managing the Signing Party and private keys for a trading house is a core business value proposition for a crypto custodian business.

## Getting Started

### Installing

**Disclaimer:** This reference application is intended to demonstrate the capabilities of the DAML. Consider other non-functional aspects, like security, resiliency, recoverability, etc. prior to production use.

#### Prerequisites
Be sure you have the following installed:
- [DAML SDK][DAML-URL]
- Java
- Maven
- [Bitcoin Core][BTC-URL]

### Starting the App

1. Build the App. Type:
    ```shell
    mvn clean package
    ```
    **Note:** If you change the DAML models locally, you need to re-run this command before starting the application.


2. Use **separate terminals** to launch the individual components:

    ```shell
    launchers/sandbox+navigator+populate
    launchers/automation
    launchers/bitcoind
    ```

    The navigator will automatically open in new browser tab at http://localhost:7500.
    **Note:** The bitcoin network is started completely clean and empty for each execution.

### Stopping the App

1. Stop the every running command by pressing **Ctrl+C**.

## User Guide

This User Guide will take you step-by-step through the process of requesting and transmitting bitcoin transfer. It will lead you through all the major Navigator screens and data fields that you will need to use.

After working through these steps, you can use the Navigator interface to explore other functionality that is available in the application.

**Note**: This demo is designed to show successful conclusion of the IMS workflow without exceptions or error conditions. A full production implementation would include additional features, handle errors and exceptions, and incorporate appropriate security controls.

## Workflow

### Roles and Responsibilities
Participants in the following roles are involved in the inventory management workflow.

|    Role    | Responsibilities                                                                                                                                                                                                                               |
| :--------: | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
|  Operator  | Represents the trading house. It can see all contracts on the ledger.<br>The Operator can kick off the UTXO updating process that scans the public crypto network for inventories on a given list of public addresses. The Operator is responsible for the limit and address checks and the creation of the transfer on the crypto network.<br>The following bots act on behalf of the Operator: `UTXOUpdaterBot`, `RawTxPusherBot`, `TransferRequestValidatorBot`, `SubmitForSigningBot`. |
|   Trader   | Can request a new transfer and also see the destination addresses with their reputation. |
| Compliance | Can override a requested transfer to a bad address. Can also change the address reputation for known addresses. |
|  DeskHead  | Can override a requested transfer over the desk limit and also change transfer limit. |
| SigningParty |Can see all owned address with their balance and also manipulate owned addresses. SigningParty is the only party who possesses the secret keys of the own addresses, thus the name "Signing".<br>`TransactionSignerBot` and `OwnedAddressRegistrarBot` act on behalf of the SigningParty.  |

`UTXOUpdaterBot` and `RawTxPusherBot` are essential for syncing the bitcoin network with the ledger. `TransactionSignerBot` also performs a bitcoin-specific operation by generating the transaction binaries. As you can see below, `OwnedAddressRegistrarBot`, `TransferRequestValidatorBot` and `SubmitForSigningBot` are just trying to streamline the workflow by impersonating the Operator at some point.

## Running the Application

### Setting Up the Organization

A simple organization structure (with addresses) is set up in the **onboarding** scenario of `Test.daml`. This scenario is executed during both the integration test and the application startup. The `OwnedAddressRegistrarBot` takes part only in the initialization by registering the owned addresses of `src/main/bitcoin_data.regtest.md` on behalf of the **SigningParty**. We could also build up the structure using a Navigator or other clients using the Ledger API. However, this User Guide focuses only on the primary functions of an operating organization.  

### Setting Up Bitcoin Network

If you are on Unix and want some values in the Bitcoin regtest network, run:
```shell
scripts/setup-bitcoin.sh
```
The script performs a local mining to fill up one of the owned addresses located in `src/main/bitcoin_data.regtest.md` with 5000 BTC. The first transaction in a block corresponding to the miner is the so-called coinbase transaction. According to the bitcoin protocol, UTXOs coming from coinbase transactions will be available for spending only after 100 confirmations. Beside the protocol, the bitcoin client itself also tries to prevent double spending by excluding consumed UTXOs from the balance.

**Note**: on Windows you can install [Bitcoin Core][BTC-URL], and after that, you should be able to follow the steps in the script.

### Requesting an UTXO Update
The operator triggers the `UTXOUpdaterBot` to fetch UTXOs belonging to owned addresses (`src/main/bitcoin_data.regtest.md`) from the bitcoin network and populate them onto the ledger. In a production scenario, this task would be automated and run on a regular basis. 

1. Login as **Operator**.
2. Choose the **Operator** tab.
3. Open the **Operator** role contract.
4. Select the choice **RequestUTXOUpdate**.
5. Check the populated UTXOs on the UTXO tab. 

### Creating a Transfer Request
The trader has to select an external address and request a transfer.

1. Login as **Trader1**.
2. Choose the **Destination Addresses** tab.
3. Open an arbitrary **Bitcoin Address** contract with good reputation.
4. Copy the bitcoin address to the clipboard.
5. Choose the **Trader** tab.
6. Open the **Trader** role contract.
7. Select the choice **RequestTransfer** then fill the address field using the clipboard content and specify an amount in Satoshi to be transferred.

### Checking Transfer Limits and Address Reputations
`TransferRequestValidatorBot` will start the validation over **UncheckedTransferRequest** contracts.
**DeskHead** can prescribe a daily limit for their traders. In our scenario, it is set to 1 BTC = 100 000 000 Satoshi.
**ComplianceOfficer** can register new destination addresses augmented with a reputation field. Reputation can be modified during the operations (see the **BlacklistAddress** and **WhitelistAddress** choices). The address check fails if the Trader specified a target address with bad reputation.

The happy path would result in a **ValidatedTransferRequest** contract.
Overriding failed checks requires manual intervention.

#### Overriding Failed Limit Checks 

1. Login as **DeskHead**.
2. Choose the **Requests Over Limit** tab.
3. Open the appropriate **FailedLimitsCheckTransferRequest** contract.
4. Copy the contractId (upper left corner) to the clipboard.<br>(The **OverrideFailedLimits** choice cannot be executed by a single party.)
5. Choose the **Desk Head** tab.
6. Select the **DeskHead** role contract.
7. Select the choice **ApproveRequestOverLimits** and then fill the failedRequestCid field using the clipboard content and specify a reason. (Keep in mind that the transfer could fail on the address check as well.)

#### Overriding Failed Address Checks 

1. Login as **ComplianceOfficer**.
2. Choose the **Requests to Bad Addresses** tab. 
3. Open the appropriate **FailedAddressCheckTransferRequest** contract.
4. Copy the contractId (upper left corner) to the clipboard.<br>(The **OverrideFailedAddressCheckTransferRequest** choice can not be executed by a single party.)
5. Choose the **Compliance Officer** tab.
6. Select the **ComplianceOfficer** role contract.
7. Select the choice **ApproveRequestToBadAddress** and then fill the failedRequestCid field using the clipboard content and specify a reason.

### Creating the Transfer

The `SubmitForSigningBot` picks up the **ValidatedTransferRequest** contract, collects all the owned UTXOs, and tries to create a **NewTransfer** on behalf of the **Operator**. 

The choice could also result in a **InsufficientTransferRequest** in case of insufficient funds. **Operator** can convert it back to **UncheckedTransferRequest** by selecting the **Retry** choice on it.

### Signing the Transfer

The `TransactionSignerBot` picks up the **NewTransfer** contract and signs it on behalf of the **SigningParty**.

The choice will select the necessary number of UTXOs and create the **SignedTransfer** with the binary transaction data included (e.g., the digital signatures).

### Confirming the Transfer

1. Login as **Trader1**.
2. Choose the **Signed Transfers** tab.
3. Select the appropriate **SignedTransfer** contract.
4. Select the choice **Confirm**.

The newly generated **PendingTransfer** contract triggers the `RawTxPusherBot` which submits the transfer to the bitcoin network and populates the response onto the ledger. You can check the status of the submission as follows:
1. Login as **Trader1**.
2. Look up the appropriate contract in the **Transmitted Transfers** tab or in the **Failed Transfers** tab.

### Confirmations

This application launches a bitcoin network in [regtest mode](https://bitcoin.org/en/developer-examples#regtest-mode) which is a developer mode allowing full control over block generation.
To generate 100 confirming blocks over your transaction in the bitcoin network, type: 
```shell
scripts/mine-bitcoin.sh
```
In testnet or mainnet modes, you could just wait about an hour to reach 6 confirmations. Now, if you repeat the [UTXO Update](#requesting-an-utxo-update) step, you will see the changes in the UTXO level as well. This is the final step of the main workflow.

## The Risk Factors in Crypto Trading

The IMS addresses some of the risks of crypto trading, making it safer for institutional investors to enter the crypto market. Crypto trading faces risks similar to traditional asset trading, but it is more challenging for several reasons:

- The current market infrastructure is still at its infancy. Lack of regulation and transparency exacerbates credit risks and counterparty risks.
- Ownership of a crypto asset is solely defined by access to the private key. This means hacking, embezzlement, or asset loss due to key loss are more plausible than with a traditional asset.
- The counterparty's identity is obfuscated. This increases counterparty risks and risk of collusion for embezzlement.

## Switching between Bitcoin Network Modes
This application launches a bitcoin network in [regtest mode](https://bitcoin.org/en/developer-examples#regtest-mode) which is a developer mode allowing full control over block generation. Switching to other modes (testnet, mainnet) or pushing the application to a production scenario would require additional work:

- The bitcoin client would require a totally different configuration file.
- The lack of manual block generation would ruin the following steps: [Bitcoin setup](#setting-up-bitcoin-network), [bitcoin confirmation](#confirmations). You should mine or get coins from real users to increase your balance and wait for confirmations.
- Private keys are available in `src/main/resources/bitcoin_data.regtest.md`. The configuration files `src/main/resources/bitcoin-*.conf` also store the `rpcpassword` in plaintext. This should be hardened considerably before going to public.

CONFIDENTIAL © 2019 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
Any unauthorized use, duplication or distribution is strictly prohibited.

[DAML-URL]: https://docs.daml.com/
[BTC-URL]: https://bitcoin.org/en/bitcoin-core/
