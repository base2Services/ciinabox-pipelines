/***********************************
 chefspec DSL

 Invokes package and stash cookbook

 example usage
 chefspec 'cookbook_dir'
 ************************************/

def call(body) {
  def config = body

  bakeEnv = []
  bakeEnv << "REGION=${config.get('region')}"
  bakeEnv << "PACKER_INSTANCE_TYPE=${config.get('bakeAMIType', 'm4.large')}"
  bakeEnv << "CHEF_RUN_LIST=${config.get('bakeChefRunList')}"
  bakeEnv << "PACKER_DEFAULT_PARAMS=${config.get('bakeParams', 'base_params.json')}"
  bakeEnv << "COOKBOOK_TAR=${config.get('bakeCookbookPackage, 'cookbooks.tar.gz')}"
  bakeEnv << "ROLE=${config.get('role')}"
  bakeEnv << "CLIENT=${config.get('client')}"
  bakeEnv << "CIINABOX_NAME=${config.get('ciinabox', 'ciinabox')}"
  bakeEnv << "AMI_USERS=${config.get('shareAmiWith')}"
  config.amiName = config.get('baseAMI')

  println "bake config:${config}"

  git(url: 'https://github.com/base2Services/ciinabox-bakery.git', branch: 'master')
  withEnv(bakeEnv) {
    lookupAMI config
    sh './configure $CIINABOX_NAME $REGION $AMI_USERS'
    unstash 'cookbook'
    sh '''
    tar xvfz cookbooks.tar.gz
    mkdir -p data_bags
    mkdir -p environments
    mkdir -p encrypted_data_bag_secret
    '''
    sh '''#!/bin/bash
    echo "==================================================="
    echo "Baking AMI: ${ROLE}"
    echo "==================================================="
    printenv
    cat base_params.json
    ls -al
    echo "==================================================="
    echo "completed baking AMI for : ${ROLE}"
    echo "==================================================="
    '''
  }
}
