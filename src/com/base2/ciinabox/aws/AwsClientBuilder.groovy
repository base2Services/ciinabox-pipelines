package com.base2.ciinabox.aws

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicSessionCredentials

import com.amazonaws.retry.RetryPolicy
import com.amazonaws.retry.PredefinedRetryPolicies.SDKDefaultRetryCondition
import com.amazonaws.retry.PredefinedBackoffStrategies.SDKDefaultBackoffStrategy

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest

import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder
import com.amazonaws.services.s3.AmazonS3ClientBuilder

class AwsClientBuilder implements Serializable {
  
  def region
  def awsAccountId
  def role
  def maxErrorRetry
  def env
  
  AwsClientBuilder(Map config) {
    this.region = config.region
    this.awsAccountId = config.get('awsAccountId', null)
    this.role = config.get('role', null)
    this.maxErrorRetry = config.get('maxErrorRetry', 3)
    this.env = config.get('env', [:])
  }

  def s3() {
    def cb = new AmazonS3ClientBuilder().standard()
      .withRegion(region)
      .withClientConfiguration(config())

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  }

  def cloudformation() {
    def cb = new AmazonCloudFormationClientBuilder().standard()
      .withRegion(region)
      .withClientConfiguration(config())

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  }

  private def config() {
    def clientConfiguration = new ClientConfiguration()
      .withRetryPolicy(new RetryPolicy(
        new SDKDefaultRetryCondition(), 
        new SDKDefaultBackoffStrategy(), 
        maxErrorRetry, 
        true))
    
    return clientConfiguration
  }

  private def assumeRole() {
    def roleArn = "arn:aws:iam::" + awsAccountId + ":role/" + role
    def roleSessionName = "sts-session-" + awsAccountId
    def sts = new AWSSecurityTokenServiceClient()
    if (!region.equals("us-east-1")) {
        sts.setEndpoint("sts." + region + ".amazonaws.com")
    }
    def assumeRoleResult = sts.assumeRole(new AssumeRoleRequest()
              .withRoleArn(roleArn).withDurationSeconds(3600)
              .withRoleSessionName(roleSessionName))
    return assumeRoleResult.getCredentials()
  }

  private def getCredentials() {
    if(env['AWS_SESSION_TOKEN'] != null) {
      return new BasicSessionCredentials(
        env['AWS_ACCESS_KEY_ID'],
        env['AWS_SECRET_ACCESS_KEY'],
        env['AWS_SESSION_TOKEN']
      )
    } else if(awsAccountId != null && role != null) {
      def stsCreds = assumeRole()
      return new BasicSessionCredentials(
        stsCreds.getAccessKeyId(),
        stsCreds.getSecretAccessKey(),
        stsCreds.getSessionToken()
      )
    } else {
      return null
    }
  }

}