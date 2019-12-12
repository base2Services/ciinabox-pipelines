/***********************************
shareAMI DSL

# example usage
shareAMI(
  region: env.REGION,
  ami: env.AMI_ID,
  accounts: ['111111111','222222222']
)
************************************/

import com.amazonaws.services.ec2.*
import com.amazonaws.services.ec2.model.*
import com.amazonaws.regions.*

def call(body) {
  def config = body
  AmazonEC2 client = setupClient(config.region)
  //Share Ami
  shareAmi(client,config)
  //Share ebs volume if ebs backed ami
  def ebsSnapshots = getEbsSnapshot(client,config)
  if (!ebsSnapshots?.empty) {
    ebsSnapshots.each { snapshot ->
      modifySnapshotAttribute(client,snapshot,config)
    }
  }
}

def shareAmi(client,config) {
  def launchPermission = []
  config.accounts.each { account ->
    launchPermission << new LaunchPermission().withUserId(account)
  }
  LaunchPermissionModifications launchPermissionModifications = new LaunchPermissionModifications()
    .withAdd(launchPermission)
  def status = client.describeImages(new DescribeImagesRequest().withImageIds(config.ami)).getImages().get(0).getState()
  while (status.toLowerCase() != 'available') {
    println "Ami status: ${status}, waiting for available"
    Thread.sleep(10000)
    status = client.describeImages(new DescribeImagesRequest().withImageIds(config.ami)).getImages().get(0).getState()
  }
  println "Sharing ${config.ami} with ${launchPermission}"
  client.modifyImageAttribute(new ModifyImageAttributeRequest()
    .withImageId(config.ami)
    .withAttribute('launchPermission')
    .withLaunchPermission(launchPermissionModifications)
  )
}

def modifySnapshotAttribute(client,snapshot,config) {
  def volumePermission = []
  config.accounts.each { account ->
    volumePermission << new CreateVolumePermission().withUserId(account)
  }
  CreateVolumePermissionModifications volumePermissionModifications = new CreateVolumePermissionModifications()
    .withAdd(volumePermission)
  println "Adding create volume permission to ${snapshot}"
  client.modifySnapshotAttribute(new ModifySnapshotAttributeRequest()
    .withSnapshotId(snapshot)
    .withCreateVolumePermission(volumePermissionModifications)
  )
}

def getEbsSnapshot(client,config) {
  def result = client.describeImages(new DescribeImagesRequest().withImageIds(config.ami))
  def ebsSnaphots = []
  if (!result.getImages().isEmpty()) {
    println "Found AMI ${config.ami}"
    for (BlockDeviceMapping blockingDevice : result.getImages().get(0).getBlockDeviceMappings()) {
      if (blockingDevice.getEbs() != null) {
        def snapshot = blockingDevice.getEbs().getSnapshotId()
        ebsSnaphots << snapshot
        println "Found snapshot ${snapshot} for ami ${config.ami}"
      }
    }
  }
  return ebsSnaphots
}

def setupClient(region) {
  return AmazonEC2ClientBuilder.standard()
    .withRegion(region)
    .build()
}
