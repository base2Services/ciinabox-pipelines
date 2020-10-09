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
import com.base2.ciinabox.aws.AwsClientBuilder

def call(body) {
  def config = body
  config.accountId = config.get('accountId', null)
  config.role = config.get('role', null)
  share(this, config)
}

@NonCPS
def share(steps, config) {
  def clientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.accountId,
    role: config.role
  ])
  def client = clientBuilder.ec2()
  //Share Ami
  shareAmi(steps, client, config)
  //Share ebs volume if ebs backed ami
  def ebsSnapshots = getEbsSnapshot(steps, client, config)
  if (!ebsSnapshots?.empty) {
    ebsSnapshots.each { snapshot ->
      modifySnapshotAttribute(steps, client, snapshot, config)
    }
  }
}

@NonCPS
def shareAmi(steps, client, config) {
  def launchPermission = []
  config.accounts.each { account ->
    launchPermission << new LaunchPermission().withUserId(account)
  }
  def launchPermissionModifications = new LaunchPermissionModifications()
    .withAdd(launchPermission)
  steps.echo "Sharing ${config.ami} with ${launchPermission}"
  client.modifyImageAttribute(new ModifyImageAttributeRequest()
    .withImageId(config.ami)
    .withAttribute('launchPermission')
    .withLaunchPermission(launchPermissionModifications)
  )
}

@NonCPS
def modifySnapshotAttribute(steps, client, snapshot, config) {
  def volumePermission = []
  config.accounts.each { account ->
    volumePermission << new CreateVolumePermission().withUserId(account)
  }
  def volumePermissionModifications = new CreateVolumePermissionModifications()
    .withAdd(volumePermission)
  steps.echo "Adding create volume permission to ${snapshot}"
  client.modifySnapshotAttribute(new ModifySnapshotAttributeRequest()
    .withSnapshotId(snapshot)
    .withCreateVolumePermission(volumePermissionModifications)
  )
}

@NonCPS
def getEbsSnapshot(steps, client,config) {
  def result = client.describeImages(new DescribeImagesRequest().withImageIds(config.ami))
  def ebsSnaphots = []
  if (!result.getImages().isEmpty()) {
    steps.echo "Found AMI ${config.ami}"
    for (BlockDeviceMapping blockingDevice : result.getImages().get(0).getBlockDeviceMappings()) {
      if (blockingDevice.getEbs() != null) {
        def snapshot = blockingDevice.getEbs().getSnapshotId()
        ebsSnaphots << snapshot
        steps.echo "Found snapshot ${snapshot} for ami ${config.ami}"
      }
    }
  }
  return ebsSnaphots
}