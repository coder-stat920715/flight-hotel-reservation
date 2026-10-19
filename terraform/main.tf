terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

provider "aws" {
  region = var.region
}

# CloudFront certificates and CLOUDFRONT-scope WAF ACLs must live in us-east-1
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}

locals {
  managed_rule_groups = [
    "AWSManagedRulesCommonRuleSet",       # XSS, bad inputs, size restrictions
    "AWSManagedRulesSQLiRuleSet",         # SQL injection
    "AWSManagedRulesKnownBadInputsRuleSet",
  ]
  api_fqdn    = "api.${var.domain_name}"
  origin_fqdn = "origin.${var.domain_name}"
}

data "aws_route53_zone" "this" {
  name         = var.domain_name
  private_zone = false
}

# ---------------------------------------------------------------- TLS certificates

resource "aws_acm_certificate" "origin" {
  domain_name       = "${local.origin_fqdn}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "origin_validation" {
  for_each = { for o in aws_acm_certificate.origin.domain_validation_options : o.domain_name => o }

  allow_overwrite = true
  zone_id         = data.aws_route53_zone.this.zone_id
  name            = each.value.resource_record_name
  type            = each.value.resource_record_type
  records         = [each.value.resource_record_value]
  ttl             = 60
}

resource "aws_acm_certificate_validation" "origin" {
  certificate_arn         = aws_acm_certificate.origin.arn
  validation_record_fqdns = [for r in aws_route53_record.origin_validation : r.fqdn]
}

resource "aws_acm_certificate" "edge" {
  provider = aws.us_east_1
  domain_name       = "${local.api_fqdn}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "edge_validation" {
  for_each = { for o in aws_acm_certificate.edge.domain_validation_options : o.domain_name => o }

  allow_overwrite = true
  zone_id         = data.aws_route53_zone.this.zone_id
  name            = each.value.resource_record_name
  type            = each.value.resource_record_type
  records         = [each.value.resource_record_value]
  ttl             = 60
}

resource "aws_acm_certificate_validation" "edge" {
  provider = aws.us_east_1
  certificate_arn         = aws_acm_certificate.edge.arn
  validation_record_fqdns = [for r in aws_route53_record.edge_validation : r.fqdn]
}

# ---------------------------------------------------------------- WAF (edge + regional)

resource "aws_wafv2_web_acl" "edge" {
  provider = aws.us_east_1
  name  = "${var.project}-edge"
  scope = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "rate-limit-per-ip"
    priority = 1
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "rate-limit-per-ip"
      sampled_requests_enabled   = true
    }
  }

  dynamic "rule" {
    for_each = { for i, n in local.managed_rule_groups : n => i }
    content {
      name     = rule.key
      priority = 10 + rule.value
      override_action {
        none {}
      }
      statement {
        managed_rule_group_statement {
          name        = rule.key
          vendor_name = "AWS"
        }
      }
      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = rule.key
        sampled_requests_enabled   = true
      }
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.project}-edge"
    sampled_requests_enabled   = true
  }
}

resource "aws_wafv2_web_acl" "regional" {
  name  = "${var.project}-regional"
  scope = "REGIONAL"

  default_action {
    allow {}
  }

  rule {
    name     = "rate-limit-per-ip"
    priority = 1
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "rate-limit-per-ip"
      sampled_requests_enabled   = true
    }
  }

  dynamic "rule" {
    for_each = { for i, n in local.managed_rule_groups : n => i }
    content {
      name     = rule.key
      priority = 10 + rule.value
      override_action {
        none {}
      }
      statement {
        managed_rule_group_statement {
          name        = rule.key
          vendor_name = "AWS"
        }
      }
      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = rule.key
        sampled_requests_enabled   = true
      }
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.project}-regional"
    sampled_requests_enabled   = true
  }
}

# ---------------------------------------------------------------- External ALB (Layer 1, public subnets)
resource "aws_lb" "external" {
  name                       = "${var.project}-ext"
  load_balancer_type         = "application"
  internal                   = false
  subnets                    = aws_subnet.public[*].id
  security_groups            = [aws_security_group.alb.id]
  drop_invalid_header_fields = true
  enable_deletion_protection = true
}

resource "aws_lb_target_group" "gateway" {
  name        = "${var.project}-gateway"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.this.id

  health_check {
    path    = "/actuator/health/readiness"
    port    = "9090"
    matcher = "200"
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.external.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.origin.certificate_arn

  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Forbidden"
      status_code  = "403"
    }
  }
}

resource "aws_lb_listener_rule" "from_cloudfront" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 10

  condition {
    http_header {
      http_header_name = "X-Origin-Verify"
      values           = [var.origin_verify_secret]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }
}

resource "aws_wafv2_web_acl_association" "alb" {
  resource_arn = aws_lb.external.arn
  web_acl_arn  = aws_wafv2_web_acl.regional.arn
}

resource "aws_route53_record" "origin" {
  zone_id = data.aws_route53_zone.this.zone_id
  name    = local.origin_fqdn
  type    = "A"

  alias {
    name                   = aws_lb.external.dns_name
    zone_id                = aws_lb.external.zone_id
    evaluate_target_health = true
  }
}

