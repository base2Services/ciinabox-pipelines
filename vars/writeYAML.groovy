/***********************************
 writeYAML DSL

 writes a groovy map to a yaml file

 example usage
 writeYAML
   file: 'myfile.yaml'
   map: ['mykey':'myvalue']
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
