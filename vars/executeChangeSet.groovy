/***********************************
executeChangeSet DSL

Executes a cloudformation changeset

example usage
executeChangeSet(
  changeSetName: env.MY_STACK_CHANGESET_NAME, // (optional, name of your change to execute. defaults to the environment variable set with the stackname)
  region: 'us-east-1', // (required, aws region to deploy the stack)
  stackName: 'my-stack', // (required, name of the CloudFormation stack)
  awsAccountId: '012345678901', // (optional, aws account the cloudformation stack is to be deployed to)
  role: 'iam-role-name', // (optional, IAM role to assume if deploying to a different aws account)
  serviceRole: 'iam-cfn-service-role', // (optional, execution role for the cloudformation service)
  maxErrorRetry: 3, // (optional, defaults to 3)
)
************************************/

import com.base2.ciinabox.aws.AwsClientBuilder
import com.base2.ciinabox.aws.CloudformationStack
import com.base2.ciinabox.aws.CloudformationStackEvents

import com.amazonaws.services.cloudformation.model.ExecuteChangeSetRequest
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest
import com.amazonaws.services.cloudformation.waiters.AmazonCloudFormationWaiters
import com.amazonaws.waiters.WaiterParameters
import com.amazonaws.waiters.WaiterUnrecoverableException

def call(body) {
  def config = body
  
  def clientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.get('awsAccountId'),
    role: config.get('role'),
    maxErrorRetry: config.get('maxErrorRetry', 3),
    env: env])
    
  def cfclient = clientBuilder.cloudformation()

  def cfstack = new CloudformationStack(cfclient, config.stackName)
  def changeSetType = cfstack.getChangeSetType()
  def changeSetName = null
  
  if (config.changeSetName) {
    changeSetName = config.changeSetName
  } else {
    def stackNameUpper = config.stackName.toUpperCase().replaceAll("-", "_")
    changeSetName = env["${stackNameUpper}_CHANGESET_NAME"]
  }

  def request = new ExecuteChangeSetRequest()
    .withChangeSetName(changeSetName)
    .withStackName(config.stackName)
  
  echo "Executing change set ${changeSetName}"

  cfclient.executeChangeSet(request)

  def waitRequest = new DescribeStacksRequest()
    .withStackName(config.stackName)
    
  def waitParameter = new WaiterParameters(waitRequest)
  def waiter = new AmazonCloudFormationWaiters(cfclient)

  echo "Waiting for change set ${changeSetName} to ${changeSetType}"
  
  try {
    if (changeSetType == 'CREATE') {
      waiter.stackCreateComplete().run(waitParameter)
    } else {
      waiter.stackUpdateComplete().run(waitParameter)
    }
  } catch (WaiterUnrecoverableException ex) {
    if (ex.getMessage().equals('Resource never entered the desired state as it failed.')) {
      def events = new CloudformationStackEvents(cfclient, config.region, config.stackName)
      def table = events.getFailedEventsTable()
      echo table
      error "Changeset ${changeSetName} for stack ${config.stackName} failed to execute."
    } else {
      error "Failed to wait for the changeset ${changeSetName} to execute due to error: ${ex.getErrorMessage()}"
    }
  }
    
  echo "Change set ${changeSetName} ${changeSetType}D"
}