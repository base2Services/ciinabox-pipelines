package com.base2.ciinabox

import groovy.json.JsonSlurper
import java.net.SocketException

class InstanceMetadata implements Serializable {
  
  private static String region
  private static String az
  private static String accountId
  private static String instanceId
  private static boolean isEc2
  
  InstanceMetadata() {
    def jsonSlurper = new JsonSlurper()

    try {
      def resp = "http://169.254.169.254/latest/dynamic/instance-identity/document".toURL().text
      this.isEc2 = true
      def doc = jsonSlurper.parseText(resp)
      this.region = doc.region
      this.az = doc.availabilityZone[-1]
      this.accountId = doc.accountId
      this.instanceId = doc.instanceId
    } catch (SocketException ex) {
      this.isEc2 = false
    }
  }

  def isEc2() {
    return this.isEc2
  }
  
  def getRegion() {
    return this.region
  }
  
  def getAz() {
    return this.az
  }
  
  def getAccountId() {
    return this.accountId
  }
  
  def getInstanceId() {
    return this.instanceId
  }
  
}