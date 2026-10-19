# Everything below is placed in the private ISOLATED database subnets (no internet route).

resource "aws_db_instance" "postgres" {
  identifier                  = "${var.project}-pg"
  engine                      = "postgres"
  engine_version              = "16.4"
  instance_class              = var.db_instance_class
  allocated_storage           = 100
  max_allocated_storage       = 1000
  storage_type                = "gp3"
  storage_encrypted           = true
  db_name                     = "travel"
  username                    = "travel_admin"
  manage_master_user_password = true
  multi_az                    = true
  db_subnet_group_name        = aws_db_subnet_group.this.name
  vpc_security_group_ids      = [aws_security_group.postgres.id]
  backup_retention_period     = 14
  deletion_protection         = true
  performance_insights_enabled = true
  skip_final_snapshot         = false
  final_snapshot_identifier   = "${var.project}-pg-final"
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "${var.project}-redis"
  description                = "L2 cache + gateway rate limiter"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = "cache.r6g.large"
  num_node_groups            = 1
  replicas_per_node_group    = 2
  automatic_failover_enabled = true
  multi_az_enabled           = true
  subnet_group_name          = aws_elasticache_subnet_group.this.name
  security_group_ids         = [aws_security_group.redis.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
}

# TLS in transit + security-group isolation. For IAM auth add the aws-msk-iam-auth library to the services
# and switch client_authentication to sasl { iam = true }.
resource "aws_msk_cluster" "kafka" {
  cluster_name           = "${var.project}-kafka"
  kafka_version          = "3.7.x"
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type   = "kafka.m5.large"
    client_subnets  = aws_subnet.private_db[*].id
    security_groups = [aws_security_group.kafka.id]

    storage_info {
      ebs_storage_info {
        volume_size = 500
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  client_authentication {
    unauthenticated = true
  }
}
