/***********************************
codedeploy DSL

performs codedeploy operations

example usage
codedeploy(
  region: env.REGION,
  accountId: env.DEV_ACCOUNT_ID,
  role: 'ciinabox',
  applicationName: 'myapp',
  deploymentGroupName: 'webservers',
  deploymentConfigName: 'CodeDeployDefault.OneAtATime',
  description: 'Deploying application'                  // Optional
  fileExistsBehavior: 'DISALLOW | OVERWRITE | RETAIN',  // Optional
  s3bucket: env.SOURCE_BUCKET,
  key: 'codedeploy/deploy.zip',
  bundleType: 'zip'
) 

************************************/

import com.amazonaws.auth.*
import com.amazonaws.regions.*
import com.amazonaws.services.codedeploy.*
import com.amazonaws.services.codedeploy.model.*
import com.amazonaws.services.securitytoken.*
import com.amazonaws.services.securitytoken.model.*
import com.amazonaws.waiters.*

import java.util.concurrent.*

def call(body) {
  def config = body
  if(createDeployment(config)) {
    echo "Successfully deployed ${config.applicationName}"
  } else {
    currentBuild.result = 'FAILED'
  }
}

@NonCPS
def createDeployment(config) {
  def codedeploy = setupClient(config.region, config.accountId, config.role)

  def applicationName = config.applicationName
  def deploymentGroupName = config.deploymentGroupName

  def deploymentRequest = new CreateDeploymentRequest()
    .withApplicationName(applicationName)
    .withDeploymentGroupName(deploymentGroupName)
    .withFileExistsBehavior('OVERWRITE')
    .withRevision(new RevisionLocation()
      .withRevisionType('S3')
      .withS3Location(new S3Location()
        .withBucket(config.s3bucket)
        .withKey(config.key)
        .withBundleType(config.get('bundleType', 'tgz'))
      )
    )
    .withDescription(config.get('description', "Deployment version ${env.BUILD_NUMBER}"))
    
  
  if(config.deploymentConfigName) {
    deploymentRequest.withDeploymentConfigName(config.deploymentConfigName)
  }

  if(config.fileExistsBehavior) {
    deploymentRequest.withFileExistsBehavior(config.fileExistsBehavior)
  }

  try {
    waitForExistingDeployment(codedeploy, applicationName, deploymentGroupName)
    def deployment = codedeploy.createDeployment(deploymentRequest)
    return wait(codedeploy, deployment.getDeploymentId())
  } catch(ApplicationDoesNotExistException ex) {
    echo "CodeDeploy Application ${applicationName} not found for account ${config.accountId}"
  }
  return false
}

@NonCPS
def waitForExistingDeployment(codedeploy, applicationName, deploymentGroupName) {
  def deployments = codedeploy.listDeployments(new ListDeploymentsRequest()
      .withApplicationName(applicationName)
      .withDeploymentGroupName(deploymentGroupName)
      .withIncludeOnlyStatuses(
        DeploymentStatus.Created,
        DeploymentStatus.Queued,
        DeploymentStatus.InProgress
      )
    )
    if(deployments.getDeployments() != null && deployments.getDeployments().size() > 0) {
      existingDeployment = deployments.getDeployments().get(0)
      echo "Waiting for existing deployment ${existingDeployment} to complete"
      wait(codedeploy, existingDeployment)
    }
}

@NonCPS
def wait(codedeploy, deployment) {
  def waiter = codedeploy.waiters().deploymentSuccessful()
  try {
    Future future = waiter.runAsync(
      new WaiterParameters<>(new GetDeploymentRequest().withDeploymentId(deployment)),
      new NoOpWaiterHandler()
    )
    echo "Waiting for codedeploy ${deployment} to complete"
    while(!future.isDone()) {
      try {
        def deployInfo = codedeploy.getDeployment(new GetDeploymentRequest().withDeploymentId(deployment)).getDeploymentInfo()
        echo "${deployInfo.deploymentId}: ${deployInfo.status} - ${deployInfo.deploymentOverview}"
        Thread.sleep(10000)
      } catch(InterruptedException ex) {
          echo "We seem to be timing out ${ex}...ignoring"
      }
    }
    def result = codedeploy.getDeployment(new GetDeploymentRequest().withDeploymentId(deployment))
    return result.getDeploymentInfo().getStatus() == 'Succeeded'

   } catch(Exception e) {
     println "Codedeploy: ${deployment} failed - ${e}"
     return false
   }
}

@NonCPS
def setupClient(region, awsAccountId = null, role =  null) {
  def client = AmazonCodeDeployClientBuilder.standard().withRegion(region)
  def creds = getCredentials(awsAccountId, region, role)
  if(creds != null) {
    client.withCredentials(new AWSStaticCredentialsProvider(creds))
  }
  return client.build()
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
  println "Assuming IAM role ${roleArn}"
  def sts = new AWSSecurityTokenServiceClient()
  if (!region.equals("us-east-1")) {
      sts.setEndpoint("sts." + region + ".amazonaws.com")
  }
  def assumeRoleResult = sts.assumeRole(new AssumeRoleRequest()
            .withRoleArn(roleArn).withDurationSeconds(3600)
            .withRoleSessionName(roleSessionName))
  return assumeRoleResult.getCredentials()
}