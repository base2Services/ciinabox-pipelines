/***********************************
cloudformation DSL

performs cloudformation operations

example usage
cloudformation
  stackName: 'dev'
  action: 'create'|'update'|'delete',
  region: 'ap-southeast-2',
  templateUrl: 'https://s3.amazonaws.com/mybucket/cloudformation/app/master.json',
  parameters: [
    'ENVIRONMENT_NAME' : 'dev',
  ],
  accountId: '1234567890' #the aws account Id you want the stack operation performed in
  role: 'myrole' # the role to assume from the account the pipeline is running from
  useExistingTemplate: true #ignores templateUrl if true
)
************************************/
@Grab(group='com.amazonaws', module='aws-java-sdk-cloudformation', version='1.11.198')
@Grab(group='com.amazonaws', module='aws-java-sdk-iam', version='1.11.226')
@Grab(group='com.amazonaws', module='aws-java-sdk-sts', version='1.11.226')

import com.amazonaws.auth.*
import com.amazonaws.regions.*
import com.amazonaws.services.cloudformation.*
import com.amazonaws.services.cloudformation.model.*
import com.amazonaws.services.securitytoken.*
import com.amazonaws.services.securitytoken.model.*
import com.amazonaws.waiters.*

def call(body) {
  def config = body
  def cf = setupClient(config.region, config['accountId'], config['role'])
  def success = false
  switch(config.action) {
    case 'create':
      if(!doesStackExist(cf,config.stackName)) {
        create(cf, config)
        success = wait(cf, config.stackName, StackStatus.CREATE_COMPLETE)
      } else {
        println "Environment ${config.stackName} already Exists"
        success = true
      }
    break
    case 'delete':
      delete(cf, config.stackName)
      success = wait(cf, config.stackName, StackStatus.DELETE_COMPLETE)
    break
    case 'update':
      update(cf, config)
      success = wait(cf, config.stackName, StackStatus.UPDATE_COMPLETE)
    break
  }
  if(!success) {
    throw new Exception("Stack ${config.stackName} failed to ${config.action}")
  }
}

@NonCPS
def create(cf, config) {
  println "Creating stack ${config.stackName}"
  def params = []
  config.parameters.each {
    params << new Parameter().withParameterKey(it.key).withParameterValue(it.value)
  }
  cf.createStack(new CreateStackRequest()
    .withStackName(config.stackName)
    .withCapabilities('CAPABILITY_IAM', 'CAPABILITY_NAMED_IAM')
    .withParameters(params)
    .withTemplateURL(config.templateUrl))
}

@NonCPS
def delete(cf, stackName) {
  if(doesStackExist(cf, stackName)) {
    cf.deleteStack(new DeleteStackRequest()
      .withStackName(stackName)
    )
  } else {
    println "ignoring delete since stack ${stackName} does not exist"
  }
}

@NonCPS
def update(cf, config) {
  if(doesStackExist(cf, config.stackName)) {
    cf.updateStack(new UpdateStackRequest()
      .withStackName(config.stackName)
      .withParameters(getStackParams(cf, config.stackName, config.parameters))
      .withTemplateURL(config.templateUrl)
      .withCapabilities('CAPABILITY_IAM', 'CAPABILITY_NAMED_IAM')
    )
  } else {
    throw new Exception("unable to update stack ${config.stackName} it does not exist")
  }
}

@NonCPS
def getStackParams(cf, stackName, overrideParams) {
  def stackParams = [:]
  def stacks = cf.describeStacks(new DescribeStacksRequest().withStackName(stackName)).getStacks()
  if (!stacks.isEmpty()) {
    for(Parameter param: stacks.get(0).getParameters()) {
      stackParams.put(param.getParameterKey(), new Parameter().withParameterKey(param.getParameterKey()).withUsePreviousValue(true))
    }
    overrideParams.each {
      stackParams.put(it.key, new Parameter().withParameterKey(it.key).withParameterValue(it.value))
    }
  }
  println "stack params: ${stackParams.values()}"
  return stackParams.values()
}

@NonCPS
def wait(cf, stackName, successStatus) {
  def waiter = null
  switch(successStatus) {
    case StackStatus.CREATE_COMPLETE:
      waiter = cf.waiters().stackCreateComplete()
    break
    case StackStatus.UPDATE_COMPLETE:
      waiter = cf.waiters().stackUpdateComplete()
    break
    case StackStatus.DELETE_COMPLETE:
      waiter = cf.waiters().stackDeleteComplete()
    break
  }
  try {
    Future future = waiter.runAsync(
      new WaiterParameters<>(new DescribeStacksRequest().withStackName(stackName)),
      new WaiterHandler() {
        @Override
        public void onWaitSuccess(DescribeStacksRequest request) {
            print "Waiting for cloudformation operation complete"
        }

        @Override
        public void onWaitFailure(Exception e) {
          print e.getMessage()
          e.printStackTrace()
        }
    )
    future.get(30, TimeUnit.MINUTES);
    println "Stack: ${stackName} success - ${successStatus}"
    return true
   } catch(Exception e) {
     println "Stack: ${stackName} failed - ${e}"
     return false
   }
}

@NonCPS
def doesStackExist(cf, stackName) {
  try {
    DescribeStacksResult result = cf.describeStacks(new DescribeStacksRequest().withStackName(stackName))
    return result != null
  } catch (AmazonCloudFormationException ex) {
    return false
  }
}

@NonCPS
def setupClient(region, awsAccountId = null, role =  null) {
  def cb = AmazonCloudFormationClientBuilder.standard().withRegion(region)
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
