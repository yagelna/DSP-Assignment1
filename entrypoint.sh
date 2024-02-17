#!/bin/bash

if [ -z "$MODE" ]; then
    echo "Error: Environment variable MODE is not set."
    exit 1
fi

if [ "$MODE" = "MANAGER" ]; then
    java -jar manager/target/manager*.jar
elif [ "$MODE" = "WORKER" ]; then
    java -jar worker/target/worker*.jar
else
    echo "Error: Invalid MODE value. MODE must be either 'MANAGER' or 'WORKER'."
    exit 1
fi