variable "region" {
  type    = string
  default = "ap-south-1"
}

variable "project" {
  type    = string
  default = "travel-platform"
}

variable "domain_name" {
  description = "Existing public Route 53 hosted zone name, e.g. example.com"
  type        = string
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "origin_verify_secret" {
  description = "Shared secret CloudFront sends as X-Origin-Verify; the ALB rejects requests without it"
  type        = string
  sensitive   = true
}

variable "dr_alias_dns_name" {
  description = "DNS name of the DR-region endpoint for Route 53 failover (empty disables the secondary record)"
  type        = string
  default     = ""
}

variable "dr_alias_zone_id" {
  type    = string
  default = ""
}

variable "eks_version" {
  type    = string
  default = "1.30"
}

variable "node_instance_types" {
  type    = list(string)
  default = ["m6i.xlarge"]
}

variable "db_instance_class" {
  type    = string
  default = "db.r6g.large"
}
