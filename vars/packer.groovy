/***********************************
  packer DSL
  uses packer and chef to bake an AMI
  packer(
    role: 'my-build', (required, suffixed with a datestamp)
    type: 'linux|windows|windows2012', // (optional, defaults to linux)
    ami: 'ami-1234', // (conditional, one of ami or amiLookup or amiLookupSSM must be supplied)
    amiLookup: 'amzn-ami-hvm-2017.03.*', // (conditional, one of ami or amiLookup or amiLookupSSM must be supplied)
    amiLookupFilterTags: ['key':'value'], // (optional, filter amis when using amiLookup by specifying tags)
    amiLookupFilters: ['architecture':'x86_64'], // (optional, filter amis when using amiLookup using aws AMI filter keys. see aws docs for filter keys)
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
    useCinc: true, (optional, default is false)
    cincVersion: '15', // (optional, default to latest)
    runList: ['cookbook::recipe'] // (optional, list of recipes if using chef)
    amiTags: ['key': 'value'], // (optional, provide tags to the baked AMI)
    packerPath: '/opt/packer/packer', // (optional, defaults to the path in base2/bakery docker image)
    cookbookS3Bucket: 'source.cookbooks', // (conditional, required for type: 'windows')
    cookbookS3Path: 'chef/0.1.0/cookbooks.tar.gz', // (conditional, required for type: 'windows')
    cookbookS3Region: 'us-east-1', // (optional, defaults to packer region)
    debug: 'true|false', // (optional)
    winUpdate: true|false,  // (optional, whether to perform windows updates on AMI) 
    ec2LaunchV2: true|false, // (optional, set to true if windows ami is using EC2 Launch V2 package)
    uninstallCinc: true|false, // (optional, uninstall the cinc installation after the cinc provisioner)
    credssp: true|false //(optional, defaults to false. Uses a CredSSP WinRM connection when connecting to windows instances)
  )
************************************/
import com.base2.ciinabox.aws.Util
import com.base2.ciinabox.InstanceMetadata
import com.base2.ciinabox.GetInstanceDetails
import com.base2.ciinabox.PackerTemplateBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

