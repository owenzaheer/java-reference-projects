#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=8000 --demo.config=ecommerce-order-and-fulfillment-service-java/project.json"
