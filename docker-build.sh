#!/bin/sh
./sbt clean universal:dist
docker build --no-cache -t ruimo/store2:${TAG_NAME:-latest} .
