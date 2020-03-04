package com.base2.ciinabox

import com.cloudbees.groovy.cps.NonCPS
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder

class AwsClient {

  @NonCPS
  static def ec2(String region) {
    return AmazonEC2ClientBuilder.standard()
      .withRegion(region)
      .build()
  }
  
  @NonCPS
  def ssm(region, awsAccountId = null, role =  null) {
    return AWSSimpleSystemsManagementClientBuilder.standard()
      .withRegion(region)
      .build()
  }

}