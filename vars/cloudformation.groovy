/***********************************
cloudformation DSL

performs cloudformation operations

example usage
cloudformation
  stackName: 'dev'
  queryType: 'element' | 'output' ,  # either queryType or action should be supplied
  query: 'mysubstack.logicalname1' | 'outputKey', # depending on queryType
  action: 'create'|'update'|'delete',
  region: 'ap-southeast-2',
  templateUrl: 'https://s3.amazonaws.com/mybucket/cloudformation/app/master.json',
  parameters: [
    'ENVIRONMENT_NAME' : 'dev',
  ],
  accountId: '1234567890' #the aws account Id you want the stack operation performed in
  role: 'myrole' # the role to assume from the account the pipeline is running from
)

If you omit the templateUrl then for updates it will use the existing template

************************************/
@Grab(group='com.amazonaws', module='aws-java-sdk-cloudformation', version='1.11.359')
@Grab(group='com.amazonaws', module='aws-java-sdk-iam', version='1.11.359')
@Grab(group='com.amazonaws', module='aws-java-sdk-sts', version='1.11.359')

import com.amazonaws.auth.*
import com.amazonaws.regions.*
import com.amazonaws.services.cloudformation.*
import com.amazonaws.services.cloudformation.model.*
import com.amazonaws.services.securitytoken.*
import com.amazonaws.services.securitytoken.model.*
import com.amazonaws.waiters.*

import java.util.concurrent.*

def call(body) {
  def config = body
  def cf = setupClient(config.region, config['accountId'], config['role'])

  if(!(config.action || config.queryType)){
    throw new GroovyRuntimeException("Either action or queryType (or both) must be specified")
  }

  if(config.action){
    handleActionRequest(cf, config)
  }

  if(config.queryType){
    return handleQueryRequest(cf, config)
  }

}

@NonCPS
def handleActionRequest(cf, config){
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
      delete(cf, config)
      success = wait(cf, config.stackName, StackStatus.DELETE_COMPLETE)
      break
    case 'update':
      if(update(cf, config)) {
        success = wait(cf, config.stackName, StackStatus.UPDATE_COMPLETE)
      } else {
        success = true
      }
    break
  }
  if(!success) {
    throw new Exception("Stack ${config.stackName} failed to ${config.action}")
  }
}

@NonCPS
def handleQueryRequest(cf, config){
  if(!doesStackExist(cf,config.stackName)){
    throw new GroovyRuntimeException("Can't query stack ${config.stackName} as it does not exist")
  }
  switch(config.queryType){
    case 'element':
      return queryStackElement(cf, config)
    case 'output':
      return queryStackOutput(cf, config)
    default:
      throw new GroovyRuntimeException("Unknown queryType '${config.queryType}'. Valid types are: element,output")
  }
}

@NonCPS
def queryStackElement(cf, config){
  try {
    def cfnPathElements = config.query.split("\\."),
        elementName = cfnPathElements[0],
        result = cf.describeStackResource(new DescribeStackResourceRequest()
                .withStackName(config.stackName)
                .withLogicalResourceId(elementName)
        )

        if(cfnPathElements.size() > 1){
          config.query = cfnPathElements[1..cfnPathElements.size()-1].join('.')
          config.stackName = result.stackResourceDetail.physicalResourceId
          return queryStackElement(cf, config)
        }

        if(cfnPathElements.size() == 1){
          return [
                  LogicalResourceId: result.stackResourceDetail.logicalResourceId,
                  PhysicalResourceId: result.stackResourceDetail.physicalResourceId,
                  ResourceStatus: result.stackResourceDetail.resourceStatus,
                  StackId: result.stackResourceDetail.stackId
          ]
        }
  } catch (AmazonCloudFormationException ex) {
    throw new GroovyRuntimeException("Couldn't describe stack resource ${config.query} on stack ${config.stackName}", ex)
  }
}

@NonCPS
def queryStackOutput(cf, config){
  try {
    def result = cf.describeStacks(new DescribeStacksRequest().withStackName(config.stackName))
    def stackInfo = result.getStacks().get(0),
        output = stackInfo.getOutputs().find { it.outputKey.equals(config.query) }

    if (output == null){
      throw new GroovyRuntimeException("Stack ${config.stackName} does not have output named '${config.query}'")
    }

    return output.outputValue

  } catch (AmazonCloudFormationException ex) {
    throw new GroovyRuntimeException("Couldn't describe stack ${config.stackName}", ex)
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
def delete(cf, config) {
  if(doesStackExist(cf, config.stackName)) {
    waitUntilComplete(cf, config.stackName)
    cf.deleteStack(new DeleteStackRequest()
      .withStackName(config.stackName)
    )
  } else {
    println "ignoring delete since stack ${config.stackName} does not exist"
  }
}

@NonCPS
def update(cf, config) {
  if(doesStackExist(cf, config.stackName)) {
    waitUntilComplete(cf, config.stackName)
    def request = new UpdateStackRequest()
      .withStackName(config.stackName)
      .withParameters(getStackParams(cf, config.stackName, config.parameters))
      .withCapabilities('CAPABILITY_IAM', 'CAPABILITY_NAMED_IAM')
    if(config['templateUrl']) {
      request.withTemplateURL(config.templateUrl)
    } else {
      request.withUsePreviousTemplate(true)
    }
    try {
      cf.updateStack(request)
      return true
    } catch(AmazonCloudFormationException ex) {
      if(!ex.message.contains("No updates are to be performed")) {
        throw ex
      }
      return false
    }
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
def wait(cf, stackName, successStatus)   {
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
      new NoOpWaiterHandler()
    )
    while(!future.isDone()) {
      try {
        echo "waitng for stack operation to complete"
        Thread.sleep(10000)
      } catch(InterruptedException ex) {
          echo "We seem to be timing out ${ex}...ignoring"
      }
    }
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
    if(ex.message.contains("does not exist")) {
      return false
    } else {
      throw ex
    }
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

@NonCPS
def waitUntilComplete(cf, stackName) {
  DescribeStacksResult result = cf.describeStacks(new DescribeStacksRequest().withStackName(stackName))
  currentState = result.getStacks().get(0).getStackStatus()
  waitStatus = StackStatus.UPDATE_COMPLETE
  switch(currentState) {
    case 'CREATE_COMPLETE':
    case 'UPDATE_COMPLETE':
      return
    break
    case 'CREATE_IN_PROGRESS':
      waitStatus = StackStatus.CREATE_COMPLETE
    break
  }
  print "waiting for stack ${stackName} to finish - ${currentState}"
  wait(cf, stackName, waitStatus)
}
