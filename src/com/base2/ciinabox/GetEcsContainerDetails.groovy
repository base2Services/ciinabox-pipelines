package com.base2.ciinabox

import groovy.json.JsonSlurper

import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest
import com.amazonaws.services.ecs.AmazonECSClientBuilder

class GetEcsContainerDetails implements Serializable {
  
  private static cluster
  private static taskArn
  private static taskDefName
  private static taskDefVersion
  private static taskDefintion
  
  GetEcsContainerDetails(String region) {
    def jsonSlurper = new JsonSlurper()
    def uri = System.getenv("ECS_CONTAINER_METADATA_URI")
    def resp = uri.toURL().text
    def doc = jsonSlurper.parseText(resp)
    cluster = doc["Labels"]["com.amazonaws.ecs.cluster"]
    taskArn = doc["Labels"]["com.amazonaws.ecs.task-arn"]
    taskDefName = doc["Labels"]["com.amazonaws.ecs.task-definition-family"]
    taskDefVersion = doc["Labels"]["com.amazonaws.ecs.task-definition-version"]
    
    def ecs = AmazonECSClientBuilder.standard().withRegion(region).build()
    def response = ecs.describeTaskDefinition(new DescribeTaskDefinitionRequest()
                    .withTaskDefinition("${taskDefName}:${taskDefVersion}"))
    
    taskDefintion = response.getTaskDefinition()
  }
  
  def cluster() {
    return this.cluster
  }
  
  def executionRole() {
    return this.taskDefintion.getExecutionRoleArn()
  }
  
}