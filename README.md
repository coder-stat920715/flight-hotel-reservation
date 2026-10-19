# Spring Microservices Ecosystem - Flight, Hotel & Travel Budget Estimator

Java 21 / Spring Boot 3.3 / Spring Cloud 2023.0 / PostgreSQL / Kafka + Debezium / Redis / AWS (EKS, Terraform).

## Quick start (local)
```bash
docker compose up -d postgres redis kafka connect keycloak jaeger prometheus grafana
curl -X POST -H "Content-Type: application/json" \
     --data @debezium/postgres-outbox-connector.json http://localhost:8084/connectors
mvn -q -DskipTests package
java -jar config-server/target/*.jar &          # 8888
java -jar discovery-server/target/*.jar &       # 8761
java -jar booking-command-service/target/*.jar &   # 8081
java -jar flight-hotel-query-service/target/*.jar &# 8082
java -jar payment-saga-service/target/*.jar &      # 8083
java -jar api-gateway/target/*.jar &            # 8080
```
Create a Keycloak realm `travel` (client roles/realm roles `CUSTOMER`, `ADMIN`; add `tenant_id` and `user_location`
token claims), obtain a token and call:
```
GET  http://localhost:8080/api/v1/travel/estimate-budget?source=CCU&destination=BKK&nights=4
POST http://localhost:8080/api/v1/bookings      {"tenantId":"t1","sourceCode":"CCU","destCode":"BKK","flightId":9,"stayNights":4,"totalAmount":25000}
```

## Event flow
`POST /bookings` -> `bookings` + 2 `outbox_events` rows (one transaction) -> Debezium -> topics `booking` (read-side
projection) and `booking-payment` (saga command, retry topics + `booking-payment.DLT`) -> payment-saga-service ->
outbox row on `booking-saga` -> Debezium -> booking-command-service sets `CONFIRMED` / `PAYMENT_FAILED`.

## Deploy to AWS
```bash
cd terraform && terraform init && terraform apply -var domain_name=example.com -var origin_verify_secret=...
kubectl apply -f k8s/namespace.yaml
export ECR_REGISTRY=... IMAGE_TAG=1.0.0 APP_DOMAIN=example.com DB_PASSWORD=... \
       RDS_ENDPOINT=$(terraform output -raw rds_endpoint) REDIS_ENDPOINT=$(terraform output -raw redis_endpoint) \
       MSK_BOOTSTRAP=$(terraform output -raw msk_bootstrap_brokers_tls) JWT_ISSUER_URI=$(terraform output -raw cognito_issuer_uri) \
       GATEWAY_TARGET_GROUP_ARN=$(terraform output -raw gateway_target_group_arn) \
       EXTERNAL_ALB_SECURITY_GROUP_ID=$(terraform output -raw external_alb_security_group_id)
for f in configmap-secrets mtls-certificates workloads external-alb-ingress hpa-booking-command hpa-travel-query; do
  envsubst < k8s/$f.yaml | kubectl apply -f -; done
```
Prerequisites in the cluster: AWS Load Balancer Controller, cert-manager, metrics-server, prometheus-adapter
(`k8s/prometheus-adapter-rules.yaml`), an OTLP collector. Load `db/V1`, `db/V2` into RDS (Flyway/psql); create the
`travel_app` DB role; register the Debezium connector against RDS (`rds.logical_replication=1`).

## Notes
* Rate limiting: Spring Cloud Gateway's Redis `RequestRateLimiter` (token bucket); Resilience4j handles gateway circuit breakers.
* The provided budget CTE pairs the cheapest hotel of the *destination city* with the flight (the original CROSS JOIN paired it with any city).
* mTLS is switched on with the `mtls` Spring profile (SSL bundle `mtls`, `client-auth: need`, management port 9090 is plain for probes).
* Sharding guidance: `db/SHARDING.md`.
