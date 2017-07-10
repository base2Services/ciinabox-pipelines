package com.base2.ciinabox

/**
 * Return AWS Region from Instance Metadata
 */
def awsRegion() {
  return shellOut('curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | jq -r \'.region\'')
}

/**
 * Return AWS Account ID gathered from default credentials
 * @return
 */
def accountId() {
  def region = awsRegion()
  shellOut("aws sts get-caller-identity --region ${region} --query Account --output text")
}


