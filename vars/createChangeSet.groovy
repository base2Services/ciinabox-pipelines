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
  failOnEmptyChangeSet: true | false, // if set to false createChangeSet wont error when no changes are detected
  templateUrl: 'bucket/template.yaml' // (required, full s3 path of template),
  tags: [ // (optional, tags to add to the cloudformation stack)
    key: value
  ],
  capabilities: false, // (optional, set to false to remove capabilities or supply a list of capabilities. Defaults to [CAPABILITY_IAM,CAPABILITY_NAMED_IAM]),
  nestedStacks: true | false // (optional, defaults to false, set to true to add nestedStacks diff )
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
import com.amazonaws.services.cloudformation.model.DeleteChangeSetRequest
import com.amazonaws.services.cloudformation.model.ListChangeSetsRequest
import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.cloudformation.model.Tag
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException
import com.amazonaws.services.cloudformation.model.AlreadyExistsException
import com.amazonaws.services.cloudformation.waiters.AmazonCloudFormationWaiters
import com.amazonaws.waiters.WaiterParameters
import com.amazonaws.waiters.WaiterUnrecoverableException

import com.amazonaws.services.s3.model.AmazonS3Exception

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest

import com.jakewharton.fliptables.FlipTable

def call(body) {
  def config = body

  def changeSetName = config.get('changeSetName', "cs-${UUID.randomUUID().toString()}")
  def stackNameUpper = config.stackName.toUpperCase().replaceAll("-", "_")
  // Converting the stackName to a string prevents passing a Groovy encoded string 
  // to this script, resulting in null being returned when searching for the stack
  def stackName = config.stackName.toString()
  env["${stackNameUpper}_CHANGESET_NAME"] = changeSetName
  
  def clientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.get('awsAccountId'),
    role: config.get('role'),
    maxErrorRetry: config.get('maxErrorRetry', 3),
    env: env])
    
  createChangeSet(clientBuilder, changeSetName, stackName, config)
  def success = wait(clientBuilder, changeSetName, stackName, true)

  def failOnEmptyChangeSet = config.get('failOnEmptyChangeSet', false)
  // if there were no changes in our changeset
  if (!success && !failOnEmptyChangeSet) {
    env["${stackNameUpper}_NO_EXECUTE_CHANGESET"] = 'TRUE'
    return null
  }

  def stackChanges = getChangeSetDetails(clientBuilder, stackName, changeSetName)
  def changeMap = [
    changeset: changeSetName,
    stack: stackName,
    region: config.region,
    changes: []
  ]

  if (stackChanges) {
    changeMap.changes << collectChanges(stackChanges, stackName)
    def nestedStacks = collectNestedStacks(stackChanges)

    def nestedChanges = null
    def nestedChangeList = null
    def nestedNestedStacks = null
    def nestedChangeSet = null

    if (nestedStacks) {

      nestedStacks.each { stack ->
        nestedChangeSet = getNestedChangeSet(clientBuilder, changeSetName, stack)
        if (nestedChangeSet) {
          wait(clientBuilder, nestedChangeSet, stack, false)
          nestedChanges = getChangeSetDetails(clientBuilder, stack, nestedChangeSet)
          changeMap.changes << collectChanges(nestedChanges, stack)
        } 
      }
    }
    printChanges(changeMap.changes,stackName)
  }

  return changeMap
}


