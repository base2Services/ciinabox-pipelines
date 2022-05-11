/***********************************
lookupAMI DSL

Looks up an AMI id by name or from a ssm parameter

example usage

lookupAMI(
  region: 'ap-southeast-2', // (optional, if not set will retrieve the current region)
  amiName: 'ami-name-*', // (conditional, ami name to search for. accepts wildcards. either name or ssm must be set)
  owner: '12345678912', // (optional, account id that owns the ami)
  tags: ['status':'verifed'], // (optional, filter ami name lookup by tags)
  filters: ['architecture', 'x86_64'], // (optional, add additional ami lookup filters, see aws docs for filter keys)
  ssm: '/ssm/path/ami', // (conditional, retrive ami from ssm parameter. either name or ssm must be set)
  env: 'MY_AMI' // (optional, environment variable name. defaults to env["SOURCE_AMI"])
)
 ************************************/

import com.base2.ciinabox.aws.Util
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Filter

def call(body) {
  def config = body
  def imageId = null
  
  if(!config.region) {
    config.region = Util.getRegion()
  }
  
  if (config.ssm) {
    def parameter = config.ssm
    echo "looking up ami id from ssm parameter ${parameter} in the ${config.region} region"
    def path = parameter - parameter.substring(parameter.lastIndexOf("/"))
    def params = ssmParameter(action: 'get', parameter: path, region: config.region)
    def resp = params.find {it.name.equals(parameter)}
    if (resp) { 
      imageId = resp.value 
    }
  } else if (config.amiName) {
    echo "looking up ami id by name ${config.amiName} in the ${config.region} region"
    def resp = lookupAMIRequest(config)
    if (resp) { 
      imageId = resp.imageId
    }
  } else {
    throw new GroovyRuntimeException("one of 'name' or 'ssm' parameters must be supplied")
  }
  
  if (!imageId) {
    throw new GroovyRuntimeException("unable to find ami from config: ${config}")
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

def lookupAMIRequest(config) {  
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

  if (config.filters) {
    config.filters.each { key, value ->
      filters << new Filter(key).withValues(value)
    }
  }

  def describeImageRequest = new DescribeImagesRequest().withFilters(filters)

  if (config.owner) {
    describeImageRequest.withOwners([config.owner])
  }

  def imagesList = ec2.describeImages(describeImageRequest)

  ec2 = null

  if(imagesList.images.size () > 0) {
    def images = imagesList.images.collect()
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
