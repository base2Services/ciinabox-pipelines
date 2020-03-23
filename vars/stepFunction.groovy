/************************************
stepFunction (
  action: 'start|wait|startAndWait',
  stateMachine: 'my-state-machine',
  region: 'us-east-1',
  accountId: '12345678',
  role: 'ciinabox',
  parameters:[
    myparam: 'myvalue',
    otherparam: 'othervalue'
  ]
)
************************************/
import com.base2.ciinabox.AwsClient
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder
import com.amazonaws.services.stepfunctions.model.StartExecutionRequest
import com.amazonaws.services.stepfunctions.model.DescribeExecutionRequest
import groovy.json.JsonOutput

def call(config) {
  def client = AwsClient.stepfunctions(config)
  switch(config.action) {
    case 'start':
      def executionArn = handleStart(client, config)
      println "Started ${executionArn} for ${config.stateMachine}"
      return executionArn
      break
    case 'wait':
      def status = handleWait(client, config)
      return status
      break
    case 'startAndWait':
      def executionArn = handleStart(client, config)
      println "Started ${executionArn} for ${config.stateMachine}"
      config['executionArn'] = executionArn
      def status = handleWait(client, config)
      return status
      break
  default:
    throw new GroovyRuntimeException("The specified action '${config.action}' is not implemented.")
  }

}

@NonCPS
def handleStart(client, config) {
  def params = [input: [:]]
  if(config.parameters) {
    params['input'] = config.parameters
  }
  def input = JsonOutput.toJson(params)
  println "Starting State Machine ${config.stateMachine} with input ${input}"
  def result = client.startExecution(new StartExecutionRequest()
    .withStateMachineArn("arn:aws:states:${config.region}:${config.accountId}:stateMachine:${config.stateMachine}")
    .withInput(input)
  )
  return result.getExecutionArn()
}

@NonCPS
def handleWait(client, config) {
  def finished = false
  def status = null
  while(!finished) {
    def result = client.describeExecution(new DescribeExecutionRequest()
      .withExecutionArn(config.executionArn)
    )
    status = result.getStatus()
    println "Waiting for ${config.executionArn} current status ${status}"
    finished = !result.getStatus().equals('RUNNING')
    if(!finished) {
      try {
        Thread.sleep(30000)
      } catch (InterruptedException ex){} //ignore
    }
  }
  println "Task exited with status ${status}"
  if(status != 'SUCCEEDED') {
    error("Step function ${config.stateMachine} failed, see cloudwatch logs for details")
  }
  return status
}
