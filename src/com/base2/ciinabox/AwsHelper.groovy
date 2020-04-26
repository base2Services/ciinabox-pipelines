package com.base2.ciinabox

import com.amazonaws.regions.Region
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest

/**
 * Return AWS Region for the current region if on EC2 other wise will return sydney
 */
def getRegion() {
  def region = Regions.getCurrentRegion()
  if (region == null) {
    region = Region.getRegion(Regions.AP_SOUTHEAST_2)
  }
  return region
}

/**
 * Return AWS Account ID gathered from default credentials
 * @return
 */
 def getAccountId() {
   def sts = AWSSecurityTokenServiceClientBuilder.standard()
     .withRegion(getRegion())
     .build()
   return sts.getCallerIdentity(new GetCallerIdentityRequest()).account
 }
