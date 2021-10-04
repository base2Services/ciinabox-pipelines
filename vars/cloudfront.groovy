/***********************************
cloudfront DSL

performs cloudfront operations

example usage
cloudfront(
  distribution: 'E2FY4Z7K9HELLO',
  action: 'invalidate'
  invalidationPath: ['/img/*.jpg', '/blog/bezos.html', '/etc/passwd'] (optional, defaults to just /*)
  accountId: '1234567890', #the aws account Id you want the stack operation performed in
  role: 'myrole', # the role to assume from the account the pipeline is running from,
  maxErrorRetry: 3,
  waitUntilComplete: false, (wait for the invalidation to be complete, this can take up to 15 minutes)
  roleArn: 'arn:aws:iam::<accountid>:role/deploy' (optional, specify cloudformation service role)
)

************************************/

import com.amazonaws.auth.*
import com.amazonaws.regions.*
import com.amazonaws.services.cloudfront.*
import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest
import com.amazonaws.services.cloudfront.model.CreateInvalidationResult
import com.amazonaws.services.cloudfront.model.GetInvalidationResult
import com.amazonaws.services.cloudfront.model.GetInvalidationRequest
import com.amazonaws.services.cloudfront.model.Paths
import com.amazonaws.services.cloudfront.model.InvalidationBatch
import com.amazonaws.services.cloudfront.model.AmazonCloudFrontException
import com.amazonaws.services.securitytoken.*
import com.amazonaws.services.securitytoken.model.*
import com.amazonaws.services.simplesystemsmanagement.*
import com.amazonaws.services.simplesystemsmanagement.model.*
import com.amazonaws.waiters.*
import com.amazonaws.ClientConfiguration
import com.amazonaws.retry.RetryPolicy
import com.amazonaws.retry.PredefinedRetryPolicies.SDKDefaultRetryCondition
import com.amazonaws.retry.PredefinedBackoffStrategies.SDKDefaultBackoffStrategy
import org.yaml.snakeyaml.Yaml
import groovy.json.JsonSlurperClassic
import java.util.concurrent.*
import java.io.InputStreamReader
import com.base2.ciinabox.aws.IntrinsicsYamlConstructor
import java.time.Instant

def call(body) {
  def config = body
  def cf = setupCfClient(config.accountId, config.role, config.maxErrorRetry)

  if(config.action){
    handleActionRequest(cf, config)
  }
}

@NonCPS
def handleActionRequest(cf, config){
  def success = false

  switch(config.action) {
    case 'invalidate':
      path = []
      if(config.invalidationPath != null){
        path = config.invalidationPath
      }else{
        path += '/*'
      }

      if(invalidateCfCache(cf,config.distribution,path,config)) {
        println "CloudFront Distribution ${config.distribution} cache has been invalidated"
      } else {
        println "CloudFront Distribution ${config.distribution} cache has NOT been invalidated"
        break
      }
      success = true
      break
  }

  if(!success) {
    throw new Exception("Error ${config.distribution} failed to ${config.action}")
  }
}

@NonCPS
def invalidateCfCache(cf, distribution, path, config) {
  try {
    def timestamp = Instant.now().getEpochSecond().toString();
    def paths = new Paths()
                    .withItems(path)
                    .withQuantity(path.size());

    def invalidation_batch = new InvalidationBatch(paths, timestamp); // Using unix timestamp as unique ref
    String invalidationId = cf.createInvalidation(new CreateInvalidationRequest(distribution, invalidation_batch)).getInvalidation().getId();

    if(config.waitUntilComplete != true) {
      return true
    }
    def output = cf.waiters().invalidationCompleted().run(new WaiterParameters<GetInvalidationRequest>(new GetInvalidationRequest(distribution, invalidationId)));
    return true

  } catch (AmazonCloudFrontException ex) {
    throw ex
  }
}

@NonCPS
def setupCfClient(awsAccountId = null, role =  null, maxErrorRetry=10) {
  ClientConfiguration clientConfiguration = new ClientConfiguration()
  maxErrorRetry = (maxErrorRetry == null)? 10 : maxErrorRetry
  clientConfiguration.withRetryPolicy(new RetryPolicy(new SDKDefaultRetryCondition(), new SDKDefaultBackoffStrategy(), maxErrorRetry, true))
  
  def cb = AmazonCloudFrontClientBuilder.standard()
    .withClientConfiguration(clientConfiguration)
  def creds = getCredentials(awsAccountId, role)
  if(creds != null) {
    cb.withCredentials(new AWSStaticCredentialsProvider(creds))
  }
  return cb.build()
}


@NonCPS
def getCredentials(awsAccountId, roleName) {
  if(env['AWS_SESSION_TOKEN'] != null) {
    return new BasicSessionCredentials(
      env['AWS_ACCESS_KEY_ID'],
      env['AWS_SECRET_ACCESS_KEY'],
      env['AWS_SESSION_TOKEN']
    )
  } else if(awsAccountId != null && roleName != null) {
    def stsCreds = assumeRole(awsAccountId, roleName)
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
def assumeRole(awsAccountId, roleName) {
  def roleArn = "arn:aws:iam::" + awsAccountId + ":role/" + roleName
  def roleSessionName = "sts-session-" + awsAccountId
  println "assuming IAM role ${roleArn}"
  def sts = new AWSSecurityTokenServiceClient()
  def assumeRoleResult = sts.assumeRole(new AssumeRoleRequest()
            .withRoleArn(roleArn).withDurationSeconds(3600)
            .withRoleSessionName(roleSessionName))
  return assumeRoleResult.getCredentials()
}


