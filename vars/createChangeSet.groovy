/***********************************
createChangeSet DSL

Creates a cloudformation changeset and prints the response.

example usage
createChangeSet(
  description: env.GIT_COMMIT, // (optional, add a description to your changeset)
  region: 'us-east-1', // (required, aws region to deploy the stack)
  stackName: 'my-stack', // (required, name of the CloudFormation stack)
  awsAccountId: '012345678901', // (optional, aws account the cloudformation stack is to be deployed to)
  role: 'iam-role-name', // (optional, IAM role to assume if deploying to a different aws account)
  roleArn: 'iam-cfn-service-role', // (optional, execution role arn for the cloudformation service)
  maxErrorRetry: 3, // (optional, defaults to 3)
  parameters: [ // (optional, map of key value pairs)
    key: value
  ],
  templateUrl: 'bucket/template.yaml' // (required, full s3 path of template),
  tags: [ // (optional, tags to add to the cloudformation stack)
    key: value
  ],
  capabilities: false // (optional, set to false to remove capabilities or supply a list of capabilities. Defaults to [CAPABILITY_IAM,CAPABILITY_NAMED_IAM])
)

returns Map with summarised stack changes
[
  changeset: 'cs-0000-0000-0000-0000',
  stack: 'my-stack',
  region: 'ap-southeast-2',
  changes: [
    [
      stack: 'my-stack',
      action: 'Add|Modify|Remove',
      logical:  'MyInstance',
      resourceType: 'AWS::EC2Instance', 
      replace: 'True|Conditional|N/A',
      details:[
        [
          name: 'InstanceType',
          attribute: 'Properties'
        ]
      ]
    ]
  ]
]
************************************/

@Grab(group='com.jakewharton.fliptables', module='fliptables', version='1.1.0')

import com.base2.ciinabox.aws.AwsClientBuilder
import com.base2.ciinabox.aws.CloudformationStack

import com.amazonaws.services.cloudformation.model.CreateChangeSetRequest
import com.amazonaws.services.cloudformation.model.DescribeChangeSetRequest
import com.amazonaws.services.cloudformation.model.ListChangeSetsRequest
import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.cloudformation.model.Tag
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException
import com.amazonaws.services.cloudformation.model.AlreadyExistsException
import com.amazonaws.services.cloudformation.waiters.AmazonCloudFormationWaiters
import com.amazonaws.waiters.WaiterParameters
import com.amazonaws.waiters.WaiterUnrecoverableException

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest

import com.jakewharton.fliptables.FlipTable

def call(body) {
  def config = body

  def changeSetName = config.get('changeSetName', "cs-${UUID.randomUUID().toString()}")
  
  def clientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.get('awsAccountId'),
    role: config.get('role'),
    maxErrorRetry: config.get('maxErrorRetry', 3),
    env: env])
    
  createChangeSet(clientBuilder,changeSetName,config)
  wait(clientBuilder,changeSetName,config.stackName)

  def stackChanges = getChangeSetDetails(clientBuilder, config.stackName, changeSetName)
  def changeMap = [
    changeset: changeSetName,
    stack: config.stackName,
    region: config.region,
    changes: []
  ]

  if (stackChanges) {
    changeMap.changes << collectChanges(stackChanges, config.stackName)
    def nestedStacks = collectNestedStacks(stackChanges)

    def nestedChanges = null
    def nestedChangeList = null
    def nestedNestedStacks = null
    def nestedChangeSet = null

    if (nestedStacks) {
      nestedStacks.each { stack ->
        echo("Getting changeset for nested stack ${stack}")
        nestedChangeSet = getNestedChangeSet(clientBuilder, changeSetName, stack)
        if (nestedChangeSet) {
          wait(clientBuilder, nestedChangeSet, stack)
          nestedChanges = getChangeSetDetails(clientBuilder, stack, nestedChangeSet)
          changeMap.changes << collectChanges(nestedChanges, stack)
        } else {
          echo("Unable to find changes set for nested stack ${stack}")
        }
      }
    }
    printChanges(changeMap.changes,config.stackName)
  }

  def stackNameUpper = config.stackName.toUpperCase().replaceAll("-", "_")
  env["${stackNameUpper}_CHANGESET_NAME"] = changeSetName

  return changeMap
}

