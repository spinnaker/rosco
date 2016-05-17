Rosco
=====
[![Build Status](https://api.travis-ci.org/spinnaker/rosco.svg?branch=master)](https://travis-ci.org/spinnaker/rosco)

A bakery for use by Spinnaker to produce machine images.

It presently supports producing Google Compute Engine images and AWS amis. It relies on packer and can be easily extended to support additional platforms.

It exposes a REST api which can be experimented with via the Swagger UI: http://localhost:8087/swagger-ui.html

# Developing rosco

Need to run rosco locally for development? Here's what you need to setup and run:

## Environment Setup
. git clone git@github.com:spinnaker/rosco.git
. cd rosco

## Docker Setup (runs redis locally)
. docker-machine create --virtualbox-disk-size 8192 --virtualbox-memory 4096 -d virtualbox rosco-instance
. eval $(docker-machine env rosco-instance)
. docker-compose up -d

## Verify redis
. docker run -it --link redis:redis --rm redis redis-cli -h redis -p 6379
. (printf "PING\r\n";) | nc -v localhost 6379

## Running App
. ./gradlew bootRun

## Verifying
. curl -v localhost:7002/backOptions

## Swagger
. http://localhost:7002/swagger-ui.html
