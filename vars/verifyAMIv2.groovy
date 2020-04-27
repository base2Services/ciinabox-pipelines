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
  vpcId: 'vpc-1234', // (optional, will lookup)
  instanceProfile: 'packer', // (optional, will lookup)
  instanceType: 't3.small' // (optional, default to m5.large)
)
************************************/
import com.base2.ciinabox.InstanceMetadata
import com.base2.ciinabox.GetInstanceDetails

def call(body) {
  def config = body
  
  if (!config.role) {
    error("(role: 'MyRole') option must be provided")
  }
  
  if (!config.cookbook) {
    error("(cookbook: 'mycookbook') option must be provided")
  }
  
  def sourceAMI = null
  
  if (config.ami) {
    sourceAMI = config.ami
  } else if (config.amiLookup) {
    sourceAMI = lookupAMI(region: region, name: config.amiLookup, tags: config.get('amiLookupFilterTags',[:]))
  } else if (config.amiLookupSSM) {
    sourceAMI = lookupAMI(region: region, ssm: config.amiLookupSSM)
  } else {
    error("no ami supplied. must supply one of (ami: 'ami-1234', amiLookup: 'my-baked-ami-*', amiLookupSSM: '/my/baked/ami')")
  }
  
  if (!sourceAMI) {
    error("Unable to find AMI")
  }
  
  def suite = config.get('suite', config.role)
  def type = config.get('type', 'amzn-linux')
  
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
    provisioner: [
      solo_rb: [
        chef_license: 'accept'
      ],
      name: 'chef_solo',
      always_update_cookbooks: false
    ],
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
      error("${config.type} is a unsuported type. Must be one of 'amzn-linux | centos | ubuntu'")
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