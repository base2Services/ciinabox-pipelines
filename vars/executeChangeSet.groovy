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
    
  def changeSetType = new CloudformationStack(clientBuilder, stackName).getChangeSetType()
  def changeSetName = null
    
  if (config.changeSetName) {
    changeSetName = config.changeSetName
  } else {
    changeSetName = env["${stackNameUpper}_CHANGESET_NAME"]
  }

  echo "Session duration: " + config.get('duration', 3600).toString()
  echo "Executing change set ${changeSetName}"
  def before = stackState(clientBuilder, stackName)
  executeChangeSet(clientBuilder, stackName, changeSetName)
  def success = wait(clientBuilder, stackName, changeSetType, before)

  if (!success) {
    echo failedEventsTable(clientBuilder, config.region, stackName)
    error "${stackName} changeset ${changeSetName} failed to execute."
  }
  
  clientBuilder = null
  echo "Change set ${changeSetName} ${changeSetType}D"
}

// The AWS calls are @NonCPS so their clients never live in a CPS frame: Jenkins can save the
// pipeline while a parallel branch is part way through CPS code, not only at a step, and a
// client there fails the build with a NotSerializableException. They run no steps.
// Each shuts its client down: the SDK's idle connection reaper keeps every client's connection
// pool until then.
@NonCPS
def executeChangeSet(clientBuilder, stackName, changeSetName) {
  def cfclient = clientBuilder.cloudformation()
  try {
    cfclient.executeChangeSet(new ExecuteChangeSetRequest()
      .withChangeSetName(changeSetName)
      .withStackName(stackName))
  } finally {
    cfclient.shutdown()
  }
}

@NonCPS
def failedEventsTable(clientBuilder, region, stackName) {
  def cfclient = clientBuilder.cloudformation()
  try {
    return new CloudformationStackEvents(cfclient, region, stackName).getFailedEventsTable()
  } finally {
    cfclient.shutdown()
  }
}

// Polls the stack until the change set's execution finishes, including an update's cleanup
// (UPDATE_COMPLETE_CLEANUP_IN_PROGRESS): until it ends, CloudFormation rejects another update of
// the stack. Only plain values are held here, across the echo and sleep steps; each poll builds
// its own client in stackState.
//
// Credentials from the environment (AWS_SESSION_TOKEN, as inside withIAMRole) can expire during a
// long update. As the waiter this replaced did, after 50 minutes the polls stop using them and
// assume the role (when one is given) on each poll instead.
def wait(clientBuilder, stackName, changeSetType, before) {
  def state = null
  def polls = 0
  def pollBuilder = clientBuilder
  while (true) {
    state = stackState(pollBuilder, stackName)
    // An update hasn't started until the stack's LastUpdatedTime moves; until then the stack
    // may still report the previous update's UPDATE_COMPLETE.
    def started = changeSetType == 'CREATE' || state.updated != before.updated
    def finished = !state.status.endsWith('_IN_PROGRESS')
    if (started && finished) {
      break
    }
    echo "waiting for execute changeset to ${changeSetType.toLowerCase()} ..."
    sleep(time: 10, unit: 'SECONDS')
    polls++
    if (polls == 300 && clientBuilder?.awsAccountId && clientBuilder?.role) {
      echo "Refreshing credentials: assuming role ${clientBuilder.role} in ${clientBuilder.awsAccountId} on each poll"
      pollBuilder = roleCredentials(clientBuilder)
    }
  }

  // check the final state of the stack to check for any false positive stack status such as ROLLBACK_COMPLETE
  if (!state.status.matches("UPDATE_COMPLETE|CREATE_COMPLETE")) {
    echo "execute changeset ${changeSetType.toLowerCase()} failed with status ${state.status}"
    return false
  }

  return true
}

// Returns the stack's status and LastUpdatedTime (epoch millis, null if never updated) as plain
// values, so the caller holds nothing that can't be serialized.
@NonCPS
def stackState(clientBuilder, stackName) {
  def cfclient = clientBuilder.cloudformation()
  try {
    def stack = cfclient.describeStacks(new DescribeStacksRequest().withStackName(stackName)).getStacks()[0]
    return [status: stack.getStackStatus().toString(), updated: stack.getLastUpdatedTime()?.getTime()]
  } finally {
    cfclient.shutdown()
  }
}

// The same builder without the environment's credentials, so each client assumes the role afresh.
def roleCredentials(clientBuilder) {
  return new AwsClientBuilder([
    region: clientBuilder.region,
    awsAccountId: clientBuilder.awsAccountId,
    role: clientBuilder.role,
    maxErrorRetry: clientBuilder.maxErrorRetry,
    env: [:],
    duration: clientBuilder.duration])
}
