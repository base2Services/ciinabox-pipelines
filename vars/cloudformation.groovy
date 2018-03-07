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
    }
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
  }
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
    waiter.run(new WaiterParameters<>(new DescribeStacksRequest().withStackName(stackName))
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
