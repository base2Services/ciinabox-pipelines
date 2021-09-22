/***********************************
getEcsServiceDesiredCount Function

Takes in an ecs cluster and service name and returns the current desired task count for the service.

example usage
  getEcsServiceDesiredCount (
    cluster: my-dev-cluster,
    region: env.AWS_REGION,
    accountId: env.DEV_ACCOUNT_ID,
    service: my-api-service,
    role: 'ciinabox'
  )

************************************/

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.ecs.*
import com.amazonaws.services.ecs.model.DescribeServicesResult
import com.amazonaws.services.ecs.model.DescribeServicesRequest
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest

import java.util.concurrent.*

def call(body) {
    def config = body
    def client = setupECSClient(config.region, config.accountId, config.role)
    getDesiredCount(client, config)
}


@NonCPS
def getDesiredCount(client, config) {
    def request = new DescribeServicesRequest()
    request.withCluster(config.cluster)
    request.withServices(config.service)
    def serviceResult = client.describeServices(request)
    def services = serviceResult.getServices()
    if (services.isEmpty()) {
        throw new GroovyRuntimeException("Nothing found for cluster: ${config.cluster} and service: ${config.service}")
    } else {
        return services.first().getDesiredCount() // currently assuming we are only searching for one service, therefore one result back
    }
}


@NonCPS
def setupECSClient(region, awsAccountId = null, role = null) {
  def cb = AmazonECSClientBuilder.standard().withRegion(region)
  def creds = getCredentials(awsAccountId, region, role)
  if(creds != null) {
    cb.withCredentials(new AWSStaticCredentialsProvider(creds))
  }
  return cb.build()
}

@NonCPS
def getCredentials(awsAccountId, region, roleName) {
  def env = System.getenv()
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
