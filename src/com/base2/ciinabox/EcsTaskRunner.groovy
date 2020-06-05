package com.base2.ciinabox

import com.amazonaws.services.ecs.AmazonECSClient
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest
import com.amazonaws.services.ecs.model.DescribeTasksRequest
import com.amazonaws.services.ecs.model.ContainerDefinition
import com.amazonaws.services.ecs.model.PortMapping
import com.amazonaws.services.ecs.model.KeyValuePair
import com.amazonaws.services.ecs.model.Tag
import com.amazonaws.services.ecs.model.NetworkMode
import com.amazonaws.services.ecs.model.Compatibility
import com.amazonaws.services.ecs.model.RunTaskRequest
import com.amazonaws.services.ecs.model.AwsVpcConfiguration
import com.amazonaws.services.ecs.model.NetworkConfiguration
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest
import com.amazonaws.services.ecs.model.StopTaskRequest
import com.amazonaws.services.ecs.model.LaunchType

import com.amazonaws.waiters.NoOpWaiterHandler
import com.amazonaws.waiters.WaiterParameters
import java.util.concurrent.Future

class EcsTaskRunner implements Serializable {
  
  def name
  def id
  def client
  
  EcsTaskRunner(String name, AmazonECSClient client) {
    this.name = name
    this.client = client
    this.id = name + '-' + UUID.randomUUID().toString()
  }
  
  /**
  
  **/
  def startTask(Map config, ArrayList taskDefinitions) {
    def containerDefinitions = createContainerDefinitions(taskDefinitions)
    
    def tags = []
    tags << new Tag().withKey('CreatedBy').withValue('ciinabox-pipelines')
    tags << new Tag().withKey('Task').withValue(this.name)
    
    client.registerTaskDefinition(new RegisterTaskDefinitionRequest()
      .withContainerDefinitions(containerDefinitions)
      .withCpu(config.cpu)
      .withMemory(config.memory)
      .withExecutionRoleArn(config.executionRole)
      .withFamily(id)
      .withNetworkMode(NetworkMode.Awsvpc)
      .withRequiresCompatibilities(Compatibility.FARGATE)
      .withTags(tags)
    )
    
    def awsVpcConfiguration = new AwsVpcConfiguration()
      .withSubnets([config.subnet])
      .withSecurityGroups([config.securityGroup])
      
    def networkConfiguration = new NetworkConfiguration()
      .withAwsvpcConfiguration(awsVpcConfiguration)
    
    def taskRequest = new RunTaskRequest()
      .withCluster(config.cluster)
      .withTaskDefinition(id)
      .withLaunchType(LaunchType.FARGATE)
      .withNetworkConfiguration(networkConfiguration)
      .withStartedBy('ciinabox-pipelines')

    def runResult = client.runTask(taskRequest)
    def taskArn = runResult.tasks.first().taskArn
    println "Started task ${taskArn} in ${config.cluster} ECS cluster"
    
    waitTillRunning(taskArn, config.cluster)
    
    return taskArn
  }
  
  /**
  
  **/
  def stopTask(Map config) {
    def taskDefinition = getTaskDefinition(config.taskArn, config.cluster)
    
    def stopTaskRequest = new StopTaskRequest()
      .withCluster(config.cluster)
      .withReason('stopped by jenkins')
      .withTask(config.taskArn)
    
    println "Stopping ${config.taskArn} in ${config.cluster} ECS cluster"
    client.stopTask(stopTaskRequest)
    
    println "Cleaning up task defintion ${taskDefinition}"
    deregisterTaskDefinition(taskDefinition)
  }
  
  /**
  
  **/
  def createContainerDefinitions(ArrayList taskDefinitions) {
    def containerDefinitions = []
    
    taskDefinitions.each { td ->
      def cd = new ContainerDefinition()
        .withName(td.name)
        .withImage(td.image)
        .withEssential(true)
      
      if (td.ports) {
        def portMappings = []
        
        td.ports.each {
          portMappings << new PortMapping().withContainerPort(it.container).withHostPort(it.host)
        }
        cd.withPortMappings(portMappings)
      }
      
      if (td.environment) {
        def envVars = []
        td.environment.each {
          envVars << new KeyValuePair().withName(it.key).withValue(it.value)
        }
        cd.withEnvironment(envVars)
      }
      
      containerDefinitions << cd
    }
    
    return containerDefinitions
  }
  
  /**
  
  **/
  def waitTillRunning(String taskArn, String cluster) {
    def waiter = client.waiters().tasksRunning()
    
    def describeTasksRequest = new DescribeTasksRequest()
      .withCluster(cluster)
      .withTasks([taskArn])
    
    Future future = waiter.runAsync(
      new WaiterParameters<>(describeTasksRequest),
      new NoOpWaiterHandler()
    )
    
    while(!future.isDone()) {
      try {
        println "waiting for task ${taskArn} to reach the RUNNING state"
        Thread.sleep(5 * 1000)
      } catch(InterruptedException ex) {
        println "We seem to be timing out ${ex}...ignoring"
      }
    }
  }
  
  /**
  
  **/
  def getEndpoint(String taskArn, String cluster) {
    def request = new DescribeTasksRequest()
      .withTasks([taskArn])
      .withCluster(cluster)
      
    def response = client.describeTasks(request)

    def task = response.getTasks().first()
    def attachment = task.getAttachments().first()
    def details = attachment.getDetails()
    def ip = details.findResult { it.getName() == "privateIPv4Address" ? it.getValue() : null }
    
    return ip
  }
  
  /**
  
  **/
  def getTaskDefinition(String taskArn, String cluster) {
    def request = new DescribeTasksRequest()
      .withTasks([taskArn])
      .withCluster(cluster)
    def response = client.describeTasks(request)
    def task = response.getTasks().first()
    
    return task.getTaskDefinitionArn()
  }
  
  /**
  
  **/
  def deregisterTaskDefinition(String taskDefinition) {
    def deregisterTaskDefinitionRequest = new DeregisterTaskDefinitionRequest()
      .withTaskDefinition(taskDefinition)
    client.deregisterTaskDefinition(deregisterTaskDefinitionRequest)
  }
}