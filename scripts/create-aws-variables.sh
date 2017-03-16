#!/usr/bin/env bash

DEFAULT_PROFILE=default
DEFAULT_OUTPUT=aws-variables.env

echo -n "AWS profile to read [${DEFAULT_PROFILE}]: "
read profile

echo -n "Output file [${DEFAULT_OUTPUT}]: "
read output

PROFILE=${profile:-$DEFAULT_PROFILE}
OUTPUT=${output:-$DEFAULT_OUTPUT}

cat ~/.aws/credentials | grep -noc "$PROFILE" | xargs echo "2 +" | bc | xargs head ~/.aws/credentials -n | tail -n 2 | awk '{print $NF}' | xargs printf 'AWS_ACCESS_KEY_ID=%s\nAWS_SECRET_ACCESS_KEY=%s' > $OUTPUT
