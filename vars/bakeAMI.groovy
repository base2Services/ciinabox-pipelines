/***********************************
 bakeAMI DSL

 Bakes an AMI using https://github.com/base2Services/ciinabox-bakery

 example usage
 bakeAMI(region: env.REGION,
   role: 'MyServer',
   baseAMI: 'amzn-ami-hvm-2017.03.*',
   bakeChefRunList: 'recipe[mycookbook::default]',
   owner: env.BASE_AMI_OWNER,
   client: env.CLIENT,
   shareAmiWith: env.SHARE_AMI_WITH,
   packerTemplate: env.PACKER_TEMPLATE,
   amiBuildNumber: env.AMI_BUILD_NUMBER,
   sshUsername: env.SSH_USERNAME
 )
 ************************************/

def call(body) {
  def config = body

  def bakeEnv = []
  bakeEnv << "REGION=${config.get('region')}"
  bakeEnv << "PACKER_INSTANCE_TYPE=${config.get('bakeAMIType', 'm4.large')}"
  bakeEnv << "CHEF_RUN_LIST=${config.get('bakeChefRunList')}"
  bakeEnv << "PACKER_TEMPLATE=${config.get('packerTemplate', 'packer/amz_ebs_ami.json')}"
  bakeEnv << "PACKER_DEFAULT_PARAMS=${config.get('packerDefaultParams', 'base_params.json')}"
  bakeEnv << "COOKBOOK_TAR=${config.get('bakeCookbookPackage', 'cookbooks.tar.gz')}"
  bakeEnv << "ROLE=${config.get('role')}"
  bakeEnv << "CLIENT=${config.get('client')}"
  bakeEnv << "CIINABOX_NAME=${config.get('ciinabox', 'ciinabox')}"
  bakeEnv << "AMI_USERS=${config.get('shareAmiWith')}"
  bakeEnv << "BAKE_VOLUME_SIZE=${config.get('bakeVolumeSize', '')}"
  bakeEnv << "AMI_BUILD_NUMBER=${config.get('amiBuildNumber', env.BUILD_NUMBER)}"
  shortCommit = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
  bakeEnv << "GIT_COMMIT=${shortCommit}"
  bakeEnv << "SSH_USERNAME=${config.get('sshUsername', '')}"
  config.amiName = config.get('baseAMI')
  config.amiBranch = config.get('baseAMIBranch')

  // Windows chef env vars
  bakeEnv << "CHEF_PATH=${config.chefPath}"
  bakeEnv << "SOURCE_BUCKET=${config.sourceBucket}"
  bakeEnv << "CB_BUILD_NO=${config.cookbookVersion}"
  bakeEnv << "BUCKET_REGION=${config.bucketRegion}"
  bakeEnv << "LOCAL_COOKBOOKS=${config.get('localCookbooks',true)}"

  def role = config.get('role').toUpperCase()

  node {
    println "bake config:${config}"
    deleteDir()
    git(url: 'https://github.com/base2Services/ciinabox-bakery.git', branch: 'master')
    def sourceAMI = lookupAMI config
    def branchName = env.BRANCH_NAME.replaceAll("/", "-")
    bakeEnv << "SOURCE_AMI=${sourceAMI}"
    bakeEnv << "BRANCH=${branchName}"
    withEnv(bakeEnv) {
      sh './configure $CIINABOX_NAME $REGION $AMI_USERS'
      if(config.get('localCookbooks',true)) {
        unstash 'cookbook'
        sh 'tar xvfz cookbooks.tar.gz'
      } else {
        sh 'mkdir -p cookbooks'
      }
      sh '''
      mkdir -p data_bags
      mkdir -p environments
      mkdir -p encrypted_data_bag_secret
      ls -al
      ls -al cookbooks
      '''
      sh '''#!/bin/bash
      AMI_BUILD_ID=${BRANCH}-${AMI_BUILD_NUMBER}
      echo "==================================================="
      echo "Baking AMI: ${ROLE}"
      exho "AMI Build NO: ${AMI_BUILD_ID}"
      echo "==================================================="
      ./bakery $CLIENT $ROLE $PACKER_TEMPLATE $PACKER_DEFAULT_PARAMS $AMI_BUILD_ID $SOURCE_AMI $AMI_BUILD_ID $GIT_COMMIT $CHEF_RUN_LIST $PACKER_INSTANCE_TYPE $BAKE_VOLUME_SIZE
      if [ $? != 0 ]; then
        echo "ERROR: Packer Baking failed"
        exit 1
      fi
      echo "==================================================="
      echo "completed baking AMI for : ${ROLE}"
      exho "AMI Build NO: ${AMI_BUILD_ID}"
      echo "==================================================="
      '''
      bakedAMI = shellOut('''#!/bin/bash
      BAKED_AMI=$(grep 'ami:' ${ROLE}-ami-*.yml | awk -F ':' {'print $2'})
      echo $BAKED_AMI
      ''')
      env["${role}_BAKED_AMI"]=bakedAMI
      println "${role} baked AMI:" + env["${role}_BAKED_AMI"]
    }
  }
}
