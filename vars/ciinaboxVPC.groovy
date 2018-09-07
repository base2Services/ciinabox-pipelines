/***********************************
 ciinabox VPC

 Lookups the output Params from the ciinabox vpc and writes them to a 
 json file in the workspace called base_params.json

 example usage
 ciinaboxVPC(
   ciinabox: 'ciinabox',
   region: env.REGION,
   availabilityZone: 'a'
 )
 the optional az attribute allows you to override which availability zone is returned
 ************************************/
 @Grab(group='com.amazonaws', module='aws-java-sdk-cloudformation', version='1.11.359')

import com.amazonaws.services.cloudformation.*
import com.amazonaws.services.cloudformation.model.*

def call(body) {
  def config = body
  
  az = config.get('availabilityZone', 'a').toUpperCase()
  ciinaboxName = config.get('ciinabox', 'ciinabox')
  def ciinabox = ciinaboxStack(ciinaboxName, config.region)
  if(ciinabox) {
    def outputs = [:]
    ciinabox.outputs.each { output ->
      outputs[output.outputKey] = output.outputValue
    }
    println "ciinabox outputs:${outputs}"
    def paramsFile = config.get('outputFile','base_params.json')
    def exist = fileExists(paramsFile)
    if(exist) {
      new File(paramsFile).delete()
    }
    writeFile file: paramsFile, text: toJson(outputs, az)
  } else {
    throw new RuntimeException("no ciinabox stack ${ciinabox} found")
  }

}

@NonCPS
def ciinaboxStack(stackName, region) {
  try {
    def cf = setupClient(region)
    DescribeStacksResult result = cf.describeStacks(new DescribeStacksRequest().withStackName(stackName))
    return result.getStacks().get(0)
  } catch (AmazonCloudFormationException ex) {
    if(ex.message.contains("does not exist")) {
      return null
    } else {
      throw ex
    }
  }
}

@NonCPS
def toJson(outputs, az) {
  subnet = "ECSPrivateSubnet${az}"
  def json_text = """{
    "region": "${outputs['Region']}",
    "vpc_id": "${outputs['VPCId']}",
    "subnet_id": "${outputs[subnet]}",
    "security_group": "${outputs['SecurityGroup']}",
    "packer_role": "${outputs['ECSRole']}",
    "packer_instance_profile": "${outputs['ECSInstanceProfile']}"
  }"""
  return json_text
}

@NonCPS
def setupClient(region) {
  def cb = AmazonCloudFormationClientBuilder.standard().withRegion(region)
  return cb.build()
}
