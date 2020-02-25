/***********************************
  packer DSL

  uses packer to bake an AMI

  packer(
    type: 'linux|windows', // (optional, defaults to linux)
    ami: 'ami-1234', // (optional, will lookup)
    subnet: 'subnet-1234', // (optional, will lookup)
    security-group: 'sg-1234', // (optional, will lookup)
    vpcId: 'vpc-1234', // (optional, will lookup)
    instanceProfile: 'packer', // (optional, will lookup)
    ebsVolumeSize: 8, // (optional)
    sshUsername: 'ec2-user|centos|ubuntu', // (optional, defaults to ec2-user) 
    chefVersion: '12.20.3', // (optional, default to latest)
    debug: 'true|false', // (optional)
  )
************************************/

def call(body) {
  /* 
  1. lookup required aws resources
      - subnets
      - security group
      - vpc-id
      - instance-profile
      - ami
  2. generate packer template
  3. unstash cookbooks
  4. run packer
  */
}

def lookupVpc() {
  
}

def lookupSubnet() {
  
}