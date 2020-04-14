package com.base2.ciinabox.aws

import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException

class CloudformationStackEvents implements Serializable {
  
  def client
  def region
  def stackName
  
  CloudformationStackEvents(client, region, stackName) {
    this.client = client
    this.region = region
    this.stackName = stackName
  }
  
  /** 
  Grabs all events for a stack and prints details of each failed resource
  including error message and link to event in web console
  **/
  def getFailedEventsTable() {
    def events = getFailedEvents()
    def text = "\n" + " FAILED EVENTS ".center(165,'-') + "\n"
    text += seperator([40,30,20,70])
    text += values(['Stack': 40, 'LogicalResourceId': 30, 'Status': 20, 'Status Reason': 70])
    text += seperator([40,30,20,70])
    
    events.each { event ->
      def reason = event.getResourceStatusReason()
      if (reason) {
        if (!reason.matches("User Initiated")) {
          text += values([
            (event.getStackName()): 40, 
            (event.getLogicalResourceId()): 30, 
            (event.getResourceStatus()): 20, 
            (event.getResourceStatusReason()): 70])
        }
      }
    }
    
    text += seperator([40,30,20,70])
    return text
  }
  
  def getFailedEvents() {
    def states = "CREATE_FAILED|ROLLBACK_IN_PROGRESS|UPDATE_FAILED|DELETE_FAILED"
    def events = getStackEvents(stackName)
    def failures = []
    
    events.each { event ->
      def eventStatus = event.getResourceStatus()
      if (eventStatus.matches(states)) {
        // If resource is a nested stack look through nested stack
        if (event.getResourceType() == "AWS::CloudFormation::Stack") {
          def nestStackName = event.getPhysicalResourceId()
          if (!nestStackName.contains(stackName)) {
            println "looking up events for nested stack ${nestStackName}"
            def nestedEvents = getStackEvents(nestStackName)
            nestedEvents.each { nestedEvent ->
              def nestedEventStatus = nestedEvent.getResourceStatus()
              if (nestedEventStatus.matches(states)) {
                failures.add(event)
              }
            }
          }
        } else {
          failures.add(event)
        }
      }
    }
    
    return failures
  }
  
  def getStackEvents(String stack) {

     def request = new DescribeStackEventsRequest().withStackName(stack)
     def results = []
     
     try {
      results = client.describeStackEvents(request).getStackEvents()
    } catch (AmazonCloudFormationException ex) {
      if(ex.message.contains("does not exist")) {
        return false
      } else {
        throw ex
      }
    }
    
    return results
  }
  
  def seperator(list=[]) {
    def line = '+'
    list.each { line = line + '-'.multiply(it) + '+' }
    line += "\n"
    return line
  }

  def values(list=[:]) {
    def line = '|'
    list.each { k,v ->
      // chop off the end of the string if it's too long for the table
      if (k.size() > v) {
        k = k[0..(v-1)]
      }
      line = line + ' ' + k.padRight(v-1) + '|'
    }
    line += "\n"
    return line
  }
}