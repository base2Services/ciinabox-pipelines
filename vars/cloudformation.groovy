/***********************************
cloudformation DSL

performs cloudformation operations

example usage
cloudformation
  stackName: dev
  action: create|update|delete|create_or_update,
  region: ap-southeast-2,
  templateUrl: 's3://mybucket/cloudformation/app/master.json',
  parameters: [
    'ENVIRONMENT_NAME' : 'dev',
  ]
  useExistingTemplate: true #ignores templateUrl if true
)
************************************/
@Grab(group='com.amazonaws', module='aws-java-sdk-cloudformation', version='1.11.198')

import com.amazonaws.services.cloudformation.*
import com.amazonaws.services.cloudformation.model.*
import com.amazonaws.regions.*
import com.amazonaws.waiters.*

def call(body) {
  def config = body
  def cf = setupClient(config.region)
  def success = false
  switch(config.action) {
    case 'create':
      create(cf, config)
      success = wait(cf, config.stackName, StackStatus.CREATE_COMPLETE)
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
  if(!doesStackExist(cf,config.stackName)) {
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
  } else {
    println "Environment ${config.stackName} already Exists"
  }
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
  return stackParams
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
    waiter.run(new WaiterParameters<>(new DescribeStacksRequest().withStackName(stackName)))
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
def setupClient(region) {
  return AmazonCloudFormationClientBuilder.standard()
    .withRegion(region)
    .build()
}
