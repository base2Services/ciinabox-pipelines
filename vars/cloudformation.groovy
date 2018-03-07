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
@Grab(group='com.amazonaws', module='aws-java-sdk-cloudformation', version='1.11.288')

import com.amazonaws.services.cloudformation.*
import com.amazonaws.services.cloudformation.model.*
import com.amazonaws.regions.*

def call(body) {
  def config = body
  def cf = setupClient(config.region)
  switch(config.action) {
    case 'create':
      create(cf, config)
    break
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
      .withTemplateURL(config.templateUrl));
    if(!wait(cf,config.stackName)) {
      throw new Exception("Stack ${config.stackName} failed to create")
    }
  } else {
    println "Environment ${config.stackName} already Exists"
  }
}

@NonCPS
def wait(cf, stackName) {
  def wait = new DescribeStacksRequest().withStackName(stackName)
  def completed = false
  def success = false
  while (!completed) {
    List<Stack> stacks = cf.describeStacks(wait).getStacks()
    if (stacks.isEmpty()) {
        completed   = true
        success = false
        println "Stack ${stackName} completed but has failed - ${stack.getStackStatus()}"
    } else {
        for (Stack stack : stacks) {
            switch(stack.getStackStatus()) {
              case StackStatus.CREATE_COMPLETE.toString():
                completed = true
                success = true
                println "Stack ${stackName} completed successfully"
              break
              case StackStatus.CREATE_FAILED.toString():
              case StackStatus.ROLLBACK_FAILED.toString():
              case StackStatus.DELETE_FAILED.toString():
                println "Stack ${stackName} completed but has failed - ${stack.getStackStatus()}"
                completed = true
                success = false
              break
            }
            println "Stack ${stack.getStackName()} - stack.getStackStatus()"
        }
    }
    // Not done yet so sleep for 10 seconds.
    if (!completed) Thread.sleep(10000)
  }
  return success;
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
