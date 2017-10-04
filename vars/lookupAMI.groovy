/***********************************
 lookupAMI DSL

 Looks up an AMI to

 example usage
 lookupAMI region: 'ap-southeast-2',  name: 'xyz', tags: ['status':'verifed']
 ************************************/
@Grab(group='com.amazonaws', module='aws-java-sdk-ec2', version='1.11.198')
@Grab(group='com.amazonaws', module='aws-java-sdk-sts', version='1.11.198')

import com.amazonaws.services.ec2.*
import com.amazonaws.services.ec2.model.*
import com.amazonaws.regions.*
import com.amazonaws.services.securitytoken.*
import com.amazonaws.services.securitytoken.model.*

def call(body) {
  def config = body

  if(!config['owner']) {
    config.owner = lookupAccountId()
  }
  println "lookup config:${config}"
  def image = lookupAMI(config)
  if(image) {
    println "image:${image}"
    env["SOURCE_AMI"]=image.imageId
    return image.imageId
  } else {
    println "ami not found for ${config}"
    return null
  }
}

def lookupAMI(config) {
  def ec2 = AmazonEC2ClientBuilder.standard()
    .withRegion(config.region)
    .build()

  def filters = []
  filters << new Filter().withName('name').withValues(config.amiName)
  if(config['tags']) {
    config.tags.each { key, value ->
      filters << new Filter("tag:${key}").withValues("${value}")
    }
  }

  def imagesList = ec2.describeImages(new DescribeImagesRequest()
    .withOwners([config.owner])
    .withFilters(filters)
  )
  if(imagesList.images.size () > 0) {
    def images = imagesList.images.collect()
    println "imaage:${images}"
    return images.get(findNewestImage(images))
  }
  return null
}

def findNewestImage(images) {
  def index = 0
  def newest = Date.parse("yyyy-MM-dd", "2000-01-01")
  def found = 0
  images.each { image ->
    imageDate = Date.parse("yyyy-MM-dd'T'HH:mm:ss", image.creationDate)
    if(imageDate >= newest) {
      found = index
      newest = imageDate
    }
    index++
  }
  return found
}

def lookupAccountId() {
  def sts = AWSSecurityTokenServiceClientBuilder.standard()
    .withRegion(Regions.AP_SOUTHEAST_2)
    .build()
  return sts.getCallerIdentity(new GetCallerIdentityRequest()).account
}
