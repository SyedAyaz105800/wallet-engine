# Wallet Engine 💰

A production-grade **Idempotent Wallet/Ledger Engine** built with Spring Boot.

Directly mirrors real-world payment wallet systems — handling deposits, withdrawals, and rollbacks with strict idempotency guarantees.

## Features
- ✅ Idempotent transactions (Redis + DB two-layer check)
- ✅ Double-spend prevention (Optimistic Locking with @Version)
- ✅ Deposit / Withdrawal / Rollback APIs
- ✅ Transaction audit ledger
- ✅ Concurrent request handling with retry logic

## Tech Stack
- **Java 21** + **Spring Boot 4.1.1**
- **MySQL 8** — transaction ledger
- **Redis 7** — idempotency key cache (24hr TTL)
- **Apache Kafka** — async transaction events (coming soon)
- **Docker** — local infrastructure

## Quick Start
```bash
# Start infrastructure
docker run -d --name mysql-wallet -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=wallet_db -p 3306:3306 mysql:8.0

docker run -d --name redis-wallet -p 6379:6379 redis:7

# Run app
./mvnw spring-boot:run
```

## API Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/wallet/create/{userId}` | Create wallet |
| GET | `/api/wallet/{userId}` | Get balance |
| POST | `/api/wallet/deposit` | Deposit (idempotent) |
| POST | `/api/wallet/withdraw` | Withdraw (idempotent) |
| POST | `/api/wallet/rollback/{key}/{rollbackKey}` | Rollback transaction |

## How Idempotency Works
```
Request → Check Redis (fast) → Found? Return cached result
                             → Not found? Check DB (slow)
                                        → Found? Cache + return
                                        → Not found? Process + save + cache
```