# ---------------------------------------------------------------- CloudFront (CDN + edge WAF)
resource "aws_cloudfront_distribution" "this" {
  enabled         = true
  is_ipv6_enabled = true
  http_version    = "http2and3"
  price_class     = "PriceClass_200"
  aliases         = [local.api_fqdn]
  web_acl_id      = aws_wafv2_web_acl.edge.arn

  origin {
    domain_name = aws_route53_record.origin.fqdn
    origin_id   = "external-alb"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    custom_header {
      name  = "X-Origin-Verify"
      value = var.origin_verify_secret
    }
  }

  default_cache_behavior {
    target_origin_id         = "external-alb"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" # Managed-CachingDisabled (APIs)
    origin_request_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac" # Managed-AllViewerExceptHostHeader
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.edge.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

# ---------------------------------------------------------------- Shield Advanced (requires an active subscription)
resource "aws_shield_protection" "cloudfront" {
  name         = "${var.project}-cloudfront"
  resource_arn = aws_cloudfront_distribution.this.arn
}

resource "aws_shield_protection" "alb" {
  name         = "${var.project}-alb"
  resource_arn = aws_lb.external.arn
}

# ---------------------------------------------------------------- Route 53: failover + latency routing
resource "aws_route53_health_check" "primary" {
  fqdn              = aws_cloudfront_distribution.this.domain_name
  port              = 443
  type              = "HTTPS"
  resource_path     = "/actuator/health/liveness"
  failure_threshold = 3
  request_interval  = 30
}

resource "aws_route53_record" "api_primary" {
  zone_id        = data.aws_route53_zone.this.zone_id
  name           = local.api_fqdn
  type           = "A"
  set_identifier = "primary"
  health_check_id = aws_route53_health_check.primary.id

  failover_routing_policy {
    type = "PRIMARY"
  }

  alias {
    name                   = aws_cloudfront_distribution.this.domain_name
    zone_id                = aws_cloudfront_distribution.this.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "api_secondary" {
  count          = var.dr_alias_dns_name == "" ? 0 : 1
  zone_id        = data.aws_route53_zone.this.zone_id
  name           = local.api_fqdn
  type           = "A"
  set_identifier = "secondary"

  failover_routing_policy {
    type = "SECONDARY"
  }

  alias {
    name                   = var.dr_alias_dns_name
    zone_id                = var.dr_alias_zone_id
    evaluate_target_health = true
  }
}

# Latency-based record; add the same name with another region's alias to enable multi-region latency routing.
resource "aws_route53_record" "api_latency" {
  zone_id        = data.aws_route53_zone.this.zone_id
  name           = "latency.${var.domain_name}"
  type           = "A"
  set_identifier = var.region

  latency_routing_policy {
    region = var.region
  }

  alias {
    name                   = aws_cloudfront_distribution.this.domain_name
    zone_id                = aws_cloudfront_distribution.this.hosted_zone_id
    evaluate_target_health = false
  }
}

# ---------------------------------------------------------------- EKS
resource "aws_kms_key" "eks" {
  description         = "${var.project} EKS secrets encryption"
  enable_key_rotation = true
}

resource "aws_iam_role" "eks_cluster" {
  name = "${var.project}-eks-cluster"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "eks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "eks_cluster" {
  role       = aws_iam_role.eks_cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_eks_cluster" "this" {
  name     = "${var.project}-eks"
  role_arn = aws_iam_role.eks_cluster.arn
  version  = var.eks_version

  vpc_config {
    subnet_ids              = concat(aws_subnet.private_app[*].id, aws_subnet.public[*].id)
    endpoint_private_access = true
    endpoint_public_access  = true
  }

  access_config {
    authentication_mode                         = "API_AND_CONFIG_MAP"
    bootstrap_cluster_creator_admin_permissions = true
  }

  encryption_config {
    resources = ["secrets"]
    provider {
      key_arn = aws_kms_key.eks.arn
    }
  }

  enabled_cluster_log_types = ["api", "audit", "authenticator"]
  depends_on                = [aws_iam_role_policy_attachment.eks_cluster]
}

resource "aws_iam_role" "eks_nodes" {
  name = "${var.project}-eks-nodes"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "eks_nodes" {
  for_each = toset([
    "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy",
    "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy",
    "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly",
  ])
  role       = aws_iam_role.eks_nodes.name
  policy_arn = each.value
}

# Worker nodes live ONLY in the private application subnets (3 AZs)
resource "aws_eks_node_group" "apps" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "${var.project}-apps"
  node_role_arn   = aws_iam_role.eks_nodes.arn
  subnet_ids      = aws_subnet.private_app[*].id
  instance_types  = var.node_instance_types
  disk_size       = 50

  scaling_config {
    min_size     = 3
    desired_size = 3
    max_size     = 12
  }

  update_config {
    max_unavailable = 1
  }

  depends_on = [aws_iam_role_policy_attachment.eks_nodes]
}

data "tls_certificate" "eks" {
  url = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
}

# ---------------------------------------------------------------- OIDC identity provider (AWS Cognito alternative to Keycloak)
resource "aws_cognito_user_pool" "this" {
  name                     = "${var.project}-users"
  mfa_configuration        = "OPTIONAL"
  auto_verified_attributes = ["email"]

  software_token_mfa_configuration {
    enabled = true
  }

  schema {
    name                = "tenant_id"
    attribute_data_type = "String"
    mutable             = true
    string_attribute_constraints {}
  }

  schema {
    name                = "user_location"
    attribute_data_type = "String"
    mutable             = true
    string_attribute_constraints {}
  }
}

resource "aws_cognito_user_group" "roles" {
  for_each     = toset(["CUSTOMER", "ADMIN"])
  name         = each.key
  user_pool_id = aws_cognito_user_pool.this.id
}

resource "aws_cognito_user_pool_client" "web" {
  name            = "${var.project}-web"
  user_pool_id    = aws_cognito_user_pool.this.id
  generate_secret = false

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  callback_urls                        = ["https://${var.domain_name}/callback"]
  supported_identity_providers         = ["COGNITO"]
}
