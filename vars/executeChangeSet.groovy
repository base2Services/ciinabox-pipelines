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
import com.amazonaws.waiters.NoOpWaiterHandler
import java.util.concurrent.Future
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder

def call(body) {
  def config = body
  def stackNameUpper = config.stackName.toUpperCase().replaceAll("-", "_")
  // Converting the stackName to a string prevents passing a Groovy encoded string 
  // to this script, resulting in null being returned when searching for the stack
  def stackName = config.stackName.toString()

  if (env["${stackNameUpper}_NO_EXECUTE_CHANGESET"] == 'TRUE') {
    echo("Skipping execution changeset as no changes have been detected ...")
  } else {
    apply(config, stackName, stackNameUpper)
  }
}

def apply(config, stackName, stackNameUpper) {

  def clientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.get('awsAccountId'),
    role: config.get('role'),
    maxErrorRetry: config.get('maxErrorRetry', 3),
    env: env,
    duration: config.get('duration', 3600)])
    
  def cfclient = clientBuilder.cloudformation()
  def cfstack = new CloudformationStack(clientBuilder, stackName)
  
  def changeSetType = cfstack.getChangeSetType()
  cfStack = null
  cfclient = null
  def changeSetName = null
    
  if (config.changeSetName) {
    changeSetName = config.changeSetName
  } else {
    changeSetName = env["${stackNameUpper}_CHANGESET_NAME"]
  }

  echo "Session duration: " + config.get('duration', 3600).toString()
  echo "Executing change set ${changeSetName}"
  executeChangeSet(clientBuilder, stackName, changeSetName)
  def success = wait(clientBuilder, stackName, changeSetType, config)

  if (!success) {
    cfclient = clientBuilder.cloudformation()
    def events = new CloudformationStackEvents(cfclient, config.region, stackName)
    echo events.getFailedEventsTable()
    events = null
    cfclient = null
    error "${stackName} changeset ${changeSetName} failed to execute."
  }
  
  cfclient = null
  clientBuilder = null
  echo "Change set ${changeSetName} ${changeSetType}D"
}

def executeChangeSet(clientBuilder, stackName, changeSetName) {
  def cfclient = clientBuilder.cloudformation()
  cfclient.executeChangeSet(new ExecuteChangeSetRequest()
    .withChangeSetName(changeSetName)
    .withStackName(stackName))
  cfclient = null 
}

def wait(clientBuilder, stackName, changeSetType, config) {
  def cfclient = clientBuilder.cloudformation()
  def waiter = null
  def count = 0
  switch(changeSetType) {
    case 'CREATE':
      waiter = cfclient.waiters().stackCreateComplete()
      break
    default:
      waiter = cfclient.waiters().stackUpdateComplete()
      break
  }

  try {
    def future = waiter.runAsync(
      new WaiterParameters<>(new DescribeStacksRequest().withStackName(stackName)),
      new NoOpWaiterHandler()
    )
    while(!future.isDone()) {
      try {
        echo "waiting for execute changeset to ${changeSetType.toLowerCase()} ..."
        Thread.sleep(10000)
        count++
        // Initialise new client and waiter if count exceeds set timeout value
        if (count > 300) { //3000 seconds = 50 minutes, thread sleep is 10 secs so 300 iterations
          cfclient = updateClient(clientBuilder, cfclient, config.region) 
          waiter = updateWaiter(cfclient,changeSetType)
          future = waiter.runAsync(
             new WaiterParameters<>(new DescribeStacksRequest().withStackName(stackName)),
             new NoOpWaiterHandler()
          )
          count = 0
        }

      } catch(InterruptedException ex) {
          // suppress and continue
      }
    }
  } catch(Exception ex) {
    cfclient = null
    echo "execute changeset ${changeSetType.toLowerCase()} failed with error ${ex.getMessage()}"
    return false
  }
  
  // check the final state of the stack to check for any false positive stack status such as ROLLBACK_COMPLETE
  def request = new DescribeStacksRequest().withStackName(stackName)
  def stacks = cfclient.describeStacks(request).getStacks()
  def finalStatus = stacks[0].getStackStatus()
  cfclient = null
  if (!finalStatus.matches("UPDATE_COMPLETE|UPDATE_COMPLETE_CLEANUP_IN_PROGRESS|CREATE_COMPLETE")) {
    echo "execute changeset ${changeSetType.toLowerCase()} failed with status ${finalStatus}"
    return false
  }
  
  return true
}

def updateClient(clientBuilder, cfclient, region){
  
  echo "Updating Client"

  def cb = new AmazonCloudFormationClientBuilder().standard()
    .withClientConfiguration(clientBuilder.config())

  if (region) {
    cb.withRegion(region)
  }

  def creds =  clientBuilder.getNewCreds()

  if(creds != null) {
    cb.withCredentials(new AWSStaticCredentialsProvider(creds))
  }

  return cb.build()

}

def updateWaiter(cfclient, changeSetType){
  echo "Updating Waiter"
  switch(changeSetType) {
    case 'CREATE':
      def waiter = cfclient.waiters().stackCreateComplete()
      echo "Created new waiter - ${waiter}"
      return waiter
    default:
      def waiter = cfclient.waiters().stackUpdateComplete()
      echo "Created new waiter - ${waiter}"
      return waiter
  }
}
