/***********************************
  packer DSL

  uses packer and chef to bake an AMI

  packer(
    role: 'my-build', (required, suffixed with a datestamp)
    type: 'linux|windows|windows2012', // (optional, defaults to linux)
    ami: 'ami-1234', // (conditional, one of ami or amiLookup or amiLookupSSM must be supplied)
    amiLookup: 'amzn-ami-hvm-2017.03.*', // (conditional, one of ami or amiLookup or amiLookupSSM must be supplied)
    amiLookupFilterTags: ['key':'value'], // (optional, filter amis when using amiLookup by specifying tags)
    amiLookupSSM: '/aws/path', // (conditional, one of ami or amiLookup or amiLookupSSM must be supplied)
    region: 'ap-southeast-2', // (optional, will use jenkins region)
    az: 'a', // (optional, will use jenkins az)
    ssmLookup: '/ciinabox/packer', // (optional, the default lookup is the jenkins master details)
    subnet: 'subnet-1234', // (optional, will lookup)
    securityGroup: 'sg-1234', // (optional, will lookup)
    vpcId: 'vpc-1234', // (optional, will lookup)
    instanceProfile: 'packer', // (optional, will lookup)
    instanceType: 't3.small', // (optional, default to m5.large)
    ebsVolumeSize: "8", // (optional)
    ebsDeviceName: "/dev/xvda", // (optional, defaults to /dev/xvda)
    username: 'ec2-user|centos|ubuntu', // (optional, defaults to ec2-user) 
    chefVersion: '12.20.3', // (optional, default to latest)
    chefJSON: '{"build_number": 1.0.3}', // (optional)
    runList: ['cookbook::recipe'] // (required, list of recipes)
    amiTags: ['key': 'value'], // (optional, provide tags to the baked AMI)
    packerPath: '/opt/packer/packer', // (optional, defaults to the path in base2/bakery docker image)
    cookbookS3Bucket: 'source.cookbooks', // (conditional, required for type: 'windows')
    cookbookS3Path: 'chef/0.1.0/cookbooks.tar.gz', // (conditional, required for type: 'windows')
    cookbookS3Region: 'us-east-1', // (optional, defaults to packer region)
    debug: 'true|false', // (optional)
  )
************************************/
import com.base2.ciinabox.InstanceMetadata
import com.base2.ciinabox.GetInstanceDetails
import com.base2.ciinabox.PackerTemplateBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

def call(body) {
  def config = body
  
  if(!config.role) {
    error("(role: 'my-build') must be supplied")
  }
  
  if (!config.runList) {
    error("(runList: ['cookbook::recipe']) must be supplied")
  }
  
  if (config.type.startsWith('windows')) {
    if (!config.cookbookS3Bucket && !config.cookbookS3Path) {
      error("(cookbookS3Bucket: 'source.cookbooks', cookbookS3Path: 'chef/0.1.0/cookbooks.tar.gz') must be supplied when using type: 'windows'")
    }
  }

  def debug = config.get('debug', false)
  
  if (debug) {
    println('debug enabled')
  }
  
  def metadata = new InstanceMetadata()
  // if the node is a ec2 instance using the ec2 plugin
  def instanceId = env.NODE_NAME.find(/i-[a-zA-Z0-9]*/)
    
  if (!instanceId) {
    instanceId = metadata.instanceId()
  }

  def instance = new GetInstanceDetails(metadata.region(), instanceId)
  
  def region = config.get('region', metadata.region())
  def vpcId = config.get('vpcId', instance.vpcId())
  def subnet = config.get('subnet', instance.subnet())
  def securityGroup = config.get('securityGroup', instance.securityGroup())
  def instanceProfile = config.get('instanceProfile', instance.instanceProfile())
  def instanceType = config.get('instanceType','m5.large')
  def sourceAMI = null
  
  if (config.ami) {
    sourceAMI = config.ami
  } else if (config.amiLookup) {
    sourceAMI = lookupAMI(region: region, amiName: config.amiLookup, tags: config.get('amiLookupFilterTags',[:]))
  } else if (config.amiLookupSSM) {
    sourceAMI = lookupAMI(region: region, ssm: config.amiLookupSSM)
  } else {
    error("no ami supplied. must supply one of (ami: 'ami-1234', amiLookup: 'my-baked-ami-*', amiLookupSSM: '/my/baked/ami')")
  }
  
  if (!sourceAMI) {
    error("Unable to find AMI")
  }
  
  println("""
=================================================
| Using the following AWS details to run packer |
=================================================
| Region: ${region}
| VpcId: ${vpcId}
| Subnet: ${subnet}
| Security Group: ${securityGroup}
| Instance Profile: ${instanceProfile}
| Source AMI: ${sourceAMI}
| Instance Type: ${instanceType}
=================================================
  """)
  
  def ptb = new PackerTemplateBuilder(config.role, config.get('type', 'linux'))
  ptb.builder.region = region
  ptb.builder.source_ami = sourceAMI
  ptb.builder.instance_type = instanceType
  ptb.builder.iam_instance_profile = instanceProfile
  ptb.builder.vpc_id = vpcId
  ptb.builder.subnet_id = subnet
  ptb.builder.security_group_id = securityGroup
  
  ptb.addCommunicator(config.get('username', 'ec2-user'))
  ptb.addInstall7zipProvisioner()
  ptb.addDownloadCookbookProvisioner(
    config.get('cookbookS3Bucket'), 
    config.get('cookbookS3Region', region), 
    config.get('cookbookS3Path'))
  ptb.addChefSoloProvisioner(config.runList,config.get('chefJSON'),config.get('chefVersion'))
  ptb.addAmamzonConfigProvisioner()
  
  writeScript('packer/download_cookbooks.ps1')
  writeScript('packer/ec2_config_service.ps1')
  writeScript('packer/ec2_launch_config.ps1')
  writeScript('packer/install_7zip.ps1')
  writeScript('packer/setup_winrm.ps1')
  
  def packerTemplate = ptb.toJson()
  def packerPath = config.get('packerPath', '/opt/packer/packer')
  
  if (debug) {
    println("""
=================================================
| Generated packer template                     |
=================================================
${packerTemplate}
=================================================
    """)
  }
  
  writeFile(file: "${ptb.id}.json", text: packerTemplate)
  
  println("""
=================================================
| Validating packer template                    |
=================================================
  """)
  sh "${packerPath} validate ${ptb.id}.json"
  
  println("""
=================================================
| Executing packer with the template            |
=================================================
  """)
  
  def debugFlag = (debug) ? '-debug' : ''  
  sh "${packerPath} build ${debugFlag} -machine-readable ${ptb.id}.json"
  
  def manifest = readFile(file: 'manifest.json')
  def data = new JsonSlurperClassic().parseText(manifest)
  def build = data['builds'].first()
  env["${config.role.toUpperCase()}_BAKED_AMI"] = build['artifact_id'].split(':').last()
  env["${config.role.toUpperCase()}_BAKED_NAME"] = ptb.builder.ami_name
  env["${config.role.toUpperCase()}_BAKED_ID"] = ptb.id
}

def writeScript(path) {
  def content = libraryResource(path)
  def fileName = path.split('/').last()
  writeFile(file: fileName, text: content)
}