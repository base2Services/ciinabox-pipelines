/***********************************
shareAMI DSL

# example usage
copyAMI(
  region: 'ap-southeast-2'
  ami: 'ami-1234abcd',
  targetRegions: ['us-west-2','us-east-1']
)
************************************/

import com.amazonaws.services.ec2.*
import com.amazonaws.services.ec2.model.*
import com.amazonaws.regions.*

def call(body) {
  AmazonEC2 sourceClient = setupClient(config.region)

  config.targetRegions.each { region ->
    AmazonEC2 targetClient = setupClient(region)
    copyImageId = copyAMI(targetClient,config)
    println "copied ${config.ami} from ${config.region} to ${region} with Id ${copyImageId}"
    copyTags(sourceClient,targetClient,
      config.ami,copyImageId)
  }
}

def copyAMI(client,config) {
  CopyImageRequest copyImageRequest = new CopyImageRequest()
    .withSourceImageId(config.ami)
    .withSourceRegion(config.region)

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

def setupClient(region) {
  return AmazonEC2ClientBuilder.standard()
    .withRegion(region)
    .build()
}
