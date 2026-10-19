output "cloudfront_domain" {
  value = aws_cloudfront_distribution.this.domain_name
}

output "api_url" {
  value = "https://api.${var.domain_name}"
}

output "eks_cluster_name" {
  value = aws_eks_cluster.this.name
}

output "gateway_target_group_arn" {
  description = "-> GATEWAY_TARGET_GROUP_ARN for k8s/external-alb-ingress.yaml"
  value       = aws_lb_target_group.gateway.arn
}

output "external_alb_security_group_id" {
  value = aws_security_group.alb.id
}

output "rds_endpoint" {
  value = aws_db_instance.postgres.address
}

output "redis_endpoint" {
  value = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "msk_bootstrap_brokers_tls" {
  value = aws_msk_cluster.kafka.bootstrap_brokers_tls
}

output "cognito_issuer_uri" {
  value = "https://cognito-idp.${var.region}.amazonaws.com/${aws_cognito_user_pool.this.id}"
}

output "waf_regional_acl_arn" {
  value = aws_wafv2_web_acl.regional.arn
}
