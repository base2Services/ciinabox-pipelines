/***********************************
 lookupAMI DSL

 Looks up an AMI to

 example usage
 lookupAMI region: 'ap-southeast-2',  name: 'xyz', tags: ['status':'verifed']
 ************************************/

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
  filters << new Filter().withName('root-device-type').withValues('ebs')
  if(config['tags']) {
    config.tags.each { key, value ->
      filters << new Filter("tag:${key}").withValues("${value}")
    }
  }

  if(config.amiBranch) {
    def amiBranch = config.amiBranch.replaceAll("/", "-")
    filters << new Filter("tag:BranchName").withValues("${amiBranch}","master")
  }

  def imagesList = ec2.describeImages(new DescribeImagesRequest()
    .withOwners([config.owner])
    .withFilters(filters)
  )
  if(imagesList.images.size () > 0) {
    def images = imagesList.images.collect()
    println "image:${images}"
    if(config.amiBranch) {
      images = filterAMIBranch(images, config.amiBranch.replaceAll("/", "-"))
    }
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

def filterAMIBranch(images, amiBranch) {
  branchImages = []
  images.each { image ->
    image.tags.each { tag ->
      if(tag.key == 'BranchName' && tag.value == amiBranch) {
        branchImages << image
      }
    }
  }
  if(branchImages.size() == 0) {
    return images
  } else {
    return branchImages
  }
}

def lookupAccountId() {
  def sts = AWSSecurityTokenServiceClientBuilder.standard()
    .withRegion(Regions.AP_SOUTHEAST_2)
    .build()
  return sts.getCallerIdentity(new GetCallerIdentityRequest()).account
}
