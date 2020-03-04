package com.base2.ciinabox

import com.cloudbees.groovy.cps.NonCPS
import com.base2.ciinabox.AwsClient
import com.amazonaws.services.ec2.model.DescribeInstancesRequest

class GetInstanceDetails {
  
  private final instance
  
  GetInstanceDetails(String region, String instanceId) {
    def ec2 = AwsClient.ec2(region)
    def result = ec2.describeInstances(new DescribeInstancesRequest()
      .withInstanceIds(instanceId)
    )
    this.instance = result.getReservations().first().getInstances().first()
  }
    
  @NonCPS
  public vpcId() {
    return this.instance.getVpcId()
  }
  
  @NonCPS
  public subnet() {
    return this.instance.getSubnetId()
  }
  
  @NonCPS
  public securityGroup() {
    return (this.instance.getSecurityGroups()) ? this.instance.getSecurityGroups().first().getGroupId() : null
  }
  
  @NonCPS
  public instanceProfile() {
    if (this.instance.getIamInstanceProfile()) {
      def arn = this.instance.getIamInstanceProfile().getArn()
      return arn.substring(arn.lastIndexOf("/") + 1)
    }
    return null
  }
  
}