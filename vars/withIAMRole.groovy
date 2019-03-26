/***********************************
IAM Assume Role Step DSL

Assumes an IAM role

example usage
withIAMRole(1234567890,region,roleName) {
  sh 'aws s3 ls'
}
************************************/

import com.amazonaws.services.securitytoken.*
import com.amazonaws.services.securitytoken.model.*

def call(awsAccountId, region, roleName, options=[:], body) {
  def stsCredentials = assumeRole(awsAccountId, region, roleName, options)

  withEnv([
    "AWS_ACCESS_KEY_ID=${stsCredentials.getAccessKeyId()}",
    "AWS_SECRET_ACCESS_KEY=${stsCredentials.getSecretAccessKey()}",
    "AWS_SESSION_TOKEN=${stsCredentials.getSessionToken()}"
  ]) {
    body()
  }
}

@NonCPS
def assumeRole(awsAccountId, region, roleName, options) {
  def roleArn = "arn:aws:iam::" + awsAccountId + ":role/" + roleName
  def roleSessionName = "sts-session-" + awsAccountId
  println "assuming IAM role ${roleArn}"
  def sts = new AWSSecurityTokenServiceClient()
  if (!region.equals("us-east-1")) {
      sts.setEndpoint("sts." + region + ".amazonaws.com")
  }

  def request = new AssumeRoleRequest()
        .withRoleArn(roleArn).withDurationSeconds(3600)
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
