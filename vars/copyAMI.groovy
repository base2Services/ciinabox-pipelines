/***********************************
shareAMI DSL

# example usage
copyAMI(
  name: 'bastion',
  region: 'ap-southeast-2'
  ami: 'ami-1234abcd',
  copyRegion: 'us-west-2',
  encrypted: true,
  kmsKeyId: 'arn:aws:kms:ap-southeast-2:012345678901:key/11111111-2222-3333-4444-555555555555',
  waitOnCopy: true
)
************************************/

import com.amazonaws.services.ec2.*
import com.amazonaws.services.ec2.model.*
import com.amazonaws.regions.*
import com.amazonaws.waiters.*
import java.util.concurrent.Future
import com.base2.ciinabox.aws.AwsClientBuilder

def call(body) {
  def config = body
  config.accountId = config.get('accountId', null)
  config.role = config.get('role', null)

  def copyImageId = copyAMI(config)

  if (config.name) {
    env[config.name.toUpperCase() + '_COPIED_AMI'] = copyImageId
  }

  println "copied ${config.ami} from ${config.region} to ${config.copyRegion} with Id ${copyImageId}"
  copyTags(config.ami,copyImageId)
  if (config.waitOnCopy) {
    wait(this, config.copyRegion, copyImageId) 
  }
  return copyImageId
}

@NonCPS
def copyAMI(config) {
  def client = new AwsClientBuilder([
    region: config.copyRegion,
    awsAccountId: config.accountId,
    role: config.role
  ]).ec2()
  def copyImageRequest = new CopyImageRequest()
    .withSourceImageId(config.ami)
    .withSourceRegion(config.region)
     
  if (config.encrypted) {
    copyImageRequest.withEncrypted(true)
    if (config.kmsKeyId) {
      copyImageRequest.withKmsKeyId(config.kmsKeyId)
    }
  }

  def copyImageResult = client
    .copyImage(copyImageRequest)

  return copyImageResult.getImageId();
}

@NonCPS
def copyTags(sourceImageId,copyImageId) {
  def sourceClient = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.accountId,
    role: config.role
  ]).ec2()
  def targetClient = new AwsClientBuilder([
    region: config.copyRegion,
    awsAccountId: config.accountId,
    role: config.role
  ]).ec2()
  def describeImagesResult = sourceClient.describeImages(new DescribeImagesRequest().withImageIds(sourceImageId))

  def images = describeImagesResult.getImages()
  def image = images.get(0)

  for (Tag tag : image.getTags()) {
    targetClient.createTags(new CreateTagsRequest().withResources(copyImageId).withTags(tag))
  }
}

@NonCPS
def wait(steps, region, ami)   {
  def client = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.accountId,
    role: config.role
  ]).ec2()
  def waiter = client.waiters().imageAvailable()

  try {
    def future = waiter.runAsync(
      new WaiterParameters<>(new DescribeImagesRequest().withImageIds(ami)),
      new NoOpWaiterHandler()
    )
    while(!future.isDone()) {
      try {
        steps.echo "waiting for ami ${ami} in ${region} to finish copying"
        Thread.sleep(10000)
      } catch(InterruptedException ex) {
          steps.echo "We seem to be timing out ${ex}...ignoring"
      }
    }
    steps.echo "AMI: ${ami} in ${region} copy complete"
    return true
   } catch(Exception e) {
     steps.echo "AMI: ${ami} in ${region} copy failed"
     return false
   }
}