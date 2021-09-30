/***********************************
 writeYAML DSL

 writes a groovy map to a yaml file

 example usage
 writeYAML
   file: 'myfile.yaml'
   map: ['mykey':'myvalue']
 )
 ************************************/

import org.yaml.snakeyaml.*
import groovy.json.JsonOutput

def call(body) {
  def config = body
  writeFile file: config.file, text: mapToYaml(config.map)
}

@NonCPS
def mapToYaml(map) {
  def json = JsonOutput.toJson(map)
  def options = new DumperOptions()
  options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
  options.setDefaultScalarStyle(DumperOptions.ScalarStyle.LITERAL);
  options.setIndent(4)
  def yaml = new Yaml(options)
  def result = yaml.load(json)
  return yaml.dump(result)
}
