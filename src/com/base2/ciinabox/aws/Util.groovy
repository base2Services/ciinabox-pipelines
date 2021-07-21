package com.base2.ciinabox.aws

import com.amazonaws.regions.AwsRegionProviderChain
import com.amazonaws.regions.AwsEnvVarOverrideRegionProvider
import com.amazonaws.regions.InstanceMetadataRegionProvider
import com.amazonaws.SdkClientException
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest

class Util implements Serializable {
  
  /**
   * Return AWS Region for the current region if on EC2 other wise will return sydney
   * @return aws region string
   */
  static def getRegion() {
    def region = null
    try {
      def chain = new AwsRegionProviderChain(new InstanceMetadataRegionProvider(), new AwsEnvVarOverrideRegionProvider())
      region = chain.getRegion()
    } catch (SdkClientException ex) {
      println ex.getMessage()
    }
    return region
  }

  /**
   * Return AWS Account ID gathered from default credentials
   * @return aws account id string
   */
   static def getAccountId() {
     def sts = AWSSecurityTokenServiceClientBuilder.standard()
       .withRegion(getRegion())
       .build()
     return sts.getCallerIdentity(new GetCallerIdentityRequest()).account
   }
  
}