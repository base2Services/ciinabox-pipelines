package com.base2.ciinabox

import groovy.json.JsonSlurper
import java.net.SocketException

class InstanceMetadata implements Serializable {
  
  String region
  String az
  String accountId
  String instanceId
  boolean isEc2
  
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
}