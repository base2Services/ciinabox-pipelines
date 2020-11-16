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

  def sourceClientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.accountId,
    role: config.role
  ])
  def targetClientBuilder = new AwsClientBuilder([
    region: config.copyRegion,
    awsAccountId: config.accountId,
    role: config.role
  ])

  AmazonEC2 sourceClient = sourceClientBuilder.ec2()
  AmazonEC2 targetClient = targetClientBuilder.ec2()
  copyImageId = copyAMI(targetClient,config)

  if (config.name) {
    env[config.name.toUpperCase() + '_COPIED_AMI'] = copyImageId
  }

  println "copied ${config.ami} from ${config.region} to ${config.copyRegion} with Id ${copyImageId}"
  copyTags(sourceClient,targetClient,config.ami,copyImageId)
  if (config.waitOnCopy) {
    wait(targetClient, config.copyRegion, copyImageId) 
  }
  sourceClient = null
  targetClient = null
  return copyImageId
}

def copyAMI(client,config) {
  CopyImageRequest copyImageRequest = new CopyImageRequest()
    .withSourceImageId(config.ami)
    .withSourceRegion(config.region)
     
  if (config.encrypted) {
    copyImageRequest.withEncrypted(true)
    if (config.kmsKeyId) {
      copyImageRequest.withKmsKeyId(config.kmsKeyId)
    }
  }

  CopyImageResult copyImageResult = client
    .copyImage(copyImageRequest)

  return copyImageResult.getImageId();
}

def copyTags(sourceClient,targetClient,sourceImageId,copyImageId) {
  DescribeImagesResult describeImagesResult = sourceClient
				.describeImages(new DescribeImagesRequest()
						.withImageIds(sourceImageId))

  List<Image> images = describeImagesResult.getImages()
	Image image = images.get(0)

	for (Tag tag : image.getTags()) {
		targetClient.createTags(new CreateTagsRequest().withResources(
				copyImageId).withTags(tag))
	}
}

def wait(client, region, ami)   {
  Waiter waiter = client.waiters().imageAvailable()

  try {
    Future future = waiter.runAsync(
      new WaiterParameters<>(new DescribeImagesRequest().withImageIds(ami)),
      new NoOpWaiterHandler()
    )
    while(!future.isDone()) {
      try {
        echo "waiting for ami ${ami} in ${region} to finish copying"
        Thread.sleep(10000)
      } catch(InterruptedException ex) {
          echo "We seem to be timing out ${ex}...ignoring"
      }
    }
    println "AMI: ${ami} in ${region} copy complete"
    return true
   } catch(Exception e) {
     println "AMI: ${ami} in ${region} copy failed"
     return false
   }
}