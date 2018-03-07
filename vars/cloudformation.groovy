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

def call(body) {
  def config = body
  def cf = setupClient(config.region)
  switch(config.action) {
    case 'create':
      create(cf, config)
    break
  }
}

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
    wait(cf,config.stackName)
  } else {
    println "Environment ${config.stackName} already Exists"
  }
}

def wait(cf, stackName) {
  def wait = new DescribeStacksRequest().withStackName(stackName);
  def completed = false;
  while (!completed) {
    List<Stack> stacks = cf.describeStacks(wait).getStacks();
    if (stacks.isEmpty()) {
        completed   = true;
    } else {
        for (Stack stack : stacks) {
            if (stack.getStackStatus().equals(StackStatus.CREATE_COMPLETE.toString()) ||
                    stack.getStackStatus().equals(StackStatus.CREATE_FAILED.toString()) ||
                    stack.getStackStatus().equals(StackStatus.ROLLBACK_FAILED.toString()) ||
                    stack.getStackStatus().equals(StackStatus.DELETE_FAILED.toString())) {
                completed = true;
            }
        }
    }

    // Show we are waiting
    System.out.print(".");

    // Not done yet so sleep for 10 seconds.
    if (!completed) Thread.sleep(10000);
  }
}

def doesStackExist(cf, stackName) {
  try {
    DescribeStacksResult result = cf.describeStacks(new DescribeStacksRequest().withStackName(stackName))
    return result != null
  } catch (AmazonCloudFormationException ex) {
    return false
  }
}

def setupClient(region) {
  return AmazonCloudFormationClientBuilder.standard()
    .withRegion(region)
    .build()
}
