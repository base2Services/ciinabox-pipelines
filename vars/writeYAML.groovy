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
  writeFile file: config.file, text: mapToYaml(config.map)
}

@NonCPS
def mapToYaml(map) {
  def options = new DumperOptions()
  options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
  options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
  options.setIndent(4)
  def yaml = new Yaml(options)
  return yaml.dump(map)
}