def createChangeSet(clientBuilder,changeSetName,config) {
  def cfclient = clientBuilder.cloudformation()
  def cfstack = new CloudformationStack(cfclient, config.stackName)
  def changeSetType = cfstack.getChangeSetType()

  def request = new CreateChangeSetRequest()
    .withStackName(config.stackName)
    .withChangeSetType(changeSetType)
    .withDescription(config.get('description','ciinabox automated change set'))
    .withChangeSetName(changeSetName)

  if (config.capabilities == null) {
    request.withCapabilities(['CAPABILITY_IAM','CAPABILITY_NAMED_IAM'])
  } else if (config.capabilities) {
    request.withCapabilities(config.capabilities)
  }

  if (config.templateUrl) {
    request.withTemplateURL(config.templateUrl)
  } else {
    request.withUsePreviousTemplate(true)
  }

  if (config.roleArn) {
    echo "using cloudformation service role arn ${config.roleArn}"
    request.withRoleARN(config.roleArn)
  }

  def params = cfstack.getStackParams(config.parameters, config.get('templateUrl'))
  if (params.size() > 0) {
    request.withParameters(params)
  }

  def tags = []
  config.tags.each {
    tags << new Tag().withKey(it.key).withValue(it.value)
  }

  if (tags.size() > 0) {
    request.withTags(tags)
  }

  if (config.nestedStacks) {
    request.withIncludeNestedStacks(true)
  }

  echo "Creating change set ${changeSetName} for stack ${config.stackName} with operation ${changeSetType}"

  try {
    cfclient.createChangeSet(request)
  } catch (AlreadyExistsException ex) {
    error("Change set with name ${changeSetName} already exists. Use a different name and try again.")
  } catch (AmazonCloudFormationException ex) {
    if (ex.getErrorMessage().find(/^Parameters:(.*)must\shave\svalues$/)) {
      error("Missing parameters in the createChangeSet() step. ${ex.getErrorMessage()}")
    } else {
      error("Failed to create the changeset due to error: ${ex.getErrorMessage()}")
    }
  } finally {
      cfclient = null
  }
}

def getNestedChangeSet(clientBuilder, changeSetName, stackName) {
  def cfclient = clientBuilder.cloudformation()
  def listRequest = new ListChangeSetsRequest()
    .withStackName(stackName)

  def changeSets = cfclient.listChangeSets(listRequest).getSummaries()
  def selected = changeSets.find {it.getChangeSetName().contains(changeSetName)}

  cfclient = null
  return selected ? selected.getChangeSetName() : null
}

def wait(clientBuilder,changeSetName,stackName) {
  def cfclient = clientBuilder.cloudformation()
  echo "Waiting for change set ${changeSetName} for stack ${stackName} to complete"

  def request = new DescribeChangeSetRequest()
    .withStackName(stackName)
    .withChangeSetName(changeSetName)

  try {
    new AmazonCloudFormationWaiters(cfclient).changeSetCreateComplete().run(new WaiterParameters(request))
  } catch (WaiterUnrecoverableException ex) {
    if (ex.getMessage().equals('Resource never entered the desired state as it failed.')) {
      def changeset = cfclient.describeChangeSet(request)
      error("Change set ${changeSetName} for stack ${stackName} ${changeset.getStatus()} because ${changeset.getStatusReason()}")
    } else {
      error("Failed to wait for the changeset due to error: ${ex.getErrorMessage()}")
    }
  } finally {
    cfclient = null
  }
  
  return true
}

def getChangeSetDetails(clientBuilder, stackName, changeSetName) {
  def cfclient = clientBuilder.cloudformation()
  def request = new DescribeChangeSetRequest()
      .withStackName(stackName)
      .withChangeSetName(changeSetName)
  def cs =  cfclient.describeChangeSet(request).getChanges()
  cfclient = null
  return cs
}

@NonCPS
def collectChanges(changes, stackName) {
  def changeList = []
  
  changes.each {
    def change = it.getResourceChange()
    def changeMap = [stack: stackName]

    changeMap.action = change.getAction()
    changeMap.logical = change.getLogicalResourceId()
    changeMap.resourceType = change.getResourceType()
    changeMap.replace = change.getReplacement() ? change.getReplacement() : 'N/A'
    changeMap.details = []

    if (changeMap.action.equals('Modify')) {
      def details = change.getDetails()
      details.each {
        changeMap.details << [name: it.getTarget().getName(), attribute: it.getTarget().getAttribute()]
      }
    }

    changeList << changeMap
  }

  return changeList.sort { c1, c2 -> c1.action <=> c2.action }
}

@NonCPS
def collectNestedStacks(changes) {
  def nestedStacks = []

  changes.each {
    def change = it.getResourceChange()
    if (change.getResourceType().equals('AWS::CloudFormation::Stack')) {
      if (change.getPhysicalResourceId()) {
        nestedStacks << change.getPhysicalResourceId()
      }
    }
  }

  return nestedStacks
}

def printChanges(changeList,stackName) {
  def border = null
  def title = null
  def changeString = ""
  String[] headers = ['Operation', 'LogicalResourceId', 'ResourceType', 'Replace', 'Details']
  ArrayList data = []

  changeList.each { changes ->
    border = "\n+" + "-".multiply(7) + "+" + "-".multiply(changes.getAt(0).stack.length() + 2) + "+"
    changeString += "${border}\n| Stack | ${changes.getAt(0).stack} |${border}" + "\n"
    data = []
    if (changes) {
      changes.each { change ->
        data.add([change.action, change.logical, change.resourceType, change.replace, change.details.collect{it.name}.join('\n')])
      }
      changeString += FlipTable.of(headers, data as String[][]).toString()
    }
  }

  echo changeString
}
