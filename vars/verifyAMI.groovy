/***********************************
 verifyAMI DSL

 Verifies a baked AMI using Test Kitchen and InSpec

 example usage
 verifyAMI ......
 ************************************/

def call(body) {
  def config = body
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

platforms:
  - name: amazonLinux
    driver:
      image_id: ${VERIFY_AMI}

transport:
  ssh_key: ${WORKSPACE}/${KEYNAME}
EOT

cat <<EOT > userdata.sh
#!/bin/bash
/opt/base2/bin/ec2-bootstrap ap-southeast-2 537712071186
EOT
gem install kitchen-ec2 --no-rdoc
kitchen destroy
kitchen test
      '''
    }
  }
}
