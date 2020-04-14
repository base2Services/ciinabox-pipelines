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
************************************/

import com.base2.ciinabox.aws.AwsClientBuilder
import com.base2.ciinabox.aws.CloudformationStack

import com.amazonaws.services.cloudformation.model.CreateChangeSetRequest
import com.amazonaws.services.cloudformation.model.DescribeChangeSetRequest
import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.cloudformation.model.Tag
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException
import com.amazonaws.services.cloudformation.model.AlreadyExistsException
import com.amazonaws.services.cloudformation.waiters.AmazonCloudFormationWaiters
import com.amazonaws.waiters.WaiterParameters
import com.amazonaws.waiters.WaiterUnrecoverableException

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest

def call(body) {
  def config = body
  
  def changeSetName = "cs-${UUID.randomUUID().toString()}"
  
  def clientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.get('awsAccountId'),
    role: config.get('role'),
    maxErrorRetry: config.get('maxErrorRetry', 3),
    env: env])
    
  def cfclient = clientBuilder.cloudformation()
  createChangeSet(cfclient,changeSetName,config)
  def success = wait(cfclient,changeSetName,config.stackName, config.ignoreEmptyChanges)
  
  if (success) {
    def request = new DescribeChangeSetRequest()
      .withStackName(config.stackName)
      .withChangeSetName(changeSetName)
    def changes = cfclient.describeChangeSet(request).getChanges()
    
    if (changes) {
      printChanges(changes,config.stackName)
    }
  }
  def stackNameUpper = config.stackName.toUpperCase().replaceAll("-", "_")
  env["${stackNameUpper}_CHANGESET_NAME"] = changeSetName
}

def createChangeSet(cfclient,changeSetName,config) {
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

  echo "Creating change set ${changeSetName} for stack ${config.stackName} with operation ${changeSetType}"

  try {
    cfclient.createChangeSet(request)
  } catch (AlreadyExistsException ex) {
    error "Change set with name ${changeSetName} already exists. Use a different name and try again."
  } catch (AmazonCloudFormationException ex) {
    if (ex.getErrorMessage().find(/^Parameters:(.*)must\shave\svalues$/)) {
      error "Missing parameters in the createChangeSet() step. ${ex.getErrorMessage()}"
    } else {
      error "Failed to create the changeset due to error: ${ex.getErrorMessage()}"
    }
  }
}

def wait(cfclient,changeSetName,stackName,ignoreEmptyChanges) {
  echo "Waiting for change set ${changeSetName} for stack ${stackName} to complete"

  def request = new DescribeChangeSetRequest()
    .withStackName(stackName)
    .withChangeSetName(changeSetName)

  try {
    new AmazonCloudFormationWaiters(cfclient).changeSetCreateComplete().run(new WaiterParameters(request))
  } catch (WaiterUnrecoverableException ex) {
    if (ex.getMessage().equals('Resource never entered the desired state as it failed.')) {
      def changeset = cfclient.describeChangeSet(request)
      error "Change set ${changeSetName} for stack ${stackName} ${changeset.getStatus()} because ${changeset.getStatusReason()}"
    } else {
      error "Failed to wait for the changeset due to error: ${ex.getErrorMessage()}"
    }
  }
  
  return true
}

def printChanges(changes,stackName) {
  def changeString = "\n" + " ${stackName} ".center(134,'-') + "\n\n"
  
  changes.each {
    
    def change = it.getResourceChange()
    def logical = change.getLogicalResourceId()
    def resourceType = change.getResourceType()
    
    def action = change.getAction()
    def replace = (change.getReplacement().equals('True') ? 'Replace' : 'In-Place')
    def operation = (action.equals('Modify') ? "${action} (${replace})" : action)
    
    changeString += seperator([20,50,60])
    changeString += values(['Operation': 20, 'LogicalResourceId': 50, 'ResourceType': 60])
    changeString += seperator([20,50,60])
    changeString += values([(operation): 20, (logical): 50, (resourceType): 60])

        
    if (change.getAction().equals('Modify')) {
      changeString += seperator([20,39,10,9,20,29])
      changeString += values(['Attribute': 60, 'ChangeSource': 20, 'RequiresRecreation': 20, 'CausingEntity': 29])
      changeString += seperator([60,20,20,29])
      
      def details = change.getDetails()
      
      details.each {
        def target = it.getTarget()
        def att = target.getAttribute()
        def changeSource = it.getChangeSource().concat(" ") //conact space due to a wierd padding bug
        def causingEntity = (it.getCausingEntity() ? it.getCausingEntity() : "DirectModification")
        if (target.getAttribute().equals('Properties')) {
          att = "Property -> ${target.getName()}"
        }
        changeString += values([(att): 60, (changeSource): 20, (target.getRequiresRecreation()): 20, (causingEntity): 29])
      }
      changeString += seperator([60,20,20,29])
      
    } else {
      changeString += seperator([20,50,60])
    }
    
    changeString += "\n"
  }
  
  echo changeString
}

def seperator(list=[]) {
  line = '+'
  list.each { line += '-'.multiply(it) + '+' }
  line += "\n"
  return line
}

def values(list=[:]) {
  line = '|'
  list.each { k,v ->
    line += ' ' + k.padRight(v-1) + '|'
  }
  line += "\n"
  return line
}