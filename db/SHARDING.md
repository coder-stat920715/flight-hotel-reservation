# Horizontal Sharding Guidelines

The reference deployment uses a single PostgreSQL primary (Multi-AZ) with native partitioning. When
read volume outgrows one primary + replicas, shard the **catalogue** (read) data as follows.

## Shard key choice
| Data set | Shard key | Why |
|---|---|---|
| `airports`, `cities`, `hotels`, `hotel_pricing`, `attractions` | `hash(city_id)` (or `country_id` for coarse tenant-style isolation) | Every budget lookup is destination-city scoped, so hotel + sightseeing reads hit exactly one shard. |
| `flights`, `flight_fares` | `hash(source_airport_id)` | Searches always start from an origin; keeps route scans on one shard. Replicate the small dimension tables everywhere. |
| `bookings`, `payment_transactions`, `outbox_events` | `hash(user_id)` or `tenant_id` | Write-side locality; a booking and its payments live together. |

## Rules of thumb
1. Use **consistent hashing** (or a directory/lookup table) with 64-256 virtual shards, mapped to fewer physical
   nodes, so adding nodes only moves some virtual shards.
2. Replicate reference data (`countries`, `airlines`) to every shard; never join across shards in SQL.
3. The minimum-budget CTE needs flight data (shard by origin) and hotel data (shard by destination city). In a sharded
   world run it as **scatter-gather in the query service**: fetch the cheapest flight from the origin shard, then
   the cheapest hotel + sightseeing sum from the destination-city shard, and add the three numbers in Java.
4. Keep **partitioning inside each shard** (`flight_fares` by `departure_date`) - partitioning and sharding compose.
5. Route with a `ShardRouter` bean (e.g. Spring `AbstractRoutingDataSource` keyed by `hash(key) % virtualShards`) or
   use Citus / Vitess-style middleware (Citus: `SELECT create_distributed_table('hotels','city_id')`).
6. Cross-shard sagas already use the outbox + Kafka, so they need no distributed transactions.
7. Reads: add replicas per shard and point `flight-hotel-query-service` at the reader endpoint (`DB_URL`).
