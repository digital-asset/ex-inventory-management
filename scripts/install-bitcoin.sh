#!/usr/bin/env bash
#
# Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#

BitcoinVersion="$1"
BitcoinUrl="https://bitcoin.org/bin/bitcoin-core-$BitcoinVersion/bitcoin-$BitcoinVersion"
Binaries="${HOME:?}/.bitcoin"
BinariesTar="/var/tmp/bitcoin_binaries.tar.gz"

set -e

download_url() {
  case $OSTYPE in
  darwin*)
    echo "$BitcoinUrl-osx64.tar.gz"
    ;;
  linux*)
    local os_type
    os_type=$(uname -m)
    case "$os_type" in
    x86_64) echo "$BitcoinUrl-x86_64-linux-gnu.tar.gz" ;;
    *) echo "$BitcoinUrl-i686-pc-linux-gnu.tar.gz" ;;
    esac
    ;;
  *)
    echo "Unsupported OS detected: $OSTYPE."
    exit 1
    ;;
  esac
}

remove_binary_archive() {
  echo "Removing $BinariesTar ..."
  rm -f "$BinariesTar"
  echo "OK"
}

download_binaries() {
  local url
  url=$(download_url)
  echo "Downloading $url ..."
  curl --silent --show-error "$(download_url)" -o $BinariesTar
  echo "OK"
}

unarchive_binaries() {
  echo "Extracting CLI tools into $Binaries ..."
  rm -rf "$Binaries"
  mkdir "$Binaries"
  tar -xzf $BinariesTar -C "$Binaries"
  echo "OK"
}

is_installed() {
  if [ "$(command -v "$1")" != "" ]; then
    echo "true"
  else
    echo "false"
  fi
}

if [ "$(is_installed "bitcoind")" == "true" ] && [ "$(is_installed "bitcoin-cli")" == "true" ]; then
  echo "Bitcoin CLI tools are already installed."
  exit 0
else
  echo "Installing bitcoin CLI tools..."
  remove_binary_archive
  download_binaries
  unarchive_binaries
  remove_binary_archive
  echo "CLI tools are installed. Please, add $Binaries/bitcoin-$BitcoinVersion/bin to your PATH."
fi
