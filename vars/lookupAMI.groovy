/***********************************
 lookupAMI DSL

 Looks up an AMI id by name or from a ssm parameter

 example usage
 lookupAMI(
  region: 'ap-southeast-2', // (optional, if not set will retrieve the current region)
  name: 'ami-name-*', // (conditional, ami name to search for. accepts wildcards. either name or ssm must be set)
  owner: '12345678912', // (optional, account id that owns the ami. defaults to the current aws account)
  tags: ['status':'verifed'], // (optional, filter ami name lookup by tags)
  ssm: '/ssm/path/ami', // (conditional, retrive ami from ssm parameter. either name or ssm must be set)
  env: 'MY_AMI' // (optional, environment variable name. defaults to env["SOURCE_AMI"])
)
 ************************************/

import com.base2.ciinabox.AwsHelper
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Filter

def call(body) {
  def config = body
  def imageId = null
  
  if(!config.region) {
    config.region = getRegion()
  }
  if(!config.owner) {
    config.owner = getAccountId()
  }
  
  if (config.ssm) {
    def parameter = config.ssm
    echo "looking up ami id from ssm parameter ${parameter} in the ${config.region} region"
    def path = parameter - parameter.substring(param.lastIndexOf("/"))
    def params = ssmParameter(action: 'get', parameter: path, region: config.region)
    def resp = params.find {it.name.equals(parameter)}
    if (resp) { imageId = resp.value }
  } else if (config.name) {
    echo "looking up ami id by name ${config.name} in the ${config.region} region"
    def resp = lookupAMI(config)
    imageId = resp.imageId
  } else {
    error("one of 'name' or 'ssm' parameters must be supplied")
  }
  
  if (!imageId) {
    error("unable to find ami from config: ${config}")
  }
  
  if(imageId) {
    echo "found AMI: ${imageId}"
    
    if (config.env) {
      env[config.env] = imageId
    } else {
      env["SOURCE_AMI"] = imageId
    }
    
    return imageId
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
