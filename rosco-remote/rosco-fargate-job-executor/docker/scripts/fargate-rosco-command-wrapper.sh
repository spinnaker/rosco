#!/usr/bin/env bash

set -e

# Download the job config from vault
vault read -format json cubbyhole/data/job-context | jq -r '.data.data' > job-context.json

# Process the job context
node unpack-job-context.js

# execute the generated script
bash ./execute-rosco-job.sh