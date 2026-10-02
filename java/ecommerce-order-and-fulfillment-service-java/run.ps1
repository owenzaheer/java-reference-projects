Set-Location $PSScriptRoot
Set-Location ..
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=8000 --demo.config=ecommerce-order-and-fulfillment-service-java/project.json"
