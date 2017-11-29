#!/usr/bin/env bash

set -e

DIR="$(mktemp -d)"

trap 'rm -rf "$DIR"' EXIT

git clone https://github.com/longshorej/akka-management "$DIR"

cd "$DIR"

git checkout wip-joining-2.4-patriknw

sbt ++2.12.4 ";clean;publishLocal"

sbt ++2.11.12 ";clean;publishLocal"
