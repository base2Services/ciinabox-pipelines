package com.base2.ciinabox

import groovy.json.JsonSlurper

class InstanceMetadata implements Serializable {
  
  private static doc
  
  InstanceMetadata() {
    def jsonSlurper = new JsonSlurper()
    def resp = "http://169.254.169.254/latest/dynamic/instance-identity/document".toURL().text
    this.doc = jsonSlurper.parseText(resp)
  }
  
  def region() {
    return this.doc.region
  }
  
  def az() {
    return this.doc.availabilityZone[-1]
  }
  
  def accountId() {
    return this.doc.accountId
  }
  
  def instanceId() {
    return this.doc.instanceId
  }
  
}