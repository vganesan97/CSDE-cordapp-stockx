#!/bin/zsh
pkill -f "java.*corda-combined-worker"
docker stop CSDEpostgresql
~/dev/CSDE-cordapp-digital-currency/gradlew clean
rm -rf ~/dev/CSDE-cordapp-digital-currency/workspace/
rm -rf ~/.corda/corda5/
