/***********************************
IAM Assume Role Step DSL

Assumes an IAM role

example usage
withMFA(
  credentials: 'username-password-creds', // (required, jenkins credentials with the IAM user access key and secret key)
  role: 'my-role', // (required, aws role to assume)
  region: 'us-east-1', // (required)
  accountId: '012345678912', // (optional, account id of the role to assume. Defaults to the current AWS account)
  mfaToken: '000000', // (required, the token genrated by the mfa device)
  mfaId: 'my-mfa-user' // (required, the IAM user which the mfa device is assoicated)
  mfaAccountId: '012345678912' // (optional, account id the mfa IAM user resides in. Defaults to the current AWS account)
) {
  sh 'aws s3 ls'
}
************************************/

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest

def call(config, body) {
  def awsAccountId = getAwsAccountId()
  def mfaAccountId = config.get('mfaAccountId', awsAccountId)
  def roleAccountId = config.get('accountId', awsAccountId)
  
  withCredentials([usernamePassword(credentialsId: config.credentials, usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
    def serialNumber = 'arn:aws:iam::' + mfaAccountId + ':mfa/' + config.mfaId
    def roleArn = "arn:aws:iam::" + roleAccountId + ":role/" + config.role
    def stsCredentials = assumeRole(roleArn, config.region, config.mfaToken, serialNumber, env['AWS_ACCESS_KEY_ID'], env['AWS_SECRET_ACCESS_KEY'])

    withEnv([
      "AWS_ACCESS_KEY_ID=${stsCredentials.getAccessKeyId()}",
      "AWS_SECRET_ACCESS_KEY=${stsCredentials.getSecretAccessKey()}",
      "AWS_SESSION_TOKEN=${stsCredentials.getSessionToken()}"
    ]) {
      body()
    }
  }
}

@NonCPS
def getAwsAccountId() {
  def sts = new AWSSecurityTokenServiceClient()
  def request = new GetCallerIdentityRequest()
  def result = sts.getCallerIdentity(request)
  return result.getAccount()
}

@NonCPS
def stsClient(access_key_id, secret_access_key) {
  def creds = new BasicAWSCredentials(access_key_id, secret_access_key)
  def cb = new AWSSecurityTokenServiceClientBuilder().standard()
    .withCredentials(new AWSStaticCredentialsProvider(creds))
    
  return cb.build()
}

@NonCPS
def assumeRole(roleArn, region, mfaToken, serialNumber, access_key_id, secret_access_key) {
  def sts = stsClient(access_key_id, secret_access_key)
  
  def roleSessionName = "mfa-sts-session"
  
  echo "assuming IAM role ${roleArn} with mfa ${serialNumber}"
  
  def request = new AssumeRoleRequest()
        .withRoleArn(roleArn)
        .withDurationSeconds(3600)
        .withRoleSessionName(roleSessionName)
        .withTokenCode(mfaToken)
        .withSerialNumber(serialNumber)

  def assumeRoleResult = sts.assumeRole(request)
  return assumeRoleResult.getCredentials()
}
