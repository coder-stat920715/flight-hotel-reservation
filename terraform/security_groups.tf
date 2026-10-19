data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

# External ALB: reachable only from CloudFront's origin-facing IP ranges
resource "aws_security_group" "alb" {
  name        = "${var.project}-alb"
  description = "External ALB - CloudFront only"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "HTTPS from CloudFront"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront.id]
  }

  egress {
    description = "To pods in the VPC"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.vpc_cidr]
  }
}

# EKS cluster security group is shared by managed node ENIs/pods
resource "aws_security_group_rule" "alb_to_gateway" {
  type                     = "ingress"
  description              = "ALB to api-gateway pods"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  security_group_id        = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
  source_security_group_id = aws_security_group.alb.id
}

resource "aws_security_group_rule" "alb_health_checks" {
  type                     = "ingress"
  description              = "ALB health checks on the management port"
  from_port                = 9090
  to_port                  = 9090
  protocol                 = "tcp"
  security_group_id        = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
  source_security_group_id = aws_security_group.alb.id
}

resource "aws_security_group" "postgres" {
  name        = "${var.project}-postgres"
  description = "PostgreSQL from EKS only"
  vpc_id      = aws_vpc.this.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_eks_cluster.this.vpc_config[0].cluster_security_group_id]
  }
}

resource "aws_security_group" "redis" {
  name        = "${var.project}-redis"
  description = "Redis from EKS only"
  vpc_id      = aws_vpc.this.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_eks_cluster.this.vpc_config[0].cluster_security_group_id]
  }
}

resource "aws_security_group" "kafka" {
  name        = "${var.project}-kafka"
  description = "MSK from EKS (TLS) and between brokers"
  vpc_id      = aws_vpc.this.id

  ingress {
    from_port       = 9094
    to_port         = 9094
    protocol        = "tcp"
    security_groups = [aws_eks_cluster.this.vpc_config[0].cluster_security_group_id]
  }

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }
}
