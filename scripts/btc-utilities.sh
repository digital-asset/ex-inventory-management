#
# Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#

# To be sourced by bash scripts.

Resources="src/main/resources"
ConfigFile="$Resources/bitcoin-standalone.conf"

get_config_value() {
  local key="$1"
  local configEntry
  configEntry=$(grep "$key" "$ConfigFile")
  echo "${configEntry#$key=}"
}

bitcoin() {
  local request
  request='{"jsonrpc":"1.0","id":"curltext","method":"'"$1"'","params":['"$2"']}'
  local output
  output=$(curl --user "$UserName:$Password" --silent --show-error --data-binary "$request" -H 'content-type:text/plain;' "http://$BitcoinHost:$BitcoinPort/")
  if [[ "$output" =~ ",\"error\":null," ]]
  then
    if [ "$output" != '{"result":null,"error":null,"id":"curltext"}' ]
    then
      echo "$output"
    fi
  else
    echo Bitcoin error:
    echo output="$output"
    echo request="$request"
    return 1
  fi
}

importprivkey() {
  bitcoin importprivkey "\"$1\""
}

generatetoaddress() {
  bitcoin generatetoaddress "$1",\""$2"\"
}

getnewaddress() {
  local result
  result=$(bitcoin getnewaddress)
  local address
  # shellcheck disable=SC2001
  address=$(echo $result | sed 's/^{"result":"\([[:alnum:]]*\)","error":null,.*/\1/')
  echo "$address"
}

getbalance() {
  local result
  result=$(bitcoin getbalance)
  if [[ "$result" =~ {"result":'[0-9.]*',"error":null,"id":"curltext"} ]]
  then
    # shellcheck disable=SC2001
    echo "$result" | sed 's/.*result":\([0-9.]*\),.*/\1/'
    return 0
  else
    return 1
  fi
}

BitcoinHost=localhost
BitcoinPort=$(get_config_value "rpcport")
UserName=$(get_config_value "rpcuser")
Password=$(get_config_value "rpcpassword")
