/***********************************
 verifyAMI DSL

 Verifies a baked AMI using Test Kitchen and InSpec

 example usage
 verifyAMI role: 'MyRole', cookbook: env.COOKBOOK, ami: env.MYROLE_BAKED_AMI
 ************************************/

def call(body) {
  def config = body
  node {
    deleteDir()
    unstash 'cookbook'
    withEnv(["REGION=${config.get('region')}", "VERIFY_AMI=${config.get('ami')}", "ROLE=${config.get('role')}", "COOKBOOK=${config.get('cookbook')}"]) {
      withAWSKeyPair(config.get('region')) {
        sh '''#!/bin/bash
eval "$(/opt/chefdk/bin/chef shell-init bash)"

tar xfz cookbooks.tar.gz

cd cookbooks/$COOKBOOK

cat <<EOT > .kitchen.local.yml
---

driver:
  aws_ssh_key_id: ${KEYNAME}
  user_data: userdata.sh

verifier:
  name: inspec
  sudo: true
  format: junit
  output: ./reports/%{platform}_%{suite}_inspec.xml

platforms:
  - name: amazonLinux
    driver:
      image_id: ${VERIFY_AMI}

transport:
  ssh_key: ${WORKSPACE}/${KEYNAME}
EOT

cat <<EOT > userdata.sh
#!/bin/bash
/opt/base2/bin/ec2-bootstrap ${REGION} 123456789098
EOT
gem install kitchen-ec2 --no-rdoc
kitchen destroy
kitchen test -l debug
      '''
      }
    }
  }
}
