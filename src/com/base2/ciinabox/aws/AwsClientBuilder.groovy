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
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ecs.AmazonECSClientBuilder
import com.amazonaws.services.redshift.AmazonRedshiftClientBuilder
import com.amazonaws.services.rds.AmazonRDSClientBuilder
import com.amazonaws.services.codeartifact.AWSCodeArtifactClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder

class AwsClientBuilder implements Serializable {
  
  def region
  def awsAccountId
  def role
  def maxErrorRetry
  def env
  def duration
  
  AwsClientBuilder(Map config = [:]) {
    this.region = config.get('region', null)
    this.awsAccountId = config.get('awsAccountId', null)
    this.role = config.get('role', null)
    this.maxErrorRetry = config.get('maxErrorRetry', 10)
    this.env = config.get('env', [:])
    this.duration = config.get('duration', 3600)
  }

  def sqs() {
    def cb = new AmazonSQSClientBuilder().standard()
      .withClientConfiguration(config())

    if (region) {
      cb.withRegion(region)
    }

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  }

  def redshift() {
    def cb = new AmazonRedshiftClientBuilder().standard()
      .withClientConfiguration(config())

    if (region) {
      cb.withRegion(region)
    }

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  }

  def rds() {
    def cb = new AmazonRDSClientBuilder().standard()
      .withClientConfiguration(config())

    if (region) {
      cb.withRegion(region)
    }

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  }
  
  def ecs() {
    def cb = new AmazonECSClientBuilder().standard()
      .withClientConfiguration(config())

    if (region) {
      cb.withRegion(region)
    }

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  }
  
  def ec2() {
    def cb = new AmazonEC2ClientBuilder().standard()
      .withClientConfiguration(config())

    if (region) {
      cb.withRegion(region)
    }

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  }

  def s3() {
    def cb = new AmazonS3ClientBuilder().standard()
      .withClientConfiguration(config())

    if (region) {
      cb.withRegion(region)
    }

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  }

  def cloudformation() {
    def cb = new AmazonCloudFormationClientBuilder().standard()
      .withClientConfiguration(config())

    if (region) {
      cb.withRegion(region)
    }

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  }

  def codeartifact() {
    def cb = new AWSCodeArtifactClientBuilder().standard()
      .withClientConfiguration(config())

    if (region) {
      cb.withRegion(region)
    }

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  }

  def cloudwatch() {
    def cb = new AmazonCloudWatchClientBuilder().standard()
      .withClientConfiguration(config())

    if (region) {
      cb.withRegion(region)
    }

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  }
  
  def asg() {
    def cb = new AmazonAutoScalingClientBuilder().standard()
      .withClientConfiguration(config())

    if (region) {
      cb.withRegion(region)
    }

    def creds = getCredentials()
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }

    return cb.build()
  
  }

  def config() {
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
              .withRoleArn(roleArn)
              .withDurationSeconds(duration)
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

  def getNewCreds() {
    def stsCreds = assumeRole()
    def creds =  new BasicSessionCredentials(
        stsCreds.getAccessKeyId(),
        stsCreds.getSecretAccessKey(),
        stsCreds.getSessionToken()
      )

    return creds
  }
}
