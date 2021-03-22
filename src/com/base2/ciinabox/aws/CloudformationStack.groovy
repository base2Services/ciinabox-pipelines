package com.base2.ciinabox.aws

import com.base2.ciinabox.aws.AwsClientBuilder
import com.base2.ciinabox.aws.IntrinsicsYamlConstructor

import groovy.json.JsonSlurper
import org.yaml.snakeyaml.Yaml
import java.io.InputStreamReader

import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException
import com.amazonaws.services.cloudformation.model.Parameter

class CloudformationStack implements Serializable {
  
  private transient client
  def stackName
  def builder = null
  
  CloudformationStack(client, stackName) {
    this.client = client
    this.stackName = stackName
  }

  CloudFormation(AwsClientBuilder builder, String stackName) {
      this.builder = builder
      this.stackName = stackName
  }

  def getClient() {
      if(builder != null) {
          return clientBuilder.cloudformation()
      } else {
          return client
      }
  }
  
  /**
  Checks if the CloudFormation stack exists and return the required change set type
  returns 'CREATE || UPDATE'
  **/
  def getChangeSetType() {
    def request = new DescribeStacksRequest()
      .withStackName(stackName)
      
    try {
      def stacks = getClient().describeStacks(request).getStacks()
      def stack = stacks.find {it.getStackName().equals(stackName)}
      // if a change set has been created but the stack is not yet created
      if (stack.getStackStatus().equals('REVIEW_IN_PROGRESS')) {
        return 'CREATE'
      }
    } catch (AmazonCloudFormationException ex) {
      // catch if stack doesn't exist
      if(ex.getErrorMessage().contains("does not exist")){
        return 'CREATE'
      } else {
        throw ex
      }
    }
    
    return 'UPDATE'
  }
  
  /**
  Finds CloudFormation stack parameter key:value from a deployed CloudFormation stack and the template in s3
  and overrides values with given parameters
  returns a list of cloudformation parameters
  **/
  def getStackParams(Map overrideParams, String templateUrl) {
    def stackParams = [:]
    def newTemplateParams = []
    def stacks = []
    
    try {
      stacks = getClient().describeStacks(new DescribeStacksRequest().withStackName(stackName)).getStacks()
    } catch (AmazonCloudFormationException ex) {
      if(!ex.getErrorMessage().contains("does not exist")){
        throw ex
      }
    }
    
    if(templateUrl != null) {
      newTemplateParams = getTemplateParameterNames(templateUrl)
    }
    
    if (!stacks.isEmpty()) {
      for(Parameter param: stacks.get(0).getParameters()) {
        // if new template is part of stack update, we need to check for any
        // removed parameters in new template
        if(templateUrl != null){
          if(!newTemplateParams.contains(param.getParameterKey())){
            println "Stack parameter ${param.getParameterKey()} not present in template ${templateUrl}, thus " +
                    "removing it from stack update operation"
            continue
          }
        }
        stackParams.put(param.getParameterKey(), new Parameter().withParameterKey(param.getParameterKey()).withUsePreviousValue(true))
      }
    }
    
    overrideParams.each {
      stackParams.put(it.key, new Parameter().withParameterKey(it.key).withParameterValue(it.value))
    }
    
    println "stack params: ${stackParams.values()}"
    return stackParams.values()
  }
  
  /**
  Gets template parameters names from a S3 URL
  returns a list of parameter names
  **/
  def getTemplateParameterNames(String templateUrl) {
    def template = getTemplateFromUrl(templateUrl)
    def parameterNames = []
    
    if (template.Parameters) {
      template.Parameters.each { cfParamName, cfParamDef ->
        parameterNames << cfParamName
      }
    }
    return parameterNames
  }
  
  /**
  Gets a cloudformation template in S3 from a S3 URL
  returns the template object
  **/
  def getTemplateFromUrl(String templateUrl) {
    def s3URI = new AmazonS3URI(templateUrl)
    def s3Client = new AwsClientBuilder([region: s3URI.getRegion()]).s3()
    def template = null
    def templateBody = s3Client.getObject(new GetObjectRequest(s3URI.getBucket(), s3URI.getKey())).getObjectContent()

    if (s3URI.getKey().endsWith('yaml') || s3URI.getKey().endsWith('yml')) {
      Yaml yaml = new Yaml(new IntrinsicsYamlConstructor())
      template = yaml.load(templateBody)
    } else {
      //fallback on json
      def jsonSlurper = new JsonSlurper()
      template = jsonSlurper.parseText(templateBody)
    }
    s3Client = null
    return template
  }

}
