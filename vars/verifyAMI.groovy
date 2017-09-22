/***********************************
 verifyAMI DSL

 Verifies a baked AMI using Test Kitchen and InSpec

 example usage
 verifyAMI ......
 ************************************/

def call(body) {
  def config = body
  deleteDir()
  unstash 'baked-ami'
  unstash 'cookbook'
  withEnv(["REGION=${config.get('region')}", "ROLE=${config.get('role')}", "COOKBOOK=${config.get('cookbook')}"]) {
    withAWSKeyPair(config.get('region')) {
      sh '''#!/bin/bash
eval "$(/opt/chefdk/bin/chef shell-init bash)"

tar xfz cookbooks.tar.gz

SOURCE_AMI=$(grep 'ami:' ${ROLE}-ami-*.yml | awk -F ':' {'print $2'})
echo $SOURCE_AMI
cd cookbooks/$COOKBOOK

cat <<EOT > .kitchen.local.yml
---

driver:
  aws_ssh_key_id: ${KEYNAME}
  user_data: userdata.sh

platforms:
  - name: amazonLinux
    driver:
      image_id: ${SOURCE_AMI}

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


def createKitchenLocalOverride(keyname, sourceAMI) {
  def kitchenLocal = """
---

driver:
  aws_ssh_key_id: ${keyname}
  user_data: userdata.sh

platforms:
  - name: amazonLinux
    driver:
      image_id: ${sourceAMI}

transport:
  ssh_key: ${keyname}

  """
  def file = new File('.kitchen.local.yml')
  file << kitchenLocal
  file.close()
}

/*
cat <<EOT > cookbooks/imfree/.kitchen.local.yml
---

driver:
  aws_ssh_key_id: ${KEYNAME}
  user_data: userdata.sh

platforms:
  - name: amazonLinux
    driver:
      image_id: ${SOURCE_AMI}

transport:
  ssh_key: ${KEYNAME}


EOT
*/
