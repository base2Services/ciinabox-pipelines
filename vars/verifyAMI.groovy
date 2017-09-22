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
      sh '''
        tar xfz cookbooks.tar.gz
        ls -al
        SOURCE_AMI=$(grep 'ami:' ${ROLE}-ami-*.yml | awk -F ':' {'print $2'})
        echo $SOURCE_AMI
        cd cookbooks/$COOKBOOK
        cat .kitchen.yml
        echo ${KEYNAME}
        cat ${KEYNAME}
      '''
    }
  }
  sh 'ls -al'
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
