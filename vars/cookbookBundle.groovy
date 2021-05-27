/***********************************
cloudformation DSL

performs cloudformation operations

example usage
cookbookBundle(
  cookbook: 'mycookbook',
  source_bucket: 'source.mybucket',
  region: 'ap-southeast-2',
  version: 'overrides-the-default-version'
)

************************************/

def call(body) {
  def config = body
  versionFile = "${config.cookbook}/VERSION"
  def cookbookVersion = config.get('version',"${env.BRANCH_NAME}-${env.BUILD_NUMBER}-${readFile(versionFile).replaceAll("\n","")}")
  env['COOKBOOK_VERSION'] = cookbookVersion

  cookbookPublish(config.cookbook)
  dir(config.cookbook) {
    withEnv(["COOKBOOK=${config.cookbook}"]) {
      sh '''#!/bin/bash
        set -e
        eval "$(/opt/chefdk/bin/chef shell-init bash)"
        export LC_CTYPE=en_US.UTF-8
        echo "==================================================="
        echo "Bundle cookbook: ${COOKBOOK}"
        echo "==================================================="
        mkdir -p ${WORKSPACE}/build/cookbooks
        berks vendor ${WORKSPACE}/build/cookbooks
        [ -d "${WORKSPACE}/environments" ] && cp -R ${WORKSPACE}/environments ${WORKSPACE}/build
        [ -d "${WORKSPACE}/data_bags" ] && cp -R ${WORKSPACE}/data_bags ${WORKSPACE}/build
        [ -d "${WORKSPACE}/encrypted_data_bag_secret" ] && cp -R ${WORKSPACE}/encrypted_data_bag_secret ${WORKSPACE}/build
        [ -r "${WORKSPACE}/appspec.yml" ] && cp ${WORKSPACE}/appspec.yml ${WORKSPACE}/build
        cd ${WORKSPACE}/build
        tar cvfz ${WORKSPACE}/chef-bundle.tar.gz .
        cd ${WORKSPACE}
        echo "==================================================="
        echo "completed bundling for cookbook: ${COOKBOOK}"
        echo "==================================================="
      '''
    }
  }
  stash(name: 'chefbundle', includes: 'chef-bundle.tar.gz')
  if(config.get('source_bucket')) {
    s3(
      path: '.',
      include: '*.tar.gz',
      prefix: "chef/${config.cookbook}/${env.COOKBOOK_VERSION}",
      bucket: config.source_bucket,
      region: config.region
    )
  }
}