def createChangeSet(clientBuilder,changeSetName,stackName,config) {
  def cfclient = clientBuilder.cloudformation()
  def cfstack = new CloudformationStack(clientBuilder, stackName)
  def changeSetType = cfstack.getChangeSetType()

  def request = new CreateChangeSetRequest()
    .withStackName(stackName)
    .withChangeSetType(changeSetType)
    .withDescription(config.get('description','ciinabox automated change set'))
    .withChangeSetName(changeSetName)

  if (config.capabilities == null) {
    request.withCapabilities(['CAPABILITY_IAM','CAPABILITY_NAMED_IAM','CAPABILITY_AUTO_EXPAND'])
  } else if (config.capabilities) {
    request.withCapabilities(config.capabilities)
  }

  if (config.templateUrl) {
    request.withTemplateURL(config.templateUrl)
  } else {
    request.withUsePreviousTemplate(true)
  }

  if (config.roleArn) {
    steps.echo "using cloudformation service role arn ${config.roleArn}"
    request.withRoleARN(config.roleArn)
  }

  try {
    def params = cfstack.getStackParams(config.parameters, config.get('templateUrl'))
    if (params.size() > 0) {
      request.withParameters(params)
    }
  } catch (AmazonS3Exception ex) {
    println(ex)
    if(ex.message.contains('Access Denied (Service: Amazon S3; Status Code: 403;')) {
      throw ex;
    }
    println ("============\nThe specified CloudFormation template ${config.get('templateUrl')} was not found!\nIt seems it was not built in previous build task.\n============\n")
    env.ERROR_S3_KEY_DOES_NOT_EXIST = true
    currentBuild.getRawBuild().getExecutor().interrupt(Result.NOT_BUILT)
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

  steps.echo "Creating change set ${changeSetName} for stack ${stackName} with operation ${changeSetType}"

  try {
    cfclient.createChangeSet(request)
  } catch (AlreadyExistsException ex) {
    steps.error("Change set with name ${changeSetName} already exists. Use a different name and try again.")
  } catch (AmazonCloudFormationException ex) {
    if (ex.getErrorMessage().find(/^Parameters:(.*)must\shave\svalues$/)) {
      steps.error("Missing parameters in the createChangeSet() step. ${ex.getErrorMessage()}")
    } else {
      steps.error("Failed to create the changeset due to error: ${ex.getErrorMessage()}")
    }
  } finally {
      cfstack = null
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

def wait(clientBuilder, changeSetName, stackName, verbose) {
  def cfclient = clientBuilder.cloudformation()
  if (verbose) {
    echo "Waiting for change set ${changeSetName} for stack ${stackName} to complete"
  }

  def request = new DescribeChangeSetRequest()
    .withStackName(stackName)
    .withChangeSetName(changeSetName)

  try {
    new AmazonCloudFormationWaiters(cfclient).changeSetCreateComplete().run(new WaiterParameters(request))
  } catch (WaiterUnrecoverableException ex) {
    if (ex.getMessage().equals('Resource never entered the desired state as it failed.')) {
      def changeset = cfclient.describeChangeSet(request)
      if (changeset.getStatusReason().contains("The submitted information didn't contain changes.") || changeset.getStatusReason().contains("No updates are to be performed.")) {
        steps.echo("WARNING: No changes were detected when creating the changeset")
        def deleteRequest =  new DeleteChangeSetRequest()
            .withStackName(stackName)
            .withChangeSetName(changeSetName)
        def r = cfclient.deleteChangeSet(deleteRequest)
        return false
      }
    }
    throw ex
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
        changeMap.details << [name: it.getTarget().getName(), attribute: it.getTarget().getAttribute(), causingEntity: it.getCausingEntity(), evaluation: it.getEvaluation()]
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

@NonCPS
def printChanges(changeList,stackName) {
  def border = null
  def title = null
  def changeString = ""
  String[] headers = ['Operation', 'LogicalResourceId', 'ResourceType', 'Replace', 'Details']
  ArrayList data = []

  changeList.each { changes ->
    if (changes) {
      border = "\n+" + "-".multiply(7) + "+" + "-".multiply(changes.getAt(0).stack.length() + 2) + "+"
      changeString += "${border}\n| Stack | ${changes.getAt(0).stack} |${border}" + "\n"
      data = []
      changes.each { change ->
        data.add([change.action, change.logical, change.resourceType, change.replace, change.details.collect{
          "${it.causingEntity? it.causingEntity:''}[${it.name}] (${it.evaluation})"
        }.join('\n')])
      }
      changeString += FlipTable.of(headers, data as String[][]).toString()
    }
  }

  echo changeString
}
