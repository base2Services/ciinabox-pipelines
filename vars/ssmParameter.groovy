/***********************************
ssmParameter DSL

puts or gets an ssm parameter

example usage

ssmParameter(
  action: 'get'            # get | put,
  parameter: 'db_user',
  type: 'String',          # String | StringList | SecureString (default: String)
  region: 'us-east-1',
  accountId: '12345678',
  role: 'ciinabox'
)


************************************/

import com.amazonaws.auth.*
import com.amazonaws.regions.*
import com.amazonaws.services.securitytoken.*
import com.amazonaws.services.securitytoken.model.*
import com.amazonaws.services.simplesystemsmanagement.*
import com.amazonaws.services.simplesystemsmanagement.model.*


def call(body) {
  def config = body
  def ssm = setupSSMClient(config.region, config.accountId, config.role)

  if(!(config.action)){
    throw new GroovyRuntimeException("action get/put must be specified")
  }

  if(!(config.parameter)){
    throw new GroovyRuntimeException("parameter must be specified")
  }

  if(config.action.toLowerCase() == 'put') {
    if(!(config.value)){
      throw new GroovyRuntimeException("value must be specified")
    }
    def paramType = config.get('type', 'String')
    def result = ssm.putParameter(new PutParameterRequest()
      .withName(config.parameter)
      .withType(paramType)
      .withValue(config.value)
      .withOverwrite(true)
    )
    println "put param ${config.parameter}"
  } else if(config.action.toLowerCase() == 'get') {
    return getSSMParams(ssm, config.parameter)
  }

}

@NonCPS
def setupSSMClient(region, awsAccountId = null, role =  null) {
  def cb = AWSSimpleSystemsManagementClientBuilder.standard().withRegion(region)
  def creds = getCredentials(awsAccountId, region, role)
  if(creds != null) {
    cb.withCredentials(new AWSStaticCredentialsProvider(creds))
  }
  return cb.build()
}

@NonCPS
def getCredentials(awsAccountId, region, roleName) {
  if(env['AWS_SESSION_TOKEN'] != null) {
    return new BasicSessionCredentials(
      env['AWS_ACCESS_KEY_ID'],
      env['AWS_SECRET_ACCESS_KEY'],
      env['AWS_SESSION_TOKEN']
    )
  } else if(awsAccountId != null && roleName != null) {
    def stsCreds = assumeRole(awsAccountId, region, roleName)
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
def assumeRole(awsAccountId, region, roleName) {
  def roleArn = "arn:aws:iam::" + awsAccountId + ":role/" + roleName
  def roleSessionName = "sts-session-" + awsAccountId
  println "assuming IAM role ${roleArn}"
  def sts = new AWSSecurityTokenServiceClient()
  if (!region.equals("us-east-1")) {
      sts.setEndpoint("sts." + region + ".amazonaws.com")
  }
  def assumeRoleResult = sts.assumeRole(new AssumeRoleRequest()
            .withRoleArn(roleArn).withDurationSeconds(3600)
            .withRoleSessionName(roleSessionName))
  return assumeRoleResult.getCredentials()
}

@NonCPS
def getSSMParams(ssm, path) {
  def ssmParams = []
  def result = ssm.getParametersByPath(new GetParametersByPathRequest()
    .withPath(path)
    .withRecursive(false)
    .withWithDecryption(true)
    .withMaxResults(10)
  )
  ssmParams += result.parameters
  while(result.nextToken != null) {
    result = ssm.getParametersByPath(new GetParametersByPathRequest()
        .withPath(path)
        .withRecursive(false)
        .withWithDecryption(true)
        .withMaxResults(10)
        .withNextToken(result.nextToken)
    )
    ssmParams += result.parameters
  }
  return ssmParams
}