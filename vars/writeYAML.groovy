/***********************************
 bakeAMI DSL

 Bakes an AMI using https://github.com/base2Services/ciinabox-bakery

 example usage
 writeYAML
   map: ['mykey':'myvalue'],
   s3Bucket: env.SOURCE_BUCKET
 )
 ************************************/
@Grab(group='org.yaml', module='snakeyaml', version='1.18')

import org.yaml.snakeyaml.*

def call(body) {
  def config = body
  def options = new DumperOptions()
  options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
  options.setIndent(4)
  def yaml = new Yaml(options)
  writeFile file: config.file text: yaml.dump(config.map)
}
