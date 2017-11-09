/***********************************
IAM Assume Role Step DSL

Assumes an IAM role

example usage
withIAMRole(1234567890,region,roleName) {
  sh 'aws s3 ls'
}
************************************/
@Grab(group='com.amazonaws', module='aws-java-sdk-iam', version='1.11.226')

import com.amazonaws.services.securitytoken.*;
import com.amazonaws.services.securitytoken.model.*;

def call(awsAccountId, region, roleName, body) {
  def roleArn = "arn:aws:iam::" + awsAccountId + ":role/" + roleName;
  def roleSessionName = "sts-session-" + awsAccountId;
  println "assuming IAM role ${roleArn}"
  AWSSecurityTokenService sts = new AWSSecurityTokenServiceClient(new InstanceProfileCredentialsProvider());
  if (!region.equals("us-east-1")) {
      sts.setEndpoint("sts." + region + ".amazonaws.com");
  }
  AssumeRoleResult assumeRoleResult = sts.assumeRole(new AssumeRoleRequest()
            .withRoleArn(roleArn).withDurationSeconds(DEFAULT_DURATION_SECONDS)
            .withRoleSessionName(roleSessionName));
  Credentials stsCredentials = assumeRoleResult.getCredentials();
  env['AWS_ACCESS_KEY_ID'] = stsCredentials.getAccessKeyId();
  env['AWS_SECRET_ACCESS_KEY'] = stsCredentials.getSecretAccessKey();
  env['AWS_SESSION_TOKEN'] = stsCredentials.getSessionToken();
  body()
  env.AWS_ACCESS_KEY_ID = null
  env.AWS_SECRET_ACCESS_KEY = null
  env.AWS_SESSION_TOKEN = null
}