def call(body) {
  def config = body

  if(!config.role) {
    throw new GroovyRuntimeException("(role: 'my-build') must be supplied")
  }

  def platformType = config.get('type', 'linux')

  if (platformType.startsWith('windows')) {
    if (!config.cookbookS3Bucket && !config.cookbookS3Path) {
      throw new GroovyRuntimeException("(cookbookS3Bucket: 'source.cookbooks', cookbookS3Path: 'chef/0.1.0/cookbooks.tar.gz') must be supplied when using type: 'windows'")
    }
  }

  def debug = config.get('debug', false)

  if (debug) {
    println('debug enabled')
  }

  // get the local region if not set by the method
  def region = config.get('region', Util.getRegion())
  if (!region) {
    throw new GroovyRuntimeException("no AWS region found")
  }

  def vpcId = config.get('vpcId')
  def subnet = config.get('subnet')
  def securityGroup = config.get('securityGroup')
  def instanceProfile = config.get('instanceProfile')
  def instanceType = config.get('instanceType','m5.large')
  def sourceAMI = null

  if (!vpcId || !subnet || !securityGroup || !instanceProfile) {
    println "looking up networking details to launch packer instance in"

    // if the node is a ec2 instance using the ec2 plugin
    def instanceId = env.NODE_NAME.find(/i-[a-zA-Z0-9]*/)

    // if node name is not an instance id, try getting the instance id from the instance metadata
    if (!instanceId) {
      println "retrieving the instance metadata"
      def metadata = new InstanceMetadata()
      if (!metadata.isEc2) {
        throw new GroovyRuntimeException("unable to lookup networking details, try specifing (vpcId: subnet: securityGroup: instanceProfile:) in your method")
      }
      instanceId = metadata.getInstanceId()
    }

    // get networking details from the instance
    def instance = new GetInstanceDetails(region, instanceId)

    if (!vpcId) {
      vpcId = instance.vpcId()
    }

    if (!subnet) {
      subnet = instance.subnet()
    }

    if (!securityGroup) {
      securityGroup = instance.securityGroup()
    }

    if (!instanceProfile) {
      instanceProfile = instance.instanceProfile()
    }
  }

  if (config.ami) {
    sourceAMI = config.ami
  } else if (config.amiLookup) {
    sourceAMI = lookupAMI(
      region: region,
      amiName: config.amiLookup,
      tags: config.get('amiLookupFilterTags', [:]),
      filters: config.get('amiLookupFilters', [:])
    )
  } else if (config.amiLookupSSM) {
    sourceAMI = lookupAMI(region: region, ssm: config.amiLookupSSM)
  } else {
    throw new GroovyRuntimeException("no ami supplied. must supply one of (ami: 'ami-1234', amiLookup: 'my-baked-ami-*', amiLookupSSM: '/my/baked/ami')")
  }

  if (!sourceAMI) {
    throw new GroovyRuntimeException("Unable to find AMI")
  }

  if ((config.ebsVolumeSize && !config.ebsDeviceName) || (!config.ebsVolumeSize && config.ebsDeviceName)) {
    throw new GroovyRuntimeException("Supply both ebs Volume Size and DeviceName")
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
| PlatformType: ${platformType}
=================================================
  """)

  def ptb = new PackerTemplateBuilder(config.role, platformType)
  ptb.builder.region = region
  ptb.builder.source_ami = sourceAMI
  ptb.builder.instance_type = instanceType
  ptb.builder.iam_instance_profile = instanceProfile
  ptb.builder.vpc_id = vpcId
  ptb.builder.subnet_id = subnet
  ptb.builder.security_group_id = securityGroup
  if (config.ebsDeviceName && config.ebsVolumeSize) {
    ptb.addBlockDevice(config.ebsDeviceName, config.ebsVolumeSize) 
  }

  if (config.amiTags) {
    ptb.addAmiTags(config.amiTags)
  }

  ptb.addCommunicator(config.get('username', 'ec2-user'), config.get('credssp', false))
  ptb.addInstall7zipProvisioner()

  if (config.winUpdate) {
    ptb.addWindowsUpdate()
  }

  if (config.runList) {
    ptb.addDownloadCookbookProvisioner(
      config.get('cookbookS3Bucket'),
      config.get('cookbookS3Region', region),
      config.get('cookbookS3Path'))
    if (config.get('useCinc', false)) {
      ptb.addChefSoloProvisioner(config.runList,config.get('chefJSON'),config.get('cincVersion'), true)
    } else {
      ptb.addChefSoloProvisioner(config.runList,config.get('chefJSON'),config.get('chefVersion'))
    }
  }

  if (config.uninstallCinc) {
    ptb.addUninstallCincProvisioner()
  }

  if (config.ec2LaunchV2) {
    ptb.addAmazonEc2LaunchV2Provisioner()
  } else {
    ptb.addAmazonConfigProvisioner()
  }

  writeScript('packer/download_cookbooks.ps1')
  writeScript('packer/ec2_config_service.ps1')
  writeScript('packer/ec2_launch_config.ps1')
  writeScript('packer/install_7zip.ps1')
  writeScript('packer/setup_winrm.ps1')
  writeScript('packer/setup_winrm_credssp.ps1')
  writeScript('packer/uninstall_cinc.ps1')

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
  sh "${packerPath} plugins install github.com/hashicorp/amazon"
  sh "${packerPath} plugins install github.com/hashicorp/chef"
  sh "${packerPath} validate ${ptb.id}.json"

  println("""
=================================================
| Executing packer with the template            |
=================================================
  """)

  def debugFlag = (debug) ? '-debug' : ''
  sh "${packerPath} build ${debugFlag} -machine-readable ${ptb.id}.json"

  def manifest = readFile(file: "${ptb.id}.manifest.json")

  println("""
=================================================
| Generated packer template                     |
=================================================
${manifest}
=================================================
    """)

  def data = new JsonSlurperClassic().parseText(manifest)
  def build = data['builds'].first()
  def amiId = build['artifact_id'].split(':').last()

  env["${config.role.toUpperCase()}_BAKED_AMI"] = amiId
  env["${config.role.toUpperCase()}_BAKED_NAME"] = ptb.builder.ami_name
  env["${config.role.toUpperCase()}_BAKED_ID"] = ptb.id

  return [amiId: amiId, name: ptb.builder.ami_name, id: ptb.id]
}

def writeScript(path) {
  def content = libraryResource(path)
  def fileName = path.split('/').last()
  writeFile(file: fileName, text: content)
}
