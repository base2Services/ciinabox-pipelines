package com.base2.ciinabox.aws

import com.amazonaws.services.ec2.*
import com.amazonaws.services.ec2.model.*
import com.amazonaws.regions.*
import com.amazonaws.waiters.*
import java.util.concurrent.Future
import com.base2.ciinabox.aws.AwsClientBuilder

class AmiHelper implements Serializable {

  String accountId
  String iamRole
  String region
  Object job = null

  static AmiHelperBuilder builder() {
    return new AmiHelperBuilder()
  }

  static class AmiHelperBuilder {
    String accountId = null
    String iamRole = null
    String region
    Object job = null

    def accountId(String accountId) {
      this.accountId = accountId
      return this
    }

    def iamRole(String iamRole) {
      this.iamRole = iamRole
      return this
    }

    def region(String region) {
      this.region = region
      return this
    }

    def job(Object job) {
      this.job = job
      return this
    }

    def build() {
      return new AmiHelper(
        accountId: accountId,
        iamRole: iamRole,
        region: region,
        job: job
      )
    }
  }

  def copyAndShareAMI(String ami, List targetRegions=[], Map options=[:]) {
    def copiedAMIs = [:]
    targetRegions.each { targetRegion ->
      def copiedAMI = this.copyAMI(ami, targetRegion, options)
      this.copyTags(ami, copiedAMI, targetRegion)
      copiedAMIs[targetRegion] = copiedAMI
    }
    waitForCopy(copiedAMIs)
    copiedAMIs.each { region, imageId ->
      log("Sharing AMI ${imageId} in ${region} with ${options.accounts}")
      shareAMI(region, imageId, options.accounts)
    }
    return copiedAMIs
  }

  def copyAMI(sourceImageId, targetRegion, options=[:]) {
    def client = new AwsClientBuilder([
      region: targetRegion,
      awsAccountId: accountId,
      role: iamRole
    ]).ec2()

    def copyImageRequest = new CopyImageRequest()
      .withSourceImageId(sourceImageId)
      .withSourceRegion(region)
      
    if (options.encrypted) {
      copyImageRequest.withEncrypted(true)
      if (options.kmsKeyId) {
        copyImageRequest.withKmsKeyId(options.kmsKeyId)
      }
    }
    def copyImageResult = client.copyImage(copyImageRequest)
    client = null
    return String.valueOf(copyImageResult.getImageId());
  }

  def copyTags(sourceImageId, copyImageId, copyRegion) {
    def sourceClient = new AwsClientBuilder([
      region: region,
      awsAccountId: accountId,
      role: iamRole
    ]).ec2()

    def targetClient = new AwsClientBuilder([
      region: copyRegion,
      awsAccountId: accountId,
      role: iamRole
    ]).ec2()

    def describeImagesResult = sourceClient.describeImages(new DescribeImagesRequest().withImageIds(sourceImageId))

    def images = describeImagesResult.getImages()
    def image = images.get(0)

    for (Tag tag : image.getTags()) {
      targetClient.createTags(new CreateTagsRequest().withResources(copyImageId).withTags(tag))
    }
    sourceClient = null
    targetClient = null
  }

  def shareAMI(shareRegion, ami, accounts) {
    def client = new AwsClientBuilder([
      region: shareRegion,
      awsAccountId: accountId,
      role: iamRole
    ]).ec2()

    def launchPermission = []
    accounts.each { account ->
      launchPermission << new LaunchPermission().withUserId(account)
    }
    def launchPermissionModifications = new LaunchPermissionModifications().withAdd(launchPermission)
    client.modifyImageAttribute(new ModifyImageAttributeRequest()
      .withImageId(ami)
      .withAttribute('launchPermission')
      .withLaunchPermission(launchPermissionModifications)
    )

    //Share ebs volume if ebs backed ami
    def ebsSnapshots = getEbsSnapshot(client, ami)
    if (!ebsSnapshots?.empty) {
      ebsSnapshots.each { snapshot ->
        modifySnapshotAttribute(client, snapshot, accounts)
      }
    }
    client = null
  }

  private void getEbsSnapshot(client, ami) {
    def result = client.describeImages(new DescribeImagesRequest().withImageIds(ami))
    def ebsSnaphots = []
    if (!result.getImages().isEmpty()) {
      for (BlockDeviceMapping blockingDevice : result.getImages().get(0).getBlockDeviceMappings()) {
        if (blockingDevice.getEbs() != null) {
          def snapshot = blockingDevice.getEbs().getSnapshotId()
          ebsSnaphots << snapshot
        }
      }
    }
    return ebsSnaphots
  }

  private void modifySnapshotAttribute(client, snapshot, accounts) {

    def volumePermission = []
    accounts.each { account ->
      volumePermission << new CreateVolumePermission().withUserId(account)
    }

    def volumePermissionModifications = new CreateVolumePermissionModifications()
      .withAdd(volumePermission)

    client.modifySnapshotAttribute(new ModifySnapshotAttributeRequest()
      .withSnapshotId(snapshot)
      .withCreateVolumePermission(volumePermissionModifications)
    )
  }

  private void waitForCopy(Map amis) {
    amis.each { region, ami ->
      this.wait(region, ami)
      log("AMI: ${ami} in ${region} copy complete")
    }
  }

  def wait(amiRegion, ami) {
    def client = new AwsClientBuilder([
      region: amiRegion,
      awsAccountId: accountId,
      role: iamRole
    ]).ec2()
    def waiter = client.waiters().imageAvailable()

    try {
      def future = waiter.runAsync(
        new WaiterParameters<>(new DescribeImagesRequest().withImageIds(ami)),
        new NoOpWaiterHandler()
      )
      while(!future.isDone()) {
        try {
          log("waitng for ami ${ami} in ${amiRegion} to finish copying")
          Thread.sleep(10000)
        } catch(InterruptedException ex) {
            log("We seem to be timing out ${ex}...ignoring")
        }
      }
      client = null
      return true
    } catch(Exception e) {
      log("AMI: ${ami} in ${amiRegion} copy failed")
      client = null
      return false
    }
  }

  private void log(String message) {
    if(job) {
      job.echo(message)
    }
  }

}