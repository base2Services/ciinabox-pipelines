package com.base2.ciinabox

import com.cloudbees.groovy.cps.NonCPS
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest

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

  @NonCPS
  static def stepfunctions(config) {
    def creds = getCredentials(config.accountId, config.region, config.role, config)
    def cb = AWSStepFunctionsClientBuilder.standard()
      .withRegion(config.region)
    if(creds != null) {
      cb.withCredentials(new AWSStaticCredentialsProvider(creds))
    }
    return cb.build()
  }

  @NonCPS
  static def getCredentials(awsAccountId, region, roleName, options) {
    def env = System.getenv()
    if(env['AWS_SESSION_TOKEN'] != null) {
      return new BasicSessionCredentials(
        env['AWS_ACCESS_KEY_ID'],
        env['AWS_SECRET_ACCESS_KEY'],
        env['AWS_SESSION_TOKEN']
      )
    } else if(awsAccountId != null && roleName != null) {
      def stsCreds = assumeRole(awsAccountId, region, roleName, options)
      return new BasicSessionCredentials(
        stsCreds.getAccessKeyId(),
        stsCreds.getSecretAccessKey(),
        stsCreds.getSessionToken()
      )
    } else {
      return null
    }
  }

  @NonCPS
  static def assumeRole(awsAccountId, region, roleName, options) {
    def roleArn = "arn:aws:iam::" + awsAccountId + ":role/" + roleName
    def roleSessionName = "sts-session-" + awsAccountId
    println "assuming IAM role ${roleArn}"
    def sts = new AWSSecurityTokenServiceClient()
    if (!region.equals("us-east-1")) {
        sts.setEndpoint("sts." + region + ".amazonaws.com")
    }

    def credsDuration = options.get('credsDuration', 3600)
    def request = new AssumeRoleRequest()
          .withRoleArn(roleArn).withDurationSeconds(credsDuration)
          .withRoleSessionName(roleSessionName)

    if(options.externalId) {
      request.withExternalId(options.externalId)
    }

    if(options.mfaToken && options.serialNumber) {
      request.withTokenCode(options.mfaToken)
      request.withSerialNumber(options.serialNumber)
    }

    def assumeRoleResult = sts.assumeRole(request)
    return assumeRoleResult.getCredentials()
  }

}