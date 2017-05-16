#!/usr/bin/env bash

aws dynamodb list-tables | jq '.[][]' | grep dev-kixi\.heimdall | xargs -n1 aws dynamodb delete-table --table-name
