/***********************************
 chefspec DSL

 Invokes package and stash cookbook

 example usage
 chefspec 'cookbook_dir'
 ************************************/

def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config

  def region = config.get('region')
  def baseAMI = config.get('baseAMI')
  def shareAmiWith = config.get('shareAmiWith')
  def ciinabox = config.get('ciinabox', 'ciinabox')

  git(url: 'https://github.com/base2Services/ciinabox-bakery.git', branch: 'master')
  lookupAMI region: region, amiName: baseAMI
  sh 'echo "SOURCE_AMI:${SOURCE_AMI}"'
}
