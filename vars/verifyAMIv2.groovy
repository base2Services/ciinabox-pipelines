/***********************************
verifyAMIv2 DSL

Verifies a baked AMI using Test Kitchen and InSpec

example usage
verifyAMIv2(
  type: 'amzn-linux|centos|ubuntu' // (optional, defaults to linux)
  role: 'MyRole', // (required)
  cookbook: 'mycookbook', // (required)
  ami: 'ami-123456789098', // (conditional, one of ami or amiLookup or amiLookupSSM must be supplied)
  amiLookup: 'amzn-ami-hvm-2017.03.*', // (conditional, one of ami or amiLookup or amiLookupSSM must be supplied)
  amiLookupFilterTags: ['key':'value'], // (optional, filter amis when using amiLookup by specifying tags)
  amiLookupSSM: '/aws/path', // (conditional, one of ami or amiLookup or amiLookupSSM must be supplied)
  suite: 'InspecTestSuite' // (optional, defaults to role name)
  runlist: ['myrecipe'] // (optional, list of recipe to execute when running the test)
  region: 'ap-southeast-2', // (optional, will use jenkins region)
  az: 'a', // (optional, will use jenkins az)
  subnet: 'subnet-1234', // (optional, will lookup)
  securityGroup: 'sg-1234', // (optional, will lookup)
  instanceProfile: 'packer', // (optional, will lookup)
  instanceType: 't3.small', // (optional, default to m5.large)
  provisioner: 'chef | cinc' // (optional, set the kitchen provisioner. defaults to chef)
)
************************************/
import com.base2.ciinabox.aws.Util
import com.base2.ciinabox.InstanceMetadata
import com.base2.ciinabox.GetInstanceDetails

def call(body) {
  def config = body
  
  if (!config.role) {
    throw new GroovyRuntimeException("(role: 'MyRole') option must be provided")
  }
  
  if (!config.cookbook) {
    throw new GroovyRuntimeException("(cookbook: 'mycookbook') option must be provided")
  }
  
  def sourceAMI = null
  
  if (config.ami) {
    sourceAMI = config.ami
  } else if (config.amiLookup) {
    sourceAMI = lookupAMI(region: region, amiName: config.amiLookup, tags: config.get('amiLookupFilterTags',[:]))
  } else if (config.amiLookupSSM) {
    sourceAMI = lookupAMI(region: region, ssm: config.amiLookupSSM)
  } else {
    throw new GroovyRuntimeException("no ami supplied. must supply one of (ami: 'ami-1234', amiLookup: 'my-baked-ami-*', amiLookupSSM: '/my/baked/ami')")
  }
  
  if (!sourceAMI) {
    throw new GroovyRuntimeException("Unable to find AMI")
  }
  
  def suite = config.get('suite', config.role)
  def type = config.get('type', 'amzn-linux')
  
  // get the local region if not set by the method
  def region = config.get('region', Util.getRegion())
  if (!region) {
    throw new GroovyRuntimeException("no AWS region found")
  }

  def subnet = config.get('subnet')
  def securityGroup = config.get('securityGroup')
  def instanceProfile = config.get('instanceProfile')
  def instanceType = config.get('instanceType','m5.large')

  if (!subnet || !securityGroup || !instanceProfile) {
    println "looking up networking details to launch test kitchen instance in"

    // if the node is a ec2 instance using the ec2 plugin
    def instanceId = env.NODE_NAME.find(/i-[a-zA-Z0-9]*/)

    // if node name is not an instance id, try getting the instance id from the instance metadata
    if (!instanceId) {
      println "retrieving the instance metadata"
      def metadata = new InstanceMetadata()
      if (!metadata.isEc2) {
        throw new GroovyRuntimeException("unable to lookup networking details, try specifing (subnet: securityGroup: instanceProfile:) in your method")
      }
      instanceId = metadata.getInstanceId()
    }

    // get networking details from the instance
    def instance = new GetInstanceDetails(region, instanceId)

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

    println("""
=======================================================
| Using the following AWS details to run test kitchen |
=======================================================
| Region: ${region}
| Subnet: ${subnet}
| Security Group: ${securityGroup}
| Instance Profile: ${instanceProfile}
| Source AMI: ${sourceAMI}
| Instance Type: ${instanceType}
=======================================================
  """)
  
  def kitchenYaml = [
    driver: [
      name: 'ec2',
      region: region,
      subnet_id: subnet,
      security_group_ids: [securityGroup],
      require_chef_omnibus: true,
      iam_profile_name: instanceProfile,
      instance_type: instanceType,
      associate_public_ip: false,
      interface: 'private',
      tags: [
        "Name": "kitchen test ${config.role}",
        "Role": config.role
      ]
    ],
    provisioner: [],
    verifier: [
      name: 'inspec',
      sudo: true,
      reporter: [
        'cli'
      ]
    ],
    platforms: [
      [
        name: 'amazon',
        driver: [
          image_id: sourceAMI
        ]
      ]
    ],
    transport: [
      connection_timeout: 10,
      connection_retries: 5
    ],
    suites: []
  ]

  def provisioner = config.get('provisioner', 'chef')

  switch(provisioner) {
    case 'chef':
      kitchenYaml.provisioner = [
        solo_rb: [
          chef_license: 'accept'
        ],
        name: 'chef_solo',
        always_update_cookbooks: false
      ]
    break
    case 'cinc':
      kitchenYaml.provisioner = [
        name: 'chef_zero',
        product_name: 'cinc',
        download_url: 'https://omnitruck.cinc.sh/install.sh',
        always_update_cookbooks: false
      ]
    break
    default:
      throw new GroovyRuntimeException("${provisioner} is a unsuported provisioner. Must be one of 'chef | cinc'")
    break
  }

  def inspec_suite =[name: suite]
  
  if (config.runlist) {
    inspec_suite.run_list = []
    config.runlist.each { recipe ->
      inspec_suite.run_list << "recipe[${config.cookbook}::${recipe}]"
    }
    kitchenYaml.provisioner.always_update_cookbooks = true
  }
  
  kitchenYaml.suites << inspec_suite
  
  switch(config.type) {
    case 'amzn-linux':
      kitchenYaml.transport.username = 'ec2-user'
      break
    case 'centos':
      kitchenYaml.transport.username = 'centos'
      break
    case 'ubuntu':
      kitchenYaml.transport.username = 'ubuntu'
      break
    default:
      throw new GroovyRuntimeException("${config.type} is a unsuported type. Must be one of 'amzn-linux | centos | ubuntu'")
  }
  
  withAWSKeyPair(region) {
    dir("${env['WORKSPACE']}/${config.cookbook}") {
      kitchenYaml['driver']['aws_ssh_key_id'] = env['KEYNAME']
      kitchenYaml['transport']['ssh_key'] = "${env['WORKSPACE']}/${env['KEYNAME']}"
      kitchenYaml['verifier']['reporter'] << "junit:${env['WORKSPACE']}/${config.cookbook}/reports/%{platform}_%{suite}_inspec.xml"
      writeYAML(file: 'kitchen.yml', map: kitchenYaml)
      sh 'kitchen diagnose'
      sh 'kitchen destroy'
      sh 'kitchen test'
      sh 'kitchen destroy'
    }
  }
  
}