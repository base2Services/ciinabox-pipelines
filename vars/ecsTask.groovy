
/************************************
ecsTask (
  action: 'runAndWait',
  taskDefinition: 'example-task-definition',
  cluster: 'example-cluster',
  region: 'us-east-1',
  accountId: '12345678',
  role: 'ciinabox',
  launchType: 'FARGATE',
  subnets: ['subnet-12345'],
  securityGroup: ['sg-12345'],
  credsDuration: '3600'
)
************************************/

@Grab(group='com.amazonaws', module='aws-java-sdk-ecs', version='1.11.359')
@Grab(group='com.amazonaws', module='aws-java-sdk-iam', version='1.11.359')
@Grab(group='com.amazonaws', module='aws-java-sdk-sts', version='1.11.359')

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.ecs.*
import com.amazonaws.services.ecs.model.NetworkConfiguration
import com.amazonaws.services.ecs.model.AwsVpcConfiguration
import com.amazonaws.services.ecs.model.DescribeTasksRequest
import com.amazonaws.services.ecs.model.RunTaskRequest
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.amazonaws.waiters.NoOpWaiterHandler
import com.amazonaws.waiters.WaiterParameters
import com.amazonaws.services.securitytoken.model.ExpiredTokenException

import java.util.concurrent.*

def call(body) {
  
  def config = body
  config.wait = config.get('wait', false)
  config.credsDuration = config.get('credsDuration', 3600)

  def client = setupECSClient(config.region, config.accountId, config.role, config.credsDuration)
  handleActionRequest(client, config)
}

def handleActionRequest(client, config) {
  def success = true

  switch (config.action) {
    case 'runAndWait':
      def startedTasks = startTask(client, config)
      Thread.sleep(5 * 1000)
      success = wait(client, config, startedTasks)
      break
    default:
      throw new GroovyRuntimeException("The specified action '${config.action}' is not implemented.")
  }

  if(!success) {
    throw new Exception("Task ${config.taskDefinition} failed to run.")
  }
}

@NonCPS
def startTask(client, config) {
  def taskRequest = new RunTaskRequest()
  taskRequest.withCluster(config.cluster)
  taskRequest.launchType = config.launchType ? config.launchType : "EC2"
  taskRequest.taskDefinition = config.taskDefinition

  if (taskRequest.launchType == 'FARGATE') {
    def awsVpcConfiguration = new AwsVpcConfiguration().withSubnets(config.subnets).withSecurityGroups(config.securityGroup)
    def networkConfiguration = new NetworkConfiguration().withAwsvpcConfiguration(awsVpcConfiguration)
    taskRequest.withNetworkConfiguration(networkConfiguration)
  }


  println "Starting task ${config.taskDefinition} in cluster ${config.cluster}"
  def runResult = client.runTask(taskRequest)
  println "Started task ${runResult.tasks.first().taskArn}"
  return runResult
}


@NonCPS
def extendedWait(client, config, startedTasks, descRequest) {

  def waiter = client.waiters().tasksStopped()

  descRequest.withCluster(config.cluster)
  descRequest.withTasks(startedTasks.tasks.collectMany { [it.taskArn] })

  Future future = waiter.runAsync(
    new WaiterParameters<>(descRequest),
    new NoOpWaiterHandler()
  )
  while(!future.isDone()) {
    try {
      println "waiting for task to complete"
      Thread.sleep(5 * 1000)
    } catch(InterruptedException ex) {
      println "We seem to be timing out ${ex}...ignoring"
    }
  }

  def taskCurrentState = client.describeTasks(descRequest)
  for(task in taskCurrentState.tasks) {
    if(task.lastStatus == "RUNNING") {
      return false
    } else { return true }
  }

}

@NonCPS
def wait(client, config, startedTasks) {

  def describeTasksRequest = new DescribeTasksRequest()
  def taskComplete = false

  try {

    while(!taskComplete) {
      taskComplete = extendedWait(client, config, startedTasks, describeTasksRequest)
    }

    def taskDescriptions = client.describeTasks(describeTasksRequest)
    println taskDescriptions
    if (taskDescriptions.tasks.size() != 1) {
      println "Couldn't find launched task"
      return false
    }
    for (task in taskDescriptions.tasks) {
      for (container in task.containers) {
        if (container.exitCode != 0) {
          println "Non zero exit code in container: ${container} of task ${task}"
          return false
        }
      }
    }
    return true
  } catch(Exception e) {
      if(e.getErrorCode() == "ExpiredTokenException") {
        println "Credentials have expired, reinitialising client..."
        client = setupECSClient(config.region, config.accountId, config.role, config.credsDuration)
        wait(client, config, startedTasks) 
      }
      else {
        println "Waiting for task failed. - ${e}"
        return false
      }
  }
}

@NonCPS
def setupECSClient(region, awsAccountId = null, role = null, credsDuration) {
  def cb = AmazonECSClientBuilder.standard().withRegion(region)
  def creds = getCredentials(awsAccountId, region, role, credsDuration)
  if(creds != null) {
    cb.withCredentials(new AWSStaticCredentialsProvider(creds))
  }
  return cb.build()
}

@NonCPS
def getCredentials(awsAccountId, region, roleName, credsDuration) {
  def env = System.getenv()
  if(env['AWS_SESSION_TOKEN'] != null) {
    return new BasicSessionCredentials(
      env['AWS_ACCESS_KEY_ID'],
      env['AWS_SECRET_ACCESS_KEY'],
      env['AWS_SESSION_TOKEN']
    )
  } else if(awsAccountId != null && roleName != null) {
    def stsCreds = assumeRole(awsAccountId, region, roleName, credsDuration)
    return new BasicSessionCredentials(
      stsCreds.getAccessKeyId(),
      stsCreds.getSecretAccessKey(),
      stsCreds.getSessionToken()
    )
  } else {
    return null
  }
}

@NonCPS
def assumeRole(awsAccountId, region, roleName, credsDuration) {
  def roleArn = "arn:aws:iam::" + awsAccountId + ":role/" + roleName
  def roleSessionName = "sts-session-" + awsAccountId
  println "assuming IAM role ${roleArn}, expiring in ${credsDuration} seconds."
  def sts = new AWSSecurityTokenServiceClient()
  if (!region.equals("us-east-1")) {
      sts.setEndpoint("sts." + region + ".amazonaws.com")
  }
  def assumeRoleResult = sts.assumeRole(new AssumeRoleRequest()
            .withRoleArn(roleArn).withDurationSeconds(credsDuration)
            .withRoleSessionName(roleSessionName))
  return assumeRoleResult.getCredentials()
}