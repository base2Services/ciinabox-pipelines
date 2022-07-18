/***********************************
  packer DSL
  uses packer and ansible to bake an AMI
  packer(
    role: 'my-build', (required, suffixed with a datestamp)
    type: 'linux', // (optional, currently only supports linux)
    ami: 'ami-1234', // (conditional, one of ami or amiLookup or amiLookupSSM must be supplied)
    amiLookup: 'amzn-ami-hvm-2017.03.*', // (conditional, one of ami or amiLookup or amiLookupSSM must be supplied)
    amiLookupFilterTags: ['key':'value'], // (optional, filter amis when using amiLookup by specifying tags)
    amiLookupFilters: ['architecture':'x86_64'], // (optional, filter amis when using amiLookup using aws AMI filter keys. see aws docs for filter keys)
    amiLookupSSM: '/aws/path', // (conditional, one of ami or amiLookup or amiLookupSSM must be supplied)
    region: 'ap-southeast-2', // (optional, will use jenkins region)
    az: 'a', // (optional, will use jenkins az)
    subnet: 'subnet-1234', // (optional, will lookup)
    securityGroup: 'sg-1234', // (optional, will lookup)
    vpcId: 'vpc-1234', // (optional, will lookup)
    instanceProfile: 'packer', // (optional, will lookup)
    instanceType: 't3.small', // (optional, default to m5.large)
    ebsVolumeSize: "8", // (optional)
    ebsDeviceName: "/dev/xvda", // (optional, defaults to /dev/xvda)
    username: 'ec2-user|centos|ubuntu', // (optional, defaults to ec2-user)
    amiTags: ['key': 'value'], // (optional, provide tags to the baked AMI)
    packerPath: '/opt/packer/packer', // (optional, defaults to the path in base2/bakery docker image)
    debug: 'true|false', // (optional)
    runList: ['playbook.yaml'], (required, The playbook files to be executed by ansible)
    playbookDir: ['playbooks/'], (required, An array of directories of playbook files on your local system. These will be uploaded to the remote machine under playbook_directory/playbooks.)
    amiPlaybookDirectory: '/opt/ansible', (optional, set the AMI playbook directory where the playbooks are stored. defaults to /opt/ansible)
    cleanPlaybookPirectory: true|false, (optional, delete the contentents of the playbook_directory after executing ansible. this if false by default)
    extraArguments: ["--extra-vars", "Region=ap-southeast-2"]
    ansibleInstallCommand: ["pip install anisble"] (optional, defaults to installing ansible on amazon linux)
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

  if(!config.runList) {
    throw new GroovyRuntimeException("ansible playbook run list must be supplied")
  }

  if(!config.playbookDir) {
    throw new GroovyRuntimeException("a local directory of ansible playbooks to be supplied")
  }

  def platformType = config.get('type', 'linux')

  if (platformType != 'linux') {
    throw new GroovyRuntimeException("linux is currently the only support platform type for packerAnsible()")
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

  if ((config.ebsVolumeSize && !config.ebsVolumeSize) || (!config.ebsVolumeSize && config.ebsVolumeSize)) {
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

  def sshUser = config.get('username', 'ec2-user')
  ptb.addCommunicator(sshUser)

  def ansibleInstallCommand = ["sudo amazon-linux-extras install ansible2 -y"]

  if (config.ansibleInstallCommand) {
    ansibleInstallCommand = config.ansibleInstallCommand
  }

  def ansiblePlaybookDirectory = config.get('amiPlaybookDirectory', '/opt/ansible')
  ptb.addAnsibleInstallProvisioner(ansibleInstallCommand, ansiblePlaybookDirectory, sshUser)
  ptb.addAnsibleLocalProvisioner(
    config.runList,
    config.playbookDir,
    ansiblePlaybookDirectory,
    config.cleanPlaybookDirectory,
    config.extraArguments
  )

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
